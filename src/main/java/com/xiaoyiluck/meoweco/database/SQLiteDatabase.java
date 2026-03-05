package com.xiaoyiluck.meoweco.database;

import com.zaxxer.hikari.HikariConfig;
import com.xiaoyiluck.meoweco.MeowEco;
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
                e.printStackTrace();
            }
        }
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE IF NOT EXISTS meoweco_accounts (" +
               "uuid VARCHAR(36), " +
               "currency VARCHAR(32), " +
               "balance DOUBLE NOT NULL, " +
               "username VARCHAR(16), " +
               "hidden INTEGER DEFAULT 0, " +
               "frozen_balance DOUBLE DEFAULT 0.0, " +
               "PRIMARY KEY (uuid, currency))";
    }
}
