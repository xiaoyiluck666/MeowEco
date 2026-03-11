package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;

import java.util.Collection;
import java.util.Collections;

public class MeowEcoAPIImpl implements MeowEcoAPI {

    private final MeowEco plugin;

    public MeowEcoAPIImpl(MeowEco plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<Currency> getRegisteredCurrencies() {
        return Collections.unmodifiableCollection(plugin.getCurrencies().values());
    }

    @Override
    public Currency getCurrency(String id) {
        return plugin.getCurrency(id);
    }

    private boolean isPositiveFinite(double amount) {
        return Double.isFinite(amount) && amount > 0.0;
    }

    @Override
    public double getBalance(java.util.UUID uuid, String currencyId) {
        return plugin.getDatabaseManager().getBalance(uuid, currencyId);
    }

    @Override
    public boolean deposit(java.util.UUID uuid, String currencyId, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        return plugin.getDatabaseManager().deposit(uuid, currencyId, amount);
    }

    @Override
    public boolean withdraw(java.util.UUID uuid, String currencyId, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        return plugin.getDatabaseManager().withdraw(uuid, currencyId, amount);
    }

    @Override
    public double getFrozenBalance(java.util.UUID uuid, String currencyId) {
        return plugin.getDatabaseManager().getFrozenBalance(uuid, currencyId);
    }

    @Override
    public double getAvailableBalance(java.util.UUID uuid, String currencyId) {
        double total = plugin.getDatabaseManager().getBalance(uuid, currencyId);
        double frozen = plugin.getDatabaseManager().getFrozenBalance(uuid, currencyId);
        return total - frozen;
    }

    @Override
    public boolean freeze(java.util.UUID uuid, String currencyId, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        return plugin.getDatabaseManager().freeze(uuid, currencyId, amount);
    }

    @Override
    public boolean unfreeze(java.util.UUID uuid, String currencyId, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        return plugin.getDatabaseManager().unfreeze(uuid, currencyId, amount);
    }

    @Override
    public boolean deductFrozen(java.util.UUID uuid, String currencyId, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        return plugin.getDatabaseManager().deductFrozen(uuid, currencyId, amount);
    }
}
