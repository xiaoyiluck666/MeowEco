package com.xiaoyiluck.meoweco.database;

import java.util.Map;
import java.util.UUID;

public interface DatabaseManager {
    void init();
    void close();
    
    boolean hasAccount(UUID uuid, String currency);
    boolean createAccount(UUID uuid, String currency, double initialBalance);
    
    double getBalance(UUID uuid, String currency);
    void setBalance(UUID uuid, String currency, double amount);
    boolean deposit(UUID uuid, String currency, double amount);
    boolean withdraw(UUID uuid, String currency, double amount);
    boolean transfer(UUID from, UUID to, String currency, double amount);
    boolean transfer(UUID from, UUID to, String currency, double withdrawAmount, double depositAmount);

    double getFrozenBalance(UUID uuid, String currency);
    void setFrozenBalance(UUID uuid, String currency, double amount);
    boolean freeze(UUID uuid, String currency, double amount);
    boolean unfreeze(UUID uuid, String currency, double amount);
    boolean deductFrozen(UUID uuid, String currency, double amount);

    void updatePlayerName(UUID uuid, String name);

    Map<String, Double> getTopAccounts(String currency, int limit);
    Map<UUID, Double> getAccountsAboveBalance(String currency, double minimumBalance);
    double getTotalBalance(String currency);
    
    void setHidden(UUID uuid, boolean hidden);
    boolean isHidden(UUID uuid);
    
    Map<UUID, String> getUnknownAccounts();
}
