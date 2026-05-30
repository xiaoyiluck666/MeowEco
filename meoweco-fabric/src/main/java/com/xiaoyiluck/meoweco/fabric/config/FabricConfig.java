package com.xiaoyiluck.meoweco.fabric.config;

import com.xiaoyiluck.meoweco.database.JdbcDatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;

import java.util.Map;

public record FabricConfig(
        JdbcDatabaseManager.StorageConfig storageConfig,
        Map<String, Currency> currencies,
        String defaultCurrencyId,
        Map<String, Map<String, Double>> exchangeRates,
        boolean exchangeEnabled,
        RichTaxConfig richTaxConfig
) {
    public record RichTaxConfig(
            boolean enabled,
            Map<String, com.xiaoyiluck.meoweco.service.RichTaxEngine.Rule> currencyRules,
            String destinationType,
            String destinationPlayer,
            java.time.LocalTime startTime,
            java.time.Duration interval
    ) {
    }
}
