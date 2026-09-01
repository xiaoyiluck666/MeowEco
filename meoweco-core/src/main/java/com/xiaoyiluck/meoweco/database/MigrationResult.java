package com.xiaoyiluck.meoweco.database;

public record MigrationResult(
        boolean success,
        int importedAccounts,
        int createdAccounts,
        int updatedAccounts,
        double previousTotal,
        double importedTotal,
        String transactionId,
        String error
) {
    public static MigrationResult failed(String error) {
        return new MigrationResult(false, 0, 0, 0, 0.0D, 0.0D, "", error);
    }
}
