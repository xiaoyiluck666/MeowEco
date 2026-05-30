package com.xiaoyiluck.meoweco.fabric;

import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class FabricEconomyRuntime {
    private final DatabaseManager databaseManager;
    private final Map<String, Currency> currencies;
    private final Map<String, Map<String, Double>> exchangeRates;
    private final boolean exchangeEnabled;
    private String defaultCurrencyId;

    public FabricEconomyRuntime(
            DatabaseManager databaseManager,
            Map<String, Currency> currencies,
            String defaultCurrencyId,
            Map<String, Map<String, Double>> exchangeRates,
            boolean exchangeEnabled
    ) {
        this.databaseManager = databaseManager;
        this.currencies = new LinkedHashMap<>(currencies);
        this.defaultCurrencyId = normalizeCurrencyId(defaultCurrencyId);
        this.exchangeRates = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : exchangeRates.entrySet()) {
            this.exchangeRates.put(normalizeCurrencyId(entry.getKey()), new LinkedHashMap<>(entry.getValue()));
        }
        this.exchangeEnabled = exchangeEnabled;

        if (!this.currencies.containsKey(this.defaultCurrencyId) && !this.currencies.isEmpty()) {
            this.defaultCurrencyId = this.currencies.keySet().iterator().next();
        }
    }

    public void ensurePlayerAccounts(UUID uuid, String username) {
        for (Currency currency : currencies.values()) {
            if (!databaseManager.hasAccount(uuid, currency.getId())) {
                databaseManager.createAccount(uuid, currency.getId(), currency.getInitialBalance());
            }
        }
        databaseManager.updatePlayerName(uuid, username);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }

    public Currency getCurrency(String currencyId) {
        if (currencyId == null || currencyId.isBlank()) {
            return getDefaultCurrency();
        }
        return currencies.get(normalizeCurrencyId(currencyId));
    }

    public Currency getDefaultCurrency() {
        return currencies.get(defaultCurrencyId);
    }

    public boolean isExchangeEnabled() {
        return exchangeEnabled;
    }

    public boolean setExchangeRate(String fromCurrency, String toCurrency, double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0) {
            return false;
        }
        String from = normalizeCurrencyId(fromCurrency);
        String to = normalizeCurrencyId(toCurrency);
        exchangeRates.computeIfAbsent(from, k -> new LinkedHashMap<>()).put(to, rate);
        return true;
    }

    public double getExchangeRate(String fromCurrency, String toCurrency) {
        String from = normalizeCurrencyId(fromCurrency);
        String to = normalizeCurrencyId(toCurrency);
        if (Objects.equals(from, to)) {
            return 1.0;
        }

        Double direct = getDirectRate(from, to);
        if (direct != null) {
            return direct;
        }

        Double inverse = getDirectRate(to, from);
        if (inverse != null && inverse > 0.0) {
            return 1.0 / inverse;
        }

        return -1.0;
    }

    private Double getDirectRate(String from, String to) {
        Map<String, Double> rates = exchangeRates.get(from);
        if (rates == null) {
            return null;
        }
        Double rate = rates.get(to);
        if (rate == null || !Double.isFinite(rate) || rate <= 0.0) {
            return null;
        }
        return rate;
    }

    public String formatShort(double amount, Currency currency) {
        int decimals = Math.max(0, currency.getDecimalPlaces());
        double abs = Math.abs(amount);
        if (abs >= 1_000_000_000) {
            return trim(amount / 1_000_000_000d, 2) + "B";
        }
        if (abs >= 1_000_000) {
            return trim(amount / 1_000_000d, 2) + "M";
        }
        if (abs >= 1_000) {
            return trim(amount / 1_000d, 2) + "K";
        }
        return trim(amount, decimals);
    }

    public String formatFixed(double amount, Currency currency) {
        int decimals = Math.max(0, currency.getDecimalPlaces());
        return trim(amount, decimals);
    }

    private String trim(double value, int decimals) {
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.toPlainString();
    }

    private String normalizeCurrencyId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
