package com.xiaoyiluck.meoweco.service;

import com.xiaoyiluck.meoweco.objects.Currency;

import java.util.Map;
import java.util.UUID;

public final class CoreEconomyRegressionTest {
    private static final Currency COINS = new Currency("coins", "Coins", "coin", "coins", 100.0D, 2, 0.1D);
    private static final Currency GEMS = new Currency("gems", "Gems", "gem", "gems", 0.0D, 2, 0.0D);
    private static final Currency WHOLE = new Currency("whole", "Whole", "whole", "whole", 0.0D, 0, 0.0D);

    private CoreEconomyRegressionTest() {
    }

    public static void main(String[] args) {
        ensureAccountsCreatesConfiguredCurrenciesWithInitialBalances();
        payAppliesCurrencyTransferTax();
        payRoundsTaxAndDepositToCurrencyPrecision();
        payRejectsInvalidOrUnavailableAmounts();
        exchangeMovesBetweenCurrenciesAtomically();
        exchangeRoundsTargetAmountToTargetCurrencyPrecision();
        adminFrozenFundOperationsRespectAvailableAndFrozenBalances();
        systemTaxWithdrawsOnlyAmountAboveThreshold();
        richTaxRoundsTaxToCurrencyPrecision();
        playerTaxTransfersCollectedAmountToCollector();
        disabledOrMissingCurrencyRulesAreSkipped();
        ruleClampsInvalidValues();
        amountPolicyValidatesInputPrecision();
    }

    private static void payRoundsTaxAndDepositToCurrencyPrecision() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        Currency taxed = new Currency("taxed", "Taxed", "taxed", "taxed", 0.0D, 2, 0.333D);
        EconomyService service = new EconomyService(database);
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        database.createAccount(sender, "taxed", 100.0D);
        database.createAccount(receiver, "taxed", 0.0D);

        EconomyService.PayResult result = service.pay(sender, receiver, taxed, 10.0D);

