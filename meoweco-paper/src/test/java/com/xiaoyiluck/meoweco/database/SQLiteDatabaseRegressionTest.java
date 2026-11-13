package com.xiaoyiluck.meoweco.database;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.initial.InitialBalancePolicy;
import com.xiaoyiluck.meoweco.service.GiftFundsPolicy;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.zaxxer.hikari.HikariConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
            duplicateUsernamesRemainDistinctInLeaderboards(database);
            largeBalanceMutationsFailWithoutMoneyLoss(database);
            setBalanceBindsParametersAndWritesAudit(database);
            precisionReportFindsLegacyOverPrecisionRowsWithoutChangingMoney(database);
            auditRecordsCommittedWritesAndContext(database);
            transferAuditUsesOneTransactionId(database);
            migrationImportsNewAndExistingAccountsAtomically(database);
            invalidMigrationDoesNotChangeBalances(database);
            independentInstancesObserveCommittedBalanceSnapshots(database);
            signedIdentityInitialBalancesAreAtomicAndOneTime(database);
            initialGrantRollsBackWithAuditFailure(database);
            initialGrantSurvivesRestartAndConcurrentSessions(database);
            identityPolicyRejectsUnknownAndInvalidAmounts();
            initialFundsRemainSeparateAcrossSpendingAndTransfer(database);
            exchangesPreserveGiftTags(database);
            adminAndFrozenDebitsKeepSourceBalances(database);
            paymentReceiptsRefundOriginalSourcesExactlyOnce(database);
            sourceFailuresRollbackBalancesAndReceipts(database);
            receiptsAndSourcesSurviveConcurrentInstances(database);
            System.out.println("SQLite regression passed, including signed initial grants and fault/concurrency recovery.");
        } finally {
            database.close();
            deleteRecursively(tempDir);
        }
    }

    private static final Currency IDENTITY_COINS = new Currency("identity", "Coins", "coin", "coins", 100, 2, 0);

    private static UUID giftedWallet(TestSQLiteDatabase db) {
        UUID id = UUID.randomUUID();
        db.identityPolicy = InitialBalancePolicy.forCurrency(IDENTITY_COINS, 100, 1000);
        assertTrue(db.createAccount(id, "identity", 100));
        return id;
    }

    private static void assertFunds(TestSQLiteDatabase db, UUID id, String currency, double total, double gift) {
        FundsBalance b = db.findFundsBalance(id, currency).orElseThrow();
        assertDouble(total, b.balance()); assertDouble(gift, b.initialGiftBalance());
        assertTrue(b.initialGiftBalance() >= 0 && b.initialGiftBalance() <= b.balance());
    }

    private static void initialFundsRemainSeparateAcrossSpendingAndTransfer(TestSQLiteDatabase db) {
        db.fundsPolicy = GiftFundsPolicy.DEFAULT;
        UUID a = giftedWallet(db), b = giftedWallet(db);
        assertFunds(db, a, "identity", 100, 100);
        assertTrue(db.settleInitialBalance(a, "identity", true));
        assertFalse(db.settleInitialBalance(a, "identity", true));
        assertFunds(db, a, "identity", 1000, 1000);
        assertFalse(db.transfer(a, b, "identity", 1));
        assertFalse(db.withdrawForPlayerTrade(a, "identity", 1));
        assertTrue(db.deposit(a, "identity", 50));
        assertTrue(db.transfer(a, b, "identity", 30, 29));
        assertFunds(db, a, "identity", 1020, 1000);
        assertFunds(db, b, "identity", 129, 100);
        assertTrue(db.withdrawForPlayerTrade(a, "identity", 20));
        assertFunds(db, a, "identity", 1000, 1000);
        assertTrue(db.withdraw(a, "identity", 60));
        assertFunds(db, a, "identity", 940, 940);
        assertFalse(db.transfer(a, a, "identity", 1));

        UUID c = giftedWallet(db);
        db.fundsPolicy = new GiftFundsPolicy(false, GiftFundsPolicy.SpendingOrder.EARNED_FIRST);
        assertTrue(db.deposit(c, "identity", 60));
        assertTrue(db.withdraw(c, "identity", 40));
        assertFunds(db, c, "identity", 120, 100);
        assertTrue(db.withdraw(c, "identity", 30));
        assertFunds(db, c, "identity", 90, 90);
        db.fundsPolicy = new GiftFundsPolicy(true, GiftFundsPolicy.SpendingOrder.GIFT_FIRST);
        assertTrue(db.transfer(c, b, "identity", 50, 49));
        assertFunds(db, c, "identity", 40, 40);
        assertFunds(db, b, "identity", 178, 149);
        db.fundsPolicy = GiftFundsPolicy.DEFAULT;
        assertFalse(db.transfer(b, c, "identity", 30));
        assertTrue(db.transfer(b, c, "identity", 29));
        assertFunds(db, b, "identity", 149, 149);
    }

    private static void exchangesPreserveGiftTags(TestSQLiteDatabase db) {
        UUID a = giftedWallet(db), b = giftedWallet(db);
        assertTrue(db.createAccount(a, "exchange-points", 0));
        assertTrue(db.createAccount(b, "exchange-points", 0));
        assertTrue(db.exchange(a, "identity", "exchange-points", 100, 10));
        assertFunds(db, a, "identity", 0, 0);
        assertFunds(db, a, "exchange-points", 10, 10);
        assertFalse(db.transfer(a, b, "exchange-points", 1));
        assertTrue(db.exchange(a, "exchange-points", "identity", 10, 100));
        assertFunds(db, a, "identity", 100, 100);
        assertTrue(db.deposit(a, "identity", 100));
        assertTrue(db.exchange(a, "identity", "exchange-points", 150, 15));
        assertFunds(db, a, "identity", 50, 0);
        assertFunds(db, a, "exchange-points", 15, 10);
        assertTrue(db.transfer(a, b, "exchange-points", 5));
        assertFalse(db.transfer(a, b, "exchange-points", 0.01));
        assertFalse(db.exchange(a, "identity", "identity", 1, 2));
    }

    private static void adminAndFrozenDebitsKeepSourceBalances(TestSQLiteDatabase db) {
        UUID a = giftedWallet(db);
        assertTrue(db.freeze(a, "identity", 20));
        assertFalse(db.withdraw(a, "identity", 81));
        assertTrue(db.deductFrozen(a, "identity", 20));
        assertFunds(db, a, "identity", 80, 80);
        assertTrue(db.updateBalance(a, "identity", 50));
        assertFunds(db, a, "identity", 50, 50);
        assertTrue(db.updateBalance(a, "identity", 75));
        assertFunds(db, a, "identity", 75, 50);
        assertTrue(db.importBalances(List.of(new MigrationBalance(a, "Gifted", 30)), "identity", "test", "console").success());
        assertFunds(db, a, "identity", 30, 5);
        UUID legacy = UUID.randomUUID();
        assertTrue(db.createAccount(legacy, "legacy-coins", 123));
        assertFunds(db, legacy, "legacy-coins", 123, 0);
        UUID plain = UUID.randomUUID();
        assertTrue(db.createAccount(plain, "plain-initial", 25));
        assertFunds(db, plain, "plain-initial", 25, 25);
        UUID explicit = UUID.randomUUID();
        assertTrue(db.createAccount(explicit, "plain-initial", 7));
        assertFunds(db, explicit, "plain-initial", 7, 0);
    }

    private static void paymentReceiptsRefundOriginalSourcesExactlyOnce(TestSQLiteDatabase db) {
        UUID a = giftedWallet(db), b = giftedWallet(db);
        assertTrue(db.deposit(a, "identity", 40));
        String ref = "test:" + UUID.randomUUID();
        assertTrue(db.debitWithReceipt(a, "identity", 120, ref, false));
        assertTrue(db.debitWithReceipt(a, "identity", 120, ref, false));
        assertFalse(db.debitWithReceipt(a, "identity", 121, ref, false));
        assertFalse(db.debitWithReceipt(a, "identity", 120, ref, true));
        assertEquals("DEBITED", db.getReceiptState(a, "identity", ref));
        assertFunds(db, a, "identity", 20, 0);
        assertFalse(db.refundReceipt(b, "identity", ref));
        assertTrue(db.refundReceipt(a, "identity", ref));
        assertTrue(db.refundReceipt(a, "identity", ref));
        assertFunds(db, a, "identity", 140, 100);
        assertFalse(db.debitWithReceipt(a, "identity", 120, ref, false));
        assertEquals("REFUNDED", db.getReceiptState(a, "identity", ref));
        assertTrue(db.debitWithReceipt(a, "identity", 40, ref + ":trade", true));
        assertFunds(db, a, "identity", 100, 100);
        assertFalse(db.debitWithReceipt(a, "identity", 1, ref + ":gift-trade", true));
        assertEquals("MISSING", db.getReceiptState(a, "identity", ref + ":gift-trade"));
        assertFalse(db.debitWithReceipt(a, "identity", 1, "bad ref", false));
    }

    private static void sourceFailuresRollbackBalancesAndReceipts(TestSQLiteDatabase db) throws Exception {
        UUID a = giftedWallet(db);
        String ref = "fault:" + UUID.randomUUID();
        try (Connection c = db.getConnection(); var s = c.createStatement()) {
            s.execute("CREATE TRIGGER fail_source_audit BEFORE INSERT ON meoweco_initial_funds_audit "
                    + "BEGIN SELECT RAISE(ABORT, 'source audit failure'); END");
        }
        assertFalse(db.withdraw(a, "identity", 20));
        assertFalse(db.debitWithReceipt(a, "identity", 20, ref, false));
        assertFunds(db, a, "identity", 100, 100);
        assertEquals("MISSING", db.getReceiptState(a, "identity", ref));
        try (Connection c = db.getConnection(); var s = c.createStatement()) { s.execute("DROP TRIGGER fail_source_audit"); }
        assertTrue(db.debitWithReceipt(a, "identity", 20, ref, false));
        try (Connection c = db.getConnection(); var s = c.createStatement()) {
            s.execute("CREATE TRIGGER fail_refund_audit BEFORE INSERT ON meoweco_audit_log "
                    + "WHEN NEW.operation='RECEIPT_REFUND' BEGIN SELECT RAISE(ABORT, 'refund audit failure'); END");
        }
        assertFalse(db.refundReceipt(a, "identity", ref));
        assertFunds(db, a, "identity", 80, 80);
        assertEquals("DEBITED", db.getReceiptState(a, "identity", ref));
        try (Connection c = db.getConnection(); var s = c.createStatement()) { s.execute("DROP TRIGGER fail_refund_audit"); }
        assertTrue(db.refundReceipt(a, "identity", ref));
        assertFunds(db, a, "identity", 100, 100);
    }

    private static void receiptsAndSourcesSurviveConcurrentInstances(TestSQLiteDatabase db) throws Exception {
        UUID a = giftedWallet(db);
        String ref = "concurrent:" + UUID.randomUUID();
        TestSQLiteDatabase other = new TestSQLiteDatabase(db.databasePath); other.init();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> f = executor.submit(() -> { start.await(); return db.debitWithReceipt(a, "identity", 70, ref, false); });
            Future<Boolean> g = executor.submit(() -> { start.await(); return other.debitWithReceipt(a, "identity", 70, ref, false); });
            start.countDown(); assertTrue(f.get()); assertTrue(g.get());
            assertFunds(db, a, "identity", 30, 30);
            db.close(); db.init();
            assertFunds(db, a, "identity", 30, 30);
            assertTrue(other.refundReceipt(a, "identity", ref));
            assertTrue(db.refundReceipt(a, "identity", ref));
            assertFunds(db, a, "identity", 100, 100);
        } finally { executor.shutdownNow(); other.close(); }
    }

    private static void signedIdentityInitialBalancesAreAtomicAndOneTime(TestSQLiteDatabase db) throws Exception {
        UUID legacy = UUID.randomUUID();
        assertTrue(db.createAccount(legacy, "identity", 100));
        db.identityPolicy = InitialBalancePolicy.forCurrency(IDENTITY_COINS, 100, 1000);
        assertFalse(db.settleInitialBalance(legacy, "identity", true));
        assertDouble(100, db.getBalance(legacy, "identity"));

        UUID premium = UUID.randomUUID();
        assertTrue(db.createAccount(premium, "identity", 100));
        assertTrue(db.withdraw(premium, "identity", 40));
        assertTrue(db.deposit(premium, "identity", 7));
        assertTrue(db.freeze(premium, "identity", 10));
        assertTrue(db.settleInitialBalance(premium, "identity", true));
        assertDouble(967, db.getBalance(premium, "identity"));
        assertDouble(10, db.getFrozenBalance(premium, "identity"));
        assertFalse(db.settleInitialBalance(premium, "identity", true));
        assertFalse(db.settleInitialBalance(premium, "identity", false));
        assertDouble(967, db.getBalance(premium, "identity"));
        assertInt(1, (int) db.getAuditHistory(premium, "identity", 100).stream()
                .filter(a -> a.operation().equals("INITIAL_PREMIUM")).count());

        UUID offline = UUID.randomUUID();
        assertTrue(db.createAccount(offline, "identity", 100));
        assertTrue(db.settleInitialBalance(offline, "identity", false));
        assertFalse(db.settleInitialBalance(offline, "identity", true));
        assertDouble(100, db.getBalance(offline, "identity"));

        UUID explicit = UUID.randomUUID();
        assertTrue(db.createAccount(explicit, "identity", 0));
        assertFalse(db.settleInitialBalance(explicit, "identity", true));
        assertDouble(0, db.getBalance(explicit, "identity"));

        UUID imported = UUID.randomUUID();
        assertTrue(db.importBalances(List.<MigrationBalance>of(new MigrationBalance(imported, "Imported", 123)),
                "identity", "test", "test").success());
        assertFalse(db.settleInitialBalance(imported, "identity", true));
        assertDouble(123, db.getBalance(imported, "identity"));

        UUID nearLimit = UUID.randomUUID();
        assertTrue(db.createAccount(nearLimit, "identity", 100));
        assertTrue(db.updateBalance(nearLimit, "identity", 0x1.fffffffffffffp52));
        assertFalse(db.settleInitialBalance(nearLimit, "identity", true));
        assertDouble(0x1.fffffffffffffp52, db.getBalance(nearLimit, "identity"));
        assertTrue(db.withdraw(nearLimit, "identity", 900));
        assertTrue(db.settleInitialBalance(nearLimit, "identity", true));
        assertDouble(0x1.fffffffffffffp52, db.getBalance(nearLimit, "identity"));
    }

    private static void initialGrantRollsBackWithAuditFailure(TestSQLiteDatabase db) throws Exception {
        UUID player = UUID.randomUUID();
        assertTrue(db.createAccount(player, "identity", 100));
        try (Connection c = db.getConnection(); var s = c.createStatement()) {
            s.execute("CREATE TRIGGER fail_initial_audit BEFORE INSERT ON meoweco_audit_log "
                    + "WHEN NEW.operation = 'INITIAL_PREMIUM' BEGIN SELECT RAISE(ABORT, 'test audit failure'); END");
        }
        assertFalse(db.settleInitialBalance(player, "identity", true));
        assertDouble(100, db.getBalance(player, "identity"));
        try (Connection c = db.getConnection(); var s = c.createStatement()) {
            s.execute("DROP TRIGGER fail_initial_audit");
        }
        assertTrue(db.settleInitialBalance(player, "identity", true));
        assertDouble(1000, db.getBalance(player, "identity"));

        UUID failedCreation = UUID.randomUUID();
        try (Connection c = db.getConnection(); var s = c.createStatement()) {
            s.execute("CREATE TRIGGER fail_initial_marker BEFORE INSERT ON meoweco_initial_grants "
                    + "BEGIN SELECT RAISE(ABORT, 'test marker failure'); END");
        }
        assertFalse(db.createAccount(failedCreation, "identity", 100));
        assertFalse(db.hasAccount(failedCreation, "identity"));
        assertTrue(db.getAuditHistory(failedCreation, "identity", 10).isEmpty());
        try (Connection c = db.getConnection(); var s = c.createStatement()) {
            s.execute("DROP TRIGGER fail_initial_marker");
        }
        assertTrue(db.createAccount(failedCreation, "identity", 100));
        assertTrue(db.settleInitialBalance(failedCreation, "identity", true));
        assertDouble(1000, db.getBalance(failedCreation, "identity"));
    }

    private static void initialGrantSurvivesRestartAndConcurrentSessions(TestSQLiteDatabase db) throws Exception {
        UUID player = UUID.randomUUID();
        assertTrue(db.createAccount(player, "identity", 100));
        db.close();
        db.init();
        TestSQLiteDatabase other = new TestSQLiteDatabase(db.databasePath);
        // Old pending rows retain their creation-time amounts even after configuration changes.
        other.identityPolicy = InitialBalancePolicy.forCurrency(IDENTITY_COINS, 50, 2000);
        other.init();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> a = executor.submit(() -> { start.await(); return db.settleInitialBalance(player, "identity", true); });
            Future<Boolean> b = executor.submit(() -> { start.await(); return other.settleInitialBalance(player, "identity", true); });
            start.countDown();
            assertInt(1, (a.get() ? 1 : 0) + (b.get() ? 1 : 0));
            assertDouble(1000, db.getBalance(player, "identity"));
            assertDouble(1000, other.getBalance(player, "identity"));
            assertFalse(other.settleInitialBalance(player, "identity", true));
        } finally {
            executor.shutdownNow();
            other.close();
        }
    }

    private static void identityPolicyRejectsUnknownAndInvalidAmounts() {
        assertEquals(Boolean.TRUE, InitialBalancePolicy.verifiedPremium("true", "true"));
        assertEquals(Boolean.FALSE, InitialBalancePolicy.verifiedPremium("true", "false"));
        assertTrue(InitialBalancePolicy.verifiedPremium("false", "false") == null);
        assertTrue(InitialBalancePolicy.verifiedPremium("%meowidentity_verified%", "true") == null);
        assertTrue(InitialBalancePolicy.verifiedPremium("true", "unknown") == null);
        for (double[] amounts : new double[][]{{-1, 1000}, {100, 99}, {100, Double.NaN}, {100, 1000.001}}) {
            try {
                InitialBalancePolicy.forCurrency(IDENTITY_COINS, amounts[0], amounts[1]);
                throw new AssertionError("Invalid initial amount accepted");
            } catch (IllegalArgumentException expected) { }
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

    private static void duplicateUsernamesRemainDistinctInLeaderboards(TestSQLiteDatabase database) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        database.setOfflineName(first, "Unknown");
        database.setOfflineName(second, "Unknown");
        database.createAccount(first, "duplicate", 100.0D);
        database.createAccount(second, "duplicate", 200.0D);

        Map<String, Double> top = database.getTopAccounts("duplicate", 10);

        assertInt(2, top.size());
        assertDouble(300.0D, top.values().stream().mapToDouble(value -> value.doubleValue()).sum());
    }

    private static void largeBalanceMutationsFailWithoutMoneyLoss(TestSQLiteDatabase database) throws Exception {
        double unsafe = 0x1.0p53;
        UUID large = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        database.insertRawAccount(large, "large", unsafe, 0.0D, "Large", false);
        database.insertRawAccount(sender, "large", 10.0D, 0.0D, "Sender", false);

        assertFalse(database.deposit(large, "large", 1.0D));
        assertDouble(unsafe, database.getBalance(large, "large"));
        assertFalse(database.transfer(sender, large, "large", 1.0D));
        assertDouble(10.0D, database.getBalance(sender, "large"));
        assertDouble(unsafe, database.getBalance(large, "large"));
    }

    private static void setBalanceBindsParametersAndWritesAudit(TestSQLiteDatabase database) {
        UUID player = UUID.randomUUID();
        database.setOfflineName(player, "Setter");
        database.createAccount(player, "set-test", 70.0D);

        assertTrue(database.updateBalance(player, "set-test", 125.0D));
        assertDouble(125.0D, database.getBalance(player, "set-test"));
        AuditEntry entry = database.getAuditHistory(player, "set-test", 1).get(0);
        assertEquals("SET_BALANCE", entry.operation());
        assertDouble(70.0D, entry.balanceBefore());
        assertDouble(125.0D, entry.balanceAfter());
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

        MigrationResult result = database.importBalances(List.<MigrationBalance>of(
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

        MigrationResult result = database.importBalances(List.<MigrationBalance>of(
                new MigrationBalance(existing, "Unchanged", 99.0D),
                new MigrationBalance(UUID.randomUUID(), "Invalid", Double.NaN)
        ), "invalid-migration", "migration.test", "Console");

        assertFalse(result.success());
        assertDouble(12.0D, database.getBalance(existing, "invalid-migration"));
    }

    private static void independentInstancesObserveCommittedBalanceSnapshots(TestSQLiteDatabase first) throws Exception {
        TestSQLiteDatabase second = new TestSQLiteDatabase(first.databasePath);
        try {
            second.init();
            UUID player = UUID.randomUUID();
            first.setOfflineName(player, "Shared");
            assertTrue(first.createAccount(player, "shared", 100.0D));

            AccountBalance initial = second.findAccountBalance(player, "shared").orElseThrow();
            assertDouble(100.0D, initial.balance());
            assertDouble(0.0D, initial.frozenBalance());

            assertTrue(first.freeze(player, "shared", 30.0D));
            assertTrue(first.deposit(player, "shared", 25.0D));
            AccountBalance afterFirstWrite = second.findAccountBalance(player, "shared").orElseThrow();
            assertDouble(125.0D, afterFirstWrite.balance());
            assertDouble(30.0D, afterFirstWrite.frozenBalance());
            assertDouble(95.0D, afterFirstWrite.availableBalance());

            assertTrue(second.withdraw(player, "shared", 20.0D));
            AccountBalance afterSecondWrite = first.findAccountBalance(player, "shared").orElseThrow();
            assertDouble(105.0D, afterSecondWrite.balance());
            assertDouble(30.0D, afterSecondWrite.frozenBalance());

            assertTrue(first.unfreeze(player, "shared", 30.0D));
            assertTrue(first.updateBalance(player, "shared", 100.0D));
            assertConcurrentWithdrawPreventsDuplicateSpending(first, second, player);
        } finally {
            second.close();
        }
    }

    private static void assertConcurrentWithdrawPreventsDuplicateSpending(
            TestSQLiteDatabase first, TestSQLiteDatabase second, UUID player) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> firstWithdraw = executor.submit(() -> withdrawWhenReleased(first, player, ready, start));
            Future<Boolean> secondWithdraw = executor.submit(() -> withdrawWhenReleased(second, player, ready, start));
            ready.await();
            start.countDown();

            int successes = (firstWithdraw.get() ? 1 : 0) + (secondWithdraw.get() ? 1 : 0);
            assertInt(1, successes);
            assertDouble(20.0D, first.findAccountBalance(player, "shared").orElseThrow().balance());
            assertDouble(20.0D, second.findAccountBalance(player, "shared").orElseThrow().balance());
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean withdrawWhenReleased(
            TestSQLiteDatabase database, UUID player, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return database.withdraw(player, "shared", 80.0D);
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
        private InitialBalancePolicy identityPolicy;
        private GiftFundsPolicy fundsPolicy = GiftFundsPolicy.DEFAULT;

        @Override
        protected GiftFundsPolicy getGiftFundsPolicy() { return fundsPolicy; }

        @Override
        protected boolean isConfiguredInitialBalance(String currency, double amount) {
            return currency.equals("plain-initial") && Double.compare(amount, 25.0D) == 0;
        }

        @Override
        protected InitialBalancePolicy getInitialBalancePolicy(String currency) {
            return currency.equals("identity") ? identityPolicy : null;
        }

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
