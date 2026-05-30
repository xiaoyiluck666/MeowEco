package com.xiaoyiluck.meoweco.fabric.config;

import com.xiaoyiluck.meoweco.database.JdbcDatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.RichTaxEngine;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FabricConfigLoader {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);

    private FabricConfigLoader() {
    }

    public static FabricConfig load(Path configFile, InputStream defaultConfigInputStream) throws IOException {
        Files.createDirectories(configFile.getParent());
        if (Files.notExists(configFile)) {
            if (defaultConfigInputStream == null) {
                throw new IllegalStateException("Missing default config.yml resource");
            }
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8);
                 Reader reader = new java.io.InputStreamReader(defaultConfigInputStream, StandardCharsets.UTF_8)) {
                reader.transferTo(writer);
            }
        }

        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            Object data = yaml.load(reader);
            if (data instanceof Map<?, ?> map) {
                root = castMap(map);
            } else {
                root = Collections.emptyMap();
            }
        }

        Map<String, Object> storage = getMap(root, "storage");
        String storageType = getString(storage, "type", "sqlite").toLowerCase(Locale.ROOT);

        JdbcDatabaseManager.StorageConfig storageConfig;
        if ("mysql".equals(storageType)) {
            Map<String, Object> mysql = getMap(storage, "mysql");
            JdbcDatabaseManager.StorageConfig.MySqlConfig mySqlConfig = new JdbcDatabaseManager.StorageConfig.MySqlConfig(
                    getString(mysql, "host", "localhost"),
                    getInt(mysql, "port", 3306),
                    getString(mysql, "database", "meoweco"),
                    getString(mysql, "username", "root"),
                    getString(mysql, "password", "password"),
                    getBoolean(mysql, "use-ssl", false)
            );
            storageConfig = JdbcDatabaseManager.StorageConfig.mysql(configFile.getParent(), mySqlConfig);
        } else {
            storageConfig = JdbcDatabaseManager.StorageConfig.sqlite(configFile.getParent());
        }

        Map<String, Currency> currencies = new LinkedHashMap<>();
        Map<String, Object> currenciesMap = getMap(root, "currencies");
        for (Map.Entry<String, Object> entry : currenciesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> valueMap)) {
                continue;
            }
            String id = normalizeCurrencyId(entry.getKey());
            Map<String, Object> currencyData = castMap(valueMap);
            Currency currency = new Currency(
                    id,
                    getString(currencyData, "display-name", id),
                    getString(currencyData, "singular", id),
                    getString(currencyData, "plural", id),
                    getDouble(currencyData, "initial-balance", 0.0),
                    clamp(getInt(currencyData, "decimal-places", 2), 0, 8),
                    clamp(getDouble(currencyData, "transfer-tax", 0.0), 0.0, 1.0)
            );
            currencies.put(id, currency);
        }

        if (currencies.isEmpty()) {
            Currency fallback = new Currency("coins", "Coins", "Coin", "Coins", 0.0, 2, 0.0);
            currencies.put(fallback.getId(), fallback);
        }

        String defaultCurrencyId = normalizeCurrencyId(getString(root, "default-currency", "coins"));
        if (!currencies.containsKey(defaultCurrencyId)) {
            defaultCurrencyId = currencies.keySet().iterator().next();
        }

        Map<String, Map<String, Double>> exchangeRates = new LinkedHashMap<>();
        Map<String, Object> exchangeConfig = getMap(root, "exchange-rates");
        boolean exchangeEnabled = getBoolean(exchangeConfig, "enabled", true);
        for (Map.Entry<String, Object> entry : exchangeConfig.entrySet()) {
            String fromId = normalizeCurrencyId(entry.getKey());
            if ("enabled".equals(fromId) || !(entry.getValue() instanceof Map<?, ?> valueMap)) {
                continue;
            }

            Map<String, Double> toRates = new LinkedHashMap<>();
            for (Map.Entry<String, Object> rateEntry : castMap(valueMap).entrySet()) {
                String toId = normalizeCurrencyId(rateEntry.getKey());
                double rate = toDouble(rateEntry.getValue(), -1.0);
                if (rate > 0.0 && Double.isFinite(rate)) {
                    toRates.put(toId, rate);
                }
            }
            exchangeRates.put(fromId, toRates);
        }

        FabricConfig.RichTaxConfig richTaxConfig = parseRichTaxConfig(root);

        return new FabricConfig(storageConfig, currencies, defaultCurrencyId, exchangeRates, exchangeEnabled, richTaxConfig);
    }

    private static FabricConfig.RichTaxConfig parseRichTaxConfig(Map<String, Object> root) {
        Map<String, Object> richTax = getMap(root, "rich-tax");
        boolean enabled = getBoolean(richTax, "enabled", false);
        LocalTime startTime = parseStartTime(getString(richTax, "start-time", "03:00"));
        Duration interval = parseInterval(getString(richTax, "interval", "24h"));

        Map<String, RichTaxEngine.Rule> rules = new LinkedHashMap<>();
        Map<String, Object> currencies = getMap(richTax, "currencies");
        for (Map.Entry<String, Object> entry : currencies.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> valueMap)) {
                continue;
            }
            String currencyId = normalizeCurrencyId(entry.getKey());
            Map<String, Object> ruleMap = castMap(valueMap);
            boolean ruleEnabled = getBoolean(ruleMap, "enabled", true);
            double threshold = Math.max(0.0D, getDouble(ruleMap, "threshold", 100000.0D));
            double rate = clamp(getDouble(ruleMap, "rate", 0.05D), 0.0D, 1.0D);
            rules.put(currencyId, new RichTaxEngine.Rule(ruleEnabled, threshold, rate));
        }

        Map<String, Object> destination = getMap(richTax, "destination");
        String destinationType = getString(destination, "type", "system");
        String destinationPlayer = getString(destination, "player", "Admin");

        return new FabricConfig.RichTaxConfig(enabled, rules, destinationType, destinationPlayer, startTime, interval);
    }

    private static LocalTime parseStartTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalTime.of(3, 0);
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return LocalTime.of(3, 0);
        }
    }

    private static Duration parseInterval(String raw) {
        if (raw == null || raw.isBlank()) {
            return Duration.ofHours(24);
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.chars().allMatch(Character::isDigit)) {
            long minutes = Long.parseLong(normalized);
            return minutes > 0 ? Duration.ofMinutes(minutes) : Duration.ofHours(24);
        }

        Matcher matcher = INTERVAL_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Duration.ofHours(24);
        }

        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0L) {
            return Duration.ofHours(24);
        }

        return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> Duration.ofHours(24);
        };
    }

    private static String normalizeCurrencyId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> getMap(Map<String, Object> root, String key) {
        Object obj = root.get(key);
        if (obj instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return Collections.emptyMap();
    }

    private static String getString(Map<String, Object> root, String key, String defaultValue) {
        Object obj = root.get(key);
        return obj == null ? defaultValue : String.valueOf(obj);
    }

    private static int getInt(Map<String, Object> root, String key, int defaultValue) {
        Object obj = root.get(key);
        if (obj instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static double getDouble(Map<String, Object> root, String key, double defaultValue) {
        return toDouble(root.get(key), defaultValue);
    }

    private static boolean getBoolean(Map<String, Object> root, String key, boolean defaultValue) {
        Object obj = root.get(key);
        if (obj instanceof Boolean b) {
            return b;
        }
        if (obj == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(obj));
    }

    private static double toDouble(Object obj, double defaultValue) {
        if (obj instanceof Number number) {
            return number.doubleValue();
        }
        if (obj == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
