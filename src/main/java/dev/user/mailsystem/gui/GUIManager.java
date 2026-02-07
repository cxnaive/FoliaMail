package dev.user.mailsystem.gui;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.mail.Mail;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI管理器 - 负责管理所有GUI的打开和关闭
 */
public class GUIManager {

    private final MailSystemPlugin plugin;
    private final Map<UUID, GUIType> playerOpenGUI;
    private final Map<UUID, Integer> playerPageCache;
    private final Map<UUID, Mail> playerViewingMail;
    private final Map<UUID, ComposeData> playerComposeData;
    private final Map<UUID, Listener> playerChatListeners;

    public GUIManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.playerOpenGUI = new ConcurrentHashMap<>();
        this.playerPageCache = new ConcurrentHashMap<>();
        this.playerViewingMail = new ConcurrentHashMap<>();
        this.playerComposeData = new ConcurrentHashMap<>();
        this.playerChatListeners = new ConcurrentHashMap<>();
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        player.getScheduler().run(plugin, task -> {
            MainMenuGUI gui = new MainMenuGUI(plugin, this);
            gui.open(player);
            playerOpenGUI.put(player.getUniqueId(), GUIType.MAIN_MENU);
        }, null);
    }

    /**
     * 打开收件箱
     */
    public void openInbox(Player player, int page) {
        player.getScheduler().run(plugin, task -> {
            InboxGUI gui = new InboxGUI(plugin, this, page);
            gui.open(player);
            playerOpenGUI.put(player.getUniqueId(), GUIType.INBOX);
            playerPageCache.put(player.getUniqueId(), page);
        }, null);
    }

    /**
     * 打开发件箱
     */
    public void openSentBox(Player player, int page) {
        player.getScheduler().run(plugin, task -> {
            SentBoxGUI gui = new SentBoxGUI(plugin, this, page);
            gui.open(player);
            playerOpenGUI.put(player.getUniqueId(), GUIType.SENT_BOX);
            playerPageCache.put(player.getUniqueId(), page);
        }, null);
    }

    /**
     * 打开邮件详情
     */
    public void openMailView(Player player, Mail mail) {
        player.getScheduler().run(plugin, task -> {
            MailViewGUI gui = new MailViewGUI(plugin, this, mail);
            gui.open(player);
            playerOpenGUI.put(player.getUniqueId(), GUIType.MAIL_VIEW);
            playerViewingMail.put(player.getUniqueId(), mail);
        }, null);
    }

    /**
     * 打开写邮件界面
     */
    public void openCompose(Player player) {
        player.getScheduler().run(plugin, task -> {
            ComposeGUI gui = new ComposeGUI(plugin, this);
            gui.open(player);
            playerOpenGUI.put(player.getUniqueId(), GUIType.COMPOSE);
            // 如果已经有数据，保留；否则创建新的
            ComposeData data = playerComposeData.computeIfAbsent(player.getUniqueId(), k -> new ComposeData());
            // 重置返还标志，因为是重新打开界面
            data.setReturned(false);
        }, null);
    }

    /**
     * 关闭GUI时清理数据
     */
    public void closeGUI(Player player) {
        UUID playerUuid = player.getUniqueId();
        playerOpenGUI.remove(playerUuid);
        playerPageCache.remove(playerUuid);
        playerViewingMail.remove(playerUuid);
        playerComposeData.remove(playerUuid);
        // 清理聊天监听器
        unregisterChatListener(playerUuid);
    }

    /**
     * 注册玩家的聊天监听器
     * 如果玩家已有监听器，先注销旧的
     */
    public void registerChatListener(UUID playerUuid, Listener listener) {
        // 先注销旧的监听器（如果有）
        unregisterChatListener(playerUuid);
        // 注册新的监听器
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        playerChatListeners.put(playerUuid, listener);
    }

    /**
     * 注销玩家的聊天监听器
     */
    public void unregisterChatListener(UUID playerUuid) {
        Listener listener = playerChatListeners.remove(playerUuid);
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
        }
    }

    /**
     * 获取玩家当前打开的GUI类型
     */
    public GUIType getPlayerOpenGUI(UUID playerUuid) {
        return playerOpenGUI.get(playerUuid);
    }

    /**
     * 获取玩家当前页码
     */
    public int getPlayerPage(UUID playerUuid) {
        return playerPageCache.getOrDefault(playerUuid, 1);
    }

    /**
     * 获取玩家正在查看的邮件
     */
    public Mail getPlayerViewingMail(UUID playerUuid) {
        return playerViewingMail.get(playerUuid);
    }

    /**
     * 获取玩家的写邮件数据
     */
    public ComposeData getPlayerComposeData(UUID playerUuid) {
        return playerComposeData.get(playerUuid);
    }

    /**
     * 设置玩家的写邮件数据
     */
    public void setPlayerComposeData(UUID playerUuid, ComposeData data) {
        if (data == null) {
            playerComposeData.remove(playerUuid);
        } else {
            playerComposeData.put(playerUuid, data);
        }
    }

    /**
     * 获取插件实例
     */
    public MailSystemPlugin getPlugin() {
        return plugin;
    }

    /**
     * 打开管理员邮件管理界面
     */
    public void openAdminMailManage(Player player, UUID targetUuid, String targetName, int page) {
        player.getScheduler().run(plugin, task -> {
            AdminMailManageGUI gui = new AdminMailManageGUI(plugin, this, targetUuid, targetName, page);
            gui.open(player);
            playerOpenGUI.put(player.getUniqueId(), GUIType.ADMIN_MAIL_MANAGE);
            playerPageCache.put(player.getUniqueId(), page);
        }, null);
    }

    /**
     * GUI类型枚举
     */
    public enum GUIType {
        MAIN_MENU,
        INBOX,
        SENT_BOX,
        MAIL_VIEW,
        COMPOSE,
        ADMIN_MAIL_MANAGE
    }

    /**
     * 写邮件数据
     */
    public static class ComposeData {
        private String receiver;
        private UUID receiverUuid;
        private String title;
        private String content;
        private final List<ItemStack> attachments = new ArrayList<>();
        private double moneyAttachment = 0; // 金币附件
        private boolean returned = false; // 标记附件是否已返还
        private boolean broadcastMode = false; // 是否为群发模式
        private final java.util.concurrent.atomic.AtomicBoolean processing = new java.util.concurrent.atomic.AtomicBoolean(false); // 发送处理中标记，防止快速点击

        public String getReceiver() {
            return receiver;
        }

        public void setReceiver(String receiver) {
            this.receiver = receiver;
        }

        public UUID getReceiverUuid() {
            return receiverUuid;
        }

        public void setReceiverUuid(UUID receiverUuid) {
            this.receiverUuid = receiverUuid;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<ItemStack> getAttachments() {
            return attachments;
        }

        public void addAttachment(ItemStack item) {
            attachments.add(item.clone());
        }

        public void clearAttachments() {
            attachments.clear();
        }

        public int getAttachmentCount() {
            return attachments.size();
        }

        public double getMoneyAttachment() {
            return moneyAttachment;
        }

        public void setMoneyAttachment(double moneyAttachment) {
            this.moneyAttachment = moneyAttachment;
        }

        public boolean isReturned() {
            return returned;
        }

        public void setReturned(boolean returned) {
            this.returned = returned;
        }

        public boolean isBroadcastMode() {
            return broadcastMode;
        }

        public void setBroadcastMode(boolean broadcastMode) {
            this.broadcastMode = broadcastMode;
        }

        public java.util.concurrent.atomic.AtomicBoolean getProcessing() {
            return processing;
        }
    }
}
