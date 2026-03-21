package com.xiaoyiluck.meoweco.utils;

import com.xiaoyiluck.meoweco.MeowEco;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class PlayerLookup {

    private PlayerLookup() {
    }

    public static Optional<OfflinePlayer> resolveOfflinePlayer(MeowEco plugin, String input) {
        if (plugin == null || input == null || input.isBlank()) {
            return Optional.empty();
        }

        String trimmedInput = input.trim();
        try {
            UUID uuid = UUID.fromString(trimmedInput);
            return Optional.of(plugin.getServer().getOfflinePlayer(uuid));
        } catch (IllegalArgumentException ignored) {
        }

        Player online = plugin.getServer().getPlayerExact(trimmedInput);
        if (online != null) {
            return Optional.of(online);
        }

        for (OfflinePlayer offlinePlayer : plugin.getServer().getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && name.equalsIgnoreCase(trimmedInput)) {
                return Optional.of(offlinePlayer);
            }
        }

        return plugin.getDatabaseManager()
                .findUuidByUsername(trimmedInput)
                .map(uuid -> plugin.getServer().getOfflinePlayer(uuid))
                .or(() -> {
                    OfflinePlayer fallback = plugin.getServer().getOfflinePlayer(trimmedInput);
                    if (fallback == null) {
                        return Optional.empty();
                    }

                    String fallbackName = fallback.getName();
                    if (fallback.isOnline()
                            || fallback.hasPlayedBefore()
                            || (fallbackName != null && fallbackName.equalsIgnoreCase(trimmedInput))) {
                        return Optional.of(fallback);
                    }
                    return Optional.empty();
                });
    }

    public static String getDisplayName(OfflinePlayer player, String fallback) {
        if (player != null) {
            String playerName = player.getName();
            if (playerName != null && !playerName.isBlank()) {
                return playerName;
            }
        }
        return (fallback == null || fallback.isBlank()) ? "Unknown" : fallback;
    }
}
