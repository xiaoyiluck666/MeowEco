package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.utils.PlayerLookup;
import net.kyori.adventure.text.Component;
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

public class PayCommand implements CommandExecutor, TabCompleter {

    private final MeowEco plugin;

    public PayCommand(MeowEco plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getComponent("not-player"));
            return true;
        }

        if (!sender.hasPermission("meoweco.pay")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getComponent("pay-usage"));
            return true;
        }

        Player player = (Player) sender;
        OfflinePlayer target = PlayerLookup.resolveOfflinePlayer(plugin, args[0]).orElse(null);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getComponent("player-not-found"));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
             sender.sendMessage(plugin.getConfigManager().getComponent("pay-failed-self"));
             return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getComponent("pay-failed-amount"));
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
        final String targetName = PlayerLookup.getDisplayName(target, args[0]);
        final String playerName = player.getName();

        // Pre-fetch templates
        Component playerNotFound = plugin.getConfigManager().getComponent("player-not-found");
        Component payFailedBalance = plugin.getConfigManager().getComponent("pay-failed-balance");
        Component successSenderTemplate = plugin.getConfigManager().getComponent("pay-success-sender");
        Component successSenderTaxTemplate = plugin.getConfigManager().getComponent("pay-success-sender-tax");
        Component successReceiverTemplate = plugin.getConfigManager().getComponent("pay-success-receiver");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.getDatabaseManager().hasAccount(target.getUniqueId(), finalCurrency.getId())) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(playerNotFound));
                return;
            }

            double taxRate = finalCurrency.getTransferTax();
            if (taxRate < 0) taxRate = 0;
            if (taxRate > 1.0) taxRate = 1.0;

            double tax = finalAmount * taxRate;
            double depositAmount = finalAmount - tax;

            boolean success;
            if (tax > 0) {
                success = plugin.getDatabaseManager().transfer(player.getUniqueId(), target.getUniqueId(), finalCurrency.getId(), finalAmount, depositAmount);
            } else {
                success = plugin.getDatabaseManager().transfer(player.getUniqueId(), target.getUniqueId(), finalCurrency.getId(), finalAmount);
            }
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    // Invalidate baltop cache
                    if (plugin.getBaltopCommand() != null) {
                        plugin.getBaltopCommand().invalidateCache();
                    }
                    
                    Component senderMsg;
                    if (tax > 0) {
                        senderMsg = successSenderTaxTemplate
                                .replaceText(config -> config.matchLiteral("%player%").replacement(targetName != null ? targetName : "Unknown"))
                                .replaceText(config -> config.matchLiteral("%amount%").replacement(plugin.formatShort(finalAmount, finalCurrency)))
                                .replaceText(config -> config.matchLiteral("%tax%").replacement(plugin.formatShort(tax, finalCurrency)))
                                .replaceText(config -> config.matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(finalCurrency.getDisplayName())));
                    } else {
                        senderMsg = successSenderTemplate
                                .replaceText(config -> config.matchLiteral("%player%").replacement(targetName != null ? targetName : "Unknown"))
                                .replaceText(config -> config.matchLiteral("%amount%").replacement(plugin.formatShort(finalAmount, finalCurrency)))
                                .replaceText(config -> config.matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(finalCurrency.getDisplayName())));
                    }
                    sender.sendMessage(senderMsg);
                    
                    if (target.isOnline()) {
                        Player targetPlayer = target.getPlayer();
                        if (targetPlayer != null) {
                            Component receiverMsg = successReceiverTemplate
                                    .replaceText(config -> config.matchLiteral("%player%").replacement(playerName))
                                    .replaceText(config -> config.matchLiteral("%amount%").replacement(plugin.formatShort(depositAmount, finalCurrency)))
                                    .replaceText(config -> config.matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(finalCurrency.getDisplayName())));
                            targetPlayer.sendMessage(receiverMsg);
                        }
                    }
                } else {
                    sender.sendMessage(payFailedBalance);
                }
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(((Player) sender).getUniqueId())) continue;
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
