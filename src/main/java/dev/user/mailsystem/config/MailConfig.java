package dev.user.mailsystem.config;

import dev.user.mailsystem.MailSystemPlugin;

public class MailConfig {

    private final MailSystemPlugin plugin;
    private String databaseType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int mysqlPoolSize;

    private String h2FileName;

    private int maxAttachments;
    private int maxMailTitleLength;
    private int maxMailContentLength;
    private int mailExpirationDays;
    private int unreadCheckInterval;
    private int crossServerCheckInterval;
    private int maxMailboxSize;
    private int dailySendLimit;

    // 经济设置
    private double mailPostageFee;
    private double attachmentDeliveryFee;
    private boolean enableEconomy;
    private String currencyName;

    // 数据库超时设置（启动时加载，reload时不重载）
    private long connectionTimeout;
    private int queryTimeout;
    private int lockWaitTimeout;
    private boolean timeoutConfigLoaded = false;

    public MailConfig(MailSystemPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        this.databaseType = plugin.getConfig().getString("database.type", "h2").toLowerCase();

        this.mysqlHost = plugin.getConfig().getString("database.mysql.host", "localhost");
        this.mysqlPort = plugin.getConfig().getInt("database.mysql.port", 3306);
        this.mysqlDatabase = plugin.getConfig().getString("database.mysql.database", "mailsystem");
        this.mysqlUsername = plugin.getConfig().getString("database.mysql.username", "root");
        this.mysqlPassword = plugin.getConfig().getString("database.mysql.password", "password");
        this.mysqlPoolSize = plugin.getConfig().getInt("database.mysql.pool-size", 10);

        this.h2FileName = plugin.getConfig().getString("database.h2.filename", "mailsystem");

        this.maxAttachments = plugin.getConfig().getInt("mail.max-attachments", 5);
        this.maxMailTitleLength = plugin.getConfig().getInt("mail.max-title-length", 32);
        this.maxMailContentLength = plugin.getConfig().getInt("mail.max-content-length", 500);
        this.mailExpirationDays = plugin.getConfig().getInt("mail.expiration-days", 30);
        this.unreadCheckInterval = plugin.getConfig().getInt("mail.unread-check-interval", 60);
        this.crossServerCheckInterval = plugin.getConfig().getInt("mail.cross-server-check-interval", 10);
        this.maxMailboxSize = plugin.getConfig().getInt("mail.max-mailbox-size", 100);
        this.dailySendLimit = plugin.getConfig().getInt("mail.daily-send-limit", 0);

        // 经济配置
        this.enableEconomy = plugin.getConfig().getBoolean("economy.enabled", true);
        this.currencyName = plugin.getConfig().getString("economy.currency-name", "金币");
        this.mailPostageFee = plugin.getConfig().getDouble("economy.mail-postage-fee", 0.0);
        this.attachmentDeliveryFee = plugin.getConfig().getDouble("economy.attachment-delivery-fee", 0.0);

        // 超时配置只在第一次加载时读取，reload时不重载
        if (!timeoutConfigLoaded) {
            this.connectionTimeout = plugin.getConfig().getLong("database.timeout.connection-timeout", 5000);
            this.queryTimeout = plugin.getConfig().getInt("database.timeout.query-timeout", 10);
            this.lockWaitTimeout = plugin.getConfig().getInt("database.timeout.lock-wait-timeout", 5);
            this.timeoutConfigLoaded = true;
        } else {
            plugin.getLogger().info("[注意] 数据库超时配置已在启动时加载，重载不会生效。如需修改，请重启服务器。");
        }
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public int getMysqlPoolSize() {
        return mysqlPoolSize;
    }

    public String getH2FileName() {
        return h2FileName;
    }

    public int getMaxAttachments() {
        return maxAttachments;
    }

    public int getMaxMailTitleLength() {
        return maxMailTitleLength;
    }

    public int getMaxMailContentLength() {
        return maxMailContentLength;
    }

    public int getMailExpirationDays() {
        return mailExpirationDays;
    }

    public int getUnreadCheckInterval() {
        return unreadCheckInterval;
    }

    public int getCrossServerCheckInterval() {
        return crossServerCheckInterval;
    }

    public int getMaxMailboxSize() {
        return maxMailboxSize;
    }

    public int getDailySendLimit() {
        return dailySendLimit;
    }

    public double getMailPostageFee() {
        return mailPostageFee;
    }

    public double getAttachmentDeliveryFee() {
        return attachmentDeliveryFee;
    }

    public boolean isEnableEconomy() {
        return enableEconomy;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    // 数据库超时配置 getter
    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public int getLockWaitTimeout() {
        return lockWaitTimeout;
    }
}
