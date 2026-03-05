package com.xiaoyiluck.meoweco.database;

import com.zaxxer.hikari.HikariConfig;
import com.xiaoyiluck.meoweco.MeowEco;

public class MySQLDatabase extends AbstractSQLDatabase {

    public MySQLDatabase(MeowEco plugin) {
        super(plugin);
    }

    @Override
    protected void configureDataSource(HikariConfig config) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("MySQL JDBC driver not found. Add mysql-connector-j to the server classpath or switch storage.type to sqlite.");
            throw new IllegalStateException("MySQL JDBC driver not found", e);
        }
        String host = plugin.getConfig().getString("storage.mysql.host");
        int port = plugin.getConfig().getInt("storage.mysql.port");
        String database = plugin.getConfig().getString("storage.mysql.database");
        String username = plugin.getConfig().getString("storage.mysql.username");
        String password = plugin.getConfig().getString("storage.mysql.password");
        boolean useSsl = plugin.getConfig().getBoolean("storage.mysql.use-ssl");

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
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
