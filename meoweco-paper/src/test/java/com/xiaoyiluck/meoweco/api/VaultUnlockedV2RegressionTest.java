package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.database.AbstractSQLDatabase;
import com.xiaoyiluck.meoweco.database.AuditEntry;
import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.EconomyService;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import com.xiaoyiluck.meoweco.service.TaxPolicy;
import com.zaxxer.hikari.HikariConfig;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault2.economy.MultiEconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.SimpleServicesManager;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("deprecation") // The regression suite verifies Classic/v2 interoperability.
public final class VaultUnlockedV2RegressionTest {
    private static final Currency COINS = new Currency("coins", "Coins", "coin", "coins", 0.0D, 2, 0.0D);
    private static final Currency WHOLE = new Currency("whole", "Whole", "whole", "whole", 0.0D, 0, 0.0D);
    private static final Currency TAXED = new Currency("taxed", "Taxed", "taxed", "taxed", 0.0D, 2, 0.0D,
            List.of(new TaxPolicy.Tier(0.0D, 0.10D), new TaxPolicy.Tier(100.0D, 0.20D)));

    private VaultUnlockedV2RegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("meoweco-vault-v2-test-");
        TestSQLiteDatabase database = new TestSQLiteDatabase(tempDir.resolve("database.db"));
        ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "meoweco-economy-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            database.init();
            EconomyService service = new EconomyService(database);
            TestContext context = new TestContext(database, service);
            MeowEconomyV2 modern = new MeowEconomyV2(context, executor);
            VaultEconomyOperations classic = new VaultEconomyOperations(database, service, COINS, (uuid, currency) -> { });

