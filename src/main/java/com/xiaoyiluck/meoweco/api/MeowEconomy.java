package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.MeowEco;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

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
        return plugin.getDefaultCurrency().getDecimalPlaces();
    }

    @Override
    public String format(double amount) {
        MeowEco plugin = getPlugin();
        if (plugin == null) return String.valueOf(amount);
        return plugin.formatShort(amount, plugin.getDefaultCurrency()) + " " + currencyNamePlural();
    }

    @Override
    public String currencyNamePlural() {
        MeowEco plugin = getPlugin();
        if (plugin == null) return "Coins";
        return plugin.getDefaultCurrency().getPlural();
    }

    @Override
    public String currencyNameSingular() {
        MeowEco plugin = getPlugin();
        if (plugin == null) return "Coin";
        return plugin.getDefaultCurrency().getSingular();
    }

    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (!isEnabled()) return false;
        MeowEco plugin = getPlugin();
        double total = plugin.getDatabaseManager().getBalance(player.getUniqueId(), plugin.getDefaultCurrencyId());
        double frozen = plugin.getDatabaseManager().getFrozenBalance(player.getUniqueId(), plugin.getDefaultCurrencyId());
        return (total - frozen) >= amount;
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
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        if (!isEnabled()) return false;
        MeowEco plugin = getPlugin();
        return plugin.getDatabaseManager().hasAccount(player.getUniqueId(), plugin.getDefaultCurrencyId());
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
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) return 0.0;
        MeowEco plugin = getPlugin();
        return plugin.getDatabaseManager().getBalance(player.getUniqueId(), plugin.getDefaultCurrencyId());
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
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (!isEnabled()) return false;
        MeowEco plugin = getPlugin();
        com.xiaoyiluck.meoweco.objects.Currency def = plugin.getDefaultCurrency();
        return plugin.getDatabaseManager().createAccount(player.getUniqueId(), def.getId(), def.getInitialBalance());
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
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Plugin is disabled");
        }
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        
        MeowEco plugin = getPlugin();
        boolean success = plugin.getDatabaseManager().withdraw(player.getUniqueId(), plugin.getDefaultCurrencyId(), amount);
        double balance = getBalance(player);
        
        if (success) {
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
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
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Plugin is disabled");
        }
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        
        MeowEco plugin = getPlugin();
        plugin.getDatabaseManager().deposit(player.getUniqueId(), plugin.getDefaultCurrencyId(), amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
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
}
