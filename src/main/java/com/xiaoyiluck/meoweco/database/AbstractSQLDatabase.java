package com.xiaoyiluck.meoweco.database;

import com.xiaoyiluck.meoweco.MeowEco;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.logging.Level;

public abstract class AbstractSQLDatabase implements DatabaseManager {
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String TABLE_NAME = "meoweco_accounts";
    private static final String TEMP_TABLE_NAME = "meoweco_accounts_migrating";
    private static final String META_TABLE_NAME = "meoweco_meta";
    private static final String META_KEY_SCHEMA_VERSION = "schema_version";
    private static final String COLUMN_UUID = "uuid";
    private static final String COLUMN_CURRENCY = "currency";
    private static final String COLUMN_BALANCE = "balance";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_HIDDEN = "hidden";
    private static final String COLUMN_FROZEN_BALANCE = "frozen_balance";

    protected final MeowEco plugin;
    protected HikariDataSource dataSource;

    public AbstractSQLDatabase(MeowEco plugin) {
        this.plugin = plugin;
    }

    protected abstract void configureDataSource(HikariConfig config);

    protected void configurePool(HikariConfig config) {
        config.setPoolName("MeowEco-Pool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(5000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
    }

    protected abstract boolean isSQLite();

    protected String getTableName() {
        return TABLE_NAME;
    }

    protected String getCreateStatement() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "currency VARCHAR(32) NOT NULL, "
                + "balance DOUBLE NOT NULL DEFAULT 0.0, "
                + "username VARCHAR(16), "
                + "hidden INTEGER NOT NULL DEFAULT 0, "
                + "frozen_balance DOUBLE NOT NULL DEFAULT 0.0, "
                + "PRIMARY KEY (uuid, currency))";
    }

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        configureDataSource(config);
        configurePool(config);
        config.setAutoCommit(true);
        this.dataSource = new HikariDataSource(config);