        assertTrue(result.success());
        assertDouble(3.33D, result.tax());
        assertDouble(6.67D, result.depositAmount());
        assertDouble(90.0D, database.getBalance(sender, "taxed"));
        assertDouble(6.67D, database.getBalance(receiver, "taxed"));
    }

    private static void ensureAccountsCreatesConfiguredCurrenciesWithInitialBalances() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        EconomyService service = new EconomyService(database);
        UUID player = UUID.randomUUID();

        service.ensureAccounts(player, Map.of(COINS.getId(), COINS, GEMS.getId(), GEMS));

        assertDouble(100.0D, database.getBalance(player, "coins"));
        assertDouble(0.0D, database.getBalance(player, "gems"));
    }

    private static void exchangeRoundsTargetAmountToTargetCurrencyPrecision() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        EconomyService service = new EconomyService(database);
        UUID player = UUID.randomUUID();
        database.createAccount(player, "coins", 100.0D);
        database.createAccount(player, "whole", 0.0D);

        EconomyService.ExchangeResult result = service.exchange(player, COINS, WHOLE, 10.0D, 0.333D);

        assertTrue(result.success());
        assertDouble(10.0D, result.fromAmount());
        assertDouble(3.0D, result.toAmount());
        assertDouble(90.0D, database.getBalance(player, "coins"));
        assertDouble(3.0D, database.getBalance(player, "whole"));
    }

    private static void payAppliesCurrencyTransferTax() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        EconomyService service = new EconomyService(database);
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        database.createAccount(sender, "coins", 100.0D);
        database.createAccount(receiver, "coins", 0.0D);

        EconomyService.PayResult result = service.pay(sender, receiver, COINS, 40.0D);

        assertTrue(result.success());
        assertDouble(40.0D, result.withdrawAmount());
        assertDouble(36.0D, result.depositAmount());
        assertDouble(4.0D, result.tax());
        assertDouble(60.0D, database.getBalance(sender, "coins"));
        assertDouble(36.0D, database.getBalance(receiver, "coins"));
    }

    private static void richTaxRoundsTaxToCurrencyPrecision() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        Currency taxed = new Currency("taxed", "Taxed", "taxed", "taxed", 0.0D, 2, 0.0D);
        UUID rich = UUID.randomUUID();
        database.createAccount(rich, "taxed", 150.0D);

        RichTaxEngine.CycleResult result = RichTaxEngine.execute(
                database,
                Map.of("taxed", taxed),
                new RichTaxEngine.Settings(
                        Map.of("taxed", new RichTaxEngine.Rule(true, 100.0D, 0.333D)),
                        RichTaxEngine.DestinationType.SYSTEM,
                        null
                )
        );

        assertTrue(result.hasTaxedAccounts());
        assertDouble(16.65D, result.totalCollected());
        assertDouble(133.35D, database.getBalance(rich, "taxed"));
    }

    private static void payRejectsInvalidOrUnavailableAmounts() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        EconomyService service = new EconomyService(database);
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        database.createAccount(sender, "coins", 20.0D);
        database.createAccount(receiver, "coins", 0.0D);

        assertFalse(service.pay(sender, receiver, COINS, 0.0D).success());
        assertFalse(service.pay(sender, receiver, COINS, Double.NaN).success());
        assertFalse(service.pay(sender, receiver, COINS, 25.0D).success());
        assertDouble(20.0D, database.getBalance(sender, "coins"));
        assertDouble(0.0D, database.getBalance(receiver, "coins"));
    }

    private static void exchangeMovesBetweenCurrenciesAtomically() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        EconomyService service = new EconomyService(database);
        UUID player = UUID.randomUUID();
        database.createAccount(player, "coins", 100.0D);
        database.createAccount(player, "gems", 5.0D);

        EconomyService.ExchangeResult result = service.exchange(player, COINS, GEMS, 20.0D, 0.5D);

        assertTrue(result.success());
        assertDouble(20.0D, result.fromAmount());
        assertDouble(10.0D, result.toAmount());
        assertDouble(80.0D, database.getBalance(player, "coins"));
        assertDouble(15.0D, database.getBalance(player, "gems"));
    }

    private static void adminFrozenFundOperationsRespectAvailableAndFrozenBalances() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        EconomyService service = new EconomyService(database);
        UUID player = UUID.randomUUID();
        database.createAccount(player, "coins", 100.0D);

        assertTrue(service.applyAdminOperation("freeze", player, COINS, 30.0D));
        assertDouble(30.0D, database.getFrozenBalance(player, "coins"));

        assertFalse(service.applyAdminOperation("take", player, COINS, 80.0D));
        assertTrue(service.applyAdminOperation("unfreeze", player, COINS, 10.0D));
        assertDouble(20.0D, database.getFrozenBalance(player, "coins"));

        assertTrue(service.applyAdminOperation("deductfrozen", player, COINS, 15.0D));
        assertDouble(85.0D, database.getBalance(player, "coins"));
        assertDouble(5.0D, database.getFrozenBalance(player, "coins"));
    }

    private static void systemTaxWithdrawsOnlyAmountAboveThreshold() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        UUID rich = UUID.randomUUID();
        UUID normal = UUID.randomUUID();
        database.createAccount(rich, "coins", 150.0D);
        database.createAccount(normal, "coins", 80.0D);

        RichTaxEngine.CycleResult result = RichTaxEngine.execute(
                database,
                Map.of("coins", COINS),
                new RichTaxEngine.Settings(
                        Map.of("coins", new RichTaxEngine.Rule(true, 100.0D, 0.2D)),
                        RichTaxEngine.DestinationType.SYSTEM,
                        null
                )
        );

        assertTrue(result.hasTaxedAccounts());
        assertNull(result.collectorUuid());
        assertDouble(10.0D, result.totalCollected());
        assertInt(1, result.totalTaxedAccounts());
        assertDouble(140.0D, database.getBalance(rich, "coins"));
        assertDouble(80.0D, database.getBalance(normal, "coins"));
        assertDouble(10.0D, result.perCurrency().get("coins").collectedAmount());
    }

    private static void playerTaxTransfersCollectedAmountToCollector() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        UUID rich = UUID.randomUUID();
        UUID collector = UUID.randomUUID();
        database.createAccount(rich, "coins", 200.0D);

        RichTaxEngine.CycleResult result = RichTaxEngine.execute(
                database,
                Map.of("coins", COINS),
                new RichTaxEngine.Settings(
                        Map.of("coins", new RichTaxEngine.Rule(true, 100.0D, 0.25D)),
                        RichTaxEngine.DestinationType.PLAYER,
                        collector
                )
        );

        assertEquals(collector, result.collectorUuid());
        assertDouble(25.0D, result.totalCollected());
        assertDouble(175.0D, database.getBalance(rich, "coins"));
        assertDouble(25.0D, database.getBalance(collector, "coins"));
    }

    private static void disabledOrMissingCurrencyRulesAreSkipped() {
        InMemoryDatabaseManager database = new InMemoryDatabaseManager();
        UUID player = UUID.randomUUID();
        database.createAccount(player, "coins", 1000.0D);
        database.createAccount(player, "gems", 1000.0D);

        RichTaxEngine.CycleResult result = RichTaxEngine.execute(
                database,
                Map.of("coins", COINS, "gems", GEMS),
                new RichTaxEngine.Settings(
                        Map.of("coins", new RichTaxEngine.Rule(false, 100.0D, 1.0D)),
                        RichTaxEngine.DestinationType.SYSTEM,
                        null
                )
        );

        assertFalse(result.hasTaxedAccounts());
        assertDouble(1000.0D, database.getBalance(player, "coins"));
        assertDouble(1000.0D, database.getBalance(player, "gems"));
    }

    private static void ruleClampsInvalidValues() {
        RichTaxEngine.Rule rule = new RichTaxEngine.Rule(true, -10.0D, 5.0D);

        assertDouble(0.0D, rule.threshold());
        assertDouble(1.0D, rule.rate());
    }

    private static void amountPolicyValidatesInputPrecision() {
        assertTrue(MoneyAmountPolicy.isValidPositiveInput(1.23D, COINS));
        assertFalse(MoneyAmountPolicy.isValidPositiveInput(1.234D, COINS));
        assertTrue(MoneyAmountPolicy.isValidNonNegativeInput(0.0D, WHOLE));
        assertFalse(MoneyAmountPolicy.isValidPositiveInput(1.5D, WHOLE));
        assertDouble(1.24D, MoneyAmountPolicy.roundForStorage(1.235D, COINS));
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

    private static void assertNull(Object actual) {
        if (actual != null) {
            throw new AssertionError("Expected null but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
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
}
