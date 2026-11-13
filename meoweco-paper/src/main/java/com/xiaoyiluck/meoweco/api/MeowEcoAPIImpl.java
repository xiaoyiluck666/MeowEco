package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;

import java.util.Collection;
import java.util.Collections;

public class MeowEcoAPIImpl implements MeowEcoAPI {

    private final MeowEco plugin;

    public MeowEcoAPIImpl(MeowEco plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<Currency> getRegisteredCurrencies() {
        return Collections.unmodifiableCollection(plugin.getCurrencies().values());
    }

    @Override
    public Currency getCurrency(String id) {
        return plugin.getCurrency(id);
    }

    private boolean isPositiveFinite(double amount) {
        return Double.isFinite(amount) && amount > 0.0;
    }

    private double normalizePositiveAmount(String currencyId, double amount) {
        Currency currency = resolveCurrency(currencyId);
        if (currency == null || !MoneyAmountPolicy.isValidPositiveInput(amount, currency)) {
            return Double.NaN;
        }
        return MoneyAmountPolicy.roundForStorage(amount, currency);
    }

    private Currency resolveCurrency(String currencyId) {
        return currencyId == null ? null : plugin.getCurrency(currencyId.trim());
    }

    private String canonicalCurrencyId(String currencyId) {
        Currency currency = resolveCurrency(currencyId);
        return currency == null ? null : currency.getId();
    }

    @Override
    public double getBalance(java.util.UUID uuid, String currencyId) {
        String canonical = canonicalCurrencyId(currencyId);
        return canonical == null ? 0.0D : plugin.getDatabaseManager().findBalance(uuid, canonical).orElse(0.0D);
    }

    @Override
    public boolean deposit(java.util.UUID uuid, String currencyId, double amount) {
        double normalizedAmount = normalizePositiveAmount(currencyId, amount);
        if (!isPositiveFinite(normalizedAmount)) {
            return false;
        }
        String canonical = canonicalCurrencyId(currencyId);
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api", "external_plugin")) {
            success = plugin.getDatabaseManager().deposit(uuid, canonical, normalizedAmount);
        }
        if (success) {
            plugin.invalidateVaultEconomyCache(uuid, canonical);
        }
        return success;
    }

