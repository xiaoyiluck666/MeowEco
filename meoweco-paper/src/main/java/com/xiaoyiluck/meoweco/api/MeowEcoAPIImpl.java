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
