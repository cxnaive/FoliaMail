package dev.user.mailsystem.mail;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.database.DatabaseQueue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 邮件缓存管理器 - 管理玩家邮件的内存缓存（带过期时间）
 */
public class MailCacheManager {

    private final MailSystemPlugin plugin;
    private final DatabaseQueue databaseQueue;
    private final Map<UUID, CacheEntry> playerMailCache;
    private final Set<UUID> unreadNotificationSent;

    // 默认缓存过期时间：30分钟
    private static final long DEFAULT_CACHE_TTL_MS = 30 * 60 * 1000;

    public MailCacheManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.databaseQueue = plugin.getDatabaseQueue();
        this.playerMailCache = new ConcurrentHashMap<>();
        this.unreadNotificationSent = ConcurrentHashMap.newKeySet();
    }

    public void clear() {
        playerMailCache.clear();
        unreadNotificationSent.clear();
    }

    /**
     * 获取玩家缓存的邮件（同步），过期返回空列表
     */
    public List<Mail> getCachedMails(UUID playerUuid) {
        CacheEntry entry = playerMailCache.get(playerUuid);
        if (entry == null) {
            return new CopyOnWriteArrayList<>();
        }
        if (entry.isExpired()) {
            // 原子方式移除过期缓存（避免竞态）
            playerMailCache.compute(playerUuid, (key, existingEntry) -> {
                // 只有仍是同一个过期条目时才移除
                if (existingEntry == entry && existingEntry.isExpired()) {
                    return null; // 移除
                }
                return existingEntry; // 保留（可能已被更新）
            });
            return new CopyOnWriteArrayList<>();
        }
        return entry.mails;
    }

    /**
     * 获取或加载玩家邮件（检查过期）
     */
    public void getOrLoadMails(UUID playerUuid, Consumer<List<Mail>> callback) {
        CacheEntry entry = playerMailCache.get(playerUuid);
        if (entry != null && !entry.isExpired()) {
            callback.accept(entry.mails);
            return;
        }

        // 过期或不存在，从数据库加载
        // 注意：移除过期缓存时可能存在竞态，但数据库查询是幂等的，最终一致性
        if (entry != null && entry.isExpired()) {
            playerMailCache.compute(playerUuid, (key, existingEntry) -> {
                if (existingEntry == entry) {
                    return null; // 移除过期条目
                }
                return existingEntry; // 已被其他线程更新
            });
        }

        loadFromDatabase(playerUuid, callback);
    }

    /**
     * 从数据库加载玩家邮件
     */
    public void loadFromDatabase(UUID playerUuid, Consumer<List<Mail>> callback) {
        databaseQueue.submit("loadPlayerMails", conn -> {
            List<Mail> mails = new ArrayList<>();
            String sql = "SELECT * FROM mails WHERE receiver_uuid = ? AND (expire_time = 0 OR expire_time > ?) ORDER BY sent_time DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        mails.add(resultSetToMail(rs));
                    }
                }
            }
            return mails;
        }, mails -> {
            // 存入缓存，设置过期时间
            // 使用compute保证原子性，避免覆盖其他线程更新的缓存
            long ttl = getCacheTtl();
            playerMailCache.compute(playerUuid, (key, existingEntry) -> {
                // 只有当缓存不存在或已过期时才更新
                if (existingEntry == null || existingEntry.isExpired()) {
                    return new CacheEntry(mails, ttl);
                }
                // 保留其他线程更新的缓存（可能包含更新的数据）
                return existingEntry;
            });
            // 返回查询结果（可能不是最新的，但保证最终一致性）
            callback.accept(mails);
        });
    }

    /**
     * 加载玩家发送的邮件（不缓存）
     */
    public void loadSentMails(UUID senderUuid, Consumer<List<Mail>> callback) {
        databaseQueue.submit("loadSentMails", conn -> {
            List<Mail> mails = new ArrayList<>();
            String sql = "SELECT * FROM mails WHERE sender_uuid = ? ORDER BY sent_time DESC LIMIT 100";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, senderUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        mails.add(resultSetToMail(rs));
                    }
                }
            }
            return mails;
        }, callback);
    }

    /**
     * 使缓存失效
     */
    public void invalidate(UUID playerUuid) {
        playerMailCache.remove(playerUuid);
    }

    /**
     * 检查是否已发送未读通知
     */
    public boolean hasUnreadNotificationSent(UUID playerUuid) {
        return unreadNotificationSent.contains(playerUuid);
    }

    /**
     * 标记已发送未读通知
     */
    public void markUnreadNotificationSent(UUID playerUuid) {
        unreadNotificationSent.add(playerUuid);
    }

    /**
     * 清除未读通知标记
     */
    public void clearUnreadNotification(UUID playerUuid) {
        unreadNotificationSent.remove(playerUuid);
    }

    /**
     * 获取缓存中的玩家数量
     */
    public int getCacheSize() {
        return playerMailCache.size();
    }

    /**
     * 清理所有过期缓存
     */
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        // ConcurrentHashMap的removeIf是线程安全的
        // 但仍然存在小概率竞态：条目在检查通过后、移除前被刷新
        // 这是可接受的，下次访问时会重新加载
        playerMailCache.entrySet().removeIf(entry -> entry.getValue().expireTime < now);
    }

    /**
     * 获取缓存过期时间（毫秒）
     */
    private long getCacheTtl() {
        // 可以从配置读取，暂时使用默认值
        return DEFAULT_CACHE_TTL_MS;
    }

    private Mail resultSetToMail(ResultSet rs) throws SQLException {
        Mail mail = new Mail(
                UUID.fromString(rs.getString("sender_uuid")),
                rs.getString("sender_name"),
                UUID.fromString(rs.getString("receiver_uuid")),
                rs.getString("receiver_name"),
                rs.getString("title"),
                rs.getString("content")
        );
        mail.setId(UUID.fromString(rs.getString("id")));
        mail.setSentTime(rs.getLong("sent_time"));
        mail.setExpireTime(rs.getLong("expire_time"));
        mail.setRead(rs.getBoolean("is_read"));
        mail.setClaimed(rs.getBoolean("is_claimed"));
        mail.setMoneyAttachment(rs.getDouble("money_attachment"));

        // 反序列化附件
        byte[] attachData = rs.getBytes("attachments");
        if (attachData != null) {
            List<org.bukkit.inventory.ItemStack> items = plugin.getAttachmentManager().deserialize(attachData);
            mail.setAttachments(items);
        }

        return mail;
    }

    /**
     * 缓存条目内部类
     */
    private static class CacheEntry {
        final List<Mail> mails;
        final long expireTime;

        CacheEntry(List<Mail> mails, long ttlMillis) {
            this.mails = new CopyOnWriteArrayList<>(mails);
            this.expireTime = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
