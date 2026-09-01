package com.xiaoyiluck.meoweco.migration;

import com.xiaoyiluck.meoweco.database.MigrationBalance;

import java.util.List;

public record MigrationPreview(
        String adapter,
        String source,
        List<MigrationBalance> balances,
        int skippedEntries,
        double totalBalance,
        List<String> warnings
) {
    public boolean isEmpty() {
        return balances.isEmpty();
    }
}
