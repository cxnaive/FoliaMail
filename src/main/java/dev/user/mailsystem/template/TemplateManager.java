package dev.user.mailsystem.template;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.BatchSendResult;
import dev.user.mailsystem.api.draft.MailDraft;
import dev.user.mailsystem.api.draft.SendOptions;
import dev.user.mailsystem.database.DatabaseQueue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 邮件模板管理器
 */
public class TemplateManager {

    private final MailSystemPlugin plugin;
    private final DatabaseQueue databaseQueue;

    public TemplateManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.databaseQueue = plugin.getDatabaseQueue();
    }

    // ==================== 模板管理 ====================

    /**
     * 保存模板
     */
    public void saveTemplate(String name, String displayName, String title, String content,
                             List<ItemStack> attachments, double moneyAttachment,
                             Player creator, Consumer<Boolean> callback) {
        long now = System.currentTimeMillis();
        byte[] attachData = serializeAttachments(attachments);

        databaseQueue.submit("saveTemplate", conn -> {
            // 检查是否已存在（更新或插入）
            String checkSql = "SELECT id FROM mail_templates WHERE name = ?";
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, name);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        // 更新
                        String updateSql = "UPDATE mail_templates SET display_name = ?, title = ?, content = ?, " +
                                "attachments = ?, money_attachment = ?, creator_uuid = ?, creator_name = ?, " +
                                "updated_at = ? WHERE name = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                            ps.setString(1, displayName);
                            ps.setString(2, title);
                            ps.setString(3, content);
                            if (attachData != null) {
                                ps.setBytes(4, attachData);
                            } else {
                                ps.setNull(4, java.sql.Types.BLOB);
                            }
                            ps.setDouble(5, moneyAttachment);
                            ps.setString(6, creator.getUniqueId().toString());
                            ps.setString(7, creator.getName());
                            ps.setLong(8, now);
                            ps.setString(9, name);
                            ps.executeUpdate();
                        }
                    } else {
                        // 插入
                        String insertSql = "INSERT INTO mail_templates (name, display_name, title, content, " +
                                "attachments, money_attachment, creator_uuid, creator_name, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            ps.setString(1, name);
                            ps.setString(2, displayName);
                            ps.setString(3, title);
                            ps.setString(4, content);
                            if (attachData != null) {
                                ps.setBytes(5, attachData);
                            } else {
                                ps.setNull(5, java.sql.Types.BLOB);
                            }
                            ps.setDouble(6, moneyAttachment);
                            ps.setString(7, creator.getUniqueId().toString());
                            ps.setString(8, creator.getName());
                            ps.setLong(9, now);
                            ps.setLong(10, now);
                            ps.executeUpdate();
                        }
                    }
                }
            }
            return true;
        }, success -> {
            if (success) {
                plugin.getLogger().info("模板已保存: " + name + " (创建者: " + creator.getName() + ")");
            }
            callback.accept(success);
        }, error -> {
            plugin.getLogger().severe("保存模板失败: " + error.getMessage());
            callback.accept(false);
        });
    }

    /**
     * 删除模板
     */
    public void deleteTemplate(String name, Consumer<Boolean> callback) {
        databaseQueue.submit("deleteTemplate", conn -> {
            String sql = "DELETE FROM mail_templates WHERE name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                int affected = ps.executeUpdate();
                return affected > 0;
            }
        }, callback, error -> {
            plugin.getLogger().severe("删除模板失败: " + error.getMessage());
            callback.accept(false);
        });
    }

    /**
     * 获取所有模板列表（简要信息）
     */
    public void listTemplates(Consumer<List<TemplateInfo>> callback) {
        databaseQueue.submit("listTemplates", conn -> {
            List<TemplateInfo> list = new ArrayList<>();
            String sql = "SELECT name, display_name, creator_name, use_count FROM mail_templates ORDER BY name";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TemplateInfo(
                            rs.getString("name"),
                            rs.getString("display_name"),
                            rs.getString("creator_name"),
                            rs.getInt("use_count")
                    ));
                }
            }
            return list;
        }, callback, error -> {
            plugin.getLogger().severe("获取模板列表失败: " + error.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    /**
     * 获取单个模板详情
     */
    public void getTemplate(String name, Consumer<MailTemplate> callback) {
        databaseQueue.submit("getTemplate", conn -> {
            String sql = "SELECT * FROM mail_templates WHERE name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return extractTemplateFromResultSet(rs);
                    }
                }
            }
            return null;
        }, callback, error -> {
            plugin.getLogger().severe("获取模板失败: " + error.getMessage());
            callback.accept(null);
        });
    }

    /**
     * 增加使用次数
     */
    public void incrementUseCount(String name) {
        databaseQueue.submitAsync("incrementUseCount", conn -> {
            String sql = "UPDATE mail_templates SET use_count = use_count + 1 WHERE name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ==================== 使用模板发送 ====================

    /**
     * 使用模板发送给单个玩家
     */
    public void sendToPlayer(String templateName, Player sender, String targetName,
                             Consumer<BatchSendResult> callback) {
        getTemplate(templateName, template -> {
            if (template == null) {
                sender.sendMessage("§c[邮件系统] 模板不存在: " + templateName);
                callback.accept(BatchSendResult.builder().totalCount(0).build());
                return;
            }

            // 查找目标玩家
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                // 在线玩家
                sendTemplate(template, sender, target.getUniqueId(), target.getName(), callback);
            } else {
                // 离线玩家，查询缓存
                plugin.getPlayerCacheManager().getPlayerUuid(targetName, uuid -> {
                    if (uuid == null) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                            sender.sendMessage("§c[邮件系统] 玩家不存在: " + targetName);
                            callback.accept(BatchSendResult.builder().totalCount(0).build());
                        });
                        return;
                    }
                    plugin.getPlayerCacheManager().getPlayerName(uuid, cachedName -> {
                        String receiverName = cachedName != null ? cachedName : targetName;
                        sendTemplate(template, sender, uuid, receiverName, callback);
                    });
                });
            }
        });
    }

    /**
     * 使用模板群发
     */
    public void broadcast(String templateName, Player sender, BroadcastTarget target,
                          Consumer<BatchSendResult> callback) {
        getTemplate(templateName, template -> {
            if (template == null) {
                sender.sendMessage("§c[邮件系统] 模板不存在: " + templateName);
                callback.accept(BatchSendResult.builder().totalCount(0).build());
                return;
            }

            switch (target) {
                case ONLINE -> broadcastToOnline(template, sender, callback);
                case ALL -> broadcastToAll(template, sender, callback);
                case RECENT_3D -> broadcastToRecent(template, sender, 3, callback);
                case RECENT_7D -> broadcastToRecent(template, sender, 7, callback);
            }
        });
    }

    // ==================== 私有方法 ====================

    /**
     * 使用模板发送邮件（统一方法，支持在线和离线玩家）
     */
    private void sendTemplate(MailTemplate template, Player sender,
                              UUID targetUuid, String targetName,
                              Consumer<BatchSendResult> callback) {
        String serverName = plugin.getConfig().getString("server.id", Bukkit.getServer().getName());
        MailDraft draft = template.toDraft(sender, targetUuid, targetName, serverName);

        SendOptions options = SendOptions.systemMail();
        plugin.getMailManager().send(draft, options, sender, result -> {
            if (result.isSuccess(targetUuid)) {
                incrementUseCount(template.getName());
            }
            callback.accept(result);
        });
    }

    private void broadcastToOnline(MailTemplate template, Player sender,
                                   Consumer<BatchSendResult> callback) {
        Map<UUID, String> targets = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            targets.put(player.getUniqueId(), player.getName());
        }
        broadcastToTargets(template, sender, targets, callback);
    }

    private void broadcastToAll(MailTemplate template, Player sender,
                                Consumer<BatchSendResult> callback) {
        plugin.getPlayerCacheManager().getAllCachedPlayers(players ->
                broadcastToTargets(template, sender, players, callback));
    }

    private void broadcastToRecent(MailTemplate template, Player sender, int days,
                                   Consumer<BatchSendResult> callback) {
        long sinceTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        plugin.getPlayerCacheManager().getPlayersLoggedInSince(sinceTime, players ->
                broadcastToTargets(template, sender, players, callback));
    }

    private void broadcastToTargets(MailTemplate template, Player sender,
                                    Map<UUID, String> targets,
                                    Consumer<BatchSendResult> callback) {
        if (targets.isEmpty()) {
            sender.sendMessage("§c[邮件系统] 没有目标玩家");
            callback.accept(BatchSendResult.builder().totalCount(0).build());
            return;
        }

        String serverName = plugin.getConfig().getString("server.id", Bukkit.getServer().getName());

        // 为每个玩家创建个性化的草稿
        List<MailDraft> drafts = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : targets.entrySet()) {
            MailDraft draft = template.toDraft(sender, entry.getKey(), entry.getValue(), serverName);
            drafts.add(draft);
        }

        SendOptions options = SendOptions.systemMail();
        plugin.getMailManager().send(drafts, options, sender, result -> {
            if (result.getSuccessCount() > 0) {
                incrementUseCount(template.getName());
            }
            callback.accept(result);
        });
    }

    private MailTemplate extractTemplateFromResultSet(ResultSet rs) throws SQLException {
        MailTemplate template = new MailTemplate();
        template.setId(rs.getInt("id"));
        template.setName(rs.getString("name"));
        template.setDisplayName(rs.getString("display_name"));
        template.setTitle(rs.getString("title"));
        template.setContent(rs.getString("content"));
        template.setMoneyAttachment(rs.getDouble("money_attachment"));
        template.setCreatorUuid(UUID.fromString(rs.getString("creator_uuid")));
        template.setCreatorName(rs.getString("creator_name"));
        template.setCreatedAt(rs.getLong("created_at"));
        template.setUpdatedAt(rs.getLong("updated_at"));
        template.setUseCount(rs.getInt("use_count"));

        // 反序列化附件
        byte[] attachData = rs.getBytes("attachments");
        if (attachData != null) {
            template.setAttachments(deserializeAttachments(attachData));
        }

        return template;
    }

    private byte[] serializeAttachments(List<ItemStack> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        return plugin.getMailManager().serializeAttachmentsInternal(attachments);
    }

    private List<ItemStack> deserializeAttachments(byte[] data) {
        if (data == null) {
            return new ArrayList<>();
        }
        return plugin.getMailManager().deserializeAttachmentsInternal(data);
    }

    // ==================== 内部类 ====================

    public enum BroadcastTarget {
        ALL,        // 所有玩家
        ONLINE,     // 在线玩家
        RECENT_3D,  // 3天内登录
        RECENT_7D   // 7天内登录
    }

    public record TemplateInfo(String name, String displayName, String creatorName, int useCount) {
        public String getDisplayOrName() {
            return displayName != null && !displayName.isEmpty() ? displayName : name;
        }
    }
}
