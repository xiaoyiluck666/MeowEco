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

public class EcoCommand implements CommandExecutor, TabCompleter {

    private final MeowEco plugin;

    public EcoCommand(MeowEco plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getConfigManager().getComponent("eco-usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // Support /eco bal and /eco top for convenience
        if (sub.equals("bal") || sub.equals("balance") || sub.equals("money")) {
            String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            return new MoneyCommand(plugin).onCommand(sender, command, sub, subArgs);
        }
        if (sub.equals("top") || sub.equals("baltop")) {
            String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            return new BaltopCommand(plugin).onCommand(sender, command, sub, subArgs);
        }

        if (!sender.hasPermission("meoweco.admin")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }
        
        // Handle SetRate
        if (sub.equals("setrate")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getConfigManager().getComponent("eco-setrate-usage"));
                return true;
            }
            String fromId = args[1];
            String toId = args[2];
            double rate;
            try {
                rate = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
                return true;
            }

            if (plugin.getCurrency(fromId) == null || plugin.getCurrency(toId) == null) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                return true;
            }

            plugin.setExchangeRate(fromId, toId, rate);
            sender.sendMessage(plugin.getConfigManager().getComponent("eco-setrate-success")
                    .replaceText(config -> config.matchLiteral("%from%").replacement(fromId))
                    .replaceText(config -> config.matchLiteral("%to%").replacement(toId))
                    .replaceText(config -> config.matchLiteral("%rate%").replacement(String.valueOf(rate))));
            return true;
        }
        
        // Handle Refresh
        if (sub.equals("refresh")) {
             plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                 // 1. Clear memory caches immediately
                 if (plugin.getPlaceholders() != null) {
                     plugin.getPlaceholders().invalidateCache();
                 }
                 if (plugin.getBaltopCommand() != null) {
                     plugin.getBaltopCommand().invalidateCache();
                 }

                 // 2. Try to resolve unknown players
                 java.util.Map<java.util.UUID, String> unknowns = plugin.getDatabaseManager().getUnknownAccounts();
                 int fixed = 0;
                 for (java.util.Map.Entry<java.util.UUID, String> entry : unknowns.entrySet()) {
                     try {
                         OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                         if (p.getName() != null && !p.getName().equalsIgnoreCase("Unknown")) {
                             plugin.getDatabaseManager().updatePlayerName(entry.getKey(), p.getName());
                             fixed++;
                         }
                     } catch (Exception ignored) {}
                 }
                 
                 final int fixedCount = fixed;
                 plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(plugin.getConfigManager().getComponent("eco-refresh-success")
                            .replaceText(TextReplacementConfig.builder().matchLiteral("%count%").replacement(String.valueOf(fixedCount)).build()));
                });
             });
             return true;
        }
        
        // Handle Hide/Unhide
        if (sub.equals("hide") || sub.equals("unhide")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getConfigManager().getComponent("eco-hide-usage"));
                return true;
            }
            String targetName = args[1];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            boolean hidden = sub.equals("hide");
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                 boolean updated = plugin.getDatabaseManager().updateHidden(target.getUniqueId(), targetName, hidden);
                 plugin.getServer().getScheduler().runTask(plugin, () -> {
                     if (!updated) {
                         sender.sendMessage(plugin.getConfigManager().getComponent("player-not-found"));
                         return;
                     }

                     if (plugin.getBaltopCommand() != null) {
                         plugin.getBaltopCommand().invalidateCache();
                     }
                     if (plugin.getPlaceholders() != null) {
                         plugin.getPlaceholders().invalidateCache();
                     }

                     String key = hidden ? "eco-hide-success" : "eco-unhide-success";
                     sender.sendMessage(plugin.getConfigManager().getComponent(key)
                             .replaceText(TextReplacementConfig.builder().matchLiteral("%player%").replacement(target.getName() != null ? target.getName() : targetName).build()));
                 });
            });
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getComponent("eco-usage"));
            return true;
        }

        String requiredPermission = switch (sub) {
            case "give" -> "meoweco.eco.give";
            case "take" -> "meoweco.eco.take";
            case "set" -> "meoweco.eco.set";
            case "freeze" -> "meoweco.eco.freeze";
            case "unfreeze" -> "meoweco.eco.unfreeze";
            case "deductfrozen" -> "meoweco.eco.deductfrozen";
            default -> null;
        };

        if (requiredPermission == null) {
            sender.sendMessage(plugin.getConfigManager().getComponent("eco-usage"));
            return true;
        }

        if (!sender.hasPermission(requiredPermission)) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        if (!Double.isFinite(amount)) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        if (sub.equals("set")) {
            if (amount < 0) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
                return true;
            }
        } else if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        Currency currency = plugin.getDefaultCurrency();
        if (args.length >= 4) {
            currency = plugin.getCurrency(args[3]);
            if (currency == null) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                return true;
            }
        }

        final double finalAmount = amount;
        final Currency finalCurrency = currency;
        final OfflinePlayer finalTarget = target;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.getDatabaseManager().hasAccount(finalTarget.getUniqueId(), finalCurrency.getId())) {
                 plugin.getDatabaseManager().createAccount(finalTarget.getUniqueId(), finalCurrency.getId(), 0);
            }

            String msgKey;
            boolean success;
            switch (sub) {
                case "give":
                    success = plugin.getDatabaseManager().deposit(finalTarget.getUniqueId(), finalCurrency.getId(), finalAmount);
                    msgKey = "eco-give";
                    break;
                case "take":
                    success = plugin.getDatabaseManager().withdraw(finalTarget.getUniqueId(), finalCurrency.getId(), finalAmount);
                    msgKey = "eco-take";
                    break;
                case "set":
                    success = plugin.getDatabaseManager().updateBalance(finalTarget.getUniqueId(), finalCurrency.getId(), finalAmount);
                    msgKey = "eco-set";
                    break;
                case "freeze":
                    success = plugin.getDatabaseManager().freeze(finalTarget.getUniqueId(), finalCurrency.getId(), finalAmount);
                    msgKey = "eco-freeze-success";
                    break;
                case "unfreeze":
                    success = plugin.getDatabaseManager().unfreeze(finalTarget.getUniqueId(), finalCurrency.getId(), finalAmount);
                    msgKey = "eco-unfreeze-success";
                    break;
                case "deductfrozen":
                    success = plugin.getDatabaseManager().deductFrozen(finalTarget.getUniqueId(), finalCurrency.getId(), finalAmount);
                    msgKey = "eco-deductfrozen-success";
                    break;
                default:
                    return;
            }
            
            final String finalMsgKey = msgKey;
            final boolean finalSuccess = success;
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!finalSuccess) {
                    if (sub.equals("take")) {
                        sender.sendMessage(plugin.getConfigManager().getComponent("take-failed-balance"));
                    } else if (sub.equals("freeze")) {
                        sender.sendMessage(plugin.getConfigManager().getComponent("freeze-failed-balance"));
                    } else if (sub.equals("unfreeze")) {
                        sender.sendMessage(plugin.getConfigManager().getComponent("unfreeze-failed-balance"));
                    } else if (sub.equals("deductfrozen")) {
                        sender.sendMessage(plugin.getConfigManager().getComponent("deductfrozen-failed-balance"));
                    } else {
                        sender.sendMessage(Component.text("§c操作失败：无法更新数据库。请检查后台日志。"));
                    }
                    return;
                }
                
                // Invalidate baltop cache
                if (plugin.getBaltopCommand() != null) {
                    plugin.getBaltopCommand().invalidateCache();
                }
                
                Component msg = plugin.getConfigManager().getComponent(finalMsgKey)
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%player%").replacement(finalTarget.getName() != null ? finalTarget.getName() : "Unknown").build())
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%amount%").replacement(plugin.formatShort(finalAmount, finalCurrency)).build())
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(finalCurrency.getDisplayName())).build());
                sender.sendMessage(msg);
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> subs = new ArrayList<>();
            subs.add("bal");
            subs.add("top");
            if (sender.hasPermission("meoweco.eco.give")) subs.add("give");
            if (sender.hasPermission("meoweco.eco.take")) subs.add("take");
            if (sender.hasPermission("meoweco.eco.set")) subs.add("set");
            if (sender.hasPermission("meoweco.eco.freeze")) subs.add("freeze");
            if (sender.hasPermission("meoweco.eco.unfreeze")) subs.add("unfreeze");
            if (sender.hasPermission("meoweco.eco.deductfrozen")) subs.add("deductfrozen");
            if (sender.hasPermission("meoweco.admin")) {
                subs.add("hide");
                subs.add("unhide");
                subs.add("refresh");
                subs.add("setrate");
            }
            return subs.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("bal") || sub.equals("balance") || sub.equals("money")) {
            String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            return new MoneyCommand(plugin).onTabComplete(sender, command, sub, subArgs);
        }
        if (sub.equals("top") || sub.equals("baltop")) {
            String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            return new BaltopCommand(plugin).onTabComplete(sender, command, sub, subArgs);
        }

        if (args.length == 2) {
            if (sub.equals("refresh")) return Collections.emptyList();
            
            if (sub.equals("setrate")) {
                String prefix = args[1].toLowerCase();
                List<String> suggestions = new ArrayList<>();
                for (String id : plugin.getCurrencies().keySet()) {
                    if (id.startsWith(prefix)) suggestions.add(id);
                }
                return suggestions;
            }

            String prefix = args[1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (prefix.isEmpty() || name.toLowerCase().startsWith(prefix)) {
                    names.add(name);
                }
            }
            return names;
        }

        if (args.length == 3) {
            if (sub.equals("give") || sub.equals("take") || sub.equals("set") || sub.equals("freeze") || sub.equals("unfreeze") || sub.equals("deductfrozen")) {
                return List.of("10", "100", "1000");
            }
            if (sub.equals("setrate")) {
                String prefix = args[2].toLowerCase();
                List<String> suggestions = new ArrayList<>();
                for (String id : plugin.getCurrencies().keySet()) {
                    if (id.startsWith(prefix)) suggestions.add(id);
                }
                return suggestions;
            }
        }

        if (args.length == 4) {
            if (sub.equals("give") || sub.equals("take") || sub.equals("set") || sub.equals("freeze") || sub.equals("unfreeze") || sub.equals("deductfrozen")) {
                String prefix = args[3].toLowerCase();
                List<String> suggestions = new ArrayList<>();
                for (String id : plugin.getCurrencies().keySet()) {
                    if (id.startsWith(prefix)) suggestions.add(id);
                }
                return suggestions;
            }
        }

        return Collections.emptyList();
    }
}
