package com.xiaoyiluck.meoweco.database;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
        long id,
        String transactionId,
        Instant createdAt,
        UUID accountUuid,
        String username,
        String currency,
        String operation,
        double amount,
        double balanceBefore,
        double balanceAfter,
        double frozenBefore,
        double frozenAfter,
        String source,
        String actor
) {
}
