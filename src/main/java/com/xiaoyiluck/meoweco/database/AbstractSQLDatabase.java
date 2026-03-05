package com.xiaoyiluck.meoweco.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.xiaoyiluck.meoweco.MeowEco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public abstract class AbstractSQLDatabase implements DatabaseManager {
    protected final MeowEco plugin;
    protected HikariDataSource dataSource;
    private final String tableName = "meoweco_accounts";

    public AbstractSQLDatabase(MeowEco plugin) {
        this.plugin = plugin;
    }

    protected abstract void configureDataSource(HikariConfig config);
    protected abstract String getCreateStatement();

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        configureDataSource(config);
        config.setPoolName("MeowEco-Pool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        
        this.dataSource = new HikariDataSource(config);

        // First, ensure the table exists (legacy or new)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(getCreateStatement())) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database table", e);
        }

        // Migration logic: Add 'currency' column if it doesn't exist
        try (Connection conn = dataSource.getConnection()) {
            // Check if 'currency' exists
            boolean hasCurrency = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, "currency")) {
                if (rs.next()) hasCurrency = true;
            }
            
            if (!hasCurrency) {
                plugin.getLogger().info("Migrating database to support multiple currencies...");
                String defaultCurr = plugin.getConfig().getString("default-currency", "coins");
                
                // 1. Add column
                try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE " + tableName + " ADD COLUMN currency VARCHAR(32)")) {
                    ps.execute();
                }
                
                // 2. Set default value for existing rows
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tableName + " SET currency = ? WHERE currency IS NULL")) {
                    ps.setString(1, defaultCurr);
                    ps.executeUpdate();
                }
                
                // Note: Changing PRIMARY KEY is complex across SQL dialects. 
                // For SQLite, if we just added the currency column, we need to recreate the table to fix the PK.
                if (plugin.getConfig().getString("storage.type", "sqlite").equalsIgnoreCase("sqlite")) {
                    plugin.getLogger().info("SQLite detected. Recreating table to update Primary Key...");
                    try {
                        // 1. Create temporary table with correct schema
                        conn.createStatement().execute("CREATE TABLE meoweco_accounts_new (" +
                                "uuid VARCHAR(36), " +
                                "currency VARCHAR(32), " +
                                "balance DOUBLE NOT NULL, " +
                                "username VARCHAR(16), " +
                                "hidden INTEGER DEFAULT 0, " +
                                "PRIMARY KEY (uuid, currency))");
                        
                        // 2. Copy data
                        conn.createStatement().execute("INSERT INTO meoweco_accounts_new (uuid, currency, balance, username) " +
                                "SELECT uuid, currency, balance, username FROM " + tableName);
                        
                        // 3. Drop old table and rename new one
                        conn.createStatement().execute("DROP TABLE " + tableName);
                        conn.createStatement().execute("ALTER TABLE meoweco_accounts_new RENAME TO " + tableName);
                        
                        plugin.getLogger().info("Table recreation successful. Primary Key is now (uuid, currency).");
                    } catch (SQLException ex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to recreate table for PK update", ex);
                    }
                } else {
                    plugin.getLogger().info("MySQL detected. Updating Primary Key...");
                    try {
                        conn.createStatement().execute("ALTER TABLE " + tableName + " DROP PRIMARY KEY, ADD PRIMARY KEY (uuid, currency)");
                        plugin.getLogger().info("MySQL Primary Key updated to (uuid, currency).");
                    } catch (SQLException ex) {
                        plugin.getLogger().warning("Failed to update MySQL Primary Key. It might already be composite or requires manual intervention.");
                    }
                }
                
                plugin.getLogger().info("Database migration completed.");
            }

            // Check if PK is actually composite for SQLite even if column exists
            if (plugin.getConfig().getString("storage.type", "sqlite").equalsIgnoreCase("sqlite")) {
                try (ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(" + tableName + ")")) {
                    int pkCount = 0;
                    while (rs.next()) {
                        if (rs.getInt("pk") > 0) pkCount++;
                    }
                    if (pkCount < 2) {
                        plugin.getLogger().warning("SQLite detected with incomplete Primary Key (Count: " + pkCount + "). Re-triggering migration...");
                        // Force recreation by temporarily adding a dummy migration flag if needed, 
                        // but here we can just run the recreation logic again.
                        conn.createStatement().execute("CREATE TABLE meoweco_accounts_fix (" +
                                "uuid VARCHAR(36), " +
                                "currency VARCHAR(32), " +
                                "balance DOUBLE NOT NULL, " +
                                "username VARCHAR(16), " +
                                "hidden INTEGER DEFAULT 0, " +
                                "PRIMARY KEY (uuid, currency))");
                        conn.createStatement().execute("INSERT OR IGNORE INTO meoweco_accounts_fix (uuid, currency, balance, username, hidden) " +
                                "SELECT uuid, IFNULL(currency, '" + plugin.getConfig().getString("default-currency", "coins") + "'), balance, username, hidden FROM " + tableName);
                        conn.createStatement().execute("DROP TABLE " + tableName);
                        conn.createStatement().execute("ALTER TABLE meoweco_accounts_fix RENAME TO " + tableName);
                        plugin.getLogger().info("SQLite Primary Key fix applied.");
                    }
                }
            }
            
            // Add 'hidden' column if it doesn't exist
            boolean hasHidden = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, "hidden")) {
                if (rs.next()) hasHidden = true;
            }
            if (!hasHidden) {
                try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE " + tableName + " ADD COLUMN hidden INTEGER DEFAULT 0")) {
                    ps.execute();
                }
                // Ensure existing rows are set to 0
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tableName + " SET hidden = 0 WHERE hidden IS NULL")) {
                    ps.executeUpdate();
                }
            } else {
                // Even if column exists, some rows might be NULL if added via different migration path
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tableName + " SET hidden = 0 WHERE hidden IS NULL")) {
                    ps.executeUpdate();
                }
            }

            // Add 'frozen_balance' column if it doesn't exist
            boolean hasFrozen = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, "frozen_balance")) {
                if (rs.next()) hasFrozen = true;
            }
            if (!hasFrozen) {
                plugin.getLogger().info("Adding 'frozen_balance' column to database...");
                try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE " + tableName + " ADD COLUMN frozen_balance DOUBLE DEFAULT 0.0")) {
                    ps.execute();
                }
                // Ensure existing rows are set to 0.0
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tableName + " SET frozen_balance = 0.0 WHERE frozen_balance IS NULL")) {
                    ps.executeUpdate();
                }
            } else {
                // Ensure existing rows are set to 0.0
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tableName + " SET frozen_balance = 0.0 WHERE frozen_balance IS NULL")) {
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Migration error", e);
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

    @Override
    public boolean hasAccount(UUID uuid, String currency) {
        String sql = "SELECT 1 FROM " + tableName + " WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public boolean createAccount(UUID uuid, String currency, double initialBalance) {
        // Use a more robust check-then-insert or atomic insert if possible.
        // To handle concurrent joins and multi-currency initialization, we use a synchronized-like approach at DB level.
        
        // Check if account already exists
        if (hasAccount(uuid, currency)) return false;
        
        // Find existing info (username/hidden status) from any existing currency entry for this player
        String existingInfoSql = "SELECT username, hidden FROM " + tableName + " WHERE uuid = ? LIMIT 1";
        String name = "Unknown";
        int hidden = 0;
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(existingInfoSql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    name = rs.getString("username");
                    hidden = rs.getInt("hidden");
                } else {
                    org.bukkit.OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
                    if (player.getName() != null) {
                        name = player.getName();
                    }
                }
            }
        } catch (SQLException ignored) {}

        // Use INSERT OR IGNORE style or similar. Since we support both SQLite and MySQL, 
        // we'll handle the unique constraint violation gracefully if it happens despite the check.
        String sql = "INSERT INTO " + tableName + " (uuid, currency, balance, username, hidden, frozen_balance) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            ps.setDouble(3, initialBalance);
            ps.setString(4, name);
            ps.setInt(5, hidden);
            ps.setDouble(6, 0.0);
            
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // If it's a primary key/unique constraint violation, it means the account was created by another thread/process
            // between our hasAccount check and this insert. We can safely ignore this.
            if (e.getErrorCode() == 19 || e.getSQLState().equals("23000") || e.getMessage().contains("UNIQUE") || e.getMessage().contains("PRIMARY")) {
                return false;
            }
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid, String currency) {
        String sql = "SELECT balance FROM " + tableName + " WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
        }
        return 0.0;
    }

    @Override
    public boolean deposit(UUID uuid, String currency, double amount) {
        String sql = "UPDATE " + tableName + " SET balance = balance + ? WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.setString(3, currency);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                plugin.debug("Deposit failed: No rows updated for player " + uuid + " and currency " + currency);
            }
            return rows > 0;
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                plugin.getLogger().log(Level.SEVERE, "Deposit error", e);
            }
            return false;
        }
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        String sql = "UPDATE " + tableName + " SET balance = ? WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.setString(3, currency);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                plugin.debug("SetBalance failed: No rows updated for player " + uuid + " and currency " + currency);
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                plugin.getLogger().log(Level.SEVERE, "SetBalance error", e);
            }
        }
    }

    @Override
    public boolean withdraw(UUID uuid, String currency, double amount) {
        String sql = "UPDATE " + tableName + " SET balance = balance - ? WHERE uuid = ? AND currency = ? AND balance - frozen_balance >= ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.setString(3, currency);
            ps.setDouble(4, amount);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public boolean transfer(UUID from, UUID to, String currency, double amount) {
        return transfer(from, to, currency, amount, amount);
    }

    @Override
    public boolean transfer(UUID from, UUID to, String currency, double withdrawAmount, double depositAmount) {
        String withdrawSql = "UPDATE " + tableName + " SET balance = balance - ? WHERE uuid = ? AND currency = ? AND balance - frozen_balance >= ?";
        String depositSql = "UPDATE " + tableName + " SET balance = balance + ? WHERE uuid = ? AND currency = ?";
        
        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement psWithdraw = conn.prepareStatement(withdrawSql);
                 PreparedStatement psDeposit = conn.prepareStatement(depositSql)) {
                
                psWithdraw.setDouble(1, withdrawAmount);
                psWithdraw.setString(2, from.toString());
                psWithdraw.setString(3, currency);
                psWithdraw.setDouble(4, withdrawAmount);
                
                int rows = psWithdraw.executeUpdate();
                if (rows > 0) {
                    psDeposit.setDouble(1, depositAmount);
                    psDeposit.setString(2, to.toString());
                    psDeposit.setString(3, currency);
                    int depositRows = psDeposit.executeUpdate();
                    
                    if (depositRows > 0) {
                        conn.commit();
                        return true;
                    } else {
                        // Target account likely doesn't exist
                        conn.rollback();
                        return false;
                    }
                } else {
                    conn.rollback();
                    return false;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    @Override
    public double getFrozenBalance(UUID uuid, String currency) {
        String sql = "SELECT frozen_balance FROM " + tableName + " WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("frozen_balance");
                }
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
        }
        return 0.0;
    }

    @Override
    public void setFrozenBalance(UUID uuid, String currency, double amount) {
        String sql = "UPDATE " + tableName + " SET frozen_balance = ? WHERE uuid = ? AND currency = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.setString(3, currency);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                plugin.getLogger().log(Level.SEVERE, "SetFrozenBalance error", e);
            }
        }
    }

    @Override
    public boolean freeze(UUID uuid, String currency, double amount) {
        // We can only freeze what we have (available balance)
        String sql = "UPDATE " + tableName + " SET frozen_balance = frozen_balance + ? WHERE uuid = ? AND currency = ? AND (balance - frozen_balance) >= ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.setString(3, currency);
            ps.setDouble(4, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                plugin.getLogger().log(Level.SEVERE, "Freeze error", e);
            }
            return false;
        }
    }

    @Override
    public boolean unfreeze(UUID uuid, String currency, double amount) {
        // We can only unfreeze what is frozen
        String sql = "UPDATE " + tableName + " SET frozen_balance = frozen_balance - ? WHERE uuid = ? AND currency = ? AND frozen_balance >= ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.setString(3, currency);
            ps.setDouble(4, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                plugin.getLogger().log(Level.SEVERE, "Unfreeze error", e);
            }
            return false;
        }
    }

    @Override
    public boolean deductFrozen(UUID uuid, String currency, double amount) {
        // Deduct from both total balance and frozen balance
        String sql = "UPDATE " + tableName + " SET balance = balance - ?, frozen_balance = frozen_balance - ? WHERE uuid = ? AND currency = ? AND frozen_balance >= ? AND balance >= ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setDouble(2, amount);
            ps.setString(3, uuid.toString());
            ps.setString(4, currency);
            ps.setDouble(5, amount);
            ps.setDouble(6, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                plugin.getLogger().log(Level.SEVERE, "DeductFrozen error", e);
            }
            return false;
        }
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        String sql = "UPDATE " + tableName + " SET username = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Map<String, Double> getTopAccounts(String currency, int limit) {
        plugin.debug("Database Query: getTopAccounts for currency '" + currency + "' limit " + limit + " (excluding hidden and 'tax' accounts)");
        Map<String, Double> top = new LinkedHashMap<>();
        String sql = "SELECT username, balance FROM " + tableName + " WHERE currency = ? AND hidden = 0 AND username <> 'tax' ORDER BY balance DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currency);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String user = rs.getString("username");
                    double bal = rs.getDouble("balance");
                    top.put(user, bal);
                    plugin.debug("Found top account: " + user + " = " + bal);
                }
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
        }
        plugin.debug("getTopAccounts returned " + top.size() + " entries.");
        return top;
    }

    @Override
    public double getTotalBalance(String currency) {
        plugin.debug("Database Query: getTotalBalance for currency '" + currency + "' (excluding hidden and 'tax' accounts)");
        String sql = "SELECT SUM(balance) FROM " + tableName + " WHERE currency = ? AND hidden = 0 AND username <> 'tax'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double total = rs.getDouble(1);
                    plugin.debug("Total balance for " + currency + ": " + total);
                    return total;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    @Override
    public void setHidden(UUID uuid, boolean hidden) {
        plugin.debug("Database Update: setHidden for " + uuid + " to " + hidden);
        String sql = "UPDATE " + tableName + " SET hidden = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hidden ? 1 : 0);
            ps.setString(2, uuid.toString());
            int rows = ps.executeUpdate();
            plugin.debug("setHidden updated " + rows + " rows.");
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Map<UUID, String> getUnknownAccounts() {
        Map<UUID, String> unknowns = new java.util.HashMap<>();
        // For unknown accounts, we only need to check one entry per UUID
        String sql = "SELECT DISTINCT uuid, username FROM " + tableName + " WHERE username = 'Unknown' OR username IS NULL";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    unknowns.put(UUID.fromString(rs.getString("uuid")), rs.getString("username"));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
        }
        return unknowns;
    }

    @Override
    public boolean isHidden(UUID uuid) {
        String sql = "SELECT hidden FROM " + tableName + " WHERE uuid = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("hidden") == 1;
                }
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DataSource is closed") && !e.getMessage().contains("has been closed")) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
