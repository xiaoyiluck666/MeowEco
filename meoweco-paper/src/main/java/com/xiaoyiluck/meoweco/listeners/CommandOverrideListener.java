package com.xiaoyiluck.meoweco.listeners;

import com.xiaoyiluck.meoweco.MeowEco;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.HashSet;
import java.util.Set;

public class CommandOverrideListener implements Listener {

    private final MeowEco plugin;
    private final Set<String> targetCommands;

    public CommandOverrideListener(MeowEco plugin) {
        this.plugin = plugin;
        this.targetCommands = new HashSet<>();
        // Add all commands we want to override
        targetCommands.add("baltop");
        targetCommands.add("moneytop");
        targetCommands.add("ecotop");
        targetCommands.add("pay");
        targetCommands.add("money");
        targetCommands.add("balance");
        targetCommands.add("bal");
        // Remove "eco" from targetCommands to allow EcoCommand to handle it directly
        targetCommands.add("take"); // Explicit command if we want to hijack it
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        handleCommand(event, event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        handleCommand(event, event.getSender(), "/" + event.getCommand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (!buffer.startsWith("/")) return;
        
        String cleanLine = buffer.substring(1);
        String[] args = cleanLine.split(" ", -1);
        if (args.length == 0) return;
        
        String label = args[0].toLowerCase();
        if (targetCommands.contains(label)) {
            String primaryName = getPrimaryCommandName(label);
            PluginCommand unifiedCommand = plugin.getCommand("meoweco");
            
            if (unifiedCommand != null && unifiedCommand.getTabCompleter() != null) {
                String[] mecoArgs;
                if (primaryName.equals("baltop")) {
                    mecoArgs = new String[args.length];
                    mecoArgs[0] = "top";
                    System.arraycopy(args, 1, mecoArgs, 1, args.length - 1);
                } else if (primaryName.equals("money")) {
                    mecoArgs = new String[args.length];
                    mecoArgs[0] = "bal";
                    System.arraycopy(args, 1, mecoArgs, 1, args.length - 1);
                } else {
                    mecoArgs = args;
                }

                java.util.List<String> suggestions = unifiedCommand.getTabCompleter().onTabComplete(event.getSender(), unifiedCommand, "meco", mecoArgs);
                if (suggestions != null) {
                    event.setCompletions(suggestions);
                }
            }
        }
    }

    private void handleCommand(Cancellable event, CommandSender sender, String commandLine) {
        // Remove leading /
        String cleanLine = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String[] args = cleanLine.split(" ");
        if (args.length == 0) return;
        
        String label = args[0].toLowerCase();

        if (targetCommands.contains(label)) {
            // Redirect all overridden commands to /meco
            String primaryName = getPrimaryCommandName(label);
            PluginCommand unifiedCommand = plugin.getCommand("meoweco");
            
            if (unifiedCommand != null) {
                String[] mecoArgs;
                if (primaryName.equals("baltop")) {
                    mecoArgs = new String[args.length];
                    mecoArgs[0] = "top";
                    if (args.length > 1) {
                        System.arraycopy(args, 1, mecoArgs, 1, args.length - 1);
                    }
                } else if (primaryName.equals("money")) {
                    mecoArgs = new String[args.length];
                    mecoArgs[0] = "bal";
                    if (args.length > 1) {
                        System.arraycopy(args, 1, mecoArgs, 1, args.length - 1);
                    }
                } else {
                    mecoArgs = args;
                }

                unifiedCommand.getExecutor().onCommand(sender, unifiedCommand, "meco", mecoArgs);
                event.setCancelled(true);
            }
        }
    }

    private String getPrimaryCommandName(String label) {
        switch (label) {
            case "moneytop":
            case "ecotop":
                return "baltop";
            case "balance":
            case "bal":
                return "money";
            default:
                return label;
        }
    }
}
