package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.objects.Currency;
import org.bukkit.Bukkit;

import java.util.Collection;

/**
 * The main API for MeowEco, providing access to its core functionalities.
 */
public interface MeowEcoAPI {

    /**
     * Gets the instance of the MeowEcoAPI.
     *
     * @return The API instance, or {@code null} if not registered.
     */
    static MeowEcoAPI get() {
        var rsp = Bukkit.getServicesManager().getRegistration(MeowEcoAPI.class);
        if (rsp == null) return null;
        return rsp.getProvider();
    }

    /**
     * Retrieves a collection of all registered currencies in the plugin.
     *
     * @return A {@link Collection} of {@link Currency} objects. The collection is read-only.
     */
    Collection<Currency> getRegisteredCurrencies();

    /**
     * Gets a specific currency by its unique identifier (ID).
     *
     * @param id The ID of the currency to retrieve.
     * @return The {@link Currency} object, or {@code null} if not found.
     */
    Currency getCurrency(String id);

    /**
     * Gets the total balance of a player for a specific currency.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @return The total balance.
     */
    double getBalance(java.util.UUID uuid, String currencyId);

    /**
     * Deposits a certain amount of funds to a player's account.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to deposit. Must be a finite value greater than 0.
     * @return {@code true} if successful.
     */
    boolean deposit(java.util.UUID uuid, String currencyId, double amount);

    /**
     * Withdraws a certain amount of funds from a player's account.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to withdraw. Must be a finite value greater than 0.
     * @return {@code true} if successful, {@code false} if insufficient funds.
     */
    boolean withdraw(java.util.UUID uuid, String currencyId, double amount);

    /** Tracked remaining initial grants; legacy untracked balances are not guessed. */
    default double getInitialGiftBalance(java.util.UUID uuid, String currencyId) { return 0.0D; }

    /** Available funds eligible for direct/player trade under the current initial-funds policy. */
    default double getTransferableBalance(java.util.UUID uuid, String currencyId) { return 0.0D; }

    /** Atomic source-aware debit for player shops; generic Vault does not identify counterparties. */
    default boolean withdrawForPlayerTrade(java.util.UUID uuid, String currencyId, double amount) { return false; }

    /** Persistent idempotent debit. Reusing a reference with different arguments fails. */
    default boolean debitWithReceipt(java.util.UUID uuid, String currencyId, double amount, String reference, boolean playerTrade) { return false; }

    /** Idempotent refund to the original wallet, including its original gift labels. */
    default boolean refundReceipt(java.util.UUID uuid, String currencyId, String reference) { return false; }

    /** MISSING, DEBITED, REFUNDED, or UNAVAILABLE. */
    default String getReceiptState(java.util.UUID uuid, String currencyId, String reference) { return "UNAVAILABLE"; }

    /**
     * Gets the frozen balance of a player for a specific currency.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @return The frozen balance.
     */
    double getFrozenBalance(java.util.UUID uuid, String currencyId);

    /**
     * Gets the available balance (total balance - frozen balance) of a player for a specific currency.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @return The available balance.
     */
    double getAvailableBalance(java.util.UUID uuid, String currencyId);

    /**
     * Freezes a certain amount of funds for a player.
     * The funds must be available in the player's account.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to freeze. Must be a finite value greater than 0.
     * @return {@code true} if successful, {@code false} if insufficient available funds.
     */
    boolean freeze(java.util.UUID uuid, String currencyId, double amount);

    /**
     * Unfreezes a certain amount of funds for a player.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to unfreeze. Must be a finite value greater than 0.
     * @return {@code true} if successful, {@code false} if insufficient frozen funds.
     */
    boolean unfreeze(java.util.UUID uuid, String currencyId, double amount);

    /**
     * Deducts a certain amount of funds from the player's frozen balance.
     * This also reduces the total balance of the player.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to deduct. Must be a finite value greater than 0.
     * @return {@code true} if successful, {@code false} if insufficient frozen funds.
     */
    boolean deductFrozen(java.util.UUID uuid, String currencyId, double amount);
}
