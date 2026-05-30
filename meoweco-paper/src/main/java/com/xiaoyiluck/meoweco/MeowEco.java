package com.xiaoyiluck.meoweco;

import com.xiaoyiluck.meoweco.api.MeowEcoAPI;
import com.xiaoyiluck.meoweco.api.MeowEcoAPIImpl;
import com.xiaoyiluck.meoweco.api.MeowEconomy;
import com.xiaoyiluck.meoweco.commands.BaltopCommand;
import com.xiaoyiluck.meoweco.commands.MeowEcoCommand;
import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.database.MySQLDatabase;
import com.xiaoyiluck.meoweco.database.SQLiteDatabase;
import com.xiaoyiluck.meoweco.hooks.MeowEcoPlaceholders;
import com.xiaoyiluck.meoweco.listeners.PlayerListener;
import com.xiaoyiluck.meoweco.tax.RichTaxService;
import com.xiaoyiluck.meoweco.utils.ConfigManager;
import com.xiaoyiluck.meoweco.utils.UpdateChecker;
import com.xiaoyiluck.meoweco.objects.Currency;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import java.util.Collections;

public class MeowEco extends JavaPlugin {
    private static final String FALLBACK_CURRENCY_ID = "coins";

    private static MeowEco instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private MeowEconomy meowEconomy;
    private BaltopCommand baltopCommand;
    private MeowEcoPlaceholders placeholders;
    private UpdateChecker updateChecker;
    private RichTaxService richTaxService;
    private boolean updateAvailable = false;
    
    private final Map<String, Currency> currencies = new HashMap<>();
    private final Map<String, Map<String, Double>> exchangeRates = new HashMap<>();
    private String defaultCurrencyId;
    private boolean debug = false;

    public static MeowEco getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // Load config
        configManager = new ConfigManager(this);
        loadCurrencies();
        loadExchangeRates();

        // Initialize Database
        String storageType = getConfig().getString("storage.type", "sqlite");
        if (storageType.equalsIgnoreCase("mysql")) {
            databaseManager = new MySQLDatabase(this);
        } else {
            databaseManager = new SQLiteDatabase(this);
        }
        databaseManager.init();

        richTaxService = new RichTaxService(this);
        richTaxService.reload();

