package dev.user.mailsystem.mail;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.BatchSendResult;
import dev.user.mailsystem.api.draft.MailDraft;
import dev.user.mailsystem.api.draft.SendOptions;
import dev.user.mailsystem.database.DatabaseQueue;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendPipeline;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MailManager implements Consumer<ScheduledTask> {

    private final MailSystemPlugin plugin;
    private final DatabaseQueue databaseQueue;
    private final SendPipeline sendPipeline;

    private final BlacklistManager blacklistManager;
    private final MailCacheManager cacheManager;
    private final MailLogManager logManager;
    private final AttachmentManager attachmentManager;

    private ScheduledTask cleanupTask;
    private ScheduledTask notificationTask;
    private ScheduledTask cacheCleanupTask;

    private final ConcurrentHashMap<UUID, Boolean> processingClaims = new ConcurrentHashMap<>();

    public MailManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.databaseQueue = plugin.getDatabaseQueue();
        this.sendPipeline = new SendPipeline(plugin);
        this.blacklistManager = new BlacklistManager(plugin);
        this.cacheManager = new MailCacheManager(plugin);
        this.logManager = new MailLogManager(plugin);
        this.attachmentManager = new AttachmentManager(plugin);
        startTasks();
    }

    public void reload() {
        stopTasks();
        cacheManager.clear();
        processingClaims.clear();
        startTasks();
    }

    private void startTasks() {
        int cleanupInterval = 20 * 60 * 60;
        int checkInterval = plugin.getMailConfig().getUnreadCheckInterval() * 20;
        int cacheCleanupInterval = 20 * 60 * 10;

        cleanupTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, this, cleanupInterval, cleanupInterval);
        notificationTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> checkUnreadMails(), checkInterval, checkInterval);
        cacheCleanupTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> cacheManager.cleanExpired(), cacheCleanupInterval, cacheCleanupInterval);
    }

    private void stopTasks() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) cleanupTask.cancel();
        if (notificationTask != null && !notificationTask.isCancelled()) notificationTask.cancel();
        if (cacheCleanupTask != null && !cacheCleanupTask.isCancelled()) cacheCleanupTask.cancel();
    }

    @Override
    public void accept(ScheduledTask task) {
        cleanExpiredMails();
    }

    // ==================== 核心发送API（统一批量模式） ====================

    /**
     * 统一发送入口 - 批量发送
     * 单发 = List.of(draft) 的批量
     */
    public void send(List<MailDraft> drafts, SendOptions options, Player sender, Consumer<BatchSendResult> callback) {
        if (options == null) options = SendOptions.defaults();
        if (drafts.isEmpty()) {
            callback.accept(BatchSendResult.builder().totalCount(0).build());
            return;
        }

        // 创建上下文列表
        List<SendContext> contexts = new ArrayList<>();
        for (MailDraft draft : drafts) {
            contexts.add(new SendContext(draft, options, sender, drafts.size() > 1));
        }

        // 执行Pipeline
        sendPipeline.execute(contexts, result -> {
            // 处理结果 - 触发事件
            if (result.isAllSuccess() || result.isPartialSuccess()) {
                for (SendContext ctx : contexts) {
                    if (result.isSuccess(ctx.getReceiverUuid())) {
                        Mail mail = createMailFromContext(ctx);
                        plugin.getAPI().fireMailSendEvent(mail, false);
                    }
                }
            }
            callback.accept(result);
        });
    }

    /**
     * 单发入口 - 转换为批量调用
     */
    public void send(MailDraft draft, SendOptions options, Player sender, Consumer<BatchSendResult> callback) {
        send(List.of(draft), options, sender, callback);
    }

    public void send(MailDraft draft, Player sender, Consumer<BatchSendResult> callback) {
        send(List.of(draft), SendOptions.defaults(), sender, callback);
    }

    public void sendSystemMail(UUID receiver, String receiverName, String title, String content,
                               List<ItemStack> attachments, Consumer<BatchSendResult> callback) {
        MailDraft draft = MailDraft.builder()
                .sender(new UUID(0, 0), "系统")
                .receiver(receiver, receiverName)
                .title(title)
                .content(content)
                .attachments(attachments)
                .build();
        send(List.of(draft), SendOptions.systemMail(), null, callback);
    }

    /**
     * 批量发送系统邮件
     */
    public void sendSystemMailBatch(List<UUID> receivers, String title, String content,
                                    List<ItemStack> attachments, Consumer<BatchSendResult> callback) {
        List<MailDraft> drafts = new ArrayList<>();
        for (UUID receiver : receivers) {
            drafts.add(MailDraft.builder()
                    .sender(new UUID(0, 0), "系统")
                    .receiver(receiver, "")
                    .title(title)
                    .content(content)
                    .attachments(attachments)
                    .build());
        }
        send(drafts, SendOptions.systemMail(), null, callback);
    }

    // ==================== 邮件查询 ====================

    public void getMail(UUID mailId, Consumer<Mail> callback) {
        databaseQueue.submit("getMail", conn -> {
            String sql = "SELECT * FROM mails WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mailId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return resultSetToMail(rs);
                }
            }
            return null;
        }, callback);
    }

    public void loadPlayerMails(UUID playerUuid, Consumer<List<Mail>> callback) {
        cacheManager.getOrLoadMails(playerUuid, callback);
    }

    public void loadSentMails(UUID senderUuid, Consumer<List<Mail>> callback) {
        cacheManager.loadSentMails(senderUuid, callback);
    }

    public void getUnreadCount(UUID playerUuid, Consumer<Integer> callback) {
        cacheManager.getOrLoadMails(playerUuid, mails -> {
            int count = (int) mails.stream().filter(mail -> !mail.isRead()).count();
            callback.accept(count);
        });
    }

    // ==================== 邮件操作 ====================

    public void markAsRead(UUID mailId) {
        getMail(mailId, mail -> {
            if (mail == null) return;
            databaseQueue.submitAsync("markAsRead", conn -> {
                String sql = "UPDATE mails SET is_read = TRUE WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, mailId.toString());
                    ps.executeUpdate();
                }
                return null;
            });
            Player reader = Bukkit.getPlayer(mail.getReceiverUuid());
            if (reader != null) {
                plugin.getAPI().fireMailReadEvent(mail, reader);
            }
            // 清理缓存，确保已读状态同步
            cacheManager.invalidate(mail.getReceiverUuid());
        });
    }

    public void claimAttachments(UUID mailId, Player player) {
        // 内存锁：防止同一服务器内并发
        if (processingClaims.putIfAbsent(mailId, Boolean.TRUE) != null) {
            player.sendMessage("§c[邮件系统] 附件正在处理中，请稍后");
            return;
        }

        // 先检查权限和基本条件（快速失败）
        getMail(mailId, mail -> {
            if (mail == null) {
                player.sendMessage("§c[邮件系统] 邮件不存在！");
                processingClaims.remove(mailId);
                return;
            }
            if (!mail.hasAttachments()) {
                player.sendMessage("§c[邮件系统] 该邮件没有附件！");
                processingClaims.remove(mailId);
                return;
            }
            if (mail.isExpired()) {
                player.sendMessage("§c[邮件系统] 邮件已过期！");
                processingClaims.remove(mailId);
                return;
            }
            if (!mail.getReceiverUuid().equals(player.getUniqueId()) && !player.hasPermission("mailsystem.admin")) {
                player.sendMessage("§c你没有权限领取此邮件的附件！");
                processingClaims.remove(mailId);
                return;
            }

            // 执行数据库CAS更新并领取
            List<ItemStack> attachments = mail.getAttachments();
            doClaimAttachments(mailId, player, mail, attachments);
        });
    }

    private void doClaimAttachments(UUID mailId, Player player, Mail mail, List<ItemStack> attachments) {
        databaseQueue.submit("claimAttachments", conn -> {
            // 在同一事务中检查和更新，使用数据库锁防止跨服竞态
            conn.setAutoCommit(false);
            try {
                // 1. 使用SELECT FOR UPDATE锁定行（数据库行锁）
                String selectSql = "SELECT is_claimed, receiver_uuid, money_attachment FROM mails WHERE id = ? FOR UPDATE";
                boolean canClaim = false;
                double moneyAttachment = 0;

                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setString(1, mailId.toString());
                    try (ResultSet rs = selectPs.executeQuery()) {
                        if (rs.next()) {
                            boolean isClaimed = rs.getBoolean("is_claimed");
                            UUID receiverUuid = UUID.fromString(rs.getString("receiver_uuid"));
                            moneyAttachment = rs.getDouble("money_attachment");

                            // 再次验证权限和状态
                            if (!isClaimed && (receiverUuid.equals(player.getUniqueId()) || player.hasPermission("mailsystem.admin"))) {
                                canClaim = true;
                            }
                        }
                    }
                }

                if (!canClaim) {
                    conn.rollback();
                    return false; // 已被领取或无权限
                }

                // 2. 更新状态
                String updateSql = "UPDATE mails SET is_claimed = TRUE WHERE id = ?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setString(1, mailId.toString());
                    updatePs.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }, success -> {
            processingClaims.remove(mailId);

            if (success) {
                // 数据库更新成功，现在给予物品（放不下的丢在脚下）
                player.getScheduler().run(plugin, t -> {
                    // 给予物品，放不下的丢在脚下
                    int givenCount = 0;
                    int droppedCount = 0;

                    for (ItemStack item : attachments) {
                        ItemStack clone = item.clone();
                        // 尝试放入背包
                        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(clone);

                        if (!leftover.isEmpty()) {
                            // 背包满了，剩余物品丢在脚下
                            for (ItemStack dropItem : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
                                droppedCount += dropItem.getAmount();
                            }
                        } else {
                            givenCount += clone.getAmount();
                        }
                    }

                    // 给予金币
                    if (mail.getMoneyAttachment() > 0) {
                        plugin.getEconomyManager().deposit(player, mail.getMoneyAttachment());
                        player.sendMessage("§a[邮件系统] 已领取金币: §f" +
                                plugin.getEconomyManager().format(mail.getMoneyAttachment()));
                    }

                    // 发送领取结果消息
                    if (droppedCount > 0) {
                        player.sendMessage("§a[邮件系统] 附件领取成功！§e背包已满，部分物品已掉落在脚下");
                        player.sendMessage("§a背包内: §f" + givenCount + " §a个，掉落在地: §f" + droppedCount + " §a个");
                    } else {
                        player.sendMessage("§a[邮件系统] 附件领取成功！共 §f" + givenCount + " §a个物品");
                    }

                    // 触发事件
                    plugin.getAPI().fireAttachmentClaimEvent(mail, player);

                    // 跨服通知：通知其他服务器缓存失效
                    plugin.getCrossServerNotifier().notifyAttachmentClaimed(mailId, player.getUniqueId());

                    // 清理本地缓存
                    cacheManager.invalidate(mail.getReceiverUuid());
                }, null);
            } else {
                player.sendMessage("§c[邮件系统] 附件已被领取或无权领取！");
            }
        }, error -> {
            processingClaims.remove(mailId);
            player.sendMessage("§c[邮件系统] 领取失败，请稍后再试！");
        });
    }

    public void deleteMail(UUID mailId, UUID playerUuid) {
        databaseQueue.submit("deleteMail", conn -> {
            String checkSql = "SELECT receiver_uuid FROM mails WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, mailId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID receiverUuid = UUID.fromString(rs.getString("receiver_uuid"));
                        if (receiverUuid.equals(playerUuid)) {
                            String deleteSql = "DELETE FROM mails WHERE id = ?";
                            try (PreparedStatement delPs = conn.prepareStatement(deleteSql)) {
                                delPs.setString(1, mailId.toString());
                                delPs.executeUpdate();
                            }
                            return receiverUuid;
                        }
                    }
                }
            }
            return null;
        }, receiverUuid -> {
            if (receiverUuid != null) {
                cacheManager.invalidate(receiverUuid);
            }
        });
    }

    public void deleteMailById(UUID mailId) {
        databaseQueue.submit("deleteMailById", conn -> {
            // 先查询接收者UUID
            String selectSql = "SELECT receiver_uuid FROM mails WHERE id = ?";
            UUID receiverUuid = null;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, mailId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        receiverUuid = UUID.fromString(rs.getString("receiver_uuid"));
                    }
                }
            }
            // 删除邮件
            String sql = "DELETE FROM mails WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mailId.toString());
                ps.executeUpdate();
            }
            return receiverUuid;
        }, receiverUuid -> {
            if (receiverUuid != null) {
                cacheManager.invalidate(receiverUuid);
            }
        });
    }

    public void markAsReadStatus(UUID mailId, boolean read) {
        getMail(mailId, mail -> {
            if (mail == null) return;
            databaseQueue.submitAsync("markAsReadStatus", conn -> {
                String sql = "UPDATE mails SET is_read = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setBoolean(1, read);
                    ps.setString(2, mailId.toString());
                    ps.executeUpdate();
                }
                return null;
            });
            // 清理缓存
            cacheManager.invalidate(mail.getReceiverUuid());
        });
    }

    public void markAsClaimedStatus(UUID mailId, boolean claimed) {
        getMail(mailId, mail -> {
            if (mail == null) return;
            databaseQueue.submitAsync("markAsClaimedStatus", conn -> {
                String sql = "UPDATE mails SET is_claimed = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setBoolean(1, claimed);
                    ps.setString(2, mailId.toString());
                    ps.executeUpdate();
                }
                return null;
            });
            // 清理缓存
            cacheManager.invalidate(mail.getReceiverUuid());
        });
    }

    public void clearInbox(UUID playerUuid, Consumer<Integer> callback) {
        databaseQueue.submit("clearInbox", conn -> {
            String sql = "DELETE FROM mails WHERE receiver_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                return ps.executeUpdate();
            }
        }, deleted -> {
            cacheManager.invalidate(playerUuid);
            callback.accept(deleted);
        });
    }

    // ==================== 内部方法 ====================

    public void getMailCountAsync(UUID playerUuid, Consumer<Integer> callback) {
        databaseQueue.submit("getMailCount", conn -> {
            String sql = "SELECT COUNT(*) FROM mails WHERE receiver_uuid = ? AND (expire_time = 0 OR expire_time > ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return 0;
        }, callback);
    }

    public void getTodaySendCountAsync(UUID playerUuid, Consumer<Integer> callback) {
        logManager.getTodaySendCountAsync(playerUuid, callback);
    }

    public void logMailSend(UUID playerUuid, int amount) {
        logManager.logMailSend(playerUuid, amount);
    }

    public void isInBlacklist(UUID senderUuid, UUID receiverUuid, Consumer<Boolean> callback) {
        blacklistManager.isInBlacklist(senderUuid, receiverUuid, callback);
    }

    public void getBlacklist(UUID ownerUuid, Consumer<Set<UUID>> callback) {
        blacklistManager.getBlacklist(ownerUuid, callback);
    }

    public void addToBlacklist(UUID ownerUuid, UUID blockedUuid, Consumer<Boolean> callback) {
        blacklistManager.addToBlacklist(ownerUuid, blockedUuid, callback);
    }

    public void removeFromBlacklist(UUID ownerUuid, UUID blockedUuid, Consumer<Boolean> callback) {
        blacklistManager.removeFromBlacklist(ownerUuid, blockedUuid, callback);
    }

    public List<Mail> getPlayerMails(UUID playerUuid) {
        return cacheManager.getCachedMails(playerUuid);
    }

    public void clearPlayerCache(UUID playerUuid) {
        cacheManager.invalidate(playerUuid);
    }

    public void clearAllCache() {
        cacheManager.clear();
    }

    public byte[] serializeAttachmentsInternal(List<ItemStack> items) {
        return attachmentManager.serialize(items);
    }

    public List<ItemStack> deserializeAttachmentsInternal(byte[] data) {
        return attachmentManager.deserialize(data);
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    public MailCacheManager getCacheManager() {
        return cacheManager;
    }

    public MailLogManager getLogManager() {
        return logManager;
    }

    public AttachmentManager getAttachmentManager() {
        return attachmentManager;
    }

    // ==================== 定时任务 ====================

    private void checkUnreadMails() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (cacheManager.hasUnreadNotificationSent(player.getUniqueId())) return;
            loadPlayerMails(player.getUniqueId(), mails -> {
                int unread = (int) mails.stream().filter(m -> !m.isRead()).count();
                if (unread > 0) {
                    player.sendMessage("§a[邮件系统] §e你有 §c" + unread + " §e封未读邮件，使用 §f/fmail §e查看");
                    cacheManager.markUnreadNotificationSent(player.getUniqueId());
                }
            });
        });
    }

    private void cleanExpiredMails() {
        databaseQueue.submit("cleanExpiredMails", conn -> {
            // 1. 先查询出过期的邮件接收者
            Set<UUID> affectedReceivers = new HashSet<>();
            String selectSql = "SELECT DISTINCT receiver_uuid FROM mails WHERE expire_time > 0 AND expire_time < ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        affectedReceivers.add(UUID.fromString(rs.getString("receiver_uuid")));
                    }
                }
            }

            // 2. 删除过期邮件
            String deleteSql = "DELETE FROM mails WHERE expire_time > 0 AND expire_time < ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setLong(1, System.currentTimeMillis());
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("已清理 " + deleted + " 封过期邮件");
                }
            }
            return affectedReceivers;
        }, affectedReceivers -> {
            // 3. 清理受影响玩家的缓存
            if (affectedReceivers != null && !affectedReceivers.isEmpty()) {
                for (UUID receiverUuid : affectedReceivers) {
                    cacheManager.invalidate(receiverUuid);
                }
            }
        });
    }

    // ==================== 工具方法 ====================

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

        byte[] attachData = rs.getBytes("attachments");
        if (attachData != null) {
            mail.setAttachments(attachmentManager.deserialize(attachData));
        }
        return mail;
    }

    private Mail createMailFromContext(SendContext ctx) {
        Mail mail = new Mail(
                ctx.getSenderUuid(),
                ctx.getSenderName(),
                ctx.getReceiverUuid(),
                ctx.getReceiverName(),
                ctx.getDraft().getTitle(),
                ctx.getDraft().getContent()
        );
        mail.setId(ctx.getMailId());
        mail.setSentTime(ctx.getSentTime());
        mail.setExpireTime(ctx.getDraft().getExpireTime());
        mail.setMoneyAttachment(ctx.getDraft().getMoneyAttachment());
        if (ctx.getDraft().hasItemAttachments()) {
            mail.setAttachments(new ArrayList<>(ctx.getDraft().getAttachments()));
        }
        return mail;
    }
}
