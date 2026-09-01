package com.xiaoyiluck.meoweco.database;

@FunctionalInterface
public interface AuditScope extends AutoCloseable {
    @Override
    void close();

    static AuditScope noop() {
        return () -> {
        };
    }
}
