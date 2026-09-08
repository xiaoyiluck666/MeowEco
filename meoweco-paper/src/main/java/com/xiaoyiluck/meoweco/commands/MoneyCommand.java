package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.EconomyService;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import com.xiaoyiluck.meoweco.utils.AmountInput;
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

public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final MeowEco plugin;
    private final EconomyService economyService;

    public MoneyCommand(MeowEco plugin) {
        this.plugin = plugin;
        this.economyService = new EconomyService(plugin.getDatabaseManager());
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
                    target = PlayerLookup.resolveOfflinePlayer(plugin, args[0]).orElse(null);
                    if (target == null) {
                        sender.sendMessage(plugin.getConfigManager().getComponent("player-not-found"));
                        return true;
                    }
                } else {
                    sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                    return true;
                }
            }
        } else if (args.length == 2) {
            target = PlayerLookup.resolveOfflinePlayer(plugin, args[0]).orElse(null);
            currency = plugin.getCurrency(args[1]);

            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().getComponent("player-not-found"));
                return true;
            }

            if (currency == null) {
                sender.sendMessage(plugin.getConfigManager().getComponent("invalid-currency"));
                return true;
            }

            boolean isSelfCheck = sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId());
            if (!isSelfCheck && !sender.hasPermission("meoweco.balance.other")) {
                sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
                return true;
            }
        }

        if (target == null) {
            return true;
        }

        final OfflinePlayer finalTarget = target;
        final Currency finalCurrency = currency;
        final boolean isSelf = sender instanceof Player && ((Player) sender).getUniqueId().equals(finalTarget.getUniqueId());
        final String targetName = args.length > 0 ? PlayerLookup.getDisplayName(finalTarget, args[0]) : PlayerLookup.getDisplayName(finalTarget, "Unknown");

        Component playerNotFound = plugin.getConfigManager().getComponent("player-not-found");
        Component checkSelfTemplate = plugin.getConfigManager().getComponent("balance-check-self");
        Component checkSelfFrozenTemplate = plugin.getConfigManager().getComponent("balance-check-self-frozen");
        Component checkOtherTemplate = plugin.getConfigManager().getComponent("balance-check-other");
        Component checkOtherFrozenTemplate = plugin.getConfigManager().getComponent("balance-check-other-frozen");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            EconomyService.BalanceResult result = economyService.getBalance(finalTarget.getUniqueId(), finalCurrency);
            if (!result.exists()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(playerNotFound));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Component template = result.frozen() > 0
                        ? (isSelf ? checkSelfFrozenTemplate : checkOtherFrozenTemplate)
                        : (isSelf ? checkSelfTemplate : checkOtherTemplate);

                Component msg = template
                        .replaceText(config -> config.matchLiteral("%player%").replacement(targetName))
                        .replaceText(config -> config.matchLiteral("%amount%").replacement(plugin.formatShort(result.balance(), finalCurrency)))
                        .replaceText(config -> config.matchLiteral("%frozen%").replacement(plugin.formatShort(result.frozen(), finalCurrency)))
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
        if (!AmountInput.fitsCurrencyScaleOrNotify(plugin, sender, amount, from)) {
            return true;
        }

        double rate = plugin.getExchangeRate(from.getId(), to.getId());
        if (rate <= 0 || !Double.isFinite(rate)) {
            sender.sendMessage(plugin.getConfigManager().getComponent("exchange-rate-not-set"));
            return true;
        }

        double resultAmount = MoneyAmountPolicy.roundForStorage(amount * rate, to);
        if (!Double.isFinite(resultAmount) || resultAmount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getComponent("invalid-amount"));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            EconomyService.ExchangeResult exchangeResult;
            try (var _ = plugin.getDatabaseManager().openAuditScope("command.exchange", player.getName())) {
                economyService.ensureAccount(player.getUniqueId(), from);
                economyService.ensureAccount(player.getUniqueId(), to);
                exchangeResult = economyService.exchange(player.getUniqueId(), from, to, amount, rate);
            }
            boolean exchanged = exchangeResult.success();
            if (!exchanged) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getConfigManager().getComponent("pay-failed-balance")));
                return;
            }
            double exchangedAmount = exchangeResult.toAmount();

            if (plugin.getBaltopCommand() != null) {
                plugin.getBaltopCommand().invalidateCache();
            }
            plugin.invalidateVaultEconomyCache(player.getUniqueId(), from.getId());
            plugin.invalidateVaultEconomyCache(player.getUniqueId(), to.getId());

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(plugin.getConfigManager().getComponent("exchange-success")
                        .replaceText(config -> config.matchLiteral("%from_amount%").replacement(plugin.formatShort(amount, from)))
                        .replaceText(config -> config.matchLiteral("%from_currency%").replacement(plugin.getConfigManager().parseColor(from.getDisplayName())))
                        .replaceText(config -> config.matchLiteral("%to_amount%").replacement(plugin.formatShort(exchangedAmount, to)))
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
