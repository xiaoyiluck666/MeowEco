package com.xiaoyiluck.meoweco.database;

import java.util.UUID;

public record MigrationBalance(UUID uuid, String username, double balance) {
}
