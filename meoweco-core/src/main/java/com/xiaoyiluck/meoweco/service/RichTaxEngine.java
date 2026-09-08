package com.xiaoyiluck.meoweco.service;

import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RichTaxEngine {
    private RichTaxEngine() {
    }

    public static CycleResult execute(DatabaseManager databaseManager, Map<String, Currency> currencies, Settings settings) {
        UUID collectorUuid = settings.destinationType() == DestinationType.PLAYER ? settings.destinationPlayerUuid() : null;

        double totalCollected = 0.0D;
        int totalTaxedAccounts = 0;
        Map<String, CurrencyCycleResult> perCurrency = new LinkedHashMap<>();

        for (Currency currency : currencies.values()) {
            Rule rule = settings.ruleFor(currency.getId());
            if (rule == null || !rule.enabled() || rule.rate() <= 0.0D) {
                continue;
            }

            String currencyId = currency.getId();
            if (collectorUuid != null && !databaseManager.hasAccount(collectorUuid, currencyId)) {
                databaseManager.createAccount(collectorUuid, currencyId, 0.0D);
            }

            Map<UUID, Double> taxableAccounts = databaseManager.getAccountsAboveBalance(currencyId, rule.threshold());
            if (taxableAccounts.isEmpty()) {
                continue;
            }

            double collectedForCurrency = 0.0D;
            int taxedAccountsForCurrency = 0;

            for (Map.Entry<UUID, Double> entry : taxableAccounts.entrySet()) {
                if (collectorUuid != null && collectorUuid.equals(entry.getKey())) {
                    continue;
                }
                double taxableAmount = entry.getValue() - rule.threshold();
                if (taxableAmount <= 0.0D) {
                    continue;
                }

                double taxAmount = MoneyAmountPolicy.roundForStorage(taxableAmount * rule.rate(), currency);
                if (!Double.isFinite(taxAmount) || taxAmount <= 0.0D) {
                    continue;
                }

                boolean success = collectorUuid != null
                        ? databaseManager.transfer(entry.getKey(), collectorUuid, currencyId, taxAmount)
                        : databaseManager.withdraw(entry.getKey(), currencyId, taxAmount);
                if (!success) {
                    continue;
                }

                collectedForCurrency += taxAmount;
                taxedAccountsForCurrency++;
            }

            if (taxedAccountsForCurrency > 0) {
                totalCollected += collectedForCurrency;
                totalTaxedAccounts += taxedAccountsForCurrency;
                perCurrency.put(currencyId, new CurrencyCycleResult(currencyId, collectedForCurrency, taxedAccountsForCurrency, rule.threshold(), rule.rate()));
            }
        }

        return new CycleResult(totalCollected, totalTaxedAccounts, perCurrency, collectorUuid);
    }

    public enum DestinationType {
        SYSTEM,
        PLAYER;

        public static DestinationType fromConfig(String raw) {
            if (raw == null) {
                return SYSTEM;
            }
            return "player".equalsIgnoreCase(raw.trim()) ? PLAYER : SYSTEM;
        }
    }

    public record Rule(boolean enabled, double threshold, double rate) {
        public Rule {
            if (!Double.isFinite(threshold) || threshold < 0.0D) {
                threshold = 0.0D;
            }
            if (!Double.isFinite(rate) || rate < 0.0D) {
                rate = 0.0D;
            }
            if (rate > 1.0D) {
                rate = 1.0D;
            }
        }
    }

    public record Settings(Map<String, Rule> currencyRules, DestinationType destinationType, UUID destinationPlayerUuid) {
        public Rule ruleFor(String currencyId) {
            if (currencyId == null) {
                return null;
            }
            return currencyRules.get(currencyId.trim().toLowerCase(Locale.ROOT));
        }
    }

    public record CurrencyCycleResult(String currencyId, double collectedAmount, int taxedAccounts, double threshold, double rate) {
    }

    public record CycleResult(double totalCollected, int totalTaxedAccounts, Map<String, CurrencyCycleResult> perCurrency, UUID collectorUuid) {
        public boolean hasTaxedAccounts() {
            return totalTaxedAccounts > 0;
        }
    }
}
