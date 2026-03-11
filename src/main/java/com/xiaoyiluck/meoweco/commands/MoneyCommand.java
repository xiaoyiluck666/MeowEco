package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
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

public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final MeowEco plugin;

    public MoneyCommand(MeowEco plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("meoweco.balance")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            sender.sendMessage(Component.text("§c提示: 管理指令请使用 /eco <give|take|set> ..."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("exchange")) {
            if (!plugin.getConfig().getBoolean("exchange-rates.enabled", true)) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-subcommand"));
                return true;
            }
            return handleExchange(sender, args);
        }

        if (args.length > 2) {
            sender.sendMessage(plugin.getConfigManager().getComponent("money-usage"));
            return true;
        }

        OfflinePlayer target = null;
        Currency currency = plugin.getDefaultCurrency();

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().getComponent("not-player"));
                return true;
            }
            target = (Player) sender;
        } else if (args.length == 1) {
            // Check if it's a currency or a player
            Currency c = plugin.getCurrency(args[0]);
            if (c != null) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getComponent("not-player"));
                    return true;
                }
                target = (Player) sender;
                currency = c;
            } else {
                if (sender.hasPermission("meoweco.balance.other")) {
                    target = Bukkit.getOfflinePlayer(args[0]);
                } else {
                    // If no permission for others, treat it as currency if it was one, but it wasn't.
                    // Or if they just typed something, and they only have permission for self,
                    // we check if they are trying to check their own balance of a currency they mistyped.
                    sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                    return true;
                }
            }
        } else if (args.length == 2) {
            target = Bukkit.getOfflinePlayer(args[0]);
            currency = plugin.getCurrency(args[1]);
            
            if (currency == null) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                return true;
            }
            
            // Check permission for other
            boolean isSelfCheck = sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId());
            if (!isSelfCheck && !sender.hasPermission("meoweco.balance.other")) {
                sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
                return true;
            }
        }

        if (target == null) return true;

        final OfflinePlayer finalTarget = target;
        final Currency finalCurrency = currency;
        final boolean isSelf = sender instanceof Player && ((Player) sender).getUniqueId().equals(finalTarget.getUniqueId());
        
        // Pre-fetch templates
        Component playerNotFound = plugin.getConfigManager().getComponent("player-not-found");
        Component checkSelfTemplate = plugin.getConfigManager().getComponent("balance-check-self");
        Component checkSelfFrozenTemplate = plugin.getConfigManager().getComponent("balance-check-self-frozen");
        Component checkOtherTemplate = plugin.getConfigManager().getComponent("balance-check-other");
        Component checkOtherFrozenTemplate = plugin.getConfigManager().getComponent("balance-check-other-frozen");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.getDatabaseManager().hasAccount(finalTarget.getUniqueId(), finalCurrency.getId())) {
                 plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(playerNotFound));
                 return;
            }

            double balance = plugin.getDatabaseManager().getBalance(finalTarget.getUniqueId(), finalCurrency.getId());
            double frozen = plugin.getDatabaseManager().getFrozenBalance(finalTarget.getUniqueId(), finalCurrency.getId());
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                String targetName = finalTarget.getName();
                Component template;
                if (frozen > 0) {
                    template = isSelf ? checkSelfFrozenTemplate : checkOtherFrozenTemplate;
                } else {
                    template = isSelf ? checkSelfTemplate : checkOtherTemplate;
                }
                
                Component msg = template
                        .replaceText(config -> config.matchLiteral("%player%").replacement(targetName != null ? targetName : "Unknown"))
                        .replaceText(config -> config.matchLiteral("%amount%").replacement(plugin.formatShort(balance, finalCurrency)))
                        .replaceText(config -> config.matchLiteral("%frozen%").replacement(plugin.formatShort(frozen, finalCurrency)))
                        .replaceText(config -> config.matchLiteral("%currency%").replacement(plugin.getConfigManager().parseColor(finalCurrency.getDisplayName())));
                
                sender.sendMessage(msg);
            });
        });

        return true;
    }

    private boolean handleExchange(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getComponent("not-player"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getComponent("exchange-usage"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getComponent("pay-failed-amount"));
            return true;
        }

        Currency from = plugin.getCurrency(args[2]);
        Currency to = plugin.getCurrency(args[3]);

        if (from == null || to == null) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
            return true;
        }

        double rate = plugin.getExchangeRate(from.getId(), to.getId());
        if (rate <= 0 || !Double.isFinite(rate)) {
            sender.sendMessage(plugin.getConfigManager().getComponent("exchange-rate-not-set"));
            return true;
        }

        double resultAmount = amount * rate;
        if (!Double.isFinite(resultAmount) || resultAmount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.getDatabaseManager().hasAccount(player.getUniqueId(), from.getId())) {
                plugin.getDatabaseManager().createAccount(player.getUniqueId(), from.getId(), from.getInitialBalance());
            }
            if (!plugin.getDatabaseManager().hasAccount(player.getUniqueId(), to.getId())) {
                plugin.getDatabaseManager().createAccount(player.getUniqueId(), to.getId(), to.getInitialBalance());
            }

            boolean withdrew = plugin.getDatabaseManager().withdraw(player.getUniqueId(), from.getId(), amount);
            if (!withdrew) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getConfigManager().getComponent("pay-failed-balance")));
                return;
            }

            boolean deposited = plugin.getDatabaseManager().deposit(player.getUniqueId(), to.getId(), resultAmount);
            if (!deposited) {
                boolean rolledBack = plugin.getDatabaseManager().deposit(player.getUniqueId(), from.getId(), amount);
                if (!rolledBack) {
                    plugin.getLogger().severe("Exchange rollback failed for player " + player.getUniqueId() + ", from=" + from.getId() + ", to=" + to.getId() + ", amount=" + amount + ", result=" + resultAmount);
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Component.text("§c兑换失败：入账异常，已尝试回滚，请联系管理员。"));
                });
                return;
            }

            if (plugin.getBaltopCommand() != null) {
                plugin.getBaltopCommand().invalidateCache();
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(plugin.getConfigManager().getComponent("exchange-success")
                        .replaceText(config -> config.matchLiteral("%from_amount%").replacement(plugin.formatShort(amount, from)))
                        .replaceText(config -> config.matchLiteral("%from_currency%").replacement(plugin.getConfigManager().parseColor(from.getDisplayName())))
                        .replaceText(config -> config.matchLiteral("%to_amount%").replacement(plugin.formatShort(resultAmount, to)))
                        .replaceText(config -> config.matchLiteral("%to_currency%").replacement(plugin.getConfigManager().parseColor(to.getDisplayName()))));
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            
            // If called via /meco bal, only suggest currencies
            if (alias.equalsIgnoreCase("bal") || alias.equalsIgnoreCase("balance")) {
                for (String id : plugin.getCurrencies().keySet()) {
                    if (id.startsWith(prefix)) suggestions.add(id);
                }
                return suggestions;
            }

            if ("exchange".startsWith(prefix)) suggestions.add("exchange");
            
            // Add currencies
            for (String id : plugin.getCurrencies().keySet()) {
                if (id.startsWith(prefix)) suggestions.add(id);
            }
            
            // Add players if has permission
            if (sender.hasPermission("meoweco.balance.other")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String name = p.getName();
                    if (name.toLowerCase().startsWith(prefix)) {
                        suggestions.add(name);
                    }
                }
            }
            return suggestions;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("exchange")) return List.of("10", "100", "1000");
            
            // If first arg was a player or currency, second arg must be a currency
            String prefix = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (String id : plugin.getCurrencies().keySet()) {
                if (id.startsWith(prefix)) suggestions.add(id);
            }
            return suggestions;
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("exchange")) {
                String prefix = args[2].toLowerCase();
                List<String> suggestions = new ArrayList<>();
                for (String id : plugin.getCurrencies().keySet()) {
                    if (id.startsWith(prefix)) suggestions.add(id);
                }
                return suggestions;
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("exchange")) {
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
