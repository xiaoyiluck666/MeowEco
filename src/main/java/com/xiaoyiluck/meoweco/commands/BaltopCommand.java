package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaltopCommand implements CommandExecutor, TabCompleter {

    private final MeowEco plugin;
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private static final long CACHE_TTL = 300000; // 5 minutes

    private static class CacheEntry {
        Map<String, Double> top;
        double total;
        long timestamp;
    }

    public BaltopCommand(MeowEco plugin) {
        this.plugin = plugin;
    }
    
    public void invalidateCache() {
        this.cache.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("meoweco.top")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }

        Currency currency = plugin.getDefaultCurrency();
        if (args.length >= 1) {
            currency = plugin.getCurrency(args[0]);
            if (currency == null) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                return true;
            }
        }
        
        final Currency finalCurrency = currency;
        plugin.debug("Fetching baltop for currency: " + finalCurrency.getId() + " (Display: " + finalCurrency.getDisplayName() + ")");
        
        // Pre-fetch templates on main thread to avoid async config access
        Component headerMsg = plugin.getConfigManager().getComponent("baltop-header");
        Component totalMsg = plugin.getConfigManager().getComponent("baltop-total");
        Component entryTemplate = plugin.getConfigManager().getComponent("baltop-entry");
        int limit = 10;
        
        // Check cache validity
        CacheEntry entry = cache.get(finalCurrency.getId());
        if (entry != null && System.currentTimeMillis() - entry.timestamp < CACHE_TTL) {
            sendBaltop(sender, entry.top, entry.total, finalCurrency, headerMsg, totalMsg, entryTemplate);
            return true;
        }
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Double> top = plugin.getDatabaseManager().getTopAccounts(finalCurrency.getId(), limit);
            double total = plugin.getDatabaseManager().getTotalBalance(finalCurrency.getId());
            
            // Update cache
            CacheEntry newEntry = new CacheEntry();
            newEntry.top = top;
            newEntry.total = total;
            newEntry.timestamp = System.currentTimeMillis();
            this.cache.put(finalCurrency.getId(), newEntry);
            
            // Switch back to main thread for sending messages
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sendBaltop(sender, top, total, finalCurrency, headerMsg, totalMsg, entryTemplate);
            });
        });

        return true;
    }
    
    private void sendBaltop(CommandSender sender, Map<String, Double> top, double totalBalance, Currency currency, Component header, Component totalMsg, Component entryTemplate) {
        if (header != null) {
            sender.sendMessage(header.replaceText(TextReplacementConfig.builder().matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(currency.getDisplayName())).build()));
        }
        if (totalMsg != null) {
            sender.sendMessage(totalMsg.replaceText(TextReplacementConfig.builder()
                    .matchLiteral("%amount%")
                    .replacement(plugin.formatShort(totalBalance, currency))
                    .build())
                    .replaceText(TextReplacementConfig.builder().matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(currency.getDisplayName())).build()));
        }
        int rank = 1;
        for (Map.Entry<String, Double> entry : top.entrySet()) {
            Component msg = entryTemplate
                    .replaceText(TextReplacementConfig.builder().matchLiteral("%rank%").replacement(String.valueOf(rank)).build())
                    .replaceText(TextReplacementConfig.builder().matchLiteral("%player%").replacement(entry.getKey()).build())
                    .replaceText(TextReplacementConfig.builder().matchLiteral("%amount%").replacement(plugin.formatShort(entry.getValue(), currency)).build())
                    .replaceText(TextReplacementConfig.builder().matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(currency.getDisplayName())).build());
            sender.sendMessage(msg);
            rank++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (String id : plugin.getCurrencies().keySet()) {
                if (id.startsWith(prefix)) suggestions.add(id);
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
