package com.xiaoyiluck.meoweco.database;

import com.xiaoyiluck.meoweco.MeowEco;
import com.zaxxer.hikari.HikariConfig;

import java.io.File;
import java.io.IOException;

public class SQLiteDatabase extends AbstractSQLDatabase {

    public SQLiteDatabase(MeowEco plugin) {
        super(plugin);
    }

    @Override
    protected void configureDataSource(HikariConfig config) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite JDBC driver not found. Add org.xerial:sqlite-jdbc to the server classpath or switch storage.type to mysql.");
            throw new IllegalStateException("SQLite JDBC driver not found", e);
        }

        File dbFile = new File(plugin.getDataFolder(), "database.db");
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create SQLite database file", e);
            }
        }

        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                + "?busy_timeout=5000"
                + "&journal_mode=WAL"
                + "&synchronous=NORMAL"
                + "&foreign_keys=ON"
                + "&temp_store=MEMORY";

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
    }

    @Override
    protected void configurePool(HikariConfig config) {
        super.configurePool(config);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setIdleTimeout(0);
        config.setMaxLifetime(0);
        config.setConnectionTestQuery("SELECT 1");
    }

    @Override
    protected boolean isSQLite() {
        return true;
    }
}
