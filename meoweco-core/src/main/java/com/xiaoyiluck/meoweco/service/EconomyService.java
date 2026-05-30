package com.xiaoyiluck.meoweco.service;

import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

    public PayResult pay(UUID from, UUID to, Currency currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return PayResult.invalid();
        }

        double taxRate = clamp(currency.getTransferTax(), 0.0, 1.0);
        double tax = amount * taxRate;
        double depositAmount = amount - tax;
        if (!Double.isFinite(depositAmount) || depositAmount < 0) {
            return PayResult.invalid();
        }

        boolean success = tax > 0
                ? databaseManager.transfer(from, to, currency.getId(), amount, depositAmount)
                : databaseManager.transfer(from, to, currency.getId(), amount);
        return new PayResult(success, amount, depositAmount, tax);
    }

    public ExchangeResult exchange(UUID uuid, Currency fromCurrency, Currency toCurrency, double amount, double rate) {
        if (!isPositiveFinite(amount) || !isPositiveFinite(rate)) {
            return ExchangeResult.invalid();
        }

        double resultAmount = amount * rate;
        if (!isPositiveFinite(resultAmount)) {
            return ExchangeResult.invalid();
        }

        boolean success = databaseManager.exchange(uuid, fromCurrency.getId(), toCurrency.getId(), amount, resultAmount);
        return new ExchangeResult(success, amount, resultAmount, rate);
    }

    public boolean applyAdminOperation(String operation, UUID uuid, Currency currency, double amount) {
        String op = operation.toLowerCase(Locale.ROOT);
        return switch (op) {
            case "give" -> isPositiveFinite(amount) && databaseManager.deposit(uuid, currency.getId(), amount);
            case "take" -> isPositiveFinite(amount) && databaseManager.withdraw(uuid, currency.getId(), amount);
            case "set" -> isNonNegativeFinite(amount) && databaseManager.updateBalance(uuid, currency.getId(), amount);
            case "freeze" -> isPositiveFinite(amount) && databaseManager.freeze(uuid, currency.getId(), amount);
            case "unfreeze" -> isPositiveFinite(amount) && databaseManager.unfreeze(uuid, currency.getId(), amount);
            case "deductfrozen" -> isPositiveFinite(amount) && databaseManager.deductFrozen(uuid, currency.getId(), amount);
            default -> false;
        };
    }

    private boolean isPositiveFinite(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }

    private boolean isNonNegativeFinite(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
