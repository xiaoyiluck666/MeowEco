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
        Component header = plugin.getConfigManager().getComponent("policy-report-header");
        Component rowTemplate = plugin.getConfigManager().getComponent("policy-report-row");
        Component noCurrencies = plugin.getConfigManager().getComponent("policy-report-no-currencies");
        Component richTaxTemplate = plugin.getConfigManager().getComponent("policy-report-rich-tax");
        Component richTaxRuleTemplate = plugin.getConfigManager().getComponent("policy-report-rich-tax-rule");
        Component footer = plugin.getConfigManager().getComponent("policy-report-footer");
        Component enabledLabel = plugin.getConfigManager().getMessageComponent("policy-status-enabled");
        Component disabledLabel = plugin.getConfigManager().getMessageComponent("policy-status-disabled");
        Component systemLabel = plugin.getConfigManager().getMessageComponent("policy-destination-system");
        Component playerLabel = plugin.getConfigManager().getMessageComponent("policy-destination-player");

        sender.sendMessage(header);
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
                    sender.sendMessage(rowTemplate
                            .replaceText(config -> config.matchLiteral("%currency%").replacement(row.currencyId()))
                            .replaceText(config -> config.matchLiteral("%accounts%").replacement(String.valueOf(row.accounts())))
                            .replaceText(config -> config.matchLiteral("%total%").replacement(total))
                            .replaceText(config -> config.matchLiteral("%circulating%").replacement(available))
                            .replaceText(config -> config.matchLiteral("%top1%").replacement(formatPercent(row.top1Percent())))
                            .replaceText(config -> config.matchLiteral("%top10%").replacement(formatPercent(row.top10Percent()))));
                }
                if (rows.isEmpty()) {
                    sender.sendMessage(noCurrencies);
                }
                sendRichTaxPolicy(sender, richTaxTemplate, richTaxRuleTemplate, enabledLabel, disabledLabel, systemLabel, playerLabel);
                sender.sendMessage(footer);
            });
        });
        return true;
    }

    private void sendRichTaxPolicy(CommandSender sender, Component summaryTemplate, Component ruleTemplate,
                                   Component enabledLabel, Component disabledLabel,
                                   Component systemLabel, Component playerLabel) {
        boolean enabled = plugin.getConfig().getBoolean("rich-tax.enabled", false);
        String destination = plugin.getConfig().getString("rich-tax.destination.type", "system");
        Component destinationLabel = "player".equalsIgnoreCase(destination) ? playerLabel : systemLabel;
        sender.sendMessage(summaryTemplate
                .replaceText(config -> config.matchLiteral("%status%").replacement(enabled ? enabledLabel : disabledLabel))
                .replaceText(config -> config.matchLiteral("%destination%").replacement(destinationLabel)));
        org.bukkit.configuration.ConfigurationSection rules = plugin.getConfig().getConfigurationSection("rich-tax.currencies");
        if (rules == null) {
            return;
        }
        for (String id : rules.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection rule = rules.getConfigurationSection(id);
            if (rule == null) {
                continue;
            }
            Component enabledValue = rule.getBoolean("enabled", true) ? enabledLabel : disabledLabel;
            sender.sendMessage(ruleTemplate
                    .replaceText(config -> config.matchLiteral("%currency%").replacement(id.toLowerCase(Locale.ROOT)))
                    .replaceText(config -> config.matchLiteral("%threshold%").replacement(String.valueOf(rule.getDouble("threshold", 0.0D))))
                    .replaceText(config -> config.matchLiteral("%rate%").replacement(formatPercent(rule.getDouble("rate", 0.0D) * 100.0D)))
                    .replaceText(config -> config.matchLiteral("%enabled%").replacement(enabledValue)));
        }
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private record PolicyRow(String currencyId, double total, double available, int accounts,
                             double top1Percent, double top10Percent) {
    }
}