    @Override
    public boolean withdraw(java.util.UUID uuid, String currencyId, double amount) {
        double normalizedAmount = normalizePositiveAmount(currencyId, amount);
        if (!isPositiveFinite(normalizedAmount)) {
            return false;
        }
        String canonical = canonicalCurrencyId(currencyId);
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api", "external_plugin")) {
            success = plugin.getDatabaseManager().withdraw(uuid, canonical, normalizedAmount);
        }
        if (success) {
            plugin.invalidateVaultEconomyCache(uuid, canonical);
        }
        return success;
    }

    @Override
    public double getFrozenBalance(java.util.UUID uuid, String currencyId) {
        String canonical = canonicalCurrencyId(currencyId);
        return canonical == null ? 0.0D : plugin.getDatabaseManager().findFrozenBalance(uuid, canonical).orElse(0.0D);
    }

    @Override
    public double getInitialGiftBalance(java.util.UUID uuid, String currencyId) {
        String canonical = canonicalCurrencyId(currencyId);
        return canonical == null ? 0.0D : plugin.getDatabaseManager().findFundsBalance(uuid, canonical)
                .map(com.xiaoyiluck.meoweco.database.FundsBalance::initialGiftBalance).orElse(0.0D);
    }

    @Override
    public double getTransferableBalance(java.util.UUID uuid, String currencyId) {
        String canonical = canonicalCurrencyId(currencyId);
        if (canonical == null) return 0.0D;
        boolean allow = plugin.getGiftFundsPolicy().allowPlayerTransfer();
        return plugin.getDatabaseManager().findFundsBalance(uuid, canonical)
                .map(f -> allow ? f.available() : f.earnedAvailable()).orElse(0.0D);
    }

    @Override
    public boolean withdrawForPlayerTrade(java.util.UUID uuid, String currencyId, double amount) {
        double normalized = normalizePositiveAmount(currencyId, amount);
        if (!isPositiveFinite(normalized)) return false;
        String canonical = canonicalCurrencyId(currencyId);
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api.player_trade", "external_plugin")) {
            success = plugin.getDatabaseManager().withdrawForPlayerTrade(uuid, canonical, normalized);
        }
        if (success) plugin.invalidateVaultEconomyCache(uuid, canonical);
        return success;
    }

    @Override
    public boolean debitWithReceipt(java.util.UUID uuid, String currencyId, double amount, String reference, boolean playerTrade) {
        double normalized = normalizePositiveAmount(currencyId, amount);
        if (!isPositiveFinite(normalized)) return false;
        String canonical = canonicalCurrencyId(currencyId);
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api.receipt", "external_plugin")) {
            success = plugin.getDatabaseManager().debitWithReceipt(uuid, canonical, normalized, reference, playerTrade);
        }
        if (success) plugin.invalidateVaultEconomyCache(uuid, canonical);
        return success;
    }

    @Override
    public boolean refundReceipt(java.util.UUID uuid, String currencyId, String reference) {
        String canonical = canonicalCurrencyId(currencyId);
        if (canonical == null) return false;
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api.receipt_refund", "external_plugin")) {
            success = plugin.getDatabaseManager().refundReceipt(uuid, canonical, reference);
        }
        if (success) plugin.invalidateVaultEconomyCache(uuid, canonical);
        return success;
    }

    @Override
    public String getReceiptState(java.util.UUID uuid, String currencyId, String reference) {
        String canonical = canonicalCurrencyId(currencyId);
        return canonical == null ? "UNAVAILABLE" : plugin.getDatabaseManager().getReceiptState(uuid, canonical, reference);
    }

    @Override
    public double getAvailableBalance(java.util.UUID uuid, String currencyId) {
        String canonical = canonicalCurrencyId(currencyId);
        if (canonical == null) return 0.0D;
        double total = plugin.getDatabaseManager().findBalance(uuid, canonical).orElse(0.0D);
        double frozen = plugin.getDatabaseManager().findFrozenBalance(uuid, canonical).orElse(0.0D);
        return Math.max(0.0D, total - frozen);
    }

    @Override
    public boolean freeze(java.util.UUID uuid, String currencyId, double amount) {
        double normalizedAmount = normalizePositiveAmount(currencyId, amount);
        if (!isPositiveFinite(normalizedAmount)) {
            return false;
        }
        String canonical = canonicalCurrencyId(currencyId);
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api", "external_plugin")) {
            success = plugin.getDatabaseManager().freeze(uuid, canonical, normalizedAmount);
        }
        if (success) {
            plugin.invalidateVaultEconomyCache(uuid, canonical);
        }
        return success;
    }

    @Override
    public boolean unfreeze(java.util.UUID uuid, String currencyId, double amount) {
        double normalizedAmount = normalizePositiveAmount(currencyId, amount);
        if (!isPositiveFinite(normalizedAmount)) {
            return false;
        }
        String canonical = canonicalCurrencyId(currencyId);
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api", "external_plugin")) {
            success = plugin.getDatabaseManager().unfreeze(uuid, canonical, normalizedAmount);
        }
        if (success) {
            plugin.invalidateVaultEconomyCache(uuid, canonical);
        }
        return success;
    }

    @Override
    public boolean deductFrozen(java.util.UUID uuid, String currencyId, double amount) {
        double normalizedAmount = normalizePositiveAmount(currencyId, amount);
        if (!isPositiveFinite(normalizedAmount)) {
            return false;
        }
        String canonical = canonicalCurrencyId(currencyId);
        boolean success;
        try (var _ = plugin.getDatabaseManager().openAuditScope("meoweco_api", "external_plugin")) {
            success = plugin.getDatabaseManager().deductFrozen(uuid, canonical, normalizedAmount);
        }
        if (success) {
            plugin.invalidateVaultEconomyCache(uuid, canonical);
        }
        return success;
    }
}
