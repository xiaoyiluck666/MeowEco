package com.xiaoyiluck.meoweco.database;

import com.xiaoyiluck.meoweco.MeowEco;
import com.zaxxer.hikari.HikariConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SQLiteDatabaseRegressionTest {
    private SQLiteDatabaseRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("meoweco-sqlite-test-");
        TestSQLiteDatabase database = new TestSQLiteDatabase(tempDir.resolve("database.db"));
        try {
            database.init();
            createsAccountsAndPreservesUsernameAcrossCurrencies(database);
            transferRollsBackWhenReceiverAccountIsMissing(database);
            exchangeRollsBackWhenTargetCurrencyAccountIsMissing(database);
            frozenFundsLimitWithdrawalsAndTaxableBalances(database);
            hiddenAndTaxAccountsAreExcludedFromLeaderboards(database);
            precisionReportFindsLegacyOverPrecisionRowsWithoutChangingMoney(database);
            auditRecordsCommittedWritesAndContext(database);
            transferAuditUsesOneTransactionId(database);
            migrationImportsNewAndExistingAccountsAtomically(database);
            invalidMigrationDoesNotChangeBalances(database);
        } finally {
            database.close();
            deleteRecursively(tempDir);
        }
    }

    private static void createsAccountsAndPreservesUsernameAcrossCurrencies(TestSQLiteDatabase database) {
        UUID player = UUID.randomUUID();
        database.setOfflineName(player, "Alice");

        assertTrue(database.createAccount(player, "coins", 100.0D));
        assertTrue(database.createAccount(player, "gems", 5.0D));

        assertTrue(database.hasAccount(player, "coins"));
        assertTrue(database.hasAccount(player, "gems"));
        assertDouble(100.0D, database.getBalance(player, "coins"));
        assertDouble(5.0D, database.getBalance(player, "gems"));
        assertEquals(player, database.findUuidByUsername("Alice").orElseThrow());
    }

    private static void transferRollsBackWhenReceiverAccountIsMissing(TestSQLiteDatabase database) {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        database.setOfflineName(sender, "Sender");
        database.createAccount(sender, "coins", 100.0D);

        assertFalse(database.transfer(sender, receiver, "coins", 30.0D));
        assertDouble(100.0D, database.getBalance(sender, "coins"));
        assertDouble(0.0D, database.getBalance(receiver, "coins"));
    }

    private static void exchangeRollsBackWhenTargetCurrencyAccountIsMissing(TestSQLiteDatabase database) {
        UUID player = UUID.randomUUID();
        database.setOfflineName(player, "Exchanger");
        database.createAccount(player, "coins", 100.0D);

        assertFalse(database.exchange(player, "coins", "gems", 20.0D, 10.0D));
        assertDouble(100.0D, database.getBalance(player, "coins"));
        assertDouble(0.0D, database.getBalance(player, "gems"));

        assertTrue(database.createAccount(player, "gems", 0.0D));
        assertTrue(database.exchange(player, "coins", "gems", 20.0D, 10.0D));
        assertDouble(80.0D, database.getBalance(player, "coins"));
        assertDouble(10.0D, database.getBalance(player, "gems"));
    }

    private static void frozenFundsLimitWithdrawalsAndTaxableBalances(TestSQLiteDatabase database) {
        UUID player = UUID.randomUUID();
        database.setOfflineName(player, "Frozen");
        database.createAccount(player, "coins", 100.0D);

        assertTrue(database.freeze(player, "coins", 70.0D));
        assertFalse(database.withdraw(player, "coins", 40.0D));
        assertTrue(database.withdraw(player, "coins", 30.0D));
        assertDouble(70.0D, database.getBalance(player, "coins"));
        assertDouble(70.0D, database.getFrozenBalance(player, "coins"));
        assertTrue(database.unfreeze(player, "coins", 20.0D));
        assertTrue(database.deductFrozen(player, "coins", 10.0D));
        assertDouble(60.0D, database.getBalance(player, "coins"));
        assertDouble(40.0D, database.getFrozenBalance(player, "coins"));

        Map<UUID, Double> taxable = database.getAccountsAboveBalance("coins", 30.0D);
        assertFalse(taxable.containsKey(player));
    }

    private static void hiddenAndTaxAccountsAreExcludedFromLeaderboards(TestSQLiteDatabase database) {
        String currency = "ranking";
        UUID visible = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        UUID tax = UUID.randomUUID();
        database.setOfflineName(visible, "Visible");
        database.setOfflineName(hidden, "Hidden");
        database.setOfflineName(tax, "tax");
        database.createAccount(visible, currency, 100.0D);
        database.createAccount(hidden, currency, 500.0D);
        database.createAccount(tax, currency, 1000.0D);
        database.setHidden(hidden, true);

        Map<String, Double> top = database.getTopAccounts(currency, 10);

        assertInt(1, top.size());
        assertDouble(100.0D, top.get("Visible"));
        assertDouble(100.0D, database.getTotalBalance(currency));
    }

    private static void precisionReportFindsLegacyOverPrecisionRowsWithoutChangingMoney(TestSQLiteDatabase database) throws Exception {
        UUID legacy = UUID.randomUUID();
        database.insertRawAccount(legacy, "legacy", 10.1234D, 1.234D, "Legacy", false);
        database.insertRawAccount(UUID.randomUUID(), "legacy", 10.12D, 1.23D, "Clean", false);

        PrecisionReport report = database.reportPrecisionIssues(Map.of("legacy", 2));

        assertTrue(report.hasIssues());
        assertInt(1, report.affectedAccounts());
        assertInt(1, report.affectedBalances());
        assertInt(1, report.affectedFrozenBalances());
        assertInt(1, report.currencies().size());
        assertDouble(10.1234D, database.getBalance(legacy, "legacy"));
        assertDouble(1.234D, database.getFrozenBalance(legacy, "legacy"));
    }

    private static void auditRecordsCommittedWritesAndContext(TestSQLiteDatabase database) {
        UUID player = UUID.randomUUID();
        database.setOfflineName(player, "Audited");
        database.createAccount(player, "audit", 10.0D);

        try (AuditScope _ = database.openAuditScope("command.eco", "Console")) {
            assertTrue(database.deposit(player, "audit", 5.0D));
            assertFalse(database.withdraw(player, "audit", 100.0D));
        }

        List<AuditEntry> entries = database.getAuditHistory(player, "audit", 10);
        assertInt(2, entries.size());
        AuditEntry deposit = entries.get(0);
        assertEquals("DEPOSIT", deposit.operation());
        assertEquals("command.eco", deposit.source());
        assertEquals("Console", deposit.actor());
        assertDouble(10.0D, deposit.balanceBefore());
        assertDouble(15.0D, deposit.balanceAfter());
    }

    private static void transferAuditUsesOneTransactionId(TestSQLiteDatabase database) {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        database.setOfflineName(sender, "AuditSender");
        database.setOfflineName(receiver, "AuditReceiver");
        database.createAccount(sender, "transfer-audit", 50.0D);
        database.createAccount(receiver, "transfer-audit", 0.0D);

        assertTrue(database.transfer(sender, receiver, "transfer-audit", 20.0D, 18.0D));

        AuditEntry outgoing = database.getAuditHistory(sender, "transfer-audit", 1).get(0);
        AuditEntry incoming = database.getAuditHistory(receiver, "transfer-audit", 1).get(0);
        assertEquals("TRANSFER_OUT", outgoing.operation());
        assertEquals("TRANSFER_IN", incoming.operation());
        assertEquals(outgoing.transactionId(), incoming.transactionId());
        assertDouble(-20.0D, outgoing.amount());
        assertDouble(18.0D, incoming.amount());
    }

    private static void migrationImportsNewAndExistingAccountsAtomically(TestSQLiteDatabase database) {
        UUID existing = UUID.randomUUID();
        UUID added = UUID.randomUUID();
        database.setOfflineName(existing, "Existing");
        database.createAccount(existing, "migrated", 5.0D);

        MigrationResult result = database.importBalances(List.of(
                new MigrationBalance(existing, "Existing", 25.5D),
                new MigrationBalance(added, "Added", 70.0D)
        ), "migrated", "migration.test", "Console");

        assertTrue(result.success());
        assertInt(2, result.importedAccounts());
        assertInt(1, result.createdAccounts());
        assertInt(1, result.updatedAccounts());
        assertDouble(5.0D, result.previousTotal());
        assertDouble(95.5D, result.importedTotal());
        assertDouble(25.5D, database.getBalance(existing, "migrated"));
        assertDouble(70.0D, database.getBalance(added, "migrated"));
        assertEquals(result.transactionId(), database.getAuditHistory(existing, "migrated", 1).get(0).transactionId());
        assertEquals(result.transactionId(), database.getAuditHistory(added, "migrated", 1).get(0).transactionId());
    }

    private static void invalidMigrationDoesNotChangeBalances(TestSQLiteDatabase database) {
        UUID existing = UUID.randomUUID();
        database.setOfflineName(existing, "Unchanged");
        database.createAccount(existing, "invalid-migration", 12.0D);

        MigrationResult result = database.importBalances(List.of(
                new MigrationBalance(existing, "Unchanged", 99.0D),
                new MigrationBalance(UUID.randomUUID(), "Invalid", Double.NaN)
        ), "invalid-migration", "migration.test", "Console");

        assertFalse(result.success());
        assertDouble(12.0D, database.getBalance(existing, "invalid-migration"));
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void assertTrue(boolean actual) {
        if (!actual) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertFalse(boolean actual) {
        if (actual) {
            throw new AssertionError("Expected false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertDouble(double expected, Double actual) {
        if (actual == null) {
            throw new AssertionError("Expected " + expected + " but was null");
        }
        assertDouble(expected, actual.doubleValue());
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

    private static final class TestSQLiteDatabase extends AbstractSQLDatabase {
        private final Path databasePath;
        private final Map<UUID, String> names = new java.util.HashMap<>();
        private final Logger logger = Logger.getLogger("MeowEco-SQLite-Test");

        private TestSQLiteDatabase(Path databasePath) {
            super((MeowEco) null);
            this.databasePath = databasePath;
            this.logger.setLevel(Level.OFF);
        }

        private void setOfflineName(UUID uuid, String name) {
            names.put(uuid, name);
        }

        private void insertRawAccount(UUID uuid, String currency, double balance, double frozenBalance, String username, boolean hidden) throws Exception {
            String sql = "INSERT INTO meoweco_accounts (uuid, currency, balance, username, hidden, frozen_balance) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ps.setDouble(3, balance);
                ps.setString(4, username);
                ps.setInt(5, hidden ? 1 : 0);
                ps.setDouble(6, frozenBalance);
                ps.executeUpdate();
            }
        }

        @Override
        protected void configureDataSource(HikariConfig config) {
            config.setJdbcUrl("jdbc:sqlite:" + databasePath.toAbsolutePath()
                    + "?busy_timeout=5000"
                    + "&journal_mode=WAL"
                    + "&synchronous=NORMAL"
                    + "&foreign_keys=ON"
                    + "&temp_store=MEMORY");
            config.setDriverClassName("org.sqlite.JDBC");
        }

        @Override
        protected void configurePool(HikariConfig config) {
            config.setPoolName("MeowEco-SQLite-Test");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setIdleTimeout(0);
            config.setMaxLifetime(0);
            config.setConnectionTestQuery("SELECT 1");
        }

        @Override
        protected boolean isSQLite() {
            return true;
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
            return names.get(uuid);
        }

        @Override
        protected void debug(String message) {
        }
    }
}
