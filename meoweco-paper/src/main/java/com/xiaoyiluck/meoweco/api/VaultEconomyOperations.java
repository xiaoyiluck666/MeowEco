package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.EconomyService;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.UUID;

@SuppressWarnings("deprecation") // Isolated compatibility facade for the Classic Vault API.
final class VaultEconomyOperations {
    private final DatabaseManager database;
    private final EconomyService economyService;
    private final Currency currency;

    VaultEconomyOperations(DatabaseManager database, EconomyService economyService, Currency currency) {
        this.database = database;
        this.economyService = economyService;
        this.currency = currency;
    }

    EconomyResponse deposit(UUID uuid, double amount) {
        double normalized = normalize(amount);
        if (!Double.isFinite(normalized)) {
            return failure(uuid, "Amount must be greater than 0 and fit currency precision");
        }
        boolean success;
        try (var auditScope = database.openAuditScope("vault", "external_plugin")) {
            success = economyService.deposit(uuid, currency, normalized);
        }
        double balance = balance(uuid);
        return success
                ? new EconomyResponse(normalized, balance, EconomyResponse.ResponseType.SUCCESS, null)
                : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE,
                "Account not found, unsafe resulting balance, or database error");
    }

    EconomyResponse withdraw(UUID uuid, double amount) {
        double normalized = normalize(amount);
        if (!Double.isFinite(normalized)) {
            return failure(uuid, "Amount must be greater than 0 and fit currency precision");
        }
        boolean success;
        try (var auditScope = database.openAuditScope("vault", "external_plugin")) {
            success = economyService.withdraw(uuid, currency, normalized);
        }
        double balance = balance(uuid);
        return success
                ? new EconomyResponse(normalized, balance, EconomyResponse.ResponseType.SUCCESS, null)
                : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE,
                "Insufficient funds, unsafe resulting balance, or database error");
    }

    double balance(UUID uuid) {
        return database.findBalance(uuid, currency.getId()).orElse(0.0D);
    }

    private double normalize(double amount) {
        return MoneyAmountPolicy.isValidPositiveInput(amount, currency)
                ? MoneyAmountPolicy.roundForStorage(amount, currency)
                : Double.NaN;
    }

    private EconomyResponse failure(UUID uuid, String message) {
        return new EconomyResponse(0, balance(uuid), EconomyResponse.ResponseType.FAILURE, message);
    }
}
