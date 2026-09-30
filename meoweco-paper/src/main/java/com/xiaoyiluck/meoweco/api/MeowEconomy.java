package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.database.AccountBalance;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import com.xiaoyiluck.meoweco.utils.PlayerLookup;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation") // Implements the Classic Vault compatibility interface.
public class MeowEconomy implements Economy {
    // We don't store the plugin instance anymore to support hot reloading
    // Instead we use the static instance from MeowEco
    
    public MeowEconomy(MeowEco plugin) {
        // Constructor kept for compatibility, but we ignore the passed instance
        // for internal logic to ensure we always use the latest one.
    }
    
    private MeowEco getPlugin() {
        return MeowEco.getInstance();
    }

    private Currency resolveDefaultCurrency(MeowEco plugin) {
        Currency currency = plugin.getDefaultCurrency();
        if (currency != null) {
            return currency;
        }
        String currencyId = plugin.getDefaultCurrencyId();
        if (currencyId != null && !currencyId.isBlank()) {
            Currency configured = plugin.getCurrency(currencyId);
            if (configured != null) {
                return configured;
            }
        }
        return new Currency("coins", "Coins", "Coin", "Coins", 0.0, 2, 0.0);
    }

    private String resolveDefaultCurrencyId(MeowEco plugin) {
        String currencyId = plugin.getDefaultCurrencyId();
        if (currencyId != null && !currencyId.isBlank()) {
            return currencyId;
        }
        return resolveDefaultCurrency(plugin).getId();
    }

    private AccountBalance getDefaultCurrencyBalanceSnapshot(MeowEco plugin, UUID uuid, String currencyId) {
        return plugin.getDatabaseManager().findAccountBalance(uuid, currencyId)
                .orElseGet(() -> new AccountBalance(0.0D, 0.0D));
    }

    private OfflinePlayer resolveOfflinePlayer(MeowEco plugin, String playerName) {
        if (plugin == null) {
            return null;
        }
        return PlayerLookup.resolveOfflinePlayer(plugin, playerName).orElse(null);
    }

    private EconomyResponse playerNotFoundResponse() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
    }

    public void invalidateCache(UUID uuid, String currencyId) {
        // Retained for compatibility; Vault balance reads no longer use a local cache.
    }

    public void invalidateCache(UUID uuid) {
        // Retained for compatibility; Vault balance reads no longer use a local cache.
    }

    public void invalidateAllCache() {
        // Retained for compatibility; Vault balance reads no longer use a local cache.
    }

    @Override
    public boolean isEnabled() {
        MeowEco plugin = getPlugin();
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "MeowEco";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        MeowEco plugin = getPlugin();
        if (plugin == null) return 2;
        return resolveDefaultCurrency(plugin).getDecimalPlaces();
    }

    @Override
    public String format(double amount) {
        MeowEco plugin = getPlugin();
        if (plugin == null) return String.valueOf(amount);
        Currency currency = resolveDefaultCurrency(plugin);
        return plugin.formatShort(amount, currency) + " " + currency.getPlural();
    }

    @Override
    public String currencyNamePlural() {
        MeowEco plugin = getPlugin();
        if (plugin == null) return "Coins";
        return resolveDefaultCurrency(plugin).getPlural();
    }

    @Override
    public String currencyNameSingular() {
        MeowEco plugin = getPlugin();
        if (plugin == null) return "Coin";
        return resolveDefaultCurrency(plugin).getSingular();
    }

    @Override
    public boolean has(String playerName, double amount) {
        MeowEco plugin = getPlugin();
        OfflinePlayer player = resolveOfflinePlayer(plugin, playerName);
        return player != null && has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (!isEnabled()) return false;
        MeowEco plugin = getPlugin();
        Currency currency = resolveDefaultCurrency(plugin);
        if (!MoneyAmountPolicy.isValidNonNegativeInput(amount, currency)) return false;
        String currencyId = resolveDefaultCurrencyId(plugin);
        AccountBalance snapshot = getDefaultCurrencyBalanceSnapshot(plugin, player.getUniqueId(), currencyId);
        return snapshot.availableBalance() >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean hasAccount(String playerName) {
        MeowEco plugin = getPlugin();
        OfflinePlayer player = resolveOfflinePlayer(plugin, playerName);
        return player != null && hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        if (!isEnabled()) return false;
        MeowEco plugin = getPlugin();
        return plugin.getDatabaseManager().hasAccount(player.getUniqueId(), resolveDefaultCurrencyId(plugin));
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        MeowEco plugin = getPlugin();
        OfflinePlayer player = resolveOfflinePlayer(plugin, playerName);
        return player == null ? 0.0D : getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) return 0.0;
        MeowEco plugin = getPlugin();
        String currencyId = resolveDefaultCurrencyId(plugin);
        return getDefaultCurrencyBalanceSnapshot(plugin, player.getUniqueId(), currencyId).balance();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        MeowEco plugin = getPlugin();
        OfflinePlayer player = resolveOfflinePlayer(plugin, playerName);
        return player != null && createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (!isEnabled()) return false;
        MeowEco plugin = getPlugin();
        Currency def = resolveDefaultCurrency(plugin);
        boolean success;
        try (@SuppressWarnings("unusedLocal") var auditScope = plugin.getDatabaseManager().openAuditScope("vault", "external_plugin")) {
            success = plugin.getDatabaseManager().createAccount(player.getUniqueId(), def.getId(), def.getInitialBalance());
        }
        return success;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        MeowEco plugin = getPlugin();
        OfflinePlayer player = resolveOfflinePlayer(plugin, playerName);
        return player == null ? playerNotFoundResponse() : withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Plugin is disabled");
        }
        MeowEco plugin = getPlugin();
        return operations(plugin).withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        MeowEco plugin = getPlugin();
        OfflinePlayer player = resolveOfflinePlayer(plugin, playerName);
        return player == null ? playerNotFoundResponse() : depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Plugin is disabled");
        }
        MeowEco plugin = getPlugin();
        return operations(plugin).deposit(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // Bank methods are not supported
    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    private VaultEconomyOperations operations(MeowEco plugin) {
        return new VaultEconomyOperations(plugin.getDatabaseManager(), plugin.getEconomyService(),
                resolveDefaultCurrency(plugin));
    }

}
