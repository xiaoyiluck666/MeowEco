package com.xiaoyiluck.meoweco.fabric;

import com.xiaoyiluck.meoweco.database.JdbcDatabaseManager;
import com.xiaoyiluck.meoweco.fabric.command.FabricCommandRegistrar;
import com.xiaoyiluck.meoweco.fabric.config.FabricConfig;
import com.xiaoyiluck.meoweco.fabric.config.FabricConfigLoader;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.RichTaxEngine;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MeowEcoFabricMod implements ModInitializer {
    public static final String MOD_ID = "meoweco";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private FabricEconomyRuntime runtime;
    private JdbcDatabaseManager databaseManager;
    private ScheduledExecutorService richTaxExecutor;

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        Path configFile = configDir.resolve("config.yml");

        FabricConfig config;
        try (InputStream defaultConfig = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            config = FabricConfigLoader.load(configFile, defaultConfig);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load MeowEco Fabric config", e);
        }

        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("MeowEco-Fabric");
        julLogger.setLevel(Level.INFO);

        databaseManager = new JdbcDatabaseManager(julLogger, config.storageConfig(), false);
        databaseManager.init();

        runtime = new FabricEconomyRuntime(
                databaseManager,
                config.currencies(),
                config.defaultCurrencyId(),
                config.exchangeRates(),
                config.exchangeEnabled()
        );

        FabricCommandRegistrar.register(runtime);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                runtime.ensurePlayerAccounts(handler.getPlayer().getUUID(), handler.getPlayer().getName().getString())
        );

        if (config.richTaxConfig() != null && config.richTaxConfig().enabled()) {
            startRichTaxScheduler(config.richTaxConfig());
        }

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stopRichTaxScheduler();
            if (databaseManager != null) {
                databaseManager.close();
            }
        });

        LOGGER.info("MeowEco Fabric initialized. Currencies: {}", runtime.getCurrencies().keySet());
    }

    private void startRichTaxScheduler(FabricConfig.RichTaxConfig config) {
        stopRichTaxScheduler();

        long initialDelayMillis = calculateInitialDelayMillis(config.startTime(), config.interval());
        long periodMillis = Math.max(1000L, config.interval().toMillis());

        richTaxExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MeowEco-Fabric-RichTax");
            t.setDaemon(true);
            return t;
        });

        richTaxExecutor.scheduleAtFixedRate(() -> executeRichTaxCycle(config), initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
        LOGGER.info("Rich tax scheduled on Fabric: start={}, interval={}s, configured-currencies={}, target={}({})",
                config.startTime(),
                config.interval().toSeconds(),
                config.currencyRules().size(),
                config.destinationType(),
                config.destinationPlayer());
    }

    private void stopRichTaxScheduler() {
        if (richTaxExecutor != null) {
            richTaxExecutor.shutdownNow();
            richTaxExecutor = null;
        }
    }

    private void executeRichTaxCycle(FabricConfig.RichTaxConfig config) {
        try {
            RichTaxEngine.DestinationType destinationType = RichTaxEngine.DestinationType.fromConfig(config.destinationType());
            UUID collectorUuid = null;
            if (destinationType == RichTaxEngine.DestinationType.PLAYER && config.destinationPlayer() != null && !config.destinationPlayer().isBlank()) {
                collectorUuid = runtime.getDatabaseManager().findUuidByUsername(config.destinationPlayer()).orElse(null);
            }

            if (destinationType == RichTaxEngine.DestinationType.PLAYER && collectorUuid == null) {
                LOGGER.warn("Rich tax destination player '{}' not resolvable on this cycle, fallback to system confiscation.", config.destinationPlayer());
                destinationType = RichTaxEngine.DestinationType.SYSTEM;
            }

            RichTaxEngine.Settings settings = new RichTaxEngine.Settings(config.currencyRules(), destinationType, collectorUuid);
            RichTaxEngine.CycleResult result = RichTaxEngine.execute(runtime.getDatabaseManager(), runtime.getCurrencies(), settings);

            for (RichTaxEngine.CurrencyCycleResult currencyResult : result.perCurrency().values()) {
                Currency currency = runtime.getCurrency(currencyResult.currencyId());
                String amount = currency == null
                        ? String.valueOf(currencyResult.collectedAmount())
                        : runtime.formatFixed(currencyResult.collectedAmount(), currency);
                LOGGER.info("Rich tax collected {} from {} account(s) in currency '{}' (threshold={}, rate={}).",
                        amount,
                        currencyResult.taxedAccounts(),
                        currencyResult.currencyId(),
                        currencyResult.threshold(),
                        currencyResult.rate());
            }

            if (!result.hasTaxedAccounts()) {
                LOGGER.debug("Rich tax cycle finished with no taxable accounts.");
            } else {
                LOGGER.info("Rich tax cycle finished. Taxed accounts={}, total collected={}", result.totalTaxedAccounts(), result.totalCollected());
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to execute rich tax cycle", exception);
        }
    }

    private long calculateInitialDelayMillis(java.time.LocalTime startTime, Duration interval) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime nextRun = now.withHour(startTime.getHour()).withMinute(startTime.getMinute()).withSecond(0).withNano(0);

        if (!nextRun.isAfter(now)) {
            long intervalMillis = Math.max(1000L, interval.toMillis());
            long elapsedMillis = Duration.between(nextRun, now).toMillis();
            long intervalsPassed = (elapsedMillis / intervalMillis) + 1L;
            nextRun = nextRun.plus(interval.multipliedBy(intervalsPassed));
        }

        return Math.max(0L, Duration.between(now, nextRun).toMillis());
    }
}

