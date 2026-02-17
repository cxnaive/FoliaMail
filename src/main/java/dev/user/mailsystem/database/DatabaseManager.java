package dev.user.mailsystem.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.user.mailsystem.MailSystemPlugin;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

public class DatabaseManager {

    private final MailSystemPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        // 保存原始 ClassLoader
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // 使用插件的 ClassLoader 作为上下文 ClassLoader
            // 这样 HikariCP 就能找到打包的 H2 驱动
            Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

            HikariConfig config = new HikariConfig();
            String dbType = plugin.getMailConfig().getDatabaseType();

            if ("mysql".equals(dbType)) {
                // 手动注册 MySQL 驱动（使用重定位后的类名）
                try {
                    Driver mysqlDriver = (Driver) Class.forName("dev.user.mailsystem.libs.com.mysql.cj.jdbc.Driver", true, plugin.getClass().getClassLoader()).getDeclaredConstructor().newInstance();
                    DriverManager.registerDriver(new DriverShim(mysqlDriver));
                } catch (Exception e) {
                    plugin.getLogger().warning("MySQL 驱动注册失败（可能已注册）: " + e.getMessage());
                }

                config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true",
                        plugin.getMailConfig().getMysqlHost(),
                        plugin.getMailConfig().getMysqlPort(),
                        plugin.getMailConfig().getMysqlDatabase()));
                config.setUsername(plugin.getMailConfig().getMysqlUsername());
                config.setPassword(plugin.getMailConfig().getMysqlPassword());
                config.setMaximumPoolSize(plugin.getMailConfig().getMysqlPoolSize());
                config.setDriverClassName("dev.user.mailsystem.libs.com.mysql.cj.jdbc.Driver");
            } else {
                // 手动注册 H2 驱动（使用重定位后的类名）
                try {
                    Driver h2Driver = (Driver) Class.forName("dev.user.mailsystem.libs.org.h2.Driver", true, plugin.getClass().getClassLoader()).getDeclaredConstructor().newInstance();
                    DriverManager.registerDriver(new DriverShim(h2Driver));
                } catch (Exception e) {
                    plugin.getLogger().warning("H2 驱动注册失败（可能已注册）: " + e.getMessage());
                }

                String filePath = plugin.getDataFolder().getAbsolutePath() + "/" + plugin.getMailConfig().getH2FileName();
                config.setJdbcUrl("jdbc:h2:file:" + filePath + ";MODE=MySQL");
                config.setUsername("sa");
                config.setPassword("");
                config.setMaximumPoolSize(5);
                config.setDriverClassName("dev.user.mailsystem.libs.org.h2.Driver");
            }

            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("MailSystemPool");

            // 应用超时配置
            long connectionTimeout = plugin.getMailConfig().getConnectionTimeout();
            int lockWaitTimeout = plugin.getMailConfig().getLockWaitTimeout();

            config.setConnectionTimeout(connectionTimeout);

            // 设置数据库特定的超时参数
            if ("mysql".equals(dbType)) {
                // MySQL: lockWaitTimeout 单位是秒，需要转换为毫秒配置socketTimeout
                config.addDataSourceProperty("socketTimeout", String.valueOf(plugin.getMailConfig().getQueryTimeout() * 1000));
                config.addDataSourceProperty("lockWaitTimeout", String.valueOf(lockWaitTimeout * 1000)); // MySQL是毫秒
            } else {
                // H2: 在URL中配置LOCK_TIMEOUT
                String lockTimeoutMs = String.valueOf(lockWaitTimeout * 1000);
                config.setJdbcUrl(config.getJdbcUrl() + ";LOCK_TIMEOUT=" + lockTimeoutMs);
            }

            dataSource = new HikariDataSource(config);

            createTables();
            plugin.getLogger().info("数据库连接成功！类型: " + ("mysql".equals(dbType) ? "MySQL" : "H2"));
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("数据库连接失败: " + e.getMessage());
            // 只记录异常类型和消息，避免泄露敏感信息（如密码）
            plugin.getLogger().warning("异常类型: " + e.getClass().getName());
            if (e.getCause() != null) {
                plugin.getLogger().warning("根因: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
            }
            return false;
        } finally {
            // 恢复原始 ClassLoader
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * JDBC 驱动包装类，用于在 Shadow 打包后正确注册驱动
     */
    private static class DriverShim implements Driver {
        private final Driver driver;

        DriverShim(Driver driver) {
            this.driver = driver;
        }

        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException {
            return driver.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return driver.acceptsURL(url);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
            return driver.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(DriverShim.class.getName());
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            String dbType = plugin.getMailConfig().getDatabaseType();
            boolean isMySQL = "mysql".equals(dbType);

            String textType = isMySQL ? "TEXT" : "CLOB";
            String longTextType = isMySQL ? "LONGTEXT" : "CLOB";
            String blobType = isMySQL ? "BLOB" : "BLOB";

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mails (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "sender_uuid VARCHAR(36) NOT NULL, " +
                    "sender_name VARCHAR(32) NOT NULL, " +
                    "receiver_uuid VARCHAR(36) NOT NULL, " +
                    "receiver_name VARCHAR(32) NOT NULL, " +
                    "title VARCHAR(100) NOT NULL, " +
                    "content " + longTextType + ", " +
                    "attachments " + blobType + ", " +
                    "money_attachment DOUBLE DEFAULT 0, " +
                    "sent_time BIGINT NOT NULL, " +
                    "expire_time BIGINT NOT NULL, " +
                    "is_read BOOLEAN DEFAULT FALSE, " +
                    "is_claimed BOOLEAN DEFAULT FALSE, " +
                    "read_time BIGINT DEFAULT 0, " +
                    "server_id VARCHAR(50) DEFAULT ''" +
                    ")");

            // 检查并添加 money_attachment 列（兼容旧版本数据库）
            addColumnIfNotExists(conn, "mails", "money_attachment", "DOUBLE DEFAULT 0", isMySQL);

            // 创建索引（MySQL 和 H2 语法不同）
            createIndexIfNotExists(conn, "mails", "idx_receiver", "receiver_uuid", isMySQL);
            createIndexIfNotExists(conn, "mails", "idx_sender", "sender_uuid", isMySQL);
            createIndexIfNotExists(conn, "mails", "idx_expire", "expire_time", isMySQL);
            createIndexIfNotExists(conn, "mails", "idx_server", "server_id", isMySQL);

            // 创建玩家缓存表 - 以 player_name 为唯一键，同名则更新 UUID
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_cache (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "player_name VARCHAR(32) PRIMARY KEY, " +
                    "last_seen BIGINT NOT NULL" +
                    ")");

            createIndexIfNotExists(conn, "player_cache", "idx_player_uuid", "uuid", isMySQL);

            // 创建邮件发送日志表（用于每日发送限制统计，删除邮件不影响此表）
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mail_send_log (" +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "send_date VARCHAR(10) NOT NULL, " +  // 格式: yyyy-MM-dd
                    "send_count INT NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (player_uuid, send_date)" +
                    ")");

            createIndexIfNotExists(conn, "mail_send_log", "idx_send_log_date", "send_date", isMySQL);

            // 创建黑名单表
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mail_blacklist (" +
                    "owner_uuid VARCHAR(36) NOT NULL, " +     // 黑名单所有者
                    "blocked_uuid VARCHAR(36) NOT NULL, " +   // 被屏蔽的玩家
                    "blocked_time BIGINT NOT NULL, " +        // 添加时间
                    "PRIMARY KEY (owner_uuid, blocked_uuid)" +
                    ")");

            createIndexIfNotExists(conn, "mail_blacklist", "idx_blacklist_owner", "owner_uuid", isMySQL);
            createIndexIfNotExists(conn, "mail_blacklist", "idx_blacklist_blocked", "blocked_uuid", isMySQL);

            // 创建邮件模板表
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mail_templates (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(64) UNIQUE NOT NULL, " +
                    "display_name VARCHAR(128), " +
                    "title VARCHAR(256) NOT NULL, " +
                    "content " + longTextType + ", " +
                    "attachments " + blobType + ", " +
                    "money_attachment DOUBLE DEFAULT 0, " +
                    "creator_uuid VARCHAR(36), " +
                    "creator_name VARCHAR(64), " +
                    "created_at BIGINT NOT NULL, " +
                    "updated_at BIGINT NOT NULL, " +
                    "use_count INT DEFAULT 0" +
                    ")");

            createIndexIfNotExists(conn, "mail_templates", "idx_template_name", "name", isMySQL);
            createIndexIfNotExists(conn, "mail_templates", "idx_template_creator", "creator_uuid", isMySQL);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接已关闭。");
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // 有效的表名白名单
    private static final Set<String> VALID_TABLES = Set.of("mails", "player_cache", "mail_send_log", "mail_blacklist", "mail_templates");
    // 有效的列名白名单（用于索引）
    private static final Set<String> VALID_COLUMNS = Set.of(
        "receiver_uuid", "sender_uuid", "expire_time", "server_id", "uuid", "send_date",
        "owner_uuid", "blocked_uuid", "player_name", "name", "creator_uuid"
    );
    // 有效的索引名白名单
    private static final Set<String> VALID_INDEXES = Set.of(
        "idx_receiver", "idx_sender", "idx_expire", "idx_server",
        "idx_player_uuid", "idx_send_log_date", "idx_blacklist_owner", "idx_blacklist_blocked",
        "idx_template_name", "idx_template_creator"
    );

    /**
     * 验证标识符是否安全（仅包含字母数字和下划线）
     */
    private boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    /**
     * 创建索引（如果不存在）
     */
    private void createIndexIfNotExists(Connection conn, String table, String indexName, String column, boolean isMySQL) throws SQLException {
        // 白名单验证
        if (!VALID_TABLES.contains(table)) {
            throw new SQLException("无效的表名: " + table);
        }
        if (!VALID_INDEXES.contains(indexName)) {
            throw new SQLException("无效的索引名: " + indexName);
        }
        if (!VALID_COLUMNS.contains(column) && !isValidIdentifier(column)) {
            throw new SQLException("无效的列名: " + column);
        }

        try (Statement stmt = conn.createStatement()) {
            if (isMySQL) {
                // MySQL: 使用 PreparedStatement 查询 information_schema
                String checkSql = "SELECT 1 FROM information_schema.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = ? " +
                    "AND INDEX_NAME = ?";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, table);
                    ps.setString(2, indexName);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            // 使用白名单验证后的安全标识符拼接SQL
                            stmt.executeUpdate(String.format("CREATE INDEX %s ON %s(%s)", indexName, table, column));
                        }
                    }
                }
            } else {
                // H2: 使用 IF NOT EXISTS
                stmt.executeUpdate(String.format("CREATE INDEX IF NOT EXISTS %s ON %s(%s)", indexName, table, column));
            }
        }
    }

    // 有效的列定义白名单
    private static final Set<String> VALID_COLUMN_DEFS = Set.of(
        "DOUBLE DEFAULT 0"
    );

    /**
     * 添加列（如果不存在）
     */
    private void addColumnIfNotExists(Connection conn, String table, String column, String columnDef, boolean isMySQL) throws SQLException {
        // 白名单验证
        if (!VALID_TABLES.contains(table)) {
            throw new SQLException("无效的表名: " + table);
        }
        if (!isValidIdentifier(column)) {
            throw new SQLException("无效的列名: " + column);
        }
        if (!VALID_COLUMN_DEFS.contains(columnDef)) {
            throw new SQLException("无效的列定义: " + columnDef);
        }

        try (Statement stmt = conn.createStatement()) {
            if (isMySQL) {
                // MySQL: 使用 PreparedStatement 查询 information_schema
                String checkSql = "SELECT 1 FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = ? " +
                    "AND COLUMN_NAME = ?";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, table);
                    ps.setString(2, column);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            // 使用白名单验证后的安全标识符拼接SQL
                            stmt.executeUpdate(String.format("ALTER TABLE %s ADD COLUMN %s %s", table, column, columnDef));
                            plugin.getLogger().info("数据库表 " + table + " 已添加列: " + column);
                        }
                    }
                }
            } else {
                // H2: 使用 ALTER TABLE IF NOT EXISTS
                try {
                    stmt.executeUpdate(String.format("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s %s", table, column, columnDef));
                } catch (SQLException e) {
                    // H2 旧版本可能不支持 IF NOT EXISTS，忽略错误
                    plugin.getLogger().fine("添加列 " + column + " 时出错（可能已存在）: " + e.getMessage());
                }
            }
        }
    }
}
