package com.xiaoyiluck.meoweco.database;

import com.xiaoyiluck.meoweco.MeowEco;
import com.zaxxer.hikari.HikariConfig;

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

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&useUnicode=true"
                + "&characterEncoding=utf8"
                + "&serverTimezone=UTC"
                + "&tcpKeepAlive=true";

        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
    }

    @Override
    protected boolean isSQLite() {
        return false;
    }
}
