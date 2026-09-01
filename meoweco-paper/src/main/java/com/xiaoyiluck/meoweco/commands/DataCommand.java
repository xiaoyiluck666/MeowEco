package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.database.AuditEntry;
import com.xiaoyiluck.meoweco.database.MigrationBalance;
import com.xiaoyiluck.meoweco.database.MigrationResult;
import com.xiaoyiluck.meoweco.migration.MigrationPreview;
import com.xiaoyiluck.meoweco.migration.MigrationService;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import com.xiaoyiluck.meoweco.utils.PlayerLookup;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class DataCommand {
    private static final DateTimeFormatter AUDIT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final MeowEco plugin;
    private final MigrationService migrationService;

    DataCommand(MeowEco plugin) {
        this.plugin = plugin;
        this.migrationService = new MigrationService(plugin);
    }

    boolean handleMigration(CommandSender sender, String[] args) {
        if (!sender.hasPermission("meoweco.admin")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendMigrationUsage(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("sources")) {
            List<String> sources = migrationService.vaultSources();
            sender.sendMessage(Component.text("§bVault migration sources: §f"
                    + (sources.isEmpty() ? "none detected" : String.join(", ", sources))));
            sender.sendMessage(Component.text("§7Offline adapters: essentials, csv"));
            return true;
        }

        String adapter = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) {
            sendMigrationUsage(sender);
            return true;
        }
        Currency currency = plugin.getCurrency(args[1]);
        if (currency == null) {
            sender.sendMessage(Component.text("§cUnknown target currency: " + args[1]));
            return true;
        }
        boolean apply = Arrays.stream(args).anyMatch("--apply"::equalsIgnoreCase);

        try {
            MigrationPreview rawPreview = switch (adapter) {
                case "vault" -> migrationService.previewVault(optionalArgument(args, 2));
                case "essentials", "essentialsx" -> migrationService.previewEssentials(
                        optionalArgument(args, 2) == null
                                ? migrationService.defaultEssentialsUserdata()
                                : Path.of(optionalArgument(args, 2)));
                case "csv" -> {
                    String fileName = optionalArgument(args, 2);
                    if (fileName == null) {
                        throw new IllegalArgumentException("CSV adapter requires a file name from plugins/MeowEco/migration-input/.");
                    }
                    yield migrationService.previewCsv(migrationService.resolveInput(fileName));
                }
                default -> throw new IllegalArgumentException("Unknown adapter: " + adapter);
            };
            MigrationPreview preview = normalizePreview(rawPreview, currency);
            sendPreview(sender, currency, preview, apply);
            if (!apply || preview.isEmpty()) {
                return true;
            }

            String actor = sender.getName();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> applyMigration(sender, actor, currency, preview));
        } catch (IOException | IllegalArgumentException exception) {
            sender.sendMessage(Component.text("§cMigration preview failed: " + exception.getMessage()));
        }
        return true;
    }

    boolean handleAudit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("meoweco.admin")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("§cUsage: /meco audit <player> [currency] [limit] | /meco audit export [limit]"));
            return true;
        }
        if (args[0].equalsIgnoreCase("export")) {
            int limit = parseLimit(args.length > 1 ? args[1] : null, 10000, 10000);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<AuditEntry> entries = plugin.getDatabaseManager().getRecentAudit(limit);
                try {
                    Path output = migrationService.exportAudit(entries);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(Component.text("§aExported " + entries.size() + " audit rows to §f" + output)));
                } catch (IOException exception) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(Component.text("§cAudit export failed: " + exception.getMessage())));
                }
            });
            return true;
        }

        OfflinePlayer player = PlayerLookup.resolveOfflinePlayer(plugin, args[0]).orElse(null);
        if (player == null) {
            sender.sendMessage(plugin.getConfigManager().getComponent("player-not-found"));
            return true;
        }
        String currency = args.length > 1 && !args[1].equalsIgnoreCase("all") ? args[1].toLowerCase(Locale.ROOT) : null;
        if (currency != null && plugin.getCurrency(currency) == null) {
            sender.sendMessage(Component.text("§cUnknown currency: " + currency));
            return true;
        }
        int limit = parseLimit(args.length > 2 ? args[2] : null, 10, 50);
        String selectedCurrency = currency;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<AuditEntry> entries = plugin.getDatabaseManager().getAuditHistory(player.getUniqueId(), selectedCurrency, limit);
            plugin.getServer().getScheduler().runTask(plugin, () -> sendAudit(sender, player, entries));
        });
        return true;
    }

    List<String> tabCompleteMigration(String[] args) {
        if (args.length == 1) {
            return filter(List.of("sources", "vault", "essentials", "csv"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("sources")) {
            return filter(new ArrayList<>(plugin.getCurrencies().keySet()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("vault")) {
            return filter(migrationService.vaultSources(), args[2]);
        }
        if (args.length >= 3) {
            return filter(List.of("--apply"), args[args.length - 1]);
        }
        return List.of();
    }

    List<String> tabCompleteAudit(String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("export"));
            plugin.getServer().getOnlinePlayers().forEach(player -> values.add(player.getName()));
            return filter(values, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("export")) {
            List<String> currencies = new ArrayList<>(List.of("all"));
            currencies.addAll(plugin.getCurrencies().keySet());
            return filter(currencies, args[1]);
        }
        return List.of();
    }

    private MigrationPreview normalizePreview(MigrationPreview preview, Currency currency) {
        int rounded = 0;
        List<MigrationBalance> balances = new ArrayList<>();
        for (MigrationBalance balance : preview.balances()) {
            double normalized = MoneyAmountPolicy.roundForStorage(balance.balance(), currency);
            if (Double.compare(normalized, balance.balance()) != 0) {
                rounded++;
            }
            balances.add(new MigrationBalance(balance.uuid(), balance.username(), normalized));
        }
        List<String> warnings = new ArrayList<>(preview.warnings());
        if (rounded > 0) {
            warnings.add(rounded + " balances will be rounded to " + currency.getDecimalPlaces() + " decimal places.");
        }
        double total = balances.stream().mapToDouble(MigrationBalance::balance).sum();
        return new MigrationPreview(preview.adapter(), preview.source(), List.copyOf(balances),
                preview.skippedEntries(), total, List.copyOf(warnings));
    }

    private void sendPreview(CommandSender sender, Currency currency, MigrationPreview preview, boolean apply) {
        sender.sendMessage(Component.text("§bMigration preview §7[" + preview.adapter() + "]"));
        sender.sendMessage(Component.text("§7Source: §f" + preview.source()));
        sender.sendMessage(Component.text("§7Target: §f" + currency.getId()
                + " §7Accounts: §f" + preview.balances().size()
                + " §7Total: §f" + plugin.formatBalance(preview.totalBalance(), currency)
                + " §7Skipped: §f" + preview.skippedEntries()));
        preview.warnings().stream().limit(5).forEach(warning -> sender.sendMessage(Component.text("§e- " + warning)));
        if (!apply) {
            sender.sendMessage(Component.text("§eDry-run only. Repeat the command with §f--apply §eto create a backup and import."));
        }
    }

    private void applyMigration(CommandSender sender, String actor, Currency currency, MigrationPreview preview) {
        try {
            Path backup = migrationService.writeBackup(currency.getId(), preview.balances());
            MigrationResult result = plugin.getDatabaseManager().importBalances(
                    preview.balances(), currency.getId(), "migration." + preview.adapter(), actor);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!result.success()) {
                    sender.sendMessage(Component.text("§cMigration rolled back: " + result.error()));
                    sender.sendMessage(Component.text("§7Pre-migration backup: " + backup));
                    return;
                }
                plugin.invalidateVaultEconomyCache();
                if (plugin.getBaltopCommand() != null) {
                    plugin.getBaltopCommand().invalidateCache();
                }
                sender.sendMessage(Component.text("§aMigration complete: §f" + result.importedAccounts()
                        + " §aaccounts (§f" + result.createdAccounts() + "§a created, §f"
                        + result.updatedAccounts() + "§a updated)."));
                sender.sendMessage(Component.text("§7Transaction: " + result.transactionId()));
                sender.sendMessage(Component.text("§7Rollback CSV: " + backup));
            });
        } catch (IOException exception) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sender.sendMessage(Component.text("§cMigration stopped before import: " + exception.getMessage())));
        }
    }

    private void sendAudit(CommandSender sender, OfflinePlayer player, List<AuditEntry> entries) {
        sender.sendMessage(Component.text("§bAudit history for §f" + PlayerLookup.getDisplayName(player, player.getUniqueId().toString())));
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("§7No audit entries found."));
            return;
        }
        for (AuditEntry entry : entries) {
            sender.sendMessage(Component.text("§7" + AUDIT_TIME.format(entry.createdAt())
                    + " §e" + entry.operation() + " §f" + entry.amount() + " " + entry.currency()
                    + " §8(" + entry.balanceBefore() + " -> " + entry.balanceAfter()
                    + ", " + entry.source() + "/" + entry.actor() + ")"));
        }
    }

    private String optionalArgument(String[] args, int index) {
        return index < args.length && !args[index].startsWith("--") ? args[index] : null;
    }

    private int parseLimit(String value, int fallback, int maximum) {
        try {
            return Math.max(1, Math.min(maximum, Integer.parseInt(value)));
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private void sendMigrationUsage(CommandSender sender) {
        sender.sendMessage(Component.text("§cUsage:"));
        sender.sendMessage(Component.text("§7/meco migrate sources"));
        sender.sendMessage(Component.text("§7/meco migrate vault <currency> [provider] [--apply]"));
        sender.sendMessage(Component.text("§7/meco migrate essentials <currency> [userdata-path] [--apply]"));
        sender.sendMessage(Component.text("§7/meco migrate csv <currency> <file> [--apply]"));
    }
}
