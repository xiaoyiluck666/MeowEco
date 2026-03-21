package com.xiaoyiluck.meoweco.commands;

import com.xiaoyiluck.meoweco.MeowEco;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MeowEcoCommand implements CommandExecutor, TabCompleter {

    private final MeowEco plugin;
    private final MoneyCommand moneyCommand;
    private final PayCommand payCommand;
    private final TakeCommand takeCommand;
    private final EcoCommand ecoCommand;
    private final BaltopCommand baltopCommand;

    public MeowEcoCommand(MeowEco plugin) {
        this.plugin = plugin;
        this.moneyCommand = new MoneyCommand(plugin);
        this.payCommand = new PayCommand(plugin);
        this.takeCommand = new TakeCommand(plugin);
        this.ecoCommand = new EcoCommand(plugin);
        this.baltopCommand = new BaltopCommand(plugin);
    }

    public MoneyCommand getMoneyCommand() {
        return moneyCommand;
    }

    public PayCommand getPayCommand() {
        return payCommand;
    }

    public TakeCommand getTakeCommand() {
        return takeCommand;
    }

    public EcoCommand getEcoCommand() {
        return ecoCommand;
    }

    public BaltopCommand getBaltopCommand() {
        return baltopCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "balance":
            case "bal":
            case "money":
                return moneyCommand.onCommand(sender, command, sub, subArgs);
            case "pay":
                return payCommand.onCommand(sender, command, sub, subArgs);
            case "take":
                return takeCommand.onCommand(sender, command, sub, subArgs);
            case "give":
            case "set":
            case "setrate":
            case "hide":
            case "unhide":
            case "refresh":
            case "freeze":
            case "unfreeze":
            case "deductfrozen":
                String[] ecoArgs = new String[subArgs.length + 1];
                ecoArgs[0] = sub;
                System.arraycopy(subArgs, 0, ecoArgs, 1, subArgs.length);
                return ecoCommand.onCommand(sender, command, sub, ecoArgs);
            case "top":
            case "baltop":
                return baltopCommand.onCommand(sender, command, sub, subArgs);
            case "reload":
                if (!sender.hasPermission("meoweco.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getComponent("reload-success"));
                return true;
            case "checkupdate":
                if (!sender.hasPermission("meoweco.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
                    return true;
                }
                if (plugin.getUpdateChecker() == null) {
                    sender.sendMessage(Component.text("§cUpdate checker is disabled in config.yml"));
                    return true;
                }
                sender.sendMessage(Component.text("§eChecking for updates..."));
                plugin.getUpdateChecker().check().thenAccept(available ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (available) {
                                Component prefix = plugin.getConfigManager().getComponent("prefix");
                                Component updateMsg = plugin.getConfigManager().getComponent("update-available")
                                        .replaceText(TextReplacementConfig.builder().matchLiteral("%version%").replacement(plugin.getUpdateChecker().getLatestVersion()).build());

                                Component downloadLink = Component.text("[Click to Download]")
                                        .color(NamedTextColor.YELLOW)
                                        .clickEvent(ClickEvent.openUrl(plugin.getUpdateChecker().getDownloadUrl()));

                                sender.sendMessage(prefix.append(updateMsg).append(Component.space()).append(downloadLink));
                            } else {
                                sender.sendMessage(Component.text("§aMeowEco is up to date! (v" + plugin.getPluginMeta().getVersion() + ")"));
                            }
                        })
                );
                return true;
            case "debug":
                if (!sender.hasPermission("meoweco.debug")) {
                    sender.sendMessage(plugin.getConfigManager().getComponent("no-permission"));
                    return true;
                }
                if (subArgs.length > 0 && subArgs[0].equalsIgnoreCase("currencies")) {
                    sender.sendMessage(Component.text("§bLoaded Currencies:"));
                    for (String id : plugin.getCurrencies().keySet()) {
                        com.xiaoyiluck.meoweco.objects.Currency c = plugin.getCurrency(id);
                        if (c != null) {
                            sender.sendMessage(Component.text("§7- §e" + id + " §f(Name: " + c.getDisplayName() + "§f)"));
                        }
                    }
                    return true;
                }
                boolean newState = !plugin.isDebug();
                plugin.setDebug(newState);
                sender.sendMessage(Component.text("§a[MeowEco] Debug mode " + (newState ? "§eENABLED" : "§cDISABLED")));
                return true;
            case "exchange":
                String[] exchangeArgs = new String[args.length];
                exchangeArgs[0] = "exchange";
                System.arraycopy(subArgs, 0, exchangeArgs, 1, subArgs.length);
                return moneyCommand.onCommand(sender, command, "money", exchangeArgs);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getComponent("help-header"));
        sender.sendMessage(plugin.getConfigManager().getComponent("help-bal"));
        sender.sendMessage(plugin.getConfigManager().getComponent("help-pay"));
        sender.sendMessage(plugin.getConfigManager().getComponent("help-top"));
        if (plugin.getConfig().getBoolean("exchange-rates.enabled", true)) {
            sender.sendMessage(plugin.getConfigManager().getComponent("help-exchange"));
        }
        if (sender.hasPermission("meoweco.admin")) {
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-give"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-take"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-set"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-setrate"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-hide"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-unhide"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-refresh"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-reload"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-checkupdate"));
            sender.sendMessage(plugin.getConfigManager().getComponent("help-admin-debug"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("bal", "pay", "top"));
            if (plugin.getConfig().getBoolean("exchange-rates.enabled", true)) {
                subs.add("exchange");
            }
            if (sender.hasPermission("meoweco.admin")) {
                subs.addAll(List.of("give", "take", "set", "freeze", "unfreeze", "deductfrozen", "setrate", "reload", "debug", "refresh", "hide", "unhide", "checkupdate"));
            }
            String prefix = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "balance":
            case "bal":
            case "money":
                return moneyCommand.onTabComplete(sender, command, sub, subArgs);
            case "pay":
                return payCommand.onTabComplete(sender, command, sub, subArgs);
            case "take":
                return takeCommand.onTabComplete(sender, command, sub, subArgs);
            case "give":
            case "set":
            case "freeze":
            case "unfreeze":
            case "deductfrozen":
            case "setrate":
            case "hide":
            case "unhide":
            case "refresh":
                String[] ecoTabArgs = new String[subArgs.length + 1];
                ecoTabArgs[0] = sub;
                System.arraycopy(subArgs, 0, ecoTabArgs, 1, subArgs.length);
                return ecoCommand.onTabComplete(sender, command, sub, ecoTabArgs);
            case "top":
            case "baltop":
                return baltopCommand.onTabComplete(sender, command, sub, subArgs);
            case "debug":
                if (subArgs.length == 1) {
                    return List.of("currencies").stream().filter(s -> s.startsWith(subArgs[0].toLowerCase())).toList();
                }
                return Collections.emptyList();
            case "exchange":
                String[] exchangeArgs = new String[args.length];
                exchangeArgs[0] = "exchange";
                System.arraycopy(subArgs, 0, exchangeArgs, 1, subArgs.length);
                return moneyCommand.onTabComplete(sender, command, "money", exchangeArgs);
        }

        return Collections.emptyList();
    }
}