            providerRegistrationAndLifecycle(modern);
            crossApiOperationsShareOneBalance(database, modern, classic, context);
            lossyAmountsAndBoundaryTransfersRollBack(database, modern);
            progressiveTaxTransfersUseCurrentPolicy(database, modern, classic);
            asyncOperationsLeaveCallerThread(modern, context);
            parallelClassicAndModernOperationsRemainConsistent(database, modern, classic, executor);
        } finally {
            database.close();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            deleteRecursively(tempDir);
        }
    }

    private static void providerRegistrationAndLifecycle(MeowEconomyV2 modern) throws Exception {
        PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, arguments) -> method.getReturnType() == boolean.class ? false : null);
        Server server = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, arguments) -> "getPluginManager".equals(method.getName()) ? pluginManager
                        : method.getReturnType() == boolean.class ? false : null);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        SimpleServicesManager services = new SimpleServicesManager();
        Plugin owner = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> "MeowEco";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "MeowEco test plugin";
                    default -> method.getReturnType() == boolean.class ? false
                            : method.getReturnType().isPrimitive() ? 0 : null;
                });
        MeowEconomy classic = new MeowEconomy(null);

        services.register(net.milkbowl.vault.economy.Economy.class, classic, owner, ServicePriority.Highest);
        services.register(net.milkbowl.vault2.economy.Economy.class, modern, owner, ServicePriority.Highest);

        assertTrue(services.load(net.milkbowl.vault.economy.Economy.class) == classic);
        assertTrue(services.load(net.milkbowl.vault2.economy.Economy.class) == modern);
        services.unregisterAll(owner);
        assertTrue(services.load(net.milkbowl.vault.economy.Economy.class) == null);
        assertTrue(services.load(net.milkbowl.vault2.economy.Economy.class) == null);
    }

    private static void crossApiOperationsShareOneBalance(TestSQLiteDatabase database, MeowEconomyV2 modern,
                                                          VaultEconomyOperations classic, TestContext context) throws Exception {
        UUID player = UUID.randomUUID();
        assertTrue(modern.createAccount(player, "CrossApi", true));
        assertTrue(modern.hasMultiCurrencySupport());
        assertTrue(modern.supportsAsync());
        assertTrue(modern.currencies().containsAll(List.of("coins", "whole", "taxed")));
        assertEquals("CrossApi", modern.getAccountName(player).orElseThrow());
        assertEquals("CrossApi", modern.getUUIDNameMap().get(player));
        assertTrue(modern.renameAccount("test", player, "CrossApiRenamed"));
        assertEquals("CrossApiRenamed", modern.getAccountName(player).orElseThrow());

        EconomyResponse classicDeposit = classic.deposit(player, 100.0D);
        assertTrue(classicDeposit.transactionSuccess());
        assertBigDecimal("100.00", modern.balance("test", player, "world", "coins"));

        net.milkbowl.vault2.economy.EconomyResponse modernWithdraw =
                modern.withdraw("test", player, "world", "coins", new BigDecimal("20.00"));
        assertTrue(modernWithdraw.transactionSuccess());
        assertDouble(80.0D, classic.balance(player));

        context.lastServiceThread.set(null);
        net.milkbowl.vault2.economy.EconomyResponse asyncDeposit = modern.async().orElseThrow()
                .deposit("test", player, "world", "coins", new BigDecimal("5.00")).get(10, TimeUnit.SECONDS);
        assertTrue(asyncDeposit.transactionSuccess());
        assertDouble(85.0D, classic.balance(player));

        net.milkbowl.vault2.economy.EconomyResponse wholeDeposit =
                modern.deposit("test", player, "world", "whole", new BigDecimal("7"));
        assertTrue(wholeDeposit.transactionSuccess());
        assertBigDecimal("7", modern.balance("test", player, "world", "whole"));

        net.milkbowl.vault2.economy.EconomyResponse excessScale =
                modern.deposit("test", player, "world", "coins", new BigDecimal("0.001"));
        assertFalse(excessScale.transactionSuccess());
        assertDouble(85.0D, classic.balance(player));

        UUID receiver = UUID.randomUUID();
        assertTrue(modern.createAccount(receiver, "Receiver", true));
        MultiEconomyResponse transfer = modern.transfer("test", player, receiver, "world", "coins", new BigDecimal("10.00"));
        assertEquals(net.milkbowl.vault2.economy.EconomyResponse.ResponseType.SUCCESS, transfer.type());
        assertBigDecimal("75.00", modern.balance("test", player, "world", "coins"));
        assertBigDecimal("10.00", modern.balance("test", receiver, "world", "coins"));

        AuditEntry latest = database.getAuditHistory(receiver, "coins", 1).get(0);
        assertEquals("vault", latest.source());
        assertEquals("external_plugin", latest.actor());
    }

    private static void lossyAmountsAndBoundaryTransfersRollBack(TestSQLiteDatabase database, MeowEconomyV2 modern) {
        UUID boundary = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        database.createAccount(boundary, "whole", MoneyAmountPolicy.MAX_SAFE_BALANCE);
        database.createAccount(sender, "whole", 15.0D);
        int boundaryAudits = database.getAuditHistory(boundary, "whole", 20).size();
        int senderAudits = database.getAuditHistory(sender, "whole", 20).size();

        net.milkbowl.vault2.economy.EconomyResponse lossy =
                modern.deposit("test", boundary, "world", "whole", new BigDecimal("9007199254740992"));
        assertFalse(lossy.transactionSuccess());
        assertInt(boundaryAudits, database.getAuditHistory(boundary, "whole", 20).size());

        net.milkbowl.vault2.economy.EconomyResponse one =
                modern.deposit("test", boundary, "world", "whole", BigDecimal.ONE);
        assertFalse(one.transactionSuccess());
        assertDouble(MoneyAmountPolicy.MAX_SAFE_BALANCE, database.getBalance(boundary, "whole"));

        MultiEconomyResponse transfer = modern.transfer("test", sender, boundary, "world", "whole", BigDecimal.ONE);
        assertEquals(net.milkbowl.vault2.economy.EconomyResponse.ResponseType.FAILURE, transfer.type());
        assertDouble(15.0D, database.getBalance(sender, "whole"));
        assertDouble(MoneyAmountPolicy.MAX_SAFE_BALANCE, database.getBalance(boundary, "whole"));
        assertInt(boundaryAudits, database.getAuditHistory(boundary, "whole", 20).size());
        assertInt(senderAudits, database.getAuditHistory(sender, "whole", 20).size());
    }

    private static void progressiveTaxTransfersUseCurrentPolicy(TestSQLiteDatabase database,
                                                                 MeowEconomyV2 modern,
                                                                 VaultEconomyOperations classic) {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        assertTrue(modern.createAccount(sender, "TaxSender", true));
        assertTrue(modern.createAccount(receiver, "TaxReceiver", true));
        assertTrue(modern.deposit("test", sender, "world", "taxed", new BigDecimal("200.00")).transactionSuccess());

        MultiEconomyResponse transfer = modern.transfer(
                "test", sender, receiver, "world", "taxed", new BigDecimal("150.00"));

        assertEquals(net.milkbowl.vault2.economy.EconomyResponse.ResponseType.SUCCESS, transfer.type());
        assertBigDecimal("50.00", modern.balance("test", sender, "world", "taxed"));
        assertBigDecimal("130.00", modern.balance("test", receiver, "world", "taxed"));
        assertDouble(0.0D, classic.balance(receiver));
        AuditEntry incoming = database.getAuditHistory(receiver, "taxed", 1).get(0);
        assertEquals("TRANSFER_IN", incoming.operation());
        assertDouble(130.0D, incoming.amount());
    }

    private static void asyncOperationsLeaveCallerThread(MeowEconomyV2 modern, TestContext context) throws Exception {
        UUID player = UUID.randomUUID();
        assertTrue(modern.createAccount(player, "Async", true));
        String caller = Thread.currentThread().getName();
        context.lastServiceThread.set(null);

        net.milkbowl.vault2.economy.EconomyResponse result = modern.async().orElseThrow()
                .deposit("test", player, new BigDecimal("1.00")).get(10, TimeUnit.SECONDS);

        assertTrue(result.transactionSuccess());
        String operationThread = context.lastServiceThread.get();
        assertTrue(operationThread != null && !operationThread.equals(caller));
    }

    private static void parallelClassicAndModernOperationsRemainConsistent(TestSQLiteDatabase database,
                                                                            MeowEconomyV2 modern,
                                                                            VaultEconomyOperations classic,
                                                                            ExecutorService executor) throws Exception {
        UUID player = UUID.randomUUID();
        assertTrue(modern.createAccount(player, "Parallel", true));
        assertTrue(classic.deposit(player, 1000.0D).transactionSuccess());

        CompletableFuture<EconomyResponse> classicDeposit = CompletableFuture.supplyAsync(
                () -> classic.deposit(player, 100.0D), executor);
        CompletableFuture<net.milkbowl.vault2.economy.EconomyResponse> modernWithdraw = modern.async().orElseThrow()
                .withdraw("test", player, new BigDecimal("30.00"));
        CompletableFuture<net.milkbowl.vault2.economy.EconomyResponse> modernDeposit = modern.async().orElseThrow()
                .deposit("test", player, new BigDecimal("20.00"));

        CompletableFuture.allOf(classicDeposit, modernWithdraw, modernDeposit).get(15, TimeUnit.SECONDS);
        assertTrue(classicDeposit.get().transactionSuccess());
        assertTrue(modernWithdraw.get().transactionSuccess());
        assertTrue(modernDeposit.get().transactionSuccess());
        assertDouble(1090.0D, database.getBalance(player, "coins"));
        assertDouble(0.0D, database.getFrozenBalance(player, "coins"));
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static void assertBigDecimal(String expected, BigDecimal actual) {
        if (new BigDecimal(expected).compareTo(actual) != 0) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertDouble(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001D) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertInt(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static final class TestContext implements MeowEconomyV2.Context {
        private final TestSQLiteDatabase database;
        private final EconomyService service;
        private final Map<String, Currency> currencies = Map.of("coins", COINS, "whole", WHOLE, "taxed", TAXED);
        private final AtomicReference<String> lastServiceThread = new AtomicReference<>();

        private TestContext(TestSQLiteDatabase database, EconomyService service) {
            this.database = database;
            this.service = service;
        }

        @Override public boolean isEnabled() { return true; }
        @Override public Map<String, Currency> currencies() { return currencies; }
        @Override public Currency currency(String id) { return id == null ? null : currencies.get(id.toLowerCase(java.util.Locale.ROOT)); }
        @Override public Currency defaultCurrency() { return COINS; }
        @Override public DatabaseManager database() { return database; }
        @Override public EconomyService economyService() {
            lastServiceThread.set(Thread.currentThread().getName());
            return service;
        }
        @Override public void invalidate(UUID uuid, String currencyId) { }
    }

    private static final class TestSQLiteDatabase extends AbstractSQLDatabase {
        private final Path databasePath;
        private final Logger logger = Logger.getLogger("MeowEco-VaultV2-Test");

        private TestSQLiteDatabase(Path databasePath) {
            super((MeowEco) null);
            this.databasePath = databasePath;
            logger.setLevel(Level.OFF);
        }

        @Override protected void configureDataSource(HikariConfig config) {
            config.setJdbcUrl("jdbc:sqlite:" + databasePath.toAbsolutePath()
                    + "?busy_timeout=5000&journal_mode=WAL&synchronous=NORMAL&foreign_keys=ON&temp_store=MEMORY");
            config.setDriverClassName("org.sqlite.JDBC");
        }

        @Override protected void configurePool(HikariConfig config) {
            config.setPoolName("MeowEco-VaultV2-Test");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setIdleTimeout(0);
            config.setMaxLifetime(0);
            config.setConnectionTestQuery("SELECT 1");
        }

        @Override protected boolean isSQLite() { return true; }
        @Override protected String getDefaultCurrencyId() { return "coins"; }
        @Override protected Logger getLogger() { return logger; }
        @Override protected String getOfflinePlayerName(UUID uuid) { return null; }
        @Override protected void debug(String message) { }
    }
}
