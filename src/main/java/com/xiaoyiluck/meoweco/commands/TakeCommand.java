package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TakeCommand implements CommandExecutor, TabCompleter {

    private final MeowEco plugin;

    public TakeCommand(MeowEco plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("meoweco.take")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getComponent("take-usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getComponent("take-failed-amount"));
            return true;
        }

        Currency currency = plugin.getDefaultCurrency();
        if (args.length >= 3) {
            currency = plugin.getCurrency(args[2]);
            if (currency == null) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                return true;
            }
        }

        final double finalAmount = amount;
        final Currency finalCurrency = currency;
        
        // Pre-fetch components
        Component playerNotFound = plugin.getConfigManager().getComponent("player-not-found");
        Component senderMsgTemplate = plugin.getConfigManager().getComponent("take-success-sender");
        Component receiverMsgTemplate = plugin.getConfigManager().getComponent("take-success-receiver");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.getDatabaseManager().hasAccount(target.getUniqueId(), finalCurrency.getId())) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(playerNotFound));
                return;
            }

            boolean success = plugin.getDatabaseManager().withdraw(target.getUniqueId(), finalCurrency.getId(), finalAmount);
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!success) {
                    sender.sendMessage(plugin.getConfigManager().getComponent("take-failed-balance"));
                    return;
                }

                // Invalidate baltop cache
                if (plugin.getBaltopCommand() != null) {
                    plugin.getBaltopCommand().invalidateCache();
                }

                Component senderMsg = senderMsgTemplate
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%player%").replacement(target.getName() != null ? target.getName() : "Unknown").build())
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%amount%").replacement(plugin.formatShort(finalAmount, finalCurrency)).build())
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(finalCurrency.getDisplayName())).build());
                sender.sendMessage(senderMsg);

                if (target.isOnline()) {
                    Component receiverMsg = receiverMsgTemplate
                            .replaceText(TextReplacementConfig.builder().matchLiteral("%player%").replacement(senderName).build())
                            .replaceText(TextReplacementConfig.builder().matchLiteral("%amount%").replacement(plugin.formatShort(finalAmount, finalCurrency)).build())
                            .replaceText(TextReplacementConfig.builder().matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(finalCurrency.getDisplayName())).build());
                    ((Player) target).sendMessage(receiverMsg);
                }
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (prefix.isEmpty() || name.toLowerCase().startsWith(prefix)) {
                    names.add(name);
                }
            }
            return names;
        } else if (args.length == 2) {
            return List.of("10", "100", "1000");
        } else if (args.length == 3) {
            String prefix = args[2].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (String id : plugin.getCurrencies().keySet()) {
                if (id.startsWith(prefix)) suggestions.add(id);
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
