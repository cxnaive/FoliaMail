package dev.user.mailsystem.mail;

import dev.user.mailsystem.MailSystemPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class CrossServerNotifier implements Consumer<ScheduledTask> {

    private static final int MAX_NOTIFIED_CACHE_SIZE = 10000; // 最大缓存已通知邮件数
    private static final long TIME_OVERLAP_BUFFER = 1_000L; // 1秒重叠缓冲时间

    private final MailSystemPlugin plugin;
    private final Set<UUID> notifiedMails;
    private long lastCheckTime;
    private ScheduledTask checkTask;

    public CrossServerNotifier(MailSystemPlugin plugin) {
        this.plugin = plugin;
        // 使用 LRU 结构的 Set，当容量超过 MAX_NOTIFIED_CACHE_SIZE 时自动清理最旧的条目
        this.notifiedMails = Collections.newSetFromMap(
                Collections.synchronizedMap(new LinkedHashMap<UUID, Boolean>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                        return size() > MAX_NOTIFIED_CACHE_SIZE;
                    }
                })
        );
        this.lastCheckTime = System.currentTimeMillis();
    }

    public void start() {
        int interval = plugin.getMailConfig().getCrossServerCheckInterval() * 20;
        checkTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, this, interval, interval);
        plugin.getLogger().info("跨服务器通知器已启动，检查间隔: " + plugin.getMailConfig().getCrossServerCheckInterval() + "秒");
    }

    public void stop() {
        if (checkTask != null && !checkTask.isCancelled()) {
            checkTask.cancel();
        }
    }

    @Override
    public void accept(ScheduledTask task) {
        checkNewMails();
    }

    private void checkNewMails() {
        long currentTime = System.currentTimeMillis();
        
        // 查询起始时间 = 上次检查时间 - 缓冲时间 (重叠查询，利用缓存去重)
        long queryStartTime = lastCheckTime - TIME_OVERLAP_BUFFER;
        
        // 更新 lastCheckTime
        lastCheckTime = currentTime;

        plugin.getDatabaseQueue().submit("checkNewMails", conn -> {
            // 1. 【异步线程】只负责从数据库捞取可能是新的数据
            Set<MailNotification> potentialNewMails = new HashSet<>();
            
            // 修正 SQL: 增加 ORDER BY 优化读取顺序 (可选)
            String sql = "SELECT id, sender_name, receiver_uuid, title, sent_time FROM mails " +
                    "WHERE sent_time > ? AND server_id != ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, queryStartTime);
                ps.setString(2, getServerId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // 在这里不进行 notifiedMails.contains 检查，
                        // 因为 notifiedMails 可能会在主线程被修改，避免并发冲突。
                        // 只做简单的对象映射。
                        potentialNewMails.add(new MailNotification(
                                UUID.fromString(rs.getString("id")),
                                rs.getString("sender_name"),
                                UUID.fromString(rs.getString("receiver_uuid")),
                                rs.getString("title"),
                                rs.getLong("sent_time")
                        ));
                    }
                }
            }
            return potentialNewMails;
        }, foundMails -> {
            // 2. 【Global/主线程】负责逻辑判断、去重和发消息
            if (foundMails == null || foundMails.isEmpty()) return;

            int newCount = 0;
            for (MailNotification notification : foundMails) {
                // 双重检查：如果已经通知过，直接跳过
                if (notifiedMails.contains(notification.mailId)) {
                    continue;
                }

                // 标记为已通知
                notifiedMails.add(notification.mailId);
                newCount++;

                Player receiver = Bukkit.getPlayer(notification.receiverUuid);
                // 确保玩家在线
                if (receiver != null) {
                    receiver.sendMessage("§a[邮件系统] §e你收到了一封新邮件！来自: §f" + notification.senderName);
                    // 只有玩家在线时才清理缓存，避免不必要的 IO 或逻辑
                    plugin.getMailManager().clearPlayerCache(notification.receiverUuid);
                }
            }

            if (newCount > 0) {
                // 仅调试模式或必要时输出，避免刷屏
                // plugin.getLogger().info("检测到 " + newCount + " 封新跨服邮件");
            }
        });
    }

    public void markNotified(UUID mailId) {
        notifiedMails.add(mailId);
    }

    private String getServerId() {
        String serverId = plugin.getConfig().getString("server.id", "");
        if (serverId.isEmpty()) {
            serverId = Bukkit.getServer().getName();
        }
        return serverId;
    }

    private record MailNotification(UUID mailId, String senderName, UUID receiverUuid, String title, long sentTime) {
    }
}
