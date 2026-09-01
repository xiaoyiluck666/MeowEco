package com.xiaoyiluck.meoweco.database;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.database.PrecisionReport.CurrencyReport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractSQLDatabase implements DatabaseManager {
    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String TABLE_NAME = "meoweco_accounts";
    private static final String AUDIT_TABLE_NAME = "meoweco_audit_log";
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
    private final ThreadLocal<AuditContext> auditContext = ThreadLocal.withInitial(AuditContext::system);

    public AbstractSQLDatabase(MeowEco plugin) {
        this.plugin = plugin;
    }

    protected abstract void configureDataSource(HikariConfig config);

    protected void configurePool(HikariConfig config) {
        config.setPoolName("MeowEco-Pool");
        config.setMaximumPoolSize(getPoolInt("storage.mysql.pool.maximum-pool-size", 10));
        config.setMinimumIdle(getPoolInt("storage.mysql.pool.minimum-idle", 2));
        config.setConnectionTimeout(getPoolLong("storage.mysql.pool.connection-timeout", 10000L));
        config.setValidationTimeout(getPoolLong("storage.mysql.pool.validation-timeout", 5000L));
        config.setIdleTimeout(getPoolLong("storage.mysql.pool.idle-timeout", 600000L));
        config.setMaxLifetime(getPoolLong("storage.mysql.pool.max-lifetime", 1800000L));
    }

    protected int getPoolInt(String path, int defaultValue) {
        return plugin.getConfig().getInt(path, defaultValue);
    }

    protected long getPoolLong(String path, long defaultValue) {
        return plugin.getConfig().getLong(path, defaultValue);
    }

    protected String getDefaultCurrencyId() {
        return plugin.getConfig().getString("default-currency", "coins");
    }

    protected Logger getLogger() {
        return plugin.getLogger();
    }

    protected String getOfflinePlayerName(UUID uuid) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        return player.getName();
    }

    protected void debug(String message) {
        plugin.debug(message);
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
            getLogger().log(Level.SEVERE, "Could not initialize database schema", e);
        }
    }

    private void ensureSchema(Connection conn) throws SQLException {
        ensureMetaTable(conn);
        try (Statement statement = conn.createStatement()) {
            statement.execute(getCreateStatement());
        }
        ensureAuditTable(conn);

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int previousSchemaVersion = getSchemaVersion(conn);
            Map<String, String> columns = getColumnTypes(conn);
            String defaultCurrency = getDefaultCurrencyId();

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
                getLogger().info("Database schema version updated from "
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

        getLogger().info("Migrating SQLite economy table to the latest schema...");

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
            getLogger().info("Adding currency column to MySQL economy table...");
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " ADD COLUMN currency VARCHAR(32) NULL");
            executeUpdate(conn, "UPDATE " + getTableName() + " SET currency = ? WHERE currency IS NULL OR currency = ''", defaultCurrency);
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " MODIFY COLUMN currency VARCHAR(32) NOT NULL");
            columns.put(COLUMN_CURRENCY, "VARCHAR");
        }

        if (!columns.containsKey(COLUMN_HIDDEN)) {
            getLogger().info("Adding hidden column to MySQL economy table...");
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0");
            columns.put(COLUMN_HIDDEN, "INTEGER");
        }

        if (!columns.containsKey(COLUMN_FROZEN_BALANCE)) {
            getLogger().info("Adding frozen_balance column to MySQL economy table...");
            executeUpdate(conn, "ALTER TABLE " + getTableName() + " ADD COLUMN frozen_balance DOUBLE NOT NULL DEFAULT 0.0");
            columns.put(COLUMN_FROZEN_BALANCE, "DOUBLE");
        }

        if (!hasCompositePrimaryKey(conn)) {
            getLogger().info("Updating MySQL primary key to (uuid, currency)...");
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
        createIndexIfMissing(conn, getTableName(), indexName, createSql);
    }

    private void createIndexIfMissing(Connection conn, String tableName, String indexName, String createSql) throws SQLException {
        if (hasIndex(conn, tableName, indexName)) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute(createSql);
        }
    }

    private boolean hasIndex(Connection conn, String indexName) throws SQLException {
        return hasIndex(conn, getTableName(), indexName);
    }

    private boolean hasIndex(Connection conn, String tableName, String indexName) throws SQLException {
        String expected = indexName.toLowerCase(Locale.ROOT);
        try (ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), null, tableName, false, false)) {
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
            getLogger().log(Level.SEVERE, action, e);
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
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(existingInfoSql)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String existingName = rs.getString(COLUMN_USERNAME);
                            name = (existingName == null || existingName.isBlank()) ? "Unknown" : existingName;
                            hidden = rs.getInt(COLUMN_HIDDEN);
                        } else {
                            String playerName = getOfflinePlayerName(uuid);
                            if (playerName != null && !playerName.isBlank()) {
                                name = playerName;
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
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }
                AccountSnapshot after = readAccountSnapshot(conn, uuid, currency);
                AccountSnapshot before = new AccountSnapshot(name, 0.0D, 0.0D);
                insertAudit(conn, UUID.randomUUID().toString(), uuid, currency,
                        "CREATE_ACCOUNT", initialBalance, before, after);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
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
        return executeAccountUpdate(sql, "Deposit error", uuid, currency, "DEPOSIT", amount, amount);
    }

    @Override
    public boolean updateBalance(UUID uuid, String currency, double amount) {
        if (!isNonNegativeFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName() + " SET balance = ? WHERE uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "UpdateBalance error", uuid, currency, "SET_BALANCE", null, amount);
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
        return executeAccountUpdate(sql, "Withdraw error", uuid, currency, "WITHDRAW", -amount, amount, amount);
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
                AccountSnapshot fromBefore = readAccountSnapshot(conn, from, currency);
                AccountSnapshot toBefore = readAccountSnapshot(conn, to, currency);
                if (fromBefore == null || toBefore == null) {
                    conn.rollback();
                    return false;
                }
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

                String transactionId = UUID.randomUUID().toString();
                AccountSnapshot fromAfter = readAccountSnapshot(conn, from, currency);
                AccountSnapshot toAfter = readAccountSnapshot(conn, to, currency);
                insertAudit(conn, transactionId, from, currency, "TRANSFER_OUT", -withdrawAmount, fromBefore, fromAfter);
                insertAudit(conn, transactionId, to, currency, "TRANSFER_IN", depositAmount, toBefore, toAfter);
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
                AccountSnapshot fromBefore = readAccountSnapshot(conn, uuid, fromCurrency);
                AccountSnapshot toBefore = readAccountSnapshot(conn, uuid, toCurrency);
                if (fromBefore == null || toBefore == null) {
                    conn.rollback();
                    return false;
                }
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

                String transactionId = UUID.randomUUID().toString();
                AccountSnapshot fromAfter = readAccountSnapshot(conn, uuid, fromCurrency);
                AccountSnapshot toAfter = readAccountSnapshot(conn, uuid, toCurrency);
                insertAudit(conn, transactionId, uuid, fromCurrency, "EXCHANGE_OUT", -withdrawAmount, fromBefore, fromAfter);
                insertAudit(conn, transactionId, uuid, toCurrency, "EXCHANGE_IN", depositAmount, toBefore, toAfter);
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
        return executeAccountUpdate(sql, "UpdateFrozenBalance error", uuid, currency, "SET_FROZEN", null, amount);
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
        return executeAccountUpdate(sql, "Freeze error", uuid, currency, "FREEZE", amount, amount, amount);
    }

    @Override
    public boolean unfreeze(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName()
                + " SET frozen_balance = frozen_balance - ? WHERE frozen_balance >= ? AND uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "Unfreeze error", uuid, currency, "UNFREEZE", -amount, amount, amount);
    }

    @Override
    public boolean deductFrozen(UUID uuid, String currency, double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        String sql = "UPDATE " + getTableName()
                + " SET balance = balance - ?, frozen_balance = frozen_balance - ? "
                + "WHERE frozen_balance >= ? AND balance >= ? AND uuid = ? AND currency = ?";
        return executeAccountUpdate(sql, "DeductFrozen error", uuid, currency, "DEDUCT_FROZEN", -amount,
                amount, amount, amount, amount);
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

    private void ensureAuditTable(Connection conn) throws SQLException {
        String idColumn = isSQLite()
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + AUDIT_TABLE_NAME + " ("
                    + "id " + idColumn + ", "
                    + "transaction_id VARCHAR(36) NOT NULL, "
                    + "created_at BIGINT NOT NULL, "
                    + "account_uuid VARCHAR(36) NOT NULL, "
                    + "username VARCHAR(64) NOT NULL, "
                    + "currency VARCHAR(32) NOT NULL, "
                    + "operation VARCHAR(32) NOT NULL, "
                    + "amount DOUBLE NOT NULL, "
                    + "balance_before DOUBLE NOT NULL, "
                    + "balance_after DOUBLE NOT NULL, "
                    + "frozen_before DOUBLE NOT NULL, "
                    + "frozen_after DOUBLE NOT NULL, "
                    + "source VARCHAR(64) NOT NULL, "
                    + "actor VARCHAR(64) NOT NULL)"
            );
        }
        createIndexIfMissing(conn, AUDIT_TABLE_NAME, "idx_meoweco_audit_account_time",
                "CREATE INDEX idx_meoweco_audit_account_time ON " + AUDIT_TABLE_NAME + " (account_uuid, created_at)");
        createIndexIfMissing(conn, AUDIT_TABLE_NAME, "idx_meoweco_audit_time",
                "CREATE INDEX idx_meoweco_audit_time ON " + AUDIT_TABLE_NAME + " (created_at)");
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
        debug("Database Query: getTopAccounts for currency '" + currency + "' limit " + limit
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
                    debug("Found top account: " + user + " = " + balance);
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

    @Override
    public PrecisionReport reportPrecisionIssues(Map<String, Integer> currencyScales) {
        if (currencyScales == null || currencyScales.isEmpty()) {
            return PrecisionReport.empty();
        }

        List<CurrencyReport> reports = new ArrayList<>();
        Set<UUID> affectedAccounts = new LinkedHashSet<>();
        int totalBalances = 0;
        int totalFrozenBalances = 0;
        String sql = "SELECT uuid, balance, frozen_balance FROM " + getTableName() + " WHERE currency = ?";

        try (Connection conn = getConnection()) {
            for (Map.Entry<String, Integer> entry : currencyScales.entrySet()) {
                String currencyId = entry.getKey();
                int decimalPlaces = Math.max(0, entry.getValue());
                Set<UUID> currencyAccounts = new LinkedHashSet<>();
                int currencyBalances = 0;
                int currencyFrozenBalances = 0;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, currencyId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            UUID uuid;
                            try {
                                uuid = UUID.fromString(rs.getString(COLUMN_UUID));
                            } catch (IllegalArgumentException ignored) {
                                continue;
                            }

                            boolean balanceIssue = exceedsScale(rs.getDouble(COLUMN_BALANCE), decimalPlaces);
                            boolean frozenIssue = exceedsScale(rs.getDouble(COLUMN_FROZEN_BALANCE), decimalPlaces);
                            if (balanceIssue) {
                                currencyBalances++;
                                totalBalances++;
                            }
                            if (frozenIssue) {
                                currencyFrozenBalances++;
                                totalFrozenBalances++;
                            }
                            if (balanceIssue || frozenIssue) {
                                currencyAccounts.add(uuid);
                                affectedAccounts.add(uuid);
                            }
                        }
                    }
                }

                if (!currencyAccounts.isEmpty()) {
                    reports.add(new CurrencyReport(currencyId, decimalPlaces, currencyAccounts.size(), currencyBalances, currencyFrozenBalances));
                }
            }
        } catch (SQLException e) {
            logSqlError("ReportPrecisionIssues error", e);
        }

        return new PrecisionReport(affectedAccounts.size(), totalBalances, totalFrozenBalances, reports);
    }

    private boolean exceedsScale(double amount, int decimalPlaces) {
        if (!Double.isFinite(amount)) {
            return true;
        }
        return BigDecimal.valueOf(amount).stripTrailingZeros().scale() > decimalPlaces;
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

    private boolean executeAccountUpdate(String sql, String errorMessage, UUID uuid, String currency,
                                         String operation, Double auditAmount, Object... params) {
        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                AccountSnapshot before = readAccountSnapshot(conn, uuid, currency);
                if (before == null) {
                    conn.rollback();
                    return false;
                }

                int index = 1;
                for (Object param : params) {
                    ps.setObject(index++, param);
                }
                ps.setString(index++, uuid.toString());
                ps.setString(index, currency);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    debug("No rows updated for " + uuid + " / " + currency + " using SQL: " + sql);
                    return false;
                }

                AccountSnapshot after = readAccountSnapshot(conn, uuid, currency);
                double amount = auditAmount == null
                        ? nonZeroDifference(after.balance() - before.balance(), after.frozen() - before.frozen())
                        : auditAmount;
                insertAudit(conn, UUID.randomUUID().toString(), uuid, currency, operation, amount, before, after);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            logSqlError(errorMessage, e);
            return false;
        }
    }

    @Override
    public AuditScope openAuditScope(String source, String actor) {
        AuditContext previous = auditContext.get();
        auditContext.set(new AuditContext(cleanAuditText(source, "api"), cleanAuditText(actor, "system")));
        return () -> auditContext.set(previous);
    }

    @Override
    public List<AuditEntry> getAuditHistory(UUID uuid, String currency, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String sql = "SELECT * FROM " + AUDIT_TABLE_NAME + " WHERE account_uuid = ?"
                + (currency == null || currency.isBlank() ? "" : " AND currency = ?")
                + " ORDER BY id DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            ps.setString(index++, uuid.toString());
            if (currency != null && !currency.isBlank()) {
                ps.setString(index++, currency);
            }
            ps.setInt(index, safeLimit);
            return readAuditEntries(ps);
        } catch (SQLException e) {
            logSqlError("Audit history query failed", e);
            return List.of();
        }
    }

    @Override
    public List<AuditEntry> getRecentAudit(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10000));
        String sql = "SELECT * FROM " + AUDIT_TABLE_NAME + " ORDER BY id DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            return readAuditEntries(ps);
        } catch (SQLException e) {
            logSqlError("Recent audit query failed", e);
            return List.of();
        }
    }

    @Override
    public MigrationResult importBalances(List<MigrationBalance> balances, String currency, String source, String actor) {
        if (currency == null || currency.isBlank() || balances == null || balances.isEmpty()) {
            return MigrationResult.failed("No valid balances were supplied.");
        }

        Map<UUID, MigrationBalance> uniqueBalances = new LinkedHashMap<>();
        for (MigrationBalance balance : balances) {
            if (balance == null || balance.uuid() == null || !isNonNegativeFinite(balance.balance())) {
                return MigrationResult.failed("Migration contains an invalid UUID or balance.");
            }
            uniqueBalances.put(balance.uuid(), balance);
        }

        String selectSql = "SELECT username, balance, frozen_balance FROM " + getTableName()
                + " WHERE uuid = ? AND currency = ?";
        String updateSql = "UPDATE " + getTableName() + " SET balance = ?, username = ? WHERE uuid = ? AND currency = ?";
        String insertSql = "INSERT INTO " + getTableName()
                + " (uuid, currency, balance, username, hidden, frozen_balance) VALUES (?, ?, ?, ?, 0, 0.0)";
        String transactionId = UUID.randomUUID().toString();
        AuditContext previousContext = auditContext.get();
        auditContext.set(new AuditContext(cleanAuditText(source, "migration"), cleanAuditText(actor, "console")));

        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            int created = 0;
            int updated = 0;
            double previousTotal = 0.0D;
            double importedTotal = 0.0D;
            try (PreparedStatement select = conn.prepareStatement(selectSql);
                 PreparedStatement update = conn.prepareStatement(updateSql);
                 PreparedStatement insert = conn.prepareStatement(insertSql)) {
                for (MigrationBalance migration : uniqueBalances.values()) {
                    String username = cleanUsername(migration.username());
                    select.setString(1, migration.uuid().toString());
                    select.setString(2, currency);
                    AccountSnapshot before;
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            before = new AccountSnapshot(rs.getString(COLUMN_USERNAME),
                                    rs.getDouble(COLUMN_BALANCE), rs.getDouble(COLUMN_FROZEN_BALANCE));
                        } else {
                            before = null;
                        }
                    }

                    if (before == null) {
                        insert.setString(1, migration.uuid().toString());
                        insert.setString(2, currency);
                        insert.setDouble(3, migration.balance());
                        insert.setString(4, username);
                        insert.executeUpdate();
                        before = new AccountSnapshot(username, 0.0D, 0.0D);
                        created++;
                    } else {
                        update.setDouble(1, migration.balance());
                        update.setString(2, username);
                        update.setString(3, migration.uuid().toString());
                        update.setString(4, currency);
                        update.executeUpdate();
                        previousTotal += before.balance();
                        updated++;
                    }

                    AccountSnapshot after = readAccountSnapshot(conn, migration.uuid(), currency);
                    insertAudit(conn, transactionId, migration.uuid(), currency, "MIGRATE_SET",
                            migration.balance() - before.balance(), before, after);
                    importedTotal += migration.balance();
                }
                conn.commit();
                return new MigrationResult(true, uniqueBalances.size(), created, updated,
                        previousTotal, importedTotal, transactionId, "");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            logSqlError("Transactional balance migration failed", e);
            return MigrationResult.failed(e.getMessage());
        } finally {
            auditContext.set(previousContext);
        }
    }

    private AccountSnapshot readAccountSnapshot(Connection conn, UUID uuid, String currency) throws SQLException {
        String sql = "SELECT username, balance, frozen_balance FROM " + getTableName()
                + " WHERE uuid = ? AND currency = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AccountSnapshot(rs.getString(COLUMN_USERNAME),
                        rs.getDouble(COLUMN_BALANCE), rs.getDouble(COLUMN_FROZEN_BALANCE));
            }
        }
    }

    private void insertAudit(Connection conn, String transactionId, UUID uuid, String currency, String operation,
                             double amount, AccountSnapshot before, AccountSnapshot after) throws SQLException {
        if (before == null || after == null) {
            throw new SQLException("Audit snapshot is missing for " + uuid + " / " + currency);
        }
        AuditContext context = auditContext.get();
        String sql = "INSERT INTO " + AUDIT_TABLE_NAME
                + " (transaction_id, created_at, account_uuid, username, currency, operation, amount,"
                + " balance_before, balance_after, frozen_before, frozen_after, source, actor)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.setString(4, cleanAuditText(after.username(), "Unknown"));
            ps.setString(5, currency);
            ps.setString(6, operation);
            ps.setDouble(7, amount);
            ps.setDouble(8, before.balance());
            ps.setDouble(9, after.balance());
            ps.setDouble(10, before.frozen());
            ps.setDouble(11, after.frozen());
            ps.setString(12, context.source());
            ps.setString(13, context.actor());
            ps.executeUpdate();
        }
    }

    private List<AuditEntry> readAuditEntries(PreparedStatement ps) throws SQLException {
        List<AuditEntry> entries = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(new AuditEntry(
                        rs.getLong("id"),
                        rs.getString("transaction_id"),
                        Instant.ofEpochMilli(rs.getLong("created_at")),
                        UUID.fromString(rs.getString("account_uuid")),
                        rs.getString("username"),
                        rs.getString("currency"),
                        rs.getString("operation"),
                        rs.getDouble("amount"),
                        rs.getDouble("balance_before"),
                        rs.getDouble("balance_after"),
                        rs.getDouble("frozen_before"),
                        rs.getDouble("frozen_after"),
                        rs.getString("source"),
                        rs.getString("actor")
                ));
            }
        }
        return entries;
    }

    private double nonZeroDifference(double balanceDifference, double frozenDifference) {
        return Math.abs(balanceDifference) > 0.0000001D ? balanceDifference : frozenDifference;
    }

    private String cleanUsername(String username) {
        String clean = username == null || username.isBlank() ? "Unknown" : username.trim();
        return clean.length() <= 16 ? clean : clean.substring(0, 16);
    }

    private String cleanAuditText(String value, String fallback) {
        String clean = value == null || value.isBlank() ? fallback : value.trim();
        return clean.length() <= 64 ? clean : clean.substring(0, 64);
    }

    private record AccountSnapshot(String username, double balance, double frozen) {
    }

    private record AuditContext(String source, String actor) {
        private static AuditContext system() {
            return new AuditContext("api", "system");
        }
    }
}