        // Register Vault Economy
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            meowEconomy = new MeowEconomy(this);
            getServer().getServicesManager().register(Economy.class, meowEconomy, this, ServicePriority.Highest);
            getLogger().info("Registered with Vault.");
        } else {
            getLogger().warning("Vault not found! Economy features might not work with other plugins.");
        }

        // Register Commands
        MeowEcoCommand mainCommand = new MeowEcoCommand(this);
        this.baltopCommand = mainCommand.getBaltopCommand();
        
        getCommand("meoweco").setExecutor(mainCommand);
        getCommand("meoweco").setTabCompleter(mainCommand);

        // Register Restored Conventional Commands
        if (getCommand("money") != null) {
            getCommand("money").setExecutor(mainCommand.getMoneyCommand());
            getCommand("money").setTabCompleter(mainCommand.getMoneyCommand());
        }
        if (getCommand("pay") != null) {
            getCommand("pay").setExecutor(mainCommand.getPayCommand());
            getCommand("pay").setTabCompleter(mainCommand.getPayCommand());
        }
        if (getCommand("baltop") != null) {
            getCommand("baltop").setExecutor(mainCommand.getBaltopCommand());
            getCommand("baltop").setTabCompleter(mainCommand.getBaltopCommand());
        }
        if (getCommand("eco") != null) {
            getCommand("eco").setExecutor(mainCommand.getEcoCommand());
            getCommand("eco").setTabCompleter(mainCommand.getEcoCommand());
        }

        // Register Listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new com.xiaoyiluck.meoweco.listeners.CommandOverrideListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Class<?> expansionClass = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
                Method register = papiClass.getMethod("registerExpansion", expansionClass);
                this.placeholders = new MeowEcoPlaceholders(this);
                register.invoke(null, this.placeholders);
                getLogger().info("Registered with PlaceholderAPI.");
            } catch (Throwable t) {
                getLogger().warning("Could not register PlaceholderAPI expansion.");
            }
        }

        // Update Checker
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            this.updateChecker = new UpdateChecker(this);
            int intervalHours = getConfig().getInt("update-checker.interval", 24);
            if (intervalHours <= 0) {
                getLogger().warning("Invalid update-checker.interval: " + intervalHours + ". Falling back to 24 hours.");
                intervalHours = 24;
            }
            long ticks = intervalHours * 60L * 60L * 20L;
            
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                updateChecker.check().thenAccept(available -> {
                    this.updateAvailable = available;
                    if (available) {
                        getLogger().info("A new version of MeowEco is available: v" + updateChecker.getLatestVersion());
                        getLogger().info("Download it at: " + updateChecker.getDownloadUrl());
                    }
                });
            }, 20L * 5, ticks); // First check after 5 seconds
        }

        // Register MeowEco API
        getServer().getServicesManager().register(MeowEcoAPI.class, new MeowEcoAPIImpl(this), this, ServicePriority.Normal);
        getLogger().info("MeowEco API registered.");

        getLogger().info("MeowEco enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel all async tasks to prevent database usage during shutdown
        getServer().getScheduler().cancelTasks(this);

        // Unregister PlaceholderAPI expansion
        if (placeholders != null) {
            placeholders.unregister();
            placeholders = null;
        }
        
        if (richTaxService != null) {
            richTaxService.stop();
            richTaxService = null;
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
        
        // Do not set instance to null here if you want old Economy objects to work after reload!
        // But for safety, we should. However, since we are doing a proxy pattern,
        // the new onEnable will overwrite 'instance' immediately after.
        // If we set it to null, there is a tiny window where it is null.
        // But the real danger is keeping a reference to a disabled plugin.
        // Let's rely on the fact that new instance overwrites it.
        // instance = null; 
        
        getLogger().info("MeowEco disabled!");
    }
    
    public void reload() {
        reloadConfig();
        configManager.saveDefaultConfig();
        configManager.reloadMessages();
        loadCurrencies();
        loadExchangeRates();
        if (richTaxService != null) {
            richTaxService.reload();
        }
        if (baltopCommand != null) {
            baltopCommand.invalidateCache();
        }
        if (placeholders != null) {
            placeholders.invalidateCache();
        }
    }

    private void loadCurrencies() {
        Map<String, Currency> loadedCurrencies = new HashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection("currencies");
        if (section == null) {
            Currency fallbackCurrency = createFallbackCurrency();
            loadedCurrencies.put(fallbackCurrency.getId().toLowerCase(Locale.ROOT), fallbackCurrency);
        } else {
            for (String rawId : section.getKeys(false)) {
                ConfigurationSection currSection = section.getConfigurationSection(rawId);
                if (currSection == null) {
                    continue;
                }

                String id = rawId.toLowerCase(Locale.ROOT);
                String displayName = currSection.getString("display-name", id);
                String singular = currSection.getString("singular", "Coin");
                String plural = currSection.getString("plural", "Coins");
                double initial = currSection.getDouble("initial-balance", 0.0);
                int decimal = currSection.getInt("decimal-places", 2);
                double tax = currSection.getDouble("transfer-tax", 0.0);

                Currency currency = new Currency(id, displayName, singular, plural, initial, decimal, tax);
                loadedCurrencies.put(id, currency);
                debug("Loaded currency: " + id + " (Name: " + displayName + ", Initial: " + initial + ")");
            }
        }

        if (loadedCurrencies.isEmpty()) {
            Currency fallbackCurrency = createFallbackCurrency();
            loadedCurrencies.put(fallbackCurrency.getId().toLowerCase(Locale.ROOT), fallbackCurrency);
            getLogger().warning("No valid currencies were loaded from config. Added fallback currency '" + fallbackCurrency.getId() + "'.");
        }

        String configuredDefault = getConfig().getString("default-currency", FALLBACK_CURRENCY_ID);
        String normalizedDefault = configuredDefault == null ? "" : configuredDefault.toLowerCase(Locale.ROOT);
        if (normalizedDefault.isBlank() || !loadedCurrencies.containsKey(normalizedDefault)) {
            getLogger().warning("Configured default-currency is invalid: '" + configuredDefault + "'. Using first available currency.");
            normalizedDefault = loadedCurrencies.keySet().iterator().next();
        }

        synchronized (currencies) {
            currencies.clear();
            currencies.putAll(loadedCurrencies);
            defaultCurrencyId = normalizedDefault;
        }

        debug("Default currency set to: " + defaultCurrencyId);
    }

    private Currency createFallbackCurrency() {
        String singular = getConfig().getString("currency.singular", "Coin");
        String plural = getConfig().getString("currency.plural", "Coins");
        double initial = getConfig().getDouble("currency.initial-balance", 100.0);
        int decimal = getConfig().getInt("currency.decimal-places", 2);
        double tax = getConfig().getDouble("currency.transfer-tax", 0.0);
        return new Currency(FALLBACK_CURRENCY_ID, "Coins", singular, plural, initial, decimal, tax);
    }

    private void loadExchangeRates() {
        Map<String, Map<String, Double>> loadedRates = new HashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection("exchange-rates");
        if (section != null) {
            for (String rawFrom : section.getKeys(false)) {
                ConfigurationSection fromSection = section.getConfigurationSection(rawFrom);
                if (fromSection == null) {
                    continue;
                }

                String from = rawFrom.toLowerCase(Locale.ROOT);
                Map<String, Double> targets = new HashMap<>();
                for (String rawTo : fromSection.getKeys(false)) {
                    String to = rawTo.toLowerCase(Locale.ROOT);
                    double rate = fromSection.getDouble(rawTo, 0.0);
                    if (rate > 0 && Double.isFinite(rate)) {
                        targets.put(to, rate);
                    }
                }
                if (!targets.isEmpty()) {
                    loadedRates.put(from, targets);
                }
            }
        }

        synchronized (exchangeRates) {
            exchangeRates.clear();
            exchangeRates.putAll(loadedRates);
        }
    }

    public double getExchangeRate(String from, String to) {
        if (from == null || to == null) {
            return 0.0;
        }
        if (from.equalsIgnoreCase(to)) {
            return 1.0;
        }

        String fromId = from.toLowerCase(Locale.ROOT);
        String toId = to.toLowerCase(Locale.ROOT);
        Map<String, Map<String, Double>> ratesSnapshot;
        String baseId;

        synchronized (exchangeRates) {
            ratesSnapshot = new HashMap<>(exchangeRates.size());
            for (Map.Entry<String, Map<String, Double>> entry : exchangeRates.entrySet()) {
                ratesSnapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
        }
        synchronized (currencies) {
            baseId = defaultCurrencyId;
        }

        Map<String, Double> direct = ratesSnapshot.get(fromId);
        if (direct != null) {
            Double rate = direct.get(toId);
            if (rate != null && rate > 0) {
                return rate;
            }
        }

        if (baseId == null) {
            return 0.0;
        }

        Double fromToBase = getDirectOrInverseRate(ratesSnapshot, fromId, baseId);
        Double baseToTo = getDirectOrInverseRate(ratesSnapshot, baseId, toId);
        if (fromToBase != null && baseToTo != null) {
            return fromToBase * baseToTo;
        }

        return 0.0;
    }

    private Double getDirectOrInverseRate(Map<String, Map<String, Double>> ratesSnapshot, String from, String to) {
        if (from.equalsIgnoreCase(to)) {
            return 1.0;
        }

        Map<String, Double> direct = ratesSnapshot.get(from);
        if (direct != null) {
            Double rate = direct.get(to);
            if (rate != null && rate > 0) {
                return rate;
            }
        }

        Map<String, Double> inverseMap = ratesSnapshot.get(to);
        if (inverseMap != null) {
            Double inverse = inverseMap.get(from);
            if (inverse != null && inverse > 0) {
                return 1.0 / inverse;
            }
        }

        return null;
    }

    public void setExchangeRate(String from, String to, double rate) {
        if (from == null || to == null) {
            return;
        }

        String normalizedFrom = from.toLowerCase(Locale.ROOT);
        String normalizedTo = to.toLowerCase(Locale.ROOT);

        synchronized (exchangeRates) {
            Map<String, Double> rates = exchangeRates.computeIfAbsent(normalizedFrom, k -> new HashMap<>());
            if (rate <= 0) {
                rates.remove(normalizedTo);
                if (rates.isEmpty()) {
                    exchangeRates.remove(normalizedFrom);
                }
            } else {
                rates.put(normalizedTo, rate);
            }
        }

        getConfig().set("exchange-rates." + normalizedFrom + "." + normalizedTo, rate > 0 ? rate : null);
        saveConfig();
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void debug(String message) {
        if (debug) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public BaltopCommand getBaltopCommand() {
        return baltopCommand;
    }

    public MeowEcoPlaceholders getPlaceholders() {
        return placeholders;
    }

    public MeowEconomy getVaultEconomy() {
        return meowEconomy;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public Map<String, Currency> getCurrencies() {
        synchronized (currencies) {
            return Collections.unmodifiableMap(new HashMap<>(currencies));
        }
    }

    public Currency getCurrency(String id) {
        if (id == null) {
            return null;
        }
        String normalizedId = id.toLowerCase(Locale.ROOT);
        synchronized (currencies) {
            return currencies.get(normalizedId);
        }
    }

    public String getDefaultCurrencyId() {
        synchronized (currencies) {
            if (defaultCurrencyId == null || !currencies.containsKey(defaultCurrencyId)) {
                return getDefaultCurrency().getId();
            }
            return defaultCurrencyId;
        }
    }

    public Currency getDefaultCurrency() {
        synchronized (currencies) {
            Currency currency = defaultCurrencyId == null ? null : currencies.get(defaultCurrencyId);
            if (currency != null) {
                return currency;
            }
            if (!currencies.isEmpty()) {
                defaultCurrencyId = currencies.keySet().iterator().next();
                return currencies.get(defaultCurrencyId);
            }

            Currency fallback = createFallbackCurrency();
            String fallbackId = fallback.getId().toLowerCase(Locale.ROOT);
            currencies.put(fallbackId, fallback);
            defaultCurrencyId = fallbackId;
            getLogger().warning("Currency map is empty at runtime. Auto-restored fallback currency '" + fallback.getId() + "'.");
            return fallback;
        }
    }

    public String formatBalance(double amount, Currency currency) {
        int decimalPlaces = currency.getDecimalPlaces();
        if (decimalPlaces < 0) decimalPlaces = 0;
        return String.format(java.util.Locale.US, "%,." + decimalPlaces + "f", amount);
    }

    public String formatShort(double amount, Currency currency) {
        int decimalPlaces = currency.getDecimalPlaces();
        if (decimalPlaces < 0) decimalPlaces = 0;
        if (decimalPlaces > 2) decimalPlaces = 2;

        String[] suffixes = {"", "K", "M", "B", "T"};
        double value = amount;
        int idx = 0;
        double abs = Math.abs(value);

        while (abs >= 1000.0 && idx < suffixes.length - 1) {
            value /= 1000.0;
            abs /= 1000.0;
            idx++;
        }

        double multiplier = Math.pow(10, decimalPlaces);
        double rounded = Math.round(value * multiplier) / multiplier;
        
        if (Math.abs(rounded) >= 1000.0 && idx < suffixes.length - 1) {
            value /= 1000.0;
            idx++;
            rounded = Math.round(value * multiplier) / multiplier;
        }

        String formatted = String.format(Locale.US, "%." + decimalPlaces + "f", rounded);
        formatted = trimTrailingZeros(formatted);
        return formatted + suffixes[idx];
    }

    @Deprecated
    public String formatAmount(double amount, Currency currency) {
        return formatShort(amount, currency);
    }

    public String formatFull(double amount, Currency currency) {
        return formatBalance(amount, currency) + " " + currency.getSingular();
    }

    public String formatFixed(double amount, Currency currency) {
        int decimalPlaces = currency.getDecimalPlaces();
        if (decimalPlaces < 0) decimalPlaces = 0;
        return String.format(java.util.Locale.US, "%." + decimalPlaces + "f", amount);
    }

    private String trimTrailingZeros(String formatted) {
        if (!formatted.contains(".")) {
            return formatted;
        }
        int len = formatted.length();
        int end = len;
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        if (end == 0) {
            return "0";
        }
        return formatted.substring(0, end);
    }
}
