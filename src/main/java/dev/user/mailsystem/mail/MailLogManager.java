package dev.user.mailsystem.mail;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.database.DatabaseQueue;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 邮件日志管理器 - 管理发送日志和统计
 */
public class MailLogManager {

    private final MailSystemPlugin plugin;
    private final DatabaseQueue databaseQueue;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    public MailLogManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.databaseQueue = plugin.getDatabaseQueue();
    }

    /**
     * 记录玩家发送邮件日志
     */
    public void logMailSend(UUID playerUuid, int amount) {
        databaseQueue.submitAsync("logMailSend", conn -> {
            String today = getTodayDateString();

            // 先尝试 UPDATE
            String updateSql = "UPDATE mail_send_log SET send_count = send_count + ? WHERE player_uuid = ? AND send_date = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, amount);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, today);
                int updated = ps.executeUpdate();

                // 如果没有更新到记录，则 INSERT
                if (updated == 0) {
                    String insertSql = "INSERT INTO mail_send_log (player_uuid, send_date, send_count) VALUES (?, ?, ?)";
                    try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                        insertPs.setString(1, playerUuid.toString());
                        insertPs.setString(2, today);
                        insertPs.setInt(3, amount);
                        insertPs.executeUpdate();
                    }
                }
            }
            return null;
        });
    }

    /**
     * 异步获取玩家今日发送的邮件数量
     */
    public void getTodaySendCountAsync(UUID playerUuid, Consumer<Integer> callback) {
        databaseQueue.submit("getTodaySendCount", conn -> {
            String today = getTodayDateString();
            String sql = "SELECT send_count FROM mail_send_log WHERE player_uuid = ? AND send_date = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, today);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return 0;
        }, callback);
    }

    /**
     * 检查每日发送限制
     */
    public void checkDailySendLimit(Player sender, Consumer<Boolean> callback) {
        int dailyLimit = plugin.getMailConfig().getDailySendLimit();
        if (dailyLimit <= 0 || sender.hasPermission("mailsystem.admin")) {
            callback.accept(true);
            return;
        }

        getTodaySendCountAsync(sender.getUniqueId(), count -> {
            if (count >= dailyLimit) {
                sender.sendMessage("§c[邮件系统] 你今日发送邮件已达上限 (" + dailyLimit + "封)，请明天再试！");
                callback.accept(false);
            } else {
                callback.accept(true);
            }
        });
    }

    private String getTodayDateString() {
        return DATE_FORMATTER.format(Instant.now());
    }
}
