package com.xiaoyiluck.meoweco.service;

import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.List;

public class EconomyService {
    private final DatabaseManager databaseManager;

    public EconomyService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void ensureAccount(UUID uuid, Currency currency) {
        if (!databaseManager.hasAccount(uuid, currency.getId())) {
            databaseManager.createAccount(uuid, currency.getId(), currency.getInitialBalance());
        }
    }

    public void ensureAccounts(UUID uuid, Map<String, Currency> currencies) {
        for (Currency currency : currencies.values()) {
            ensureAccount(uuid, currency);
        }
    }

    public boolean createAccount(UUID uuid, String name, Map<String, Currency> currencies) {
        boolean created = false;
        boolean success = true;
        for (Currency currency : currencies.values()) {
            if (!databaseManager.hasAccount(uuid, currency.getId())) {
                success &= databaseManager.createAccount(uuid, currency.getId(), currency.getInitialBalance());
                created = true;
            }
        }
        if (success && name != null && !name.isBlank()) {
            databaseManager.updatePlayerName(uuid, name);
        }
        return created && success;
    }

    public BalanceResult getBalance(UUID uuid, Currency currency) {
        if (!databaseManager.hasAccount(uuid, currency.getId())) {
            return BalanceResult.missing();
        }
        double balance = databaseManager.findBalance(uuid, currency.getId()).orElse(0.0D);
        double frozen = databaseManager.findFrozenBalance(uuid, currency.getId()).orElse(0.0D);
        return new BalanceResult(true, balance, frozen);
    }

    public TopResult getTopPage(Currency currency, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int limit = safePage * safePageSize;

        Map<String, Double> topAccounts = databaseManager.getTopAccounts(currency.getId(), limit);
        if (topAccounts.isEmpty()) {
            return new TopResult(safePage, safePageSize, List.of(), 0);
        }

        List<TopEntry> entries = new ArrayList<>();
        int start = (safePage - 1) * safePageSize;
        int index = 0;
        int rank = start + 1;
        for (Map.Entry<String, Double> entry : topAccounts.entrySet()) {
            if (index++ < start) {
                continue;
            }
            if (entries.size() >= safePageSize) {
                break;
            }
            entries.add(new TopEntry(rank++, entry.getKey(), entry.getValue()));
        }

        return new TopResult(safePage, safePageSize, entries, topAccounts.size());
    }

    public double getTotalBalance(Currency currency) {
        return databaseManager.getTotalBalance(currency.getId());
    }

    public boolean canDeposit(UUID uuid, Currency currency, double amount) {
        if (!MoneyAmountPolicy.isValidPositiveInput(amount, currency)) {
            return false;
        }
        BalanceResult current = getBalance(uuid, currency);
        return current.exists() && canStoreResult(current.balance(), amount, currency);
    }

    public boolean deposit(UUID uuid, Currency currency, double amount) {
        return canDeposit(uuid, currency, amount)
                && databaseManager.deposit(uuid, currency.getId(), MoneyAmountPolicy.roundForStorage(amount, currency));
    }

    public boolean canWithdraw(UUID uuid, Currency currency, double amount) {
        if (!MoneyAmountPolicy.isValidPositiveInput(amount, currency)) {
            return false;
        }
        BalanceResult current = getBalance(uuid, currency);
        double normalized = MoneyAmountPolicy.roundForStorage(amount, currency);
        return current.exists()
                && current.balance() - current.frozen() >= normalized
                && canStoreResult(current.balance(), -normalized, currency);
    }

    public boolean withdraw(UUID uuid, Currency currency, double amount) {
        return canWithdraw(uuid, currency, amount)
                && databaseManager.withdraw(uuid, currency.getId(), MoneyAmountPolicy.roundForStorage(amount, currency));
    }

    public boolean setBalance(UUID uuid, Currency currency, double amount) {
        if (!MoneyAmountPolicy.isValidNonNegativeInput(amount, currency)) {
            return false;
        }
        BalanceResult current = getBalance(uuid, currency);
        double normalized = MoneyAmountPolicy.roundForStorage(amount, currency);
        return current.exists() && current.frozen() <= normalized
                && databaseManager.updateBalance(uuid, currency.getId(), normalized);
    }

