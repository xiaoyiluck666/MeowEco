package com.xiaoyiluck.meoweco.service;

import com.xiaoyiluck.meoweco.database.DatabaseManager;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryDatabaseManager implements DatabaseManager {
    private final Map<Key, Account> accounts = new HashMap<>();
    private final Map<UUID, Boolean> hidden = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    @Override
    public void init() {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean hasAccount(UUID uuid, String currency) {
        return accounts.containsKey(new Key(uuid, currency));
    }

    @Override
    public boolean createAccount(UUID uuid, String currency, double initialBalance) {
        Key key = new Key(uuid, currency);
        if (accounts.containsKey(key)) {
            return false;
        }
        accounts.put(key, new Account(initialBalance, 0.0D));
        return true;
    }

    @Override
    public double getBalance(UUID uuid, String currency) {
        return account(uuid, currency).balance;
    }

    @Override
    public boolean updateBalance(UUID uuid, String currency, double amount) {
        account(uuid, currency).balance = amount;
        return true;
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        updateBalance(uuid, currency, amount);
    }

    @Override
    public boolean deposit(UUID uuid, String currency, double amount) {
        if (!positive(amount)) {
            return false;
        }
        account(uuid, currency).balance += amount;
        return true;
    }

    @Override
    public boolean withdraw(UUID uuid, String currency, double amount) {
        if (!positive(amount)) {
            return false;
        }
        Account account = account(uuid, currency);
        double available = account.balance - account.frozen;
        if (available < amount) {
            return false;
        }
        account.balance -= amount;
        return true;
    }

    @Override
    public boolean transfer(UUID from, UUID to, String currency, double amount) {
        return transfer(from, to, currency, amount, amount);
    }

    @Override
    public boolean transfer(UUID from, UUID to, String currency, double withdrawAmount, double depositAmount) {
        if (!positive(withdrawAmount) || depositAmount < 0.0D || !Double.isFinite(depositAmount)) {
            return false;
        }
        Account fromAccount = account(from, currency);
        double available = fromAccount.balance - fromAccount.frozen;
        if (available < withdrawAmount) {
            return false;
        }
        fromAccount.balance -= withdrawAmount;
        account(to, currency).balance += depositAmount;
        return true;
    }

    @Override
    public boolean exchange(UUID uuid, String fromCurrency, String toCurrency, double withdrawAmount, double depositAmount) {
        if (!positive(withdrawAmount) || !positive(depositAmount)) {
            return false;
        }
        Account fromAccount = account(uuid, fromCurrency);
        double available = fromAccount.balance - fromAccount.frozen;
        if (available < withdrawAmount) {
            return false;
        }
        fromAccount.balance -= withdrawAmount;
        account(uuid, toCurrency).balance += depositAmount;
        return true;
    }

    @Override
    public double getFrozenBalance(UUID uuid, String currency) {
        return account(uuid, currency).frozen;
    }

    @Override
    public boolean updateFrozenBalance(UUID uuid, String currency, double amount) {
        Account account = account(uuid, currency);
        if (amount < 0.0D || amount > account.balance || !Double.isFinite(amount)) {
            return false;
        }
        account.frozen = amount;
        return true;
    }

    @Override
    public void setFrozenBalance(UUID uuid, String currency, double amount) {
        updateFrozenBalance(uuid, currency, amount);
    }

    @Override
    public boolean freeze(UUID uuid, String currency, double amount) {
        if (!positive(amount)) {
            return false;
        }
        Account account = account(uuid, currency);
        double available = account.balance - account.frozen;
        if (available < amount) {
            return false;
        }
        account.frozen += amount;
        return true;
    }

    @Override
    public boolean unfreeze(UUID uuid, String currency, double amount) {
        if (!positive(amount)) {
            return false;
        }
        Account account = account(uuid, currency);
        if (account.frozen < amount) {
            return false;
        }
        account.frozen -= amount;
        return true;
    }

    @Override
    public boolean deductFrozen(UUID uuid, String currency, double amount) {
        if (!positive(amount)) {
            return false;
        }
        Account account = account(uuid, currency);
        if (account.frozen < amount || account.balance < amount) {
            return false;
        }
        account.frozen -= amount;
        account.balance -= amount;
        return true;
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        names.put(uuid, name);
    }

    @Override
    public Optional<UUID> findUuidByUsername(String username) {
        return names.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(username))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    @Override
    public Map<String, Double> getTopAccounts(String currency, int limit) {
        Map<String, Double> result = new LinkedHashMap<>();
        accounts.entrySet().stream()
                .filter(entry -> entry.getKey().currency.equals(currency))
                .filter(entry -> !isHidden(entry.getKey().uuid))
                .sorted(Map.Entry.<Key, Account>comparingByValue(Comparator.comparingDouble(account -> account.balance)).reversed())
                .limit(limit)
                .forEach(entry -> result.put(names.getOrDefault(entry.getKey().uuid, entry.getKey().uuid.toString()), entry.getValue().balance));
        return result;
    }

    @Override
    public Map<UUID, Double> getAccountsAboveBalance(String currency, double minimumBalance) {
        Map<UUID, Double> result = new LinkedHashMap<>();
        accounts.entrySet().stream()
                .filter(entry -> entry.getKey().currency.equals(currency))
                .filter(entry -> !isHidden(entry.getKey().uuid))
                .filter(entry -> entry.getValue().balance > minimumBalance)
                .forEach(entry -> result.put(entry.getKey().uuid, entry.getValue().balance));
        return result;
    }

    @Override
    public double getTotalBalance(String currency) {
        return accounts.entrySet().stream()
                .filter(entry -> entry.getKey().currency.equals(currency))
                .filter(entry -> !isHidden(entry.getKey().uuid))
                .mapToDouble(entry -> entry.getValue().balance)
                .sum();
    }

    @Override
    public void setHidden(UUID uuid, boolean hidden) {
        this.hidden.put(uuid, hidden);
    }

    @Override
    public boolean isHidden(UUID uuid) {
        return hidden.getOrDefault(uuid, false);
    }

    @Override
    public Map<UUID, String> getUnknownAccounts() {
        return Map.of();
    }

    private Account account(UUID uuid, String currency) {
        return accounts.computeIfAbsent(new Key(uuid, currency), ignored -> new Account(0.0D, 0.0D));
    }

    private boolean positive(double amount) {
        return Double.isFinite(amount) && amount > 0.0D;
    }

    private record Key(UUID uuid, String currency) {
    }

    private static final class Account {
        private double balance;
        private double frozen;

        private Account(double balance, double frozen) {
            this.balance = balance;
            this.frozen = frozen;
        }
    }
}
