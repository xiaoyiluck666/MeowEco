package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PolicyCommand {
    private final MeowEco plugin;

    PolicyCommand(MeowEco plugin) {
        this.plugin = plugin;
    }

    boolean handleReport(CommandSender sender) {
        if (!sender.hasPermission("meoweco.admin")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }
        sender.sendMessage(Component.text("§bMeowEco monetary policy report (read-only)"));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PolicyRow> rows = new ArrayList<>();
            for (Currency currency : plugin.getCurrencies().values()) {
                String id = currency.getId();
                double total = plugin.getDatabaseManager().getTotalBalance(id);
                double available = plugin.getDatabaseManager().getAvailableTotalBalance(id);
                int accounts = plugin.getDatabaseManager().getAccountCount(id);
                Map<String, Double> top = plugin.getDatabaseManager().getTopAccounts(id, 10);
                double top10 = top.values().stream().mapToDouble(Double::doubleValue).sum();
                double top1 = top.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
                rows.add(new PolicyRow(id, total, available, accounts,
                        total > 0.0D ? top1 / total * 100.0D : 0.0D,
                        total > 0.0D ? top10 / total * 100.0D : 0.0D));
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (PolicyRow row : rows) {
                    Currency currency = plugin.getCurrency(row.currencyId());
                    String total = currency == null ? String.valueOf(row.total()) : plugin.formatBalance(row.total(), currency);
                    String available = currency == null ? String.valueOf(row.available()) : plugin.formatBalance(row.available(), currency);
                    sender.sendMessage(Component.text("§e" + row.currencyId()
                            + " §7accounts=§f" + row.accounts()
                            + " §7total=§f" + total
                            + " §7circulating=§f" + available
                            + " §7top1=§f" + formatPercent(row.top1Percent())
                            + " §7top10=§f" + formatPercent(row.top10Percent())));
                }
                if (rows.isEmpty()) {
                    sender.sendMessage(Component.text("§7No currencies are configured."));
                }
                sendRichTaxPolicy(sender);
                sender.sendMessage(Component.text("§7Report only: no balances or configuration were changed."));
            });
        });
        return true;
    }

    private void sendRichTaxPolicy(CommandSender sender) {
        boolean enabled = plugin.getConfig().getBoolean("rich-tax.enabled", false);
        sender.sendMessage(Component.text("§7rich-tax=§f" + (enabled ? "enabled" : "disabled")
                + " §7destination=§f" + plugin.getConfig().getString("rich-tax.destination.type", "system")));
        org.bukkit.configuration.ConfigurationSection rules = plugin.getConfig().getConfigurationSection("rich-tax.currencies");
        if (rules == null) {
            return;
        }
        for (String id : rules.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection rule = rules.getConfigurationSection(id);
            if (rule == null) {
                continue;
            }
            sender.sendMessage(Component.text("§7- " + id.toLowerCase(Locale.ROOT)
                    + " threshold=§f" + rule.getDouble("threshold", 0.0D)
                    + " §7rate=§f" + formatPercent(rule.getDouble("rate", 0.0D) * 100.0D)
                    + " §7enabled=§f" + rule.getBoolean("enabled", true)));
        }
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private record PolicyRow(String currencyId, double total, double available, int accounts,
                             double top1Percent, double top10Percent) {
    }
}
