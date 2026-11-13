package com.xiaoyiluck.meoweco.initial;

import com.xiaoyiluck.meoweco.MeowEco;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Reads signed session identity on the server thread; monetary writes stay on the database thread. */
public final class IdentityInitialBalanceService {
    private final MeowEco plugin;

    public IdentityInitialBalanceService(MeowEco plugin) {
        this.plugin = plugin;
    }

    public void onJoin(Player player) {
        if (plugin.getInitialBalancePolicies().isEmpty()) return;
        UUID uuid = player.getUniqueId();
        // Bounded retries cover MeowIdentity's 20/60/140/300 tick challenge retries.
        // Compare the Player object too: an old task must never authenticate a new connection.
        for (long delay : new long[]{40, 80, 160, 320, 640}) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || plugin.getServer().getPlayer(uuid) != player
                        || plugin.getInitialBalancePolicies().isEmpty()) return;
                var identities = plugin.getServer().getPluginManager().getPlugin("MeowIdentity");
                var placeholders = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
                if (identities == null || !identities.isEnabled()
                        || placeholders == null || !placeholders.isEnabled()) return;
                Boolean premium = InitialBalancePolicy.verifiedPremium(
                        PlaceholderAPI.setPlaceholders(player, "%meowidentity_verified%"),
                        PlaceholderAPI.setPlaceholders(player, "%meowidentity_premium%"));
                if (premium == null) return; // Unknown/unresolved is never treated as offline.
                List<String> currencies = List.copyOf(plugin.getInitialBalancePolicies().keySet());
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (var _ = plugin.getDatabaseManager().openAuditScope("identity_initial", "signed_session")) {
                        for (String currency : currencies) {
                            if (plugin.getDatabaseManager().settleInitialBalance(uuid, currency, premium)) {
                                plugin.invalidateVaultEconomyCache(uuid, currency);
                            }
                        }
                    }
                });
            }, delay);
        }
    }

}
