package com.xiaoyiluck.meoweco.hooks;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MeowEcoPlaceholders extends PlaceholderExpansion {

    private final MeowEco plugin;
    private final ConcurrentHashMap<String, CachedBalance> balanceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedTop> topCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedBalance> serverTotalCache = new ConcurrentHashMap<>();
    private final Set<String> balanceRefreshInFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> topRefreshInFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> totalRefreshInFlight = ConcurrentHashMap.newKeySet();

    public MeowEcoPlaceholders(MeowEco plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "meoweco";
    }

    @Override
    public String getAuthor() {
        return "xiaoyiluck";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) return "";

        String key = params.toLowerCase();

        // Currency info
        if (key.startsWith("currency_")) {
            String[] parts = key.split("_", 3);
            Currency currency = plugin.getDefaultCurrency();
            String field = parts[1];
            if (parts.length == 3) {
                currency = plugin.getCurrency(parts[2]);
            }
            if (currency == null) return "";
            
            if (field.equals("singular")) return currency.getSingular();
            if (field.equals("plural")) return currency.getPlural();
            if (field.equals("display")) return currency.getDisplayName();
            if (field.equals("id")) return currency.getId();
            return "";
        }

        // Balance placeholders
        if (key.startsWith("balance") || key.startsWith("frozen") || key.startsWith("available")) {
            if (player == null) return "";
            String[] parts = key.split("_");
            Currency currency = plugin.getDefaultCurrency();
            String mode = key.startsWith("balance") ? "balance" : (key.startsWith("frozen") ? "frozen" : "available");
            String type = "balance"; // %meoweco_balance% -> 1,234.56
            
            if (parts.length == 1) { // %meoweco_balance%
                type = "balance";
            } else if (parts.length == 2) {
                if (parts[1].equals("raw") || parts[1].equals("formatted") || parts[1].equals("fixed") || parts[1].equals("short")) {
                    type = parts[1];
                } else {
                    // %meoweco_balance_<currency>%
                    currency = plugin.getCurrency(parts[1]);
                    type = "balance";
                }
            } else if (parts.length == 3) {
                // %meoweco_balance_<raw|formatted|fixed|short>_<currency>%
                type = parts[1];
                currency = plugin.getCurrency(parts[2]);
            }

            if (currency == null) return "";
            CachedBalance cached = getBalanceCached(player.getUniqueId(), currency.getId());
            double amount;
            if (mode.equals("balance")) amount = cached.balance;
            else if (mode.equals("frozen")) amount = cached.frozen;
            else amount = cached.balance - cached.frozen;
            
            return formatByPlaceholderType(amount, currency, type);
        }

        // Server total placeholders
        if (key.startsWith("server_total")) {
            String[] parts = key.split("_");
            Currency currency = plugin.getDefaultCurrency();
            String type = "balance";

            if (parts.length == 2) {
                // %meoweco_server_total_<currency>
                currency = plugin.getCurrency(parts[1]);
            } else if (parts.length == 3) {
                // %meoweco_server_total_<raw|formatted|fixed|short>_<currency>
                if (parts[1].equals("raw") || parts[1].equals("formatted") || parts[1].equals("fixed") || parts[1].equals("short")) {
                    type = parts[1];
                    currency = plugin.getCurrency(parts[2]);
                } else {
                    // Maybe just legacy format or incorrect
                    currency = plugin.getCurrency(parts[2]);
                }
            } else if (parts.length == 4) {
                // %meoweco_server_total_<raw|formatted|fixed|short>_<currency>
                type = parts[2];
                currency = plugin.getCurrency(parts[3]);
            }

            if (currency == null) return "";
            double total = getServerTotalCached(currency.getId());
            
            return formatByPlaceholderType(total, currency, type);
        }

        if (key.startsWith("top_")) {
            return handleTopPlaceholder(key);
        }

        return "";
    }

    private String formatByPlaceholderType(double amount, Currency currency, String type) {
        switch (type) {
            case "raw":
                return Double.toString(amount);
            case "formatted": // 1,234.56 Coins
                return plugin.formatFull(amount, currency);
            case "short": // 1.23K
                return plugin.formatShort(amount, currency);
            case "fixed": // 1234.56
                return plugin.formatFixed(amount, currency);
            case "balance": // 1,234.56
            default:
                return plugin.formatBalance(amount, currency);
        }
    }

    private String handleTopPlaceholder(String key) {
        String[] parts = key.split("_");
        if (parts.length < 3) return "";

        int index;
        try {
            index = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "";
        }
        if (index <= 0) return "";

        String field = parts[2];
        Currency currency = plugin.getDefaultCurrency();
        if (parts.length >= 4) {
            currency = plugin.getCurrency(parts[3]);
        }
        if (currency == null) return "";

        List<Map.Entry<String, Double>> top = getTopCached(currency.getId(), Math.max(index, 10));
        if (top.size() < index) return "";

        Map.Entry<String, Double> entry = top.get(index - 1);
        if (field.equals("name")) {
            return entry.getKey() == null ? "" : entry.getKey();
        }
        
        return formatByPlaceholderType(entry.getValue(), currency, field);
    }

    public void invalidateCache() {
        balanceCache.clear();
        topCache.clear();
        serverTotalCache.clear();
        balanceRefreshInFlight.clear();
        topRefreshInFlight.clear();
        totalRefreshInFlight.clear();
    }

    private CachedBalance getBalanceCached(UUID uuid, String currencyId) {
        if (!plugin.isEnabled()) return new CachedBalance(0.0, 0.0, 0L);
        long now = System.currentTimeMillis();
        String cacheKey = uuid.toString() + ":" + currencyId;
        CachedBalance cached = balanceCache.get(cacheKey);
        if (cached != null && now - cached.timestampMs <= 1000L) {
            return cached;
        }

        scheduleBalanceRefresh(cacheKey, uuid, currencyId);
        return cached != null ? cached : new CachedBalance(0.0, 0.0, now);
    }

    private List<Map.Entry<String, Double>> getTopCached(String currencyId, int limit) {
        if (!plugin.isEnabled()) return new ArrayList<>();
        long now = System.currentTimeMillis();
        CachedTop local = topCache.get(currencyId);
        if (local != null && local.limit >= limit && now - local.timestampMs <= 30000L) {
            return local.entries;
        }
        scheduleTopRefresh(currencyId, limit);
        return local != null ? local.entries : Collections.emptyList();
    }

    private double getServerTotalCached(String currencyId) {
        if (!plugin.isEnabled()) return 0.0;
        long now = System.currentTimeMillis();
        CachedBalance local = serverTotalCache.get(currencyId);
        if (local != null && now - local.timestampMs <= 30000L) {
            return local.balance;
        }

        scheduleServerTotalRefresh(currencyId);
        return local != null ? local.balance : 0.0;
    }

    private void scheduleBalanceRefresh(String cacheKey, UUID uuid, String currencyId) {
        if (!balanceRefreshInFlight.add(cacheKey)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.isEnabled()) {
                    return;
                }
                long now = System.currentTimeMillis();
                double balance = plugin.getDatabaseManager().findBalance(uuid, currencyId).orElse(0.0D);
                double frozen = plugin.getDatabaseManager().findFrozenBalance(uuid, currencyId).orElse(0.0D);
                balanceCache.put(cacheKey, new CachedBalance(balance, frozen, now));
            } finally {
                balanceRefreshInFlight.remove(cacheKey);
            }
        });
    }

    private void scheduleTopRefresh(String currencyId, int limit) {
        if (!topRefreshInFlight.add(currencyId)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.isEnabled()) {
                    return;
                }
                long now = System.currentTimeMillis();
                Map<String, Double> map = plugin.getDatabaseManager().getTopAccounts(currencyId, limit);
                List<Map.Entry<String, Double>> list = new ArrayList<>(map.entrySet());
                topCache.put(currencyId, new CachedTop(list, limit, now));
            } finally {
                topRefreshInFlight.remove(currencyId);
            }
        });
    }

    private void scheduleServerTotalRefresh(String currencyId) {
        if (!totalRefreshInFlight.add(currencyId)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.isEnabled()) {
                    return;
                }
                long now = System.currentTimeMillis();
                double total = plugin.getDatabaseManager().getTotalBalance(currencyId);
                serverTotalCache.put(currencyId, new CachedBalance(total, 0.0, now));
            } finally {
                totalRefreshInFlight.remove(currencyId);
            }
        });
    }

    private record CachedBalance(double balance, double frozen, long timestampMs) {}

    private record CachedTop(List<Map.Entry<String, Double>> entries, int limit, long timestampMs) {}
}
