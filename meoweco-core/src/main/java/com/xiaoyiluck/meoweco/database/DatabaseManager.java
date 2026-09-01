package com.xiaoyiluck.meoweco.database;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

public interface DatabaseManager {
    void init();
    void close();

    boolean hasAccount(UUID uuid, String currency);
    boolean createAccount(UUID uuid, String currency, double initialBalance);

    default OptionalDouble findBalance(UUID uuid, String currency) {
        return OptionalDouble.of(getBalance(uuid, currency));
    }

    double getBalance(UUID uuid, String currency);

    default boolean updateBalance(UUID uuid, String currency, double amount) {
        setBalance(uuid, currency, amount);
        return true;
    }

    void setBalance(UUID uuid, String currency, double amount);
    boolean deposit(UUID uuid, String currency, double amount);
    boolean withdraw(UUID uuid, String currency, double amount);
    boolean transfer(UUID from, UUID to, String currency, double amount);
    boolean transfer(UUID from, UUID to, String currency, double withdrawAmount, double depositAmount);
    boolean exchange(UUID uuid, String fromCurrency, String toCurrency, double withdrawAmount, double depositAmount);

    default OptionalDouble findFrozenBalance(UUID uuid, String currency) {
        return OptionalDouble.of(getFrozenBalance(uuid, currency));
    }

    double getFrozenBalance(UUID uuid, String currency);

    default boolean updateFrozenBalance(UUID uuid, String currency, double amount) {
        setFrozenBalance(uuid, currency, amount);
        return true;
    }

    void setFrozenBalance(UUID uuid, String currency, double amount);
    boolean freeze(UUID uuid, String currency, double amount);
    boolean unfreeze(UUID uuid, String currency, double amount);
    boolean deductFrozen(UUID uuid, String currency, double amount);

    void updatePlayerName(UUID uuid, String name);

    default Optional<UUID> findUuidByUsername(String username) {
        return Optional.empty();
    }

    Map<String, Double> getTopAccounts(String currency, int limit);
    Map<UUID, Double> getAccountsAboveBalance(String currency, double minimumBalance);
    double getTotalBalance(String currency);

    void setHidden(UUID uuid, boolean hidden);

    default boolean updateHidden(UUID uuid, String username, boolean hidden) {
        setHidden(uuid, hidden);
        return true;
    }

    boolean isHidden(UUID uuid);

    Map<UUID, String> getUnknownAccounts();

    default PrecisionReport reportPrecisionIssues(Map<String, Integer> currencyScales) {
        return PrecisionReport.empty();
    }

    default AuditScope openAuditScope(String source, String actor) {
        return AuditScope.noop();
    }

    default List<AuditEntry> getAuditHistory(UUID uuid, String currency, int limit) {
        return List.of();
    }

    default List<AuditEntry> getRecentAudit(int limit) {
        return List.of();
    }

    default MigrationResult importBalances(List<MigrationBalance> balances, String currency, String source, String actor) {
        return MigrationResult.failed("This storage backend does not support transactional migration.");
    }
}
