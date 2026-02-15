package dev.user.mailsystem.mail;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.database.DatabaseQueue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 黑名单管理器 - 管理邮件屏蔽关系
 */
public class BlacklistManager {

    private final MailSystemPlugin plugin;
    private final DatabaseQueue databaseQueue;

    public BlacklistManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.databaseQueue = plugin.getDatabaseQueue();
    }

    /**
     * 检查发送者是否在接收者的黑名单中
     */
    public void isInBlacklist(UUID senderUuid, UUID receiverUuid, Consumer<Boolean> callback) {
        databaseQueue.submit("checkBlacklist", conn -> {
            String sql = "SELECT 1 FROM mail_blacklist WHERE owner_uuid = ? AND blocked_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, receiverUuid.toString());
                ps.setString(2, senderUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }, callback);
    }

    /**
     * 添加玩家到黑名单
     */
    public void addToBlacklist(UUID ownerUuid, UUID blockedUuid, Consumer<Boolean> callback) {
        databaseQueue.submit("addBlacklist", conn -> {
            String sql = "INSERT INTO mail_blacklist (owner_uuid, blocked_uuid, blocked_time) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, blockedUuid.toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
                return true;
            }
        }, callback, error -> callback.accept(false));
    }

    /**
     * 从黑名单移除玩家
     */
    public void removeFromBlacklist(UUID ownerUuid, UUID blockedUuid, Consumer<Boolean> callback) {
        databaseQueue.submit("removeBlacklist", conn -> {
            String sql = "DELETE FROM mail_blacklist WHERE owner_uuid = ? AND blocked_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, blockedUuid.toString());
                return ps.executeUpdate() > 0;
            }
        }, callback, error -> callback.accept(false));
    }

    /**
     * 获取玩家的黑名单列表
     */
    public void getBlacklist(UUID ownerUuid, Consumer<Set<UUID>> callback) {
        databaseQueue.submit("getBlacklist", conn -> {
            Set<UUID> blocked = new HashSet<>();
            String sql = "SELECT blocked_uuid FROM mail_blacklist WHERE owner_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        blocked.add(UUID.fromString(rs.getString("blocked_uuid")));
                    }
                }
            }
            return blocked;
        }, callback);
    }
}
