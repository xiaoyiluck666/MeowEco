package com.xiaoyiluck.meoweco.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JdbcDatabaseManager implements DatabaseManager {
    private static final String TABLE_NAME = "meoweco_accounts";

    private final Logger logger;
    private final StorageConfig storageConfig;
    private final boolean debug;

    public JdbcDatabaseManager(Logger logger, StorageConfig storageConfig, boolean debug) {
        this.logger = logger;
        this.storageConfig = storageConfig;
        this.debug = debug;
    }

    @Override
    public void init() {
        try {
            if (storageConfig.type() == StorageType.SQLITE) {
                Files.createDirectories(storageConfig.dataDirectory());
                Path dbFile = storageConfig.dataDirectory().resolve("database.db");
                if (Files.notExists(dbFile)) {
                    Files.createFile(dbFile);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare storage directory", e);
        }

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(getCreateTableSql());
            statement.execute("CREATE INDEX IF NOT EXISTS idx_meoweco_currency_balance ON " + TABLE_NAME + "(currency, balance DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_meoweco_username ON " + TABLE_NAME + "(username)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize database schema", e);
        }
    }

    private String getCreateTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "currency VARCHAR(32) NOT NULL,"
                + "balance DOUBLE NOT NULL DEFAULT 0,"
                + "frozen_balance DOUBLE NOT NULL DEFAULT 0,"
                + "username VARCHAR(64) DEFAULT NULL,"
                + "hidden BOOLEAN NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid, currency)"
                + ")";
    }

    @Override
    public void close() {
        // No pooled resources to close.
    }

    private Connection getConnection() throws SQLException {
        if (storageConfig.type() == StorageType.MYSQL) {
            StorageConfig.MySqlConfig mysql = storageConfig.mySqlConfig()
                    .orElseThrow(() -> new IllegalStateException("Missing MySQL configuration"));
            String jdbcUrl = "jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database()
                    + "?useSSL=" + mysql.useSsl()
                    + "&useUnicode=true"
                    + "&characterEncoding=utf8"
                    + "&serverTimezone=UTC"
                    + "&tcpKeepAlive=true";
            return DriverManager.getConnection(jdbcUrl, mysql.username(), mysql.password());
        }

        String jdbcUrl = "jdbc:sqlite:" + storageConfig.dataDirectory().resolve("database.db").toAbsolutePath()
                + "?busy_timeout=5000"
                + "&journal_mode=WAL"
                + "&synchronous=NORMAL"
                + "&foreign_keys=ON"
                + "&temp_store=MEMORY";
        return DriverManager.getConnection(jdbcUrl);
    }

    private void logSqlError(String action, SQLException e) {
        logger.log(Level.SEVERE, action, e);
    }

    private boolean isPositiveFinite(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }

    private boolean isNonNegativeFinite(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }

    @Override
    public boolean hasAccount(UUID uuid, String currency) {
        String sql = "SELECT 1 FROM " + TABLE_NAME + " WHERE uuid = ? AND currency = ? LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currency.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            logSqlError("Failed to query account existence", e);
            return false;
        }
    }

    @Override
    public boolean createAccount(UUID uuid, String currency, double initialBalance) {
        if (!isNonNegativeFinite(initialBalance)) {
            return false;
        }

        String sql = "INSERT INTO " + TABLE_NAME + " (uuid, currency, balance, frozen_balance, username, hidden) VALUES (?, ?, ?, 0, ?, 0)";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currency.toLowerCase());
            statement.setDouble(3, initialBalance);
            statement.setString(4, "Unknown");
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (debug) {
                logSqlError("Failed to create account", e);
            }
            return false;
        }
    }

    @Override
    public OptionalDouble findBalance(UUID uuid, String currency) {
        String sql = "SELECT balance FROM " + TABLE_NAME + " WHERE uuid = ? AND currency = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currency.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return OptionalDouble.of(resultSet.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            logSqlError("Failed to query balance", e);
        }
        return OptionalDouble.empty();
    }

    @Override
    public double getBalance(UUID uuid, String currency) {
        return findBalance(uuid, currency).orElse(0.0D);
    }

    @Override
    public boolean updateBalance(UUID uuid, String currency, double amount) {
        if (!isNonNegativeFinite(amount)) {
            return false;
        }
        return executeAccountUpdate("UPDATE " + TABLE_NAME + " SET balance = ? WHERE uuid = ? AND currency = ?", amount, uuid, currency);
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        updateBalance(uuid, currency, amount);
    }

    @Override
    public boolean deposit(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        return executeAccountUpdate("UPDATE " + TABLE_NAME + " SET balance = balance + ? WHERE uuid = ? AND currency = ?", amount, uuid, currency);
    }

    @Override
    public boolean withdraw(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        return executeAccountUpdate(
                "UPDATE " + TABLE_NAME + " SET balance = balance - ? WHERE uuid = ? AND currency = ? AND balance >= ?",
                amount,
                uuid,
                currency,
                amount
        );
    }

    @Override
    public boolean transfer(UUID from, UUID to, String currency, double amount) {
        return transfer(from, to, currency, amount, amount);
    }

    @Override
    public boolean transfer(UUID from, UUID to, String currency, double withdrawAmount, double depositAmount) {
        if (!isPositiveFinite(withdrawAmount) || !isNonNegativeFinite(depositAmount)) {
            return false;
        }

        String withdrawSql = "UPDATE " + TABLE_NAME + " SET balance = balance - ? WHERE uuid = ? AND currency = ? AND balance >= ?";
        String depositSql = "UPDATE " + TABLE_NAME + " SET balance = balance + ? WHERE uuid = ? AND currency = ?";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement withdrawStatement = connection.prepareStatement(withdrawSql);
                 PreparedStatement depositStatement = connection.prepareStatement(depositSql)) {
                withdrawStatement.setDouble(1, withdrawAmount);
                withdrawStatement.setString(2, from.toString());
                withdrawStatement.setString(3, currency.toLowerCase());
                withdrawStatement.setDouble(4, withdrawAmount);
                if (withdrawStatement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }

                depositStatement.setDouble(1, depositAmount);
                depositStatement.setString(2, to.toString());
                depositStatement.setString(3, currency.toLowerCase());
                if (depositStatement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logSqlError("Failed to transfer balance", e);
            return false;
        }
    }

    @Override
    public boolean exchange(UUID uuid, String fromCurrency, String toCurrency, double withdrawAmount, double depositAmount) {
        if (!isPositiveFinite(withdrawAmount) || !isNonNegativeFinite(depositAmount)) {
            return false;
        }

        String withdrawSql = "UPDATE " + TABLE_NAME + " SET balance = balance - ? WHERE uuid = ? AND currency = ? AND balance >= ?";
        String depositSql = "UPDATE " + TABLE_NAME + " SET balance = balance + ? WHERE uuid = ? AND currency = ?";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement withdrawStatement = connection.prepareStatement(withdrawSql);
                 PreparedStatement depositStatement = connection.prepareStatement(depositSql)) {
                withdrawStatement.setDouble(1, withdrawAmount);
                withdrawStatement.setString(2, uuid.toString());
                withdrawStatement.setString(3, fromCurrency.toLowerCase());
                withdrawStatement.setDouble(4, withdrawAmount);
                if (withdrawStatement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }

                depositStatement.setDouble(1, depositAmount);
                depositStatement.setString(2, uuid.toString());
                depositStatement.setString(3, toCurrency.toLowerCase());
                if (depositStatement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logSqlError("Failed to exchange balance", e);
            return false;
        }
    }

    @Override
    public OptionalDouble findFrozenBalance(UUID uuid, String currency) {
        String sql = "SELECT frozen_balance FROM " + TABLE_NAME + " WHERE uuid = ? AND currency = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currency.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return OptionalDouble.of(resultSet.getDouble("frozen_balance"));
                }
            }
        } catch (SQLException e) {
            logSqlError("Failed to query frozen balance", e);
        }
        return OptionalDouble.empty();
    }

    @Override
    public double getFrozenBalance(UUID uuid, String currency) {
        return findFrozenBalance(uuid, currency).orElse(0.0D);
    }

    @Override
    public boolean updateFrozenBalance(UUID uuid, String currency, double amount) {
        if (!isNonNegativeFinite(amount)) {
            return false;
        }
        return executeAccountUpdate("UPDATE " + TABLE_NAME + " SET frozen_balance = ? WHERE uuid = ? AND currency = ?", amount, uuid, currency);
    }

    @Override
    public void setFrozenBalance(UUID uuid, String currency, double amount) {
        updateFrozenBalance(uuid, currency, amount);
    }

    @Override
    public boolean freeze(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + TABLE_NAME + " SET balance = balance - ?, frozen_balance = frozen_balance + ? WHERE uuid = ? AND currency = ? AND balance >= ?";
        return executeAccountUpdate(sql, amount, amount, uuid, currency, amount);
    }

    @Override
    public boolean unfreeze(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + TABLE_NAME + " SET frozen_balance = frozen_balance - ?, balance = balance + ? WHERE uuid = ? AND currency = ? AND frozen_balance >= ?";
        return executeAccountUpdate(sql, amount, amount, uuid, currency, amount);
    }

    @Override
    public boolean deductFrozen(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + TABLE_NAME + " SET frozen_balance = frozen_balance - ? WHERE uuid = ? AND currency = ? AND frozen_balance >= ?";
        return executeAccountUpdate(sql, amount, uuid, currency, amount);
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        String sql = "UPDATE " + TABLE_NAME + " SET username = ? WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            logSqlError("Failed to update player name", e);
        }
    }

    @Override
    public Optional<UUID> findUuidByUsername(String username) {
        String sql = "SELECT uuid FROM " + TABLE_NAME + " WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(UUID.fromString(resultSet.getString("uuid")));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            SQLException sqlException = (e instanceof SQLException)
                    ? (SQLException) e
                    : new SQLException(e);
            logSqlError("Failed to resolve UUID by username", sqlException);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Double> getTopAccounts(String currency, int limit) {
        String sql = "SELECT username, uuid, balance FROM " + TABLE_NAME + " WHERE currency = ? AND hidden = 0 ORDER BY balance DESC LIMIT ?";
        Map<String, Double> topAccounts = new LinkedHashMap<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currency.toLowerCase());
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String username = resultSet.getString("username");
                    String fallback = resultSet.getString("uuid");
                    topAccounts.put(username != null && !username.isBlank() ? username : fallback, resultSet.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            logSqlError("Failed to query top accounts", e);
        }
        return topAccounts;
    }

    @Override
    public Map<UUID, Double> getAccountsAboveBalance(String currency, double minimumBalance) {
        String sql = "SELECT uuid, balance FROM " + TABLE_NAME + " WHERE currency = ? AND hidden = 0 AND balance >= ?";
        Map<UUID, Double> result = new LinkedHashMap<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currency.toLowerCase());
            statement.setDouble(2, minimumBalance);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.put(UUID.fromString(resultSet.getString("uuid")), resultSet.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            logSqlError("Failed to query accounts above balance", e);
        }
        return result;
    }

    @Override
    public double getTotalBalance(String currency) {
        String sql = "SELECT COALESCE(SUM(balance), 0) AS total_balance FROM " + TABLE_NAME + " WHERE currency = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currency.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("total_balance");
                }
            }
        } catch (SQLException e) {
            logSqlError("Failed to query total balance", e);
        }
        return 0;
    }

    @Override
    public void setHidden(UUID uuid, boolean hidden) {
        updateHidden(uuid, uuid.toString(), hidden);
    }

    @Override
    public boolean updateHidden(UUID uuid, String username, boolean hidden) {
        String sql = "UPDATE " + TABLE_NAME + " SET hidden = ?, username = COALESCE(username, ?) WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, hidden);
            statement.setString(2, username);
            statement.setString(3, uuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logSqlError("Failed to update hidden status", e);
            return false;
        }
    }

    @Override
    public boolean isHidden(UUID uuid) {
        String sql = "SELECT hidden FROM " + TABLE_NAME + " WHERE uuid = ? LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("hidden");
                }
            }
        } catch (SQLException e) {
            logSqlError("Failed to query hidden status", e);
        }
        return false;
    }

    @Override
    public Map<UUID, String> getUnknownAccounts() {
        String sql = "SELECT DISTINCT uuid, username FROM " + TABLE_NAME + " WHERE username IS NULL OR username = '' OR LOWER(username) = 'unknown'";
        Map<UUID, String> result = new LinkedHashMap<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.put(UUID.fromString(resultSet.getString("uuid")), resultSet.getString("username"));
            }
        } catch (SQLException e) {
            logSqlError("Failed to query unknown accounts", e);
        }
        return result;
    }

    private boolean executeAccountUpdate(String sql, Object... params) {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof UUID uuid) {
                    statement.setString(i + 1, uuid.toString());
                } else if (param instanceof String s) {
                    statement.setString(i + 1, s.toLowerCase());
                } else if (param instanceof Double d) {
                    statement.setDouble(i + 1, d);
                } else if (param instanceof Integer in) {
                    statement.setInt(i + 1, in);
                } else if (param instanceof Boolean b) {
                    statement.setBoolean(i + 1, b);
                } else {
                    statement.setObject(i + 1, param);
                }
            }
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logSqlError("Failed to execute account update", e);
            return false;
        }
    }

    public enum StorageType {
        SQLITE,
        MYSQL
    }

    public record StorageConfig(StorageType type, Path dataDirectory, Optional<MySqlConfig> mySqlConfig) {
        public static StorageConfig sqlite(Path dataDirectory) {
            return new StorageConfig(StorageType.SQLITE, dataDirectory, Optional.empty());
        }

        public static StorageConfig mysql(Path dataDirectory, MySqlConfig mySqlConfig) {
            return new StorageConfig(StorageType.MYSQL, dataDirectory, Optional.of(mySqlConfig));
        }

        public record MySqlConfig(String host, int port, String database, String username, String password, boolean useSsl) {
        }
    }
}