        try (Connection conn = getConnection()) {
            ensureSchema(conn);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database schema", e);
        }
    }

    private void ensureSchema(Connection conn) throws SQLException {
        ensureMetaTable(conn);
        try (Statement statement = conn.createStatement()) {
            statement.execute(getCreateStatement());
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int previousSchemaVersion = getSchemaVersion(conn);
            Map<String, String> columns = getColumnTypes(conn);
            String defaultCurrency = plugin.getConfig().getString("default-currency", "coins");

            if (isSQLite()) {
                migrateSQLite(conn, columns, defaultCurrency);
            } else {
                migrateMySQL(conn, columns, defaultCurrency);
            }

            normalizeNullableData(conn, columns, defaultCurrency);
            ensureIndexes(conn);
            setSchemaVersion(conn, CURRENT_SCHEMA_VERSION);
            conn.commit();
            if (previousSchemaVersion != CURRENT_SCHEMA_VERSION) {
                plugin.getLogger().info("Database schema version updated from "
                        + previousSchemaVersion + " to " + CURRENT_SCHEMA_VERSION + ".");
            }
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private void ensureMetaTable(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + META_TABLE_NAME + " ("
                    + "meta_key VARCHAR(64) PRIMARY KEY, "
                    + "meta_value VARCHAR(255) NOT NULL)");
        }
    }

    private int getSchemaVersion(Connection conn) throws SQLException {
        String sql = "SELECT meta_value FROM " + META_TABLE_NAME + " WHERE meta_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, META_KEY_SCHEMA_VERSION);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Integer.parseInt(rs.getString(1));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    private void setSchemaVersion(Connection conn, int version) throws SQLException {
        String updateSql = "UPDATE " + META_TABLE_NAME + " SET meta_value = ? WHERE meta_key = ?";
        try (PreparedStatement update = conn.prepareStatement(updateSql)) {
            update.setString(1, Integer.toString(version));
            update.setString(2, META_KEY_SCHEMA_VERSION);
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        String insertSql = "INSERT INTO " + META_TABLE_NAME + " (meta_key, meta_value) VALUES (?, ?)";
        try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
            insert.setString(1, META_KEY_SCHEMA_VERSION);
            insert.setString(2, Integer.toString(version));
            insert.executeUpdate();
        }
    }

    private Map<String, String> getColumnTypes(Connection conn) throws SQLException {
        Map<String, String> columns = new HashMap<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, getTableName(), null)) {
            while (rs.next()) {
                columns.put(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), rs.getString("TYPE_NAME"));
            }
        }
        return columns;
    }

    private void migrateSQLite(Connection conn, Map<String, String> columns, String defaultCurrency) throws SQLException {
        boolean needsRewrite = !columns.containsKey(COLUMN_CURRENCY)
                || !columns.containsKey(COLUMN_HIDDEN)
                || !columns.containsKey(COLUMN_FROZEN_BALANCE)
                || !hasCompositePrimaryKey(conn);

        if (!needsRewrite) {
            return;
        }

        plugin.getLogger().info("Migrating SQLite economy table to the latest schema...");

        try (Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TEMP_TABLE_NAME);
            statement.execute(getCreateStatement().replace(getTableName(), TEMP_TABLE_NAME));
        }

        String currencySelect = columns.containsKey(COLUMN_CURRENCY)
                ? "COALESCE(" + COLUMN_CURRENCY + ", ?)"
                : "?";
        String hiddenSelect = columns.containsKey(COLUMN_HIDDEN)
                ? "COALESCE(" + COLUMN_HIDDEN + ", 0)"
                : "0";
        String frozenSelect = columns.containsKey(COLUMN_FROZEN_BALANCE)
                ? "COALESCE(" + COLUMN_FROZEN_BALANCE + ", 0.0)"
                : "0.0";
        String usernameSelect = columns.containsKey(COLUMN_USERNAME)
                ? "COALESCE(" + COLUMN_USERNAME + ", 'Unknown')"
                : "'Unknown'";

        String insertSql = "INSERT OR REPLACE INTO " + TEMP_TABLE_NAME
                + " (uuid, currency, balance, username, hidden, frozen_balance) "
                + "SELECT uuid, " + currencySelect + ", COALESCE(balance, 0.0), "
                + usernameSelect + ", " + hiddenSelect + ", " + frozenSelect
                + " FROM " + getTableName();

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, defaultCurrency);
            ps.executeUpdate();
        }

        try (Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE " + getTableName());
            statement.execute("ALTER TABLE " + TEMP_TABLE_NAME + " RENAME TO " + getTableName());
        }
    }

    private void migrateMySQL(Connection conn, Map<String, String> columns, String defaultCurrency) throws SQLException {
        if (!columns.containsKey(COLUMN_CURRENCY)) {
            plugin.getLogger().info("Adding currency column to MySQL economy table...");
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " ADD COLUMN currency VARCHAR(32) NULL");
            executeUpdate(conn, "UPDATE " + getTableName() + " SET currency = ? WHERE currency IS NULL OR currency = ''", defaultCurrency);
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " MODIFY COLUMN currency VARCHAR(32) NOT NULL");
            columns.put(COLUMN_CURRENCY, "VARCHAR");
        }

        if (!columns.containsKey(COLUMN_HIDDEN)) {
            plugin.getLogger().info("Adding hidden column to MySQL economy table...");
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0");
            columns.put(COLUMN_HIDDEN, "INTEGER");
        }

        if (!columns.containsKey(COLUMN_FROZEN_BALANCE)) {
            plugin.getLogger().info("Adding frozen_balance column to MySQL economy table...");
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " ADD COLUMN frozen_balance DOUBLE NOT NULL DEFAULT 0.0");
            columns.put(COLUMN_FROZEN_BALANCE, "DOUBLE");
        }

        if (!hasCompositePrimaryKey(conn)) {
            plugin.getLogger().info("Updating MySQL primary key to (uuid, currency)...");
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " DROP PRIMARY KEY, ADD PRIMARY KEY (uuid, currency)");
        }
    }

    private void ensureIndexes(Connection conn) throws SQLException {
        createIndexIfMissing(conn,
                "idx_meoweco_currency_hidden_balance",
                "CREATE INDEX idx_meoweco_currency_hidden_balance ON " + getTableName() + " (currency, hidden, balance)");
        createIndexIfMissing(conn,
                "idx_meoweco_currency_username",
                "CREATE INDEX idx_meoweco_currency_username ON " + getTableName() + " (currency, username)");
        createIndexIfMissing(conn,
                "idx_meoweco_username",
                "CREATE INDEX idx_meoweco_username ON " + getTableName() + " (username)");
    }

    private void createIndexIfMissing(Connection conn, String indexName, String createSql) throws SQLException {
        if (hasIndex(conn, indexName)) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute(createSql);
        }
    }

    private boolean hasIndex(Connection conn, String indexName) throws SQLException {
        String expected = indexName.toLowerCase(Locale.ROOT);
        try (ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), null, getTableName(), false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name != null && name.toLowerCase(Locale.ROOT).equals(expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void normalizeNullableData(Connection conn, Map<String, String> columns, String defaultCurrency) throws SQLException {
        if (columns.containsKey(COLUMN_CURRENCY)) {
            executeUpdate(conn, "UPDATE " + getTableName() + " SET currency = ? WHERE currency IS NULL OR currency = ''", defaultCurrency);
        }
        if (columns.containsKey(COLUMN_HIDDEN)) {
            executeUpdate(conn, "UPDATE " + getTableName() + " SET hidden = 0 WHERE hidden IS NULL");
        }
        if (columns.containsKey(COLUMN_FROZEN_BALANCE)) {
            executeUpdate(conn, "UPDATE " + getTableName() + " SET frozen_balance = 0.0 WHERE frozen_balance IS NULL");
        }
        if (columns.containsKey(COLUMN_USERNAME)) {
            executeUpdate(conn, "UPDATE " + getTableName() + " SET username = 'Unknown' WHERE username IS NULL OR username = ''");
        }
    }

    private boolean hasCompositePrimaryKey(Connection conn) throws SQLException {
        if (isSQLite()) {
            int primaryKeyColumns = 0;
            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("PRAGMA table_info(" + getTableName() + ")")) {
                while (rs.next()) {
                    if (rs.getInt("pk") > 0) {
                        primaryKeyColumns++;
                    }
                }
            }
            return primaryKeyColumns >= 2;
        }

        Map<Short, String> primaryKeyColumns = new HashMap<>();
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, getTableName())) {
            while (rs.next()) {
                primaryKeyColumns.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }

        return COLUMN_UUID.equals(primaryKeyColumns.get((short) 1))
                && COLUMN_CURRENCY.equals(primaryKeyColumns.get((short) 2))
                && primaryKeyColumns.size() == 2;
    }

    protected void executeUpdate(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    protected Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is closed or null");
        }
        return dataSource.getConnection();
    }

    private boolean isPositiveFinite(double amount) {
        return Double.isFinite(amount) && amount > 0.0;
    }

    private boolean isNonNegativeFinite(double amount) {
        return Double.isFinite(amount) && amount >= 0.0;
    }

    private boolean isClosedDataSourceError(SQLException e) {
        String message = e.getMessage();
        return message != null && (message.contains("DataSource is closed") || message.contains("has been closed"));
    }

    private void logSqlError(String action, SQLException e) {
        if (!isClosedDataSourceError(e)) {
            plugin.getLogger().log(Level.SEVERE, action, e);
        }
    }

    @Override
    public boolean hasAccount(UUID uuid, String currency) {
        String sql = "SELECT 1 FROM " + getTableName() + " WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logSqlError("Account existence check failed", e);
            return false;
        }
    }

    @Override
    public boolean createAccount(UUID uuid, String currency, double initialBalance) {
        if (!isNonNegativeFinite(initialBalance)) {
            return false;
        }

        String existingInfoSql = "SELECT username, hidden FROM " + getTableName() + " WHERE uuid = ? LIMIT 1";
        String insertSql = "INSERT INTO " + getTableName()
                + " (uuid, currency, balance, username, hidden, frozen_balance) VALUES (?, ?, ?, ?, ?, ?)";

        String name = "Unknown";
        int hidden = 0;
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(existingInfoSql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String existingName = rs.getString(COLUMN_USERNAME);
                        name = (existingName == null || existingName.isBlank()) ? "Unknown" : existingName;
                        hidden = rs.getInt(COLUMN_HIDDEN);
                    } else {
                        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
                        if (player.getName() != null && !player.getName().isBlank()) {
                            name = player.getName();
                        }
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ps.setDouble(3, initialBalance);
                ps.setString(4, name);
                ps.setInt(5, hidden);
                ps.setDouble(6, 0.0D);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 19
                    || "23000".equals(e.getSQLState())
                    || (e.getMessage() != null
                    && (e.getMessage().contains("UNIQUE") || e.getMessage().contains("PRIMARY")))) {
                return false;
            }
            logSqlError("CreateAccount error", e);
            return false;
        }
    }

    @Override
    public OptionalDouble findBalance(UUID uuid, String currency) {
        return findNumericColumn(uuid, currency, COLUMN_BALANCE);
    }

    @Override
    public double getBalance(UUID uuid, String currency) {
        return findBalance(uuid, currency).orElse(0.0D);
    }

    @Override
    public boolean deposit(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName() + " SET balance = balance + ? WHERE uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "Deposit error", uuid, currency, amount);
    }

    @Override
    public boolean updateBalance(UUID uuid, String currency, double amount) {
        if (!isNonNegativeFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName() + " SET balance = ? WHERE uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "UpdateBalance error", uuid, currency, amount);
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        updateBalance(uuid, currency, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName()
                + " SET balance = balance - ? WHERE balance - frozen_balance >= ? AND uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "Withdraw error", uuid, currency, amount, amount);
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

        String withdrawSql = "UPDATE " + getTableName()
                + " SET balance = balance - ? WHERE uuid = ? AND currency = ? AND balance - frozen_balance >= ?";
        String depositSql = "UPDATE " + getTableName() + " SET balance = balance + ? WHERE uuid = ? AND currency = ?";

        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement psWithdraw = conn.prepareStatement(withdrawSql);
                 PreparedStatement psDeposit = conn.prepareStatement(depositSql)) {
                psWithdraw.setDouble(1, withdrawAmount);
                psWithdraw.setString(2, from.toString());
                psWithdraw.setString(3, currency);
                psWithdraw.setDouble(4, withdrawAmount);

                int withdrawRows = psWithdraw.executeUpdate();
                if (withdrawRows == 0) {
                    conn.rollback();
                    return false;
                }

                psDeposit.setDouble(1, depositAmount);
                psDeposit.setString(2, to.toString());
                psDeposit.setString(3, currency);
                int depositRows = psDeposit.executeUpdate();

                if (depositRows == 0) {
                    conn.rollback();
                    return false;
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            logSqlError("Transfer error", e);
            return false;
        }
    }

    @Override
    public boolean exchange(UUID uuid, String fromCurrency, String toCurrency, double withdrawAmount, double depositAmount) {
        if (!isPositiveFinite(withdrawAmount) || !isPositiveFinite(depositAmount)) {
            return false;
        }
        if (fromCurrency == null || toCurrency == null) {
            return false;
        }

        String withdrawSql = "UPDATE " + getTableName()
                + " SET balance = balance - ? WHERE uuid = ? AND currency = ? AND balance - frozen_balance >= ?";
        String depositSql = "UPDATE " + getTableName()
                + " SET balance = balance + ? WHERE uuid = ? AND currency = ?";

        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement psWithdraw = conn.prepareStatement(withdrawSql);
                 PreparedStatement psDeposit = conn.prepareStatement(depositSql)) {
                psWithdraw.setDouble(1, withdrawAmount);
                psWithdraw.setString(2, uuid.toString());
                psWithdraw.setString(3, fromCurrency);
                psWithdraw.setDouble(4, withdrawAmount);

                int withdrawRows = psWithdraw.executeUpdate();
                if (withdrawRows == 0) {
                    conn.rollback();
                    return false;
                }

                psDeposit.setDouble(1, depositAmount);
                psDeposit.setString(2, uuid.toString());
                psDeposit.setString(3, toCurrency);

                int depositRows = psDeposit.executeUpdate();
                if (depositRows == 0) {
                    conn.rollback();
                    return false;
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            logSqlError("Exchange error", e);
            return false;
        }
    }

    @Override
    public OptionalDouble findFrozenBalance(UUID uuid, String currency) {
        return findNumericColumn(uuid, currency, COLUMN_FROZEN_BALANCE);
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
        String sql = "UPDATE " + getTableName() + " SET frozen_balance = ? WHERE uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "UpdateFrozenBalance error", uuid, currency, amount);
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
        String sql = "UPDATE " + getTableName()
                + " SET frozen_balance = frozen_balance + ? WHERE (balance - frozen_balance) >= ? AND uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "Freeze error", uuid, currency, amount, amount);
    }

    @Override
    public boolean unfreeze(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName()
                + " SET frozen_balance = frozen_balance - ? WHERE frozen_balance >= ? AND uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "Unfreeze error", uuid, currency, amount, amount);
    }

    @Override
    public boolean deductFrozen(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName()
                + " SET balance = balance - ?, frozen_balance = frozen_balance - ? "
                + "WHERE frozen_balance >= ? AND balance >= ? AND uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "DeductFrozen error", uuid, currency, amount, amount, amount, amount);
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        String sql = "UPDATE " + getTableName() + " SET username = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logSqlError("UpdatePlayerName error", e);
        }
    }

    @Override
    public Optional<UUID> findUuidByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT uuid FROM " + getTableName()
                + " WHERE LOWER(username) = LOWER(?) GROUP BY uuid"
                + " ORDER BY CASE WHEN MIN(username) = ? THEN 0 ELSE 1 END, uuid ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                UUID resolvedUuid = UUID.fromString(rs.getString(COLUMN_UUID));
                if (rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(resolvedUuid);
            }
        } catch (IllegalArgumentException ignored) {
        } catch (SQLException e) {
            logSqlError("FindUuidByUsername error", e);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Double> getTopAccounts(String currency, int limit) {
        plugin.debug("Database Query: getTopAccounts for currency '" + currency + "' limit " + limit
                + " (excluding hidden and 'tax' accounts)");
        Map<String, Double> top = new LinkedHashMap<>();
        String sql = "SELECT username, balance FROM " + getTableName()
                + " WHERE currency = ? AND hidden = 0 AND username <> 'tax' ORDER BY balance DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currency);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String user = rs.getString(COLUMN_USERNAME);
                    double balance = rs.getDouble(COLUMN_BALANCE);
                    top.put(user, balance);
                    plugin.debug("Found top account: " + user + " = " + balance);
                }
            }
        } catch (SQLException e) {
            logSqlError("GetTopAccounts error", e);
        }
        return top;
    }

    @Override
    public Map<UUID, Double> getAccountsAboveBalance(String currency, double minimumBalance) {
        Map<UUID, Double> accounts = new LinkedHashMap<>();
        String sql = "SELECT uuid, (balance - frozen_balance) AS available_balance FROM " + getTableName()
                + " WHERE currency = ? AND username <> 'tax' AND (balance - frozen_balance) > ?"
                + " ORDER BY available_balance DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currency);
            ps.setDouble(2, minimumBalance);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        accounts.put(UUID.fromString(rs.getString(COLUMN_UUID)), rs.getDouble("available_balance"));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            logSqlError("GetAccountsAboveBalance error", e);
        }
        return accounts;
    }

    @Override
    public double getTotalBalance(String currency) {
        String sql = "SELECT SUM(balance) FROM " + getTableName()
                + " WHERE currency = ? AND hidden = 0 AND username <> 'tax'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            logSqlError("GetTotalBalance error", e);
        }
        return 0.0D;
    }

    @Override
    public void setHidden(UUID uuid, boolean hidden) {
        updateHidden(uuid, null, hidden);
    }

    @Override
    public boolean updateHidden(UUID uuid, String username, boolean hidden) {
        boolean hasUsername = username != null && !username.isBlank();
        String sql = hasUsername
                ? "UPDATE " + getTableName() + " SET hidden = ? WHERE uuid = ? OR LOWER(username) = LOWER(?)"
                : "UPDATE " + getTableName() + " SET hidden = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hidden ? 1 : 0);
            ps.setString(2, uuid.toString());
            if (hasUsername) {
                ps.setString(3, username);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logSqlError("SetHidden error", e);
            return false;
        }
    }

    @Override
    public Map<UUID, String> getUnknownAccounts() {
        Map<UUID, String> unknowns = new HashMap<>();
        String sql = "SELECT DISTINCT uuid, username FROM " + getTableName()
                + " WHERE username = 'Unknown' OR username IS NULL OR username = ''";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    unknowns.put(UUID.fromString(rs.getString(COLUMN_UUID)), rs.getString(COLUMN_USERNAME));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            logSqlError("GetUnknownAccounts error", e);
        }
        return unknowns;
    }

    @Override
    public boolean isHidden(UUID uuid) {
        String sql = "SELECT hidden FROM " + getTableName() + " WHERE uuid = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(COLUMN_HIDDEN) == 1;
                }
            }
        } catch (SQLException e) {
            logSqlError("IsHidden error", e);
        }
        return false;
    }

    private OptionalDouble findNumericColumn(UUID uuid, String currency, String column) {
        String sql = "SELECT " + column + " FROM " + getTableName() + " WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return OptionalDouble.of(rs.getDouble(column));
                }
            }
        } catch (SQLException e) {
            logSqlError("Read " + column + " error", e);
        }
        return OptionalDouble.empty();
    }

    private boolean executeAccountUpdate(String sql, String errorMessage, UUID uuid, String currency, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Object param : params) {
                ps.setObject(index++, param);
            }
            ps.setString(index++, uuid.toString());
            ps.setString(index, currency);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                plugin.debug("No rows updated for " + uuid + " / " + currency + " using SQL: " + sql);
            }
            return rows > 0;
        } catch (SQLException e) {
            logSqlError(errorMessage, e);
            return false;
        }
    }
}
