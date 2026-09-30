package com.xiaoyiluck.meoweco.database;

import com.xiaoyiluck.meoweco.MeowEco;
import com.zaxxer.hikari.HikariConfig;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MySQLMultiInstanceRegressionTest {
    private static final int CONCURRENCY_ITERATIONS = 25;

    private MySQLMultiInstanceRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        String host = requiredEnvironment("MEOWECO_TEST_MYSQL_HOST");
        int port = Integer.parseInt(requiredEnvironment("MEOWECO_TEST_MYSQL_PORT"));
        String database = requiredEnvironment("MEOWECO_TEST_MYSQL_DATABASE");
        String username = requiredEnvironment("MEOWECO_TEST_MYSQL_USERNAME");
        String password = requiredEnvironment("MEOWECO_TEST_MYSQL_PASSWORD");

        TestMySQLDatabase first = new TestMySQLDatabase("first", host, port, database, username, password);
        TestMySQLDatabase second = new TestMySQLDatabase("second", host, port, database, username, password);
        try {
            first.init();
            second.init();
            committedWritesAreImmediatelyVisible(first, second);
            for (int iteration = 0; iteration < CONCURRENCY_ITERATIONS; iteration++) {
                concurrentWithdrawalsPreventDuplicateSpending(first, second);
                concurrentDepositsDoNotLoseMoney(first, second);
                opposingConcurrentTransfersPreserveTotalBalance(first, second);
            }
        } finally {
            second.close();
            first.close();
        }
    }

    private static void committedWritesAreImmediatelyVisible(
            TestMySQLDatabase first, TestMySQLDatabase second) {
        UUID player = UUID.randomUUID();
        assertTrue(first.createAccount(player, "visibility", 100.0D));
        assertSnapshot(second, player, "visibility", 100.0D, 0.0D);

        assertTrue(first.freeze(player, "visibility", 30.0D));
        assertTrue(first.deposit(player, "visibility", 25.0D));
        assertSnapshot(second, player, "visibility", 125.0D, 30.0D);

        assertTrue(second.withdraw(player, "visibility", 20.0D));
        assertSnapshot(first, player, "visibility", 105.0D, 30.0D);
    }

    private static void concurrentWithdrawalsPreventDuplicateSpending(
            TestMySQLDatabase first, TestMySQLDatabase second) throws Exception {
        UUID player = UUID.randomUUID();
        String currency = uniqueCurrency("withdraw");
        assertTrue(first.createAccount(player, currency, 100.0D));

        ConcurrentResults results = runConcurrently(
                () -> first.withdraw(player, currency, 80.0D),
                () -> second.withdraw(player, currency, 80.0D));

        assertInt(1, results.successes());
        assertSnapshot(first, player, currency, 20.0D, 0.0D);
        assertSnapshot(second, player, currency, 20.0D, 0.0D);
    }

    private static void concurrentDepositsDoNotLoseMoney(
            TestMySQLDatabase first, TestMySQLDatabase second) throws Exception {
        UUID player = UUID.randomUUID();
        String currency = uniqueCurrency("deposit");
        assertTrue(first.createAccount(player, currency, 0.0D));

        ConcurrentResults results = runConcurrently(
                () -> first.deposit(player, currency, 40.0D),
                () -> second.deposit(player, currency, 60.0D));

        assertInt(2, results.successes());
        assertSnapshot(first, player, currency, 100.0D, 0.0D);
        assertSnapshot(second, player, currency, 100.0D, 0.0D);
    }

    private static void opposingConcurrentTransfersPreserveTotalBalance(
            TestMySQLDatabase first, TestMySQLDatabase second) throws Exception {
        UUID left = UUID.randomUUID();
        UUID right = UUID.randomUUID();
        String currency = uniqueCurrency("transfer");
        assertTrue(first.createAccount(left, currency, 100.0D));
        assertTrue(first.createAccount(right, currency, 100.0D));

        ConcurrentResults results = runConcurrently(
                () -> first.transfer(left, right, currency, 30.0D),
                () -> second.transfer(right, left, currency, 40.0D));

        assertInt(2, results.successes());
        assertSnapshot(first, left, currency, 110.0D, 0.0D);
        assertSnapshot(second, right, currency, 90.0D, 0.0D);
        assertDouble(200.0D, first.getTotalBalance(currency));
    }

    private static String uniqueCurrency(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static ConcurrentResults runConcurrently(CheckedBooleanSupplier first,
                                                     CheckedBooleanSupplier second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> firstResult = executor.submit(() -> runWhenReleased(first, ready, start));
            Future<Boolean> secondResult = executor.submit(() -> runWhenReleased(second, ready, start));
            ready.await();
            start.countDown();
            return new ConcurrentResults((firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean runWhenReleased(CheckedBooleanSupplier operation,
                                           CountDownLatch ready,
                                           CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return operation.getAsBoolean();
    }

    private static void assertSnapshot(TestMySQLDatabase database,
                                       UUID player,
                                       String currency,
                                       double expectedBalance,
                                       double expectedFrozen) {
        AccountBalance snapshot = database.findAccountBalance(player, currency).orElseThrow();
        assertDouble(expectedBalance, snapshot.balance());
        assertDouble(expectedFrozen, snapshot.frozenBalance());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertDouble(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001D) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertInt(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    private record ConcurrentResults(int successes) {
    }

    private static final class TestMySQLDatabase extends AbstractSQLDatabase {
        private final String poolName;
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final Logger logger = Logger.getLogger("MeowEco-MySQL-Multi-Instance-Test");

        private TestMySQLDatabase(String instanceName,
                                  String host,
                                  int port,
                                  String database,
                                  String username,
                                  String password) {
            super((MeowEco) null);
            this.poolName = "MeowEco-MySQL-IT-" + instanceName;
            this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            this.username = username;
            this.password = password;
            logger.setLevel(Level.OFF);
        }

        @Override
        protected void configureDataSource(HikariConfig config) {
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
        }

        @Override
        protected void configurePool(HikariConfig config) {
            config.setPoolName(poolName);
            config.setMaximumPoolSize(4);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10_000L);
            config.setValidationTimeout(5_000L);
        }

        @Override
        protected boolean isSQLite() {
            return false;
        }

        @Override
        protected String getDefaultCurrencyId() {
            return "coins";
        }

        @Override
        protected Logger getLogger() {
            return logger;
        }

        @Override
        protected String getOfflinePlayerName(UUID uuid) {
            return null;
        }

        @Override
        protected void debug(String message) {
        }
    }
}
