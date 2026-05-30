package com.xiaoyiluck.meoweco.listeners;

import com.xiaoyiluck.meoweco.MeowEco;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final MeowEco plugin;

    public PlayerListener(MeowEco plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        final java.util.UUID uuid = player.getUniqueId();
        final String name = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (com.xiaoyiluck.meoweco.objects.Currency currency : plugin.getCurrencies().values()) {
                if (!plugin.getDatabaseManager().hasAccount(uuid, currency.getId())) {
                    plugin.getDatabaseManager().createAccount(uuid, currency.getId(), currency.getInitialBalance());
                }
            }
            plugin.getDatabaseManager().updatePlayerName(uuid, name);
        });

        // Update notification
        if (player.hasPermission("meoweco.admin") && plugin.isUpdateAvailable()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Component prefix = plugin.getConfigManager().getComponent("prefix");
                Component updateMsg = plugin.getConfigManager().getComponent("update-available")
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%version%").replacement(plugin.getUpdateChecker().getLatestVersion()).build());

                Component downloadLink = Component.text("[Click to Download]")
                        .color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.openUrl(plugin.getUpdateChecker().getDownloadUrl()));

                player.sendMessage(prefix.append(updateMsg).append(Component.space()).append(downloadLink));
            }, 40L); // 2 seconds delay
        }
    }
}