    private boolean canStoreResult(double currentBalance, double delta, Currency currency) {
        if (!Double.isFinite(currentBalance) || !Double.isFinite(delta)) {
            return false;
        }
        int scale = MoneyAmountPolicy.decimalPlaces(currency);
        BigDecimal current = BigDecimal.valueOf(currentBalance).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal change = BigDecimal.valueOf(delta).setScale(scale, RoundingMode.HALF_UP);
        return MoneyAmountPolicy.toExactDouble(current.add(change), currency).isPresent();
    }

    public PayResult pay(UUID from, UUID to, Currency currency, double amount) {
        if (!MoneyAmountPolicy.isPositiveFinite(amount)) {
            return PayResult.invalid();
        }
        double withdrawAmount = MoneyAmountPolicy.roundForStorage(amount, currency);
        if (!MoneyAmountPolicy.isPositiveFinite(withdrawAmount)) {
            return PayResult.invalid();
        }

        double tax = MoneyAmountPolicy.roundForStorage(TaxPolicy.calculate(withdrawAmount, currency.getTransferTaxTiers()), currency);
        double depositAmount = MoneyAmountPolicy.roundForStorage(withdrawAmount - tax, currency);
        if (!Double.isFinite(depositAmount) || depositAmount < 0) {
            return PayResult.invalid();
        }

        boolean success = tax > 0
                ? databaseManager.transfer(from, to, currency.getId(), withdrawAmount, depositAmount)
                : databaseManager.transfer(from, to, currency.getId(), withdrawAmount);
        return new PayResult(success, withdrawAmount, depositAmount, tax);
    }

    public ExchangeResult exchange(UUID uuid, Currency fromCurrency, Currency toCurrency, double amount, double rate) {
        if (!MoneyAmountPolicy.isPositiveFinite(amount) || !MoneyAmountPolicy.isPositiveFinite(rate)) {
            return ExchangeResult.invalid();
        }

        double withdrawAmount = MoneyAmountPolicy.roundForStorage(amount, fromCurrency);
        double resultAmount = MoneyAmountPolicy.roundForStorage(withdrawAmount * rate, toCurrency);
        if (!MoneyAmountPolicy.isPositiveFinite(withdrawAmount) || !MoneyAmountPolicy.isPositiveFinite(resultAmount)) {
            return ExchangeResult.invalid();
        }

        boolean success = databaseManager.exchange(uuid, fromCurrency.getId(), toCurrency.getId(), withdrawAmount, resultAmount);
        return new ExchangeResult(success, withdrawAmount, resultAmount, rate);
    }

    public boolean applyAdminOperation(String operation, UUID uuid, Currency currency, double amount) {
        String op = operation.toLowerCase(Locale.ROOT);
        double normalizedAmount = MoneyAmountPolicy.roundForStorage(amount, currency);
        return switch (op) {
            case "give" -> MoneyAmountPolicy.isPositiveFinite(normalizedAmount) && databaseManager.deposit(uuid, currency.getId(), normalizedAmount);
            case "take" -> MoneyAmountPolicy.isPositiveFinite(normalizedAmount) && databaseManager.withdraw(uuid, currency.getId(), normalizedAmount);
            case "set" -> MoneyAmountPolicy.isNonNegativeFinite(normalizedAmount) && databaseManager.updateBalance(uuid, currency.getId(), normalizedAmount);
            case "freeze" -> MoneyAmountPolicy.isPositiveFinite(normalizedAmount) && databaseManager.freeze(uuid, currency.getId(), normalizedAmount);
            case "unfreeze" -> MoneyAmountPolicy.isPositiveFinite(normalizedAmount) && databaseManager.unfreeze(uuid, currency.getId(), normalizedAmount);
            case "deductfrozen" -> MoneyAmountPolicy.isPositiveFinite(normalizedAmount) && databaseManager.deductFrozen(uuid, currency.getId(), normalizedAmount);
            default -> false;
        };
    }

    public record BalanceResult(boolean exists, double balance, double frozen) {
        public static BalanceResult missing() {
            return new BalanceResult(false, 0, 0);
        }
    }

    public record TopEntry(int rank, String playerName, double balance) {
    }

    public record TopResult(int page, int pageSize, List<TopEntry> entries, int fetchedCount) {
    }

    public record PayResult(boolean success, double withdrawAmount, double depositAmount, double tax) {
        public static PayResult invalid() {
            return new PayResult(false, 0, 0, 0);
        }
    }

    public record ExchangeResult(boolean success, double fromAmount, double toAmount, double rate) {
        public static ExchangeResult invalid() {
            return new ExchangeResult(false, 0, 0, 0);
        }
    }
}
