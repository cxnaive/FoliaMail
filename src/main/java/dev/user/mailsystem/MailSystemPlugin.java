package dev.user.mailsystem;

import dev.user.mailsystem.api.MailSystemAPI;
import dev.user.mailsystem.api.MailSystemAPIImpl;
import dev.user.mailsystem.cache.PlayerCacheManager;
import dev.user.mailsystem.command.MailCommand;
import dev.user.mailsystem.config.MailConfig;
import dev.user.mailsystem.database.DatabaseManager;
import dev.user.mailsystem.database.DatabaseQueue;
import dev.user.mailsystem.economy.EconomyManager;
import dev.user.mailsystem.gui.GUIManager;
import dev.user.mailsystem.listener.MailListener;
import dev.user.mailsystem.mail.AttachmentManager;
import dev.user.mailsystem.mail.CrossServerNotifier;
import dev.user.mailsystem.mail.MailManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MailSystemPlugin extends JavaPlugin {

    private static MailSystemPlugin instance;
    private MailConfig mailConfig;
    private DatabaseManager databaseManager;
    private DatabaseQueue databaseQueue;
    private PlayerCacheManager playerCacheManager;
    private MailManager mailManager;
    private CrossServerNotifier crossServerNotifier;
    private GUIManager guiManager;
    private EconomyManager economyManager;
    private MailSystemAPI api;
    private AttachmentManager attachmentManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        this.mailConfig = new MailConfig(this);

        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getLogger().severe("数据库初始化失败，插件将禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseQueue = new DatabaseQueue(this);
        this.databaseQueue.start();

        this.playerCacheManager = new PlayerCacheManager(this);
        this.playerCacheManager.loadAllCache(null);

        this.mailManager = new MailManager(this);
        this.attachmentManager = new AttachmentManager(this);
        this.api = new MailSystemAPIImpl(this);

        this.crossServerNotifier = new CrossServerNotifier(this);
        this.crossServerNotifier.start();

        this.guiManager = new GUIManager(this);

        // 初始化经济系统（软依赖）
        this.economyManager = new EconomyManager(this);
        this.economyManager.init();

        // 注册命令
        MailCommand mailCommand = new MailCommand(this);
        getCommand("fmail").setExecutor(mailCommand);
        getCommand("fmail").setTabCompleter(mailCommand);

        getServer().getPluginManager().registerEvents(new MailListener(this), this);

        getLogger().info("MailSystem 插件已启用！");
        getLogger().info("数据库类型: " + mailConfig.getDatabaseType());
    }

    @Override
    public void onDisable() {
        if (crossServerNotifier != null) {
            crossServerNotifier.stop();
        }
        if (databaseQueue != null) {
            databaseQueue.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("MailSystem 插件已禁用！");
    }

    public void reload() {
        reloadConfig();
        mailConfig.load();

        if (crossServerNotifier != null) {
            crossServerNotifier.stop();
        }
        if (databaseQueue != null) {
            databaseQueue.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        databaseManager = new DatabaseManager(this);
        databaseManager.init();

        databaseQueue = new DatabaseQueue(this);
        databaseQueue.start();

        mailManager.reload();

        crossServerNotifier = new CrossServerNotifier(this);
        crossServerNotifier.start();

        // 重新初始化经济系统
        if (economyManager != null) {
            economyManager.init();
        }
    }

    public static MailSystemPlugin getInstance() {
        return instance;
    }

    public MailConfig getMailConfig() {
        return mailConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DatabaseQueue getDatabaseQueue() {
        return databaseQueue;
    }

    public PlayerCacheManager getPlayerCacheManager() {
        return playerCacheManager;
    }

    public MailManager getMailManager() {
        return mailManager;
    }

    public CrossServerNotifier getCrossServerNotifier() {
        return crossServerNotifier;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public MailSystemAPI getAPI() {
        return api;
    }

    public AttachmentManager getAttachmentManager() {
        return attachmentManager;
    }
}
