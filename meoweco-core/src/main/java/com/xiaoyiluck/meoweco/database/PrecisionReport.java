package com.xiaoyiluck.meoweco.database;

import java.util.List;

public record PrecisionReport(int affectedAccounts, int affectedBalances, int affectedFrozenBalances, List<CurrencyReport> currencies) {
    public static PrecisionReport empty() {
        return new PrecisionReport(0, 0, 0, List.of());
    }

    public boolean hasIssues() {
        return affectedBalances > 0 || affectedFrozenBalances > 0;
    }

    public record CurrencyReport(String currencyId, int decimalPlaces, int affectedAccounts, int affectedBalances, int affectedFrozenBalances) {
    }
}
