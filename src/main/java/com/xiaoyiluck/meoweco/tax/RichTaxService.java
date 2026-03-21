package com.xiaoyiluck.meoweco.tax;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.utils.PlayerLookup;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RichTaxService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);

    private final MeowEco plugin;
    private BukkitTask task;

    public RichTaxService(MeowEco plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        stop();

        RichTaxSettings settings = loadSettings();
        if (!settings.enabled()) {
            plugin.debug("Rich tax is disabled in config.");
            return;
        }

        long periodTicks = durationToTicks(settings.interval());
        long initialDelayTicks = calculateInitialDelayTicks(settings.startTime(), settings.interval());
        this.task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> executeCycle(settings),
                initialDelayTicks,
                periodTicks
        );

        plugin.getLogger().info("Rich tax scheduled: start=" + settings.startTime()
                + ", interval=" + formatDuration(settings.interval())
                + ", configured-currencies=" + settings.currencyRules().size()
                + ", target=" + settings.destinationType()
                + (settings.destinationType() == DestinationType.PLAYER ? "(" + settings.destinationPlayerName() + ")" : ""));
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void executeCycle(RichTaxSettings settings) {
        try {
            UUID collectorUuid = settings.destinationPlayerUuid();
            if (settings.destinationType() == DestinationType.PLAYER && collectorUuid == null) {
                plugin.getLogger().warning("Rich tax target player is invalid. Falling back to system confiscation for this cycle.");
            }

            double totalCollected = 0.0D;
            int totalTaxedAccounts = 0;

            for (Currency currency : plugin.getCurrencies().values()) {
                String currencyId = currency.getId();
                RichTaxRule rule = settings.ruleFor(currencyId);
                if (rule == null || !rule.enabled() || rule.rate() <= 0.0D) {
                    continue;
                }

                double threshold = rule.threshold();
                double rate = rule.rate();

                if (collectorUuid != null && !plugin.getDatabaseManager().hasAccount(collectorUuid, currencyId)) {
                    plugin.getDatabaseManager().createAccount(collectorUuid, currencyId, 0.0D);
                }

                Map<UUID, Double> taxableAccounts = plugin.getDatabaseManager().getAccountsAboveBalance(currencyId, threshold);
                if (taxableAccounts.isEmpty()) {
                    continue;
                }

                double collectedForCurrency = 0.0D;
                int taxedAccountsForCurrency = 0;

                for (Map.Entry<UUID, Double> entry : taxableAccounts.entrySet()) {
                    double taxableAmount = entry.getValue() - threshold;
                    if (taxableAmount <= 0.0D) {
                        continue;
                    }

                    double taxAmount = taxableAmount * rate;
                    if (taxAmount <= 0.0D || !Double.isFinite(taxAmount)) {
                        continue;
                    }

                    boolean success;
                    if (collectorUuid != null) {
                        success = plugin.getDatabaseManager().transfer(entry.getKey(), collectorUuid, currencyId, taxAmount);
                    } else {
                        success = plugin.getDatabaseManager().withdraw(entry.getKey(), currencyId, taxAmount);
                    }

                    if (success) {
                        collectedForCurrency += taxAmount;
                        taxedAccountsForCurrency++;
                    }
                }

                if (taxedAccountsForCurrency > 0) {
                    totalCollected += collectedForCurrency;
                    totalTaxedAccounts += taxedAccountsForCurrency;
                    plugin.getLogger().info("Rich tax collected " + plugin.formatFixed(collectedForCurrency, currency)
                            + " from " + taxedAccountsForCurrency + " account(s) in currency '" + currencyId + "'"
                            + " (threshold=" + threshold + ", rate=" + rate + ").");
                }
            }

            if (totalTaxedAccounts > 0 && plugin.getBaltopCommand() != null) {
                plugin.getBaltopCommand().invalidateCache();
            }

            if (totalTaxedAccounts == 0) {
                plugin.debug("Rich tax cycle finished with no taxable accounts.");
            } else {
                plugin.getLogger().info("Rich tax cycle finished. Taxed accounts=" + totalTaxedAccounts + ", total collected=" + totalCollected + ".");
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute rich tax cycle", exception);
        }
    }

    private UUID ensureCollector(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        OfflinePlayer collector = PlayerLookup.resolveOfflinePlayer(plugin, playerName).orElse(null);
        if (collector == null) {
            return null;
        }
        if (collector.getUniqueId() == null) {
            return null;
        }
        return collector.getUniqueId();
    }

    private RichTaxSettings loadSettings() {
        boolean enabled = plugin.getConfig().getBoolean("rich-tax.enabled", false);
        LocalTime startTime = parseStartTime(plugin.getConfig().getString("rich-tax.start-time", "03:00"));
        Duration interval = parseInterval(plugin.getConfig().getString("rich-tax.interval", "24h"));

        Map<String, RichTaxRule> currencyRules = new java.util.LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection currencyRulesSection = plugin.getConfig().getConfigurationSection("rich-tax.currencies");
        if (currencyRulesSection != null) {
            for (String rawCurrencyId : currencyRulesSection.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection ruleSection = currencyRulesSection.getConfigurationSection(rawCurrencyId);
                if (ruleSection == null) {
                    continue;
                }

                boolean currencyEnabled = ruleSection.getBoolean("enabled", true);
                double threshold = Math.max(0.0D, ruleSection.getDouble("threshold", 100000.0D));
                double rate = clampRate(ruleSection.getDouble("rate", 0.05D));
                currencyRules.put(normalizeCurrencyId(rawCurrencyId), new RichTaxRule(currencyEnabled, threshold, rate));
            }
        }

        String destinationTypeRaw = plugin.getConfig().getString("rich-tax.destination.type", "system");
        DestinationType destinationType = DestinationType.fromConfig(destinationTypeRaw);
        String destinationPlayerName = plugin.getConfig().getString("rich-tax.destination.player", "Admin");
        UUID destinationPlayerUuid = null;

        if (destinationType == DestinationType.PLAYER && (destinationPlayerName == null || destinationPlayerName.isBlank())) {
            plugin.getLogger().warning("rich-tax.destination.player is empty. Falling back to system confiscation.");
            destinationType = DestinationType.SYSTEM;
        } else if (destinationType == DestinationType.PLAYER) {
            destinationPlayerUuid = ensureCollector(destinationPlayerName);
        }

        return new RichTaxSettings(enabled, currencyRules, startTime, interval, destinationType, destinationPlayerName, destinationPlayerUuid);
    }

    private static String normalizeCurrencyId(String currencyId) {
        return currencyId == null ? "" : currencyId.trim().toLowerCase(Locale.ROOT);
    }

    private double clampRate(double rate) {
        if (!Double.isFinite(rate)) {
            return 0.0D;
        }
        if (rate < 0.0D) {
            return 0.0D;
        }
        return Math.min(rate, 1.0D);
    }

    private LocalTime parseStartTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalTime.of(3, 0);
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            plugin.getLogger().warning("Invalid rich-tax.start-time: '" + raw + "'. Falling back to 03:00.");
            return LocalTime.of(3, 0);
        }
    }

    private Duration parseInterval(String raw) {
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
            plugin.getLogger().warning("Invalid rich-tax.interval: '" + raw + "'. Use values like 30m, 6h or 1d. Falling back to 24h.");
            return Duration.ofHours(24);
        }

        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0L) {
            plugin.getLogger().warning("Invalid rich-tax.interval: '" + raw + "'. Falling back to 24h.");
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

    private long calculateInitialDelayTicks(LocalTime startTime, Duration interval) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime nextRun = now.withHour(startTime.getHour()).withMinute(startTime.getMinute()).withSecond(0).withNano(0);

        if (!nextRun.isAfter(now)) {
            long intervalMillis = Math.max(1L, interval.toMillis());
            long elapsedMillis = Duration.between(nextRun, now).toMillis();
            long intervalsPassed = (elapsedMillis / intervalMillis) + 1L;
            nextRun = nextRun.plus(interval.multipliedBy(intervalsPassed));
        }

        return durationToTicks(Duration.between(now, nextRun));
    }

    private long durationToTicks(Duration duration) {
        long millis = Math.max(0L, duration.toMillis());
        return Math.max(1L, (millis + 49L) / 50L);
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds % 86400L == 0L) {
            return (seconds / 86400L) + "d";
        }
        if (seconds % 3600L == 0L) {
            return (seconds / 3600L) + "h";
        }
        if (seconds % 60L == 0L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }

    private enum DestinationType {
        SYSTEM,
        PLAYER;

        private static DestinationType fromConfig(String raw) {
            if (raw == null) {
                return SYSTEM;
            }
            return "player".equalsIgnoreCase(raw.trim()) ? PLAYER : SYSTEM;
        }
    }


    private record RichTaxRule(
            boolean enabled,
            double threshold,
            double rate
    ) {
    }

    private record RichTaxSettings(
            boolean enabled,
            Map<String, RichTaxRule> currencyRules,
            LocalTime startTime,
            Duration interval,
            DestinationType destinationType,
            String destinationPlayerName,
            UUID destinationPlayerUuid
    ) {
        private RichTaxRule ruleFor(String currencyId) {
            return currencyRules.get(normalizeCurrencyId(currencyId));
        }
    }
}
