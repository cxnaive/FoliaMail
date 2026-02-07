package dev.user.mailsystem.mail;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.database.DatabaseQueue;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.*;
import java.sql.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MailManager implements Consumer<ScheduledTask> {

    private final MailSystemPlugin plugin;
    private final DatabaseQueue databaseQueue;
    private final Map<UUID, List<Mail>> playerMailCache;
    private final Set<UUID> unreadNotificationSent;
    private final ConcurrentHashMap<UUID, Boolean> processingClaims;
    private ScheduledTask cleanupTask;
    private ScheduledTask notificationTask;

    public MailManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.databaseQueue = plugin.getDatabaseQueue();
        this.playerMailCache = new ConcurrentHashMap<>();
        this.unreadNotificationSent = ConcurrentHashMap.newKeySet();
        this.processingClaims = new ConcurrentHashMap<>();
        startTasks();
    }

    public void reload() {
        stopTasks();
        playerMailCache.clear();
        unreadNotificationSent.clear();
        processingClaims.clear();
        startTasks();
    }

    private void startTasks() {
        int cleanupInterval = 20 * 60 * 60; // 1小时
        int checkInterval = plugin.getMailConfig().getUnreadCheckInterval() * 20;

        cleanupTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, this, cleanupInterval, cleanupInterval);
        notificationTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> checkUnreadMails(), checkInterval, checkInterval);
    }

    private void stopTasks() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        if (notificationTask != null && !notificationTask.isCancelled()) {
            notificationTask.cancel();
        }
    }

    @Override
    public void accept(ScheduledTask task) {
        cleanExpiredMails();
    }

    /**
     * 发送邮件（供内部使用，不检查邮箱上限）
     */
    public void sendMail(Mail mail) {
        doSendMail(mail, null, true);
    }

    /**
     * 发送邮件（带发送者检查，普通用户受邮箱上限限制）
     * @param mail 邮件对象
     * @param sender 发送者，如果为null或管理员则跳过上限检查
     * @param callback 回调函数，参数为是否发送成功
     */
    public void sendMail(Mail mail, Player sender, Consumer<Boolean> callback) {
        sendMail(mail, sender, 0, callback, true);
    }

    /**
     * 发送邮件（带费用扣除）
     * @param mail 邮件对象
     * @param sender 发送者
     * @param cost 发送费用
     * @param callback 回调函数，参数为是否发送成功
     */
    public void sendMail(Mail mail, Player sender, double cost, Consumer<Boolean> callback) {
        sendMail(mail, sender, cost, callback, true);
    }

    /**
     * 发送邮件（带缓存控制和费用扣除）
     * @param mail 邮件对象
     * @param sender 发送者，如果为null或管理员则跳过上限检查
     * @param cost 发送费用（0表示不扣费）
     * @param callback 回调函数，参数为是否发送成功
     * @param clearCache 是否清除收件人缓存（批量发送时可设为false，发送完成后统一清除）
     */
    public void sendMail(Mail mail, Player sender, double cost, Consumer<Boolean> callback, boolean clearCache) {
        // 检查发送者是否有管理员权限，没有则检查邮箱上限
        if (sender == null || !sender.hasPermission("mailsystem.admin")) {
            int maxSize = plugin.getMailConfig().getMaxMailboxSize();
            if (maxSize > 0) {
                // 异步获取邮件数量
                getMailCountAsync(mail.getReceiverUuid(), currentSize -> {
                    if (currentSize >= maxSize) {
                        if (sender != null) {
                            sender.sendMessage("§c[邮件系统] 收件人 " + mail.getReceiverName() + " 的邮箱已满 (" + currentSize + "/" + maxSize + ")，无法发送邮件！");
                        }
                        callback.accept(false);
                    } else {
                        // 邮箱未满，检查每日发送限制
                        checkAndProcessDailyLimit(mail, sender, cost, callback, clearCache);
                    }
                });
                return;
            }
        }
        // 管理员或上限为0，检查每日发送限制
        checkAndProcessDailyLimit(mail, sender, cost, callback, clearCache);
    }

    /**
     * 检查每日发送限制并继续处理
     */
    private void checkAndProcessDailyLimit(Mail mail, Player sender, double cost, Consumer<Boolean> callback, boolean clearCache) {
        // 检查每日发送限制（管理员不受限制）
        if (sender != null && !sender.hasPermission("mailsystem.admin")) {
            int dailyLimit = plugin.getMailConfig().getDailySendLimit();
            if (dailyLimit > 0) {
                // 异步获取今日已发送数量
                getTodaySendCountAsync(sender.getUniqueId(), todayCount -> {
                    if (todayCount >= dailyLimit) {
                        sender.sendMessage("§c[邮件系统] 你今日发送邮件已达上限 (" + dailyLimit + "封)，请明天再试！");
                        callback.accept(false);
                    } else {
                        // 未达上限，检查黑名单
                        checkBlacklistAndSend(mail, sender, cost, callback, clearCache);
                    }
                });
                return;
            }
        }
        // 管理员或无限制，检查黑名单
        checkBlacklistAndSend(mail, sender, cost, callback, clearCache);
    }

    /**
     * 检查黑名单并继续处理
     */
    private void checkBlacklistAndSend(Mail mail, Player sender, double cost, Consumer<Boolean> callback, boolean clearCache) {
        // 检查发送者是否在接收者的黑名单中（管理员不受限制）
        if (sender != null && !sender.hasPermission("mailsystem.admin")) {
            isInBlacklist(sender.getUniqueId(), mail.getReceiverUuid(), isBlocked -> {
                if (isBlocked) {
                    sender.sendMessage("§c[邮件系统] 你已被对方加入黑名单，无法发送邮件！");
                    callback.accept(false);
                } else {
                    // 不在黑名单，处理扣费
                    processPaymentAndSend(mail, sender, cost, callback, clearCache);
                }
            });
            return;
        }
        // 管理员，直接处理扣费
        processPaymentAndSend(mail, sender, cost, callback, clearCache);
    }

    /**
     * 处理扣费并发送邮件
     */
    private void processPaymentAndSend(Mail mail, Player sender, double cost, Consumer<Boolean> callback, boolean clearCache) {
        // 检查是否需要扣费（经济启用、有费用、有发送者）
        if (sender != null && cost > 0 && plugin.getMailConfig().isEnableEconomy()
                && plugin.getEconomyManager().isEnabled()) {
            // 检查余额
            if (!plugin.getEconomyManager().hasEnough(sender, cost)) {
                sender.sendMessage("§c[邮件系统] §e余额不足！需要 §f" +
                    plugin.getEconomyManager().format(cost) + " §e，当前余额 §f" +
                    plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(sender)));
                callback.accept(false);
                return;
            }

            // 扣除费用
            if (!plugin.getEconomyManager().withdraw(sender, cost)) {
                sender.sendMessage("§c[邮件系统] §e扣费失败，请稍后再试！");
                callback.accept(false);
                return;
            }

            // 扣费成功，发送扣费提示（在主线程）
            final double finalCost = cost;
            sender.getScheduler().run(plugin, task -> {
                sender.sendMessage("§a[邮件系统] §e已扣费 §f" + plugin.getEconomyManager().format(finalCost));
            }, null);
        }

        // 执行发送
        doSendMail(mail, sender, clearCache);

        // 记录发送日志
        if (sender != null) {
            logMailSend(sender.getUniqueId(), 1);
        }

        callback.accept(true);
    }

    /**
     * 异步获取玩家的邮件数量（使用COUNT查询，不加载数据）
     */
    public void getMailCountAsync(UUID playerUuid, Consumer<Integer> callback) {
        databaseQueue.submit("getMailCount", conn -> {
            String sql = "SELECT COUNT(*) FROM mails WHERE receiver_uuid = ? AND (expire_time = 0 OR expire_time > ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
                return 0;
            }
        }, callback);
    }

    /**
     * 异步获取玩家今日发送的邮件数量（从发送日志表查询，删除邮件不影响统计）
     * @param playerUuid 玩家UUID
     * @param callback 回调函数，参数为今日已发送数量
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
                return 0;
            }
        }, callback);
    }

    /**
     * 记录玩家发送邮件日志
     * @param playerUuid 发送者UUID
     * @param amount 发送数量
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
     * 检查发送者是否在接收者的黑名单中
     * @param senderUuid 发送者UUID
     * @param receiverUuid 接收者UUID
     * @param callback 回调函数，参数为是否在黑名单中
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
     * @param ownerUuid 黑名单所有者UUID
     * @param blockedUuid 被屏蔽的玩家UUID
     * @param callback 回调函数，参数为是否添加成功
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
     * @param ownerUuid 黑名单所有者UUID
     * @param blockedUuid 被屏蔽的玩家UUID
     * @param callback 回调函数，参数为是否移除成功
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
     * @param ownerUuid 黑名单所有者UUID
     * @param callback 回调函数，参数为黑名单中的玩家UUID集合
     */
    public void getBlacklist(UUID ownerUuid, Consumer<Set<UUID>> callback) {
        databaseQueue.submit("getBlacklist", conn -> {
            Set<UUID> blacklist = new HashSet<>();
            String sql = "SELECT blocked_uuid FROM mail_blacklist WHERE owner_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        blacklist.add(UUID.fromString(rs.getString("blocked_uuid")));
                    }
                }
            }
            return blacklist;
        }, callback);
    }

    /**
     * 获取今日日期字符串 (yyyy-MM-dd)
     */
    private String getTodayDateString() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return today.toString(); // 格式: yyyy-MM-dd
    }

    /**
     * 检查玩家今日是否还可以发送邮件（异步回调，不卡顿主线程）
     * @param sender 发送者
     * @param callback 回调函数，参数1为是否允许发送，参数2为剩余可发送数量（-1表示无限制）
     */
    public void checkDailySendLimit(Player sender, java.util.function.BiConsumer<Boolean, Integer> callback) {
        // 管理员不受限制
        if (sender.hasPermission("mailsystem.admin")) {
            callback.accept(true, -1);
            return;
        }

        int dailyLimit = plugin.getMailConfig().getDailySendLimit();
        // 限制为0或负数表示无限制
        if (dailyLimit <= 0) {
            callback.accept(true, -1);
            return;
        }

        // 异步查询今日已发送数量
        getTodaySendCountAsync(sender.getUniqueId(), count -> {
            int remaining = dailyLimit - count;
            if (remaining <= 0) {
                callback.accept(false, 0);
            } else {
                callback.accept(true, remaining);
            }
        });
    }

    /**
     * 获取今日零点的时间戳
     */
    private long getTodayStartTimestamp() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 批量发送邮件（优化版本，使用批量插入）
     * @param mails 邮件列表
     * @param sender 发送者
     * @param callback 回调，参数为实际发送成功的UUID集合
     */
    public void sendMailsBatch(List<Mail> mails, Player sender, Consumer<Set<UUID>> callback) {
        if (mails.isEmpty()) {
            callback.accept(new HashSet<>());
            return;
        }

        int maxSize = plugin.getMailConfig().getMaxMailboxSize();
        boolean isAdmin = sender == null || sender.hasPermission("mailsystem.admin");

        // 非管理员需要检查每日发送限制
        if (!isAdmin && sender != null) {
            int dailyLimit = plugin.getMailConfig().getDailySendLimit();
            if (dailyLimit > 0) {
                getTodaySendCountAsync(sender.getUniqueId(), todayCount -> {
                    int remaining = dailyLimit - todayCount;
                    if (remaining <= 0) {
                        sender.sendMessage("§c[邮件系统] 你今日发送邮件已达上限 (" + dailyLimit + "封)，请明天再试！");
                        callback.accept(new HashSet<>());
                    } else if (mails.size() > remaining) {
                        // 超过剩余额度，截取可发送的部分
                        List<Mail> limitedMails = mails.subList(0, remaining);
                        sender.sendMessage("§e[邮件系统] 注意：你今日还可发送 " + remaining + " 封邮件，超出部分已跳过。");
                        processBatchSendWithLimit(limitedMails, sender, maxSize, callback);
                    } else {
                        // 未超限制，正常发送
                        processBatchSendWithLimit(mails, sender, maxSize, callback);
                    }
                });
                return;
            }
        }

        // 管理员或无限制，直接处理
        processBatchSendWithLimit(mails, sender, maxSize, callback);
    }

    /**
     * 处理批量发送（带邮箱上限检查）
     */
    private void processBatchSendWithLimit(List<Mail> mails, Player sender, int maxSize, Consumer<Set<UUID>> callback) {
        boolean isAdmin = sender == null || sender.hasPermission("mailsystem.admin");

        // 管理员或上限为0，直接批量插入
        if (isAdmin || maxSize == 0) {
            doSendMailsBatch(mails);
            Set<UUID> sentUuids = mails.stream()
                    .map(Mail::getReceiverUuid)
                    .collect(java.util.stream.Collectors.toSet());
            callback.accept(sentUuids);
            return;
        }

        // 需要检查邮箱上限，先批量查询所有收件人的邮件数量
        // 分批处理，每批最多 500 个，避免 IN 子句参数限制（H2/MySQL 通常限制 1000）
        Set<UUID> allReceivers = mails.stream()
                .map(Mail::getReceiverUuid)
                .collect(java.util.stream.Collectors.toSet());

        databaseQueue.submit("batchGetMailCount", conn -> {
            Map<UUID, Integer> counts = new HashMap<>();
            List<UUID> receiverList = new ArrayList<>(allReceivers);
            int batchSize = 500; // 每批查询 500 个
            long currentTime = System.currentTimeMillis();

            for (int i = 0; i < receiverList.size(); i += batchSize) {
                List<UUID> batch = receiverList.subList(i, Math.min(i + batchSize, receiverList.size()));
                // 构建 IN 查询
                String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
                String sql = "SELECT receiver_uuid, COUNT(*) as count FROM mails " +
                        "WHERE receiver_uuid IN (" + placeholders + ") " +
                        "AND (expire_time = 0 OR expire_time > ?) " +
                        "GROUP BY receiver_uuid";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int index = 1;
                    for (UUID uuid : batch) {
                        ps.setString(index++, uuid.toString());
                    }
                    ps.setLong(index, currentTime);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            counts.put(UUID.fromString(rs.getString("receiver_uuid")), rs.getInt("count"));
                        }
                    }
                }
            }
            return counts;
        }, countsMap -> {
            // 过滤掉邮箱已满的收件人
            List<Mail> toSend = new ArrayList<>();
            for (Mail mail : mails) {
                int currentCount = countsMap.getOrDefault(mail.getReceiverUuid(), 0);
                if (currentCount >= maxSize) {
                    sender.sendMessage("§c[邮件系统] 收件人 " + mail.getReceiverName() + " 的邮箱已满 (" + currentCount + "/" + maxSize + ")，已跳过。");
                } else {
                    toSend.add(mail);
                }
            }

            if (!toSend.isEmpty()) {
                doSendMailsBatch(toSend);
            }

            Set<UUID> sentUuids = toSend.stream()
                    .map(Mail::getReceiverUuid)
                    .collect(java.util.stream.Collectors.toSet());
            callback.accept(sentUuids);
        });
    }

    /**
     * 执行批量邮件插入
     */
    private void doSendMailsBatch(List<Mail> mails) {
        long expireDays = plugin.getMailConfig().getMailExpirationDays();
        long currentTime = System.currentTimeMillis();
        String serverId = getServerId();

        // 预处理所有邮件
        for (Mail mail : mails) {
            if (expireDays > 0) {
                mail.setExpireTime(currentTime + (expireDays * 24 * 60 * 60 * 1000));
            }
            mail.setServerId(serverId);
            if (plugin.getCrossServerNotifier() != null) {
                plugin.getCrossServerNotifier().markNotified(mail.getId());
            }
        }

        databaseQueue.submitAsync("sendMailsBatch", conn -> {
            String sql = "INSERT INTO mails (id, sender_uuid, sender_name, receiver_uuid, receiver_name, " +
                    "title, content, attachments, money_attachment, sent_time, expire_time, is_read, is_claimed, read_time, server_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int batchCount = 0;
                for (Mail mail : mails) {
                    ps.setString(1, mail.getId().toString());
                    ps.setString(2, mail.getSenderUuid().toString());
                    ps.setString(3, mail.getSenderName());
                    ps.setString(4, mail.getReceiverUuid().toString());
                    ps.setString(5, mail.getReceiverName());
                    ps.setString(6, mail.getTitle());
                    ps.setString(7, mail.getContent());
                    ps.setBytes(8, serializeAttachments(mail.getAttachments()));
                    ps.setDouble(9, mail.getMoneyAttachment());
                    ps.setLong(10, mail.getSentTime());
                    ps.setLong(11, mail.getExpireTime());
                    ps.setBoolean(12, mail.isRead());
                    ps.setBoolean(13, mail.isClaimed());
                    ps.setLong(14, mail.getReadTime());
                    ps.setString(15, mail.getServerId());
                    ps.addBatch();
                    batchCount++;

                    // 每 100 条执行一次批量插入，避免内存溢出
                    if (batchCount % 100 == 0) {
                        ps.executeBatch();
                        batchCount = 0;
                    }
                }
                // 执行剩余的
                if (batchCount > 0) {
                    ps.executeBatch();
                }
            }
            return null;
        });

        // 批量清除缓存并通知在线玩家
        for (Mail mail : mails) {
            playerMailCache.remove(mail.getReceiverUuid());
            unreadNotificationSent.remove(mail.getReceiverUuid());

            Player receiver = Bukkit.getPlayer(mail.getReceiverUuid());
            if (receiver != null && receiver.isOnline() && receiver.hasPermission("mailsystem.read")) {
                receiver.sendMessage("§a[邮件系统] §e你收到了一封新邮件！来自: §f" + mail.getSenderName());
            }
        }

        // 记录发送日志（批量发送只记录一次）
        if (!mails.isEmpty()) {
            UUID senderUuid = mails.get(0).getSenderUuid();
            logMailSend(senderUuid, mails.size());
        }
    }

    private void doSendMail(Mail mail, Player sender, boolean clearCache) {
        long expireDays = plugin.getMailConfig().getMailExpirationDays();
        if (expireDays > 0) {
            mail.setExpireTime(System.currentTimeMillis() + (expireDays * 24 * 60 * 60 * 1000));
        }
        mail.setServerId(getServerId());

        // 标记此邮件已由本服发送，避免跨服通知器重复通知
        if (plugin.getCrossServerNotifier() != null) {
            plugin.getCrossServerNotifier().markNotified(mail.getId());
        }

        databaseQueue.submitAsync("sendMail", conn -> {
            String sql = "INSERT INTO mails (id, sender_uuid, sender_name, receiver_uuid, receiver_name, " +
                    "title, content, attachments, money_attachment, sent_time, expire_time, is_read, is_claimed, read_time, server_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mail.getId().toString());
                ps.setString(2, mail.getSenderUuid().toString());
                ps.setString(3, mail.getSenderName());
                ps.setString(4, mail.getReceiverUuid().toString());
                ps.setString(5, mail.getReceiverName());
                ps.setString(6, mail.getTitle());
                ps.setString(7, mail.getContent());
                ps.setBytes(8, serializeAttachments(mail.getAttachments()));
                ps.setDouble(9, mail.getMoneyAttachment());
                ps.setLong(10, mail.getSentTime());
                ps.setLong(11, mail.getExpireTime());
                ps.setBoolean(12, mail.isRead());
                ps.setBoolean(13, mail.isClaimed());
                ps.setLong(14, mail.getReadTime());
                ps.setString(15, mail.getServerId());
                ps.executeUpdate();
            }
            return null;
        });

        // 批量发送时可以选择不清除缓存，在批量发送完成后统一清除
        if (clearCache) {
            playerMailCache.remove(mail.getReceiverUuid());
            unreadNotificationSent.remove(mail.getReceiverUuid());
        }

        // 只通知本服在线的玩家，跨服玩家由 CrossServerNotifier 处理
        Player receiver = Bukkit.getPlayer(mail.getReceiverUuid());
        if (receiver != null && receiver.isOnline()) {
            // 检查接收者是否有阅读权限
            if (receiver.hasPermission("mailsystem.read")) {
                receiver.sendMessage("§a[邮件系统] §e你收到了一封新邮件！来自: §f" + mail.getSenderName());
            }
        }
    }

    public void loadPlayerMails(UUID playerUuid, Consumer<List<Mail>> callback) {
        databaseQueue.submit("loadPlayerMails", conn -> {
            List<Mail> mails = new ArrayList<>();
            String sql = "SELECT * FROM mails WHERE receiver_uuid = ? ORDER BY sent_time DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        mails.add(resultSetToMail(rs));
                    }
                }
            }
            return mails;
        }, mails -> {
            List<Mail> cached = new CopyOnWriteArrayList<>(mails);
            playerMailCache.put(playerUuid, cached);
            if (callback != null) {
                callback.accept(cached);
            }
        });
    }

    /**
     * 异步加载玩家发送的邮件（发件箱）
     */
    public void loadSentMails(UUID senderUuid, Consumer<List<Mail>> callback) {
        databaseQueue.submit("loadSentMails", conn -> {
            List<Mail> mails = new ArrayList<>();
            String sql = "SELECT * FROM mails WHERE sender_uuid = ? ORDER BY sent_time DESC";

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

    public List<Mail> getPlayerMails(UUID playerUuid) {
        return playerMailCache.computeIfAbsent(playerUuid, uuid -> {
            loadPlayerMails(uuid, null);
            return new CopyOnWriteArrayList<>();
        });
    }

    public void getMail(UUID mailId, Consumer<Mail> callback) {
        databaseQueue.submit("getMail", conn -> {
            String sql = "SELECT * FROM mails WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mailId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return resultSetToMail(rs);
                    }
                }
            }
            return null;
        }, callback);
    }

    public void markAsRead(UUID mailId) {
        databaseQueue.submitAsync("markAsRead", conn -> {
            String sql = "UPDATE mails SET is_read = ?, read_time = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, true);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, mailId.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void claimAttachments(UUID mailId, Player player) {
        // 第一层保护：内存原子操作，防止同服务器内的重复领取
        // putIfAbsent 返回 null 表示之前不存在，即成功占用；返回非 null 表示已在处理中
        if (processingClaims.putIfAbsent(mailId, Boolean.TRUE) != null) {
            player.sendMessage("§c[邮件系统] 该邮件附件正在处理中，请稍后...");
            return;
        }

        databaseQueue.submit("claimAttachments", conn -> {
            // 先验证玩家是否是邮件的接收者
            String checkSql = "SELECT receiver_uuid FROM mails WHERE id = ? AND is_claimed = ?";
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, mailId.toString());
                checkPs.setBoolean(2, false);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (!rs.next()) {
                        return -1; // 邮件不存在或已被领取
                    }
                    UUID receiverUuid = UUID.fromString(rs.getString("receiver_uuid"));
                    if (!receiverUuid.equals(player.getUniqueId())) {
                        return -2; // 不是接收者，无权领取
                    }
                }
            }

            // 第二层保护：数据库原子操作，防止跨服务器的重复领取
            // UPDATE ... WHERE is_claimed = false 确保只有真正更新了记录才返回成功
            String sql = "UPDATE mails SET is_claimed = ? WHERE id = ? AND is_claimed = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, true);
                ps.setString(2, mailId.toString());
                ps.setBoolean(3, false);
                return ps.executeUpdate();  // 返回实际更新的行数
            }
        }, updated -> {
            if (updated > 0) {
                // 数据库确实更新了，说明领取成功
                getMail(mailId, mail -> {
                    if (mail != null && mail.hasAttachments()) {
                        player.getScheduler().run(plugin, giveTask -> {
                            try {
                                // 更新内存中的邮件状态
                                mail.setClaimed(true);
                                // 给玩家物品
                                for (ItemStack item : mail.getAttachments()) {
                                    if (item != null) {
                                        player.getInventory().addItem(item).values()
                                                .forEach(drop -> player.getWorld().dropItem(player.getLocation(), drop));
                                    }
                                }
                                // 给玩家金币
                                if (mail.getMoneyAttachment() > 0) {
                                    if (plugin.getEconomyManager().isEnabled()) {
                                        plugin.getEconomyManager().deposit(player, mail.getMoneyAttachment());
                                        player.sendMessage("§a[邮件系统] §e附件已领取！获得金币 §f" +
                                            plugin.getEconomyManager().format(mail.getMoneyAttachment()));
                                    } else {
                                        player.sendMessage("§a[邮件系统] §e物品附件已领取！§c(经济功能未启用，无法领取金币)");
                                    }
                                } else {
                                    player.sendMessage("§a[邮件系统] §e附件已领取！");
                                }
                            } finally {
                                processingClaims.remove(mailId);
                                playerMailCache.remove(player.getUniqueId());
                            }
                        }, null);
                    } else {
                        // 邮件不存在或没有附件，释放锁并刷新缓存
                        processingClaims.remove(mailId);
                        playerMailCache.remove(player.getUniqueId());
                        player.getScheduler().run(plugin, task -> {
                            player.sendMessage("§c[邮件系统] 附件领取失败：邮件不存在或没有附件。");
                        }, null);
                    }
                });
            } else if (updated == -1) {
                // 邮件不存在或已被领取
                processingClaims.remove(mailId);
                playerMailCache.remove(player.getUniqueId());
                player.getScheduler().run(plugin, task -> {
                    player.sendMessage("§c[邮件系统] 该邮件不存在或附件已被领取。");
                }, null);
            } else if (updated == -2) {
                // 不是接收者
                processingClaims.remove(mailId);
                playerMailCache.remove(player.getUniqueId());
                player.getScheduler().run(plugin, task -> {
                    player.sendMessage("§c[邮件系统] 你没有权限领取这封邮件的附件！");
                }, null);
            } else {
                // 数据库没有更新，说明已经被其他线程/服务器领取
                player.getScheduler().run(plugin, task -> {
                    player.sendMessage("§c[邮件系统] 该邮件附件已被领取或正在处理中。");
                }, null);
                processingClaims.remove(mailId);
                playerMailCache.remove(player.getUniqueId());
            }
        }, ex -> {
            // SQL 报错处理，确保释放锁
            processingClaims.remove(mailId);
            playerMailCache.remove(player.getUniqueId());
            player.getScheduler().run(plugin, task -> {
                player.sendMessage("§c[邮件系统] §e附件领取失败: 数据库错误，请联系管理员。");
            }, null);
        });
    }

    public void deleteMail(UUID mailId, UUID playerUuid) {
        databaseQueue.submitAsync("deleteMail", conn -> {
            String sql = "DELETE FROM mails WHERE id = ? AND receiver_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mailId.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            }
            return null;
        });

        playerMailCache.remove(playerUuid);
    }

    public int getUnreadCount(UUID playerUuid) {
        return (int) getPlayerMails(playerUuid).stream()
                .filter(mail -> !mail.isRead() && !mail.isExpired())
                .count();
    }

    public Map<UUID, List<Mail>> getPlayerMailCache() {
        return playerMailCache;
    }

    public void clearPlayerCache(UUID playerUuid) {
        playerMailCache.remove(playerUuid);
    }

    /**
     * 管理员设置邮件已读/未读状态
     */
    public void markAsReadStatus(UUID mailId, boolean read) {
        databaseQueue.submitAsync("markAsReadStatus", conn -> {
            String sql = "UPDATE mails SET is_read = ?, read_time = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, read);
                ps.setLong(2, read ? System.currentTimeMillis() : 0);
                ps.setString(3, mailId.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 管理员设置邮件领取/未领取状态
     */
    public void markAsClaimedStatus(UUID mailId, boolean claimed) {
        databaseQueue.submitAsync("markAsClaimedStatus", conn -> {
            String sql = "UPDATE mails SET is_claimed = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, claimed);
                ps.setString(2, mailId.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 管理员删除邮件（无需验证接收者）
     */
    public void deleteMailById(UUID mailId) {
        databaseQueue.submitAsync("deleteMailById", conn -> {
            String sql = "DELETE FROM mails WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mailId.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 清空玩家收件箱
     * @param playerUuid 玩家UUID
     * @param callback 回调，参数为删除的邮件数量
     */
    public void clearInbox(UUID playerUuid, Consumer<Integer> callback) {
        databaseQueue.submit("clearInbox", conn -> {
            String sql = "DELETE FROM mails WHERE receiver_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                return ps.executeUpdate();
            }
        }, deleted -> {
            playerMailCache.remove(playerUuid);
            unreadNotificationSent.remove(playerUuid);
            if (callback != null) {
                callback.accept(deleted);
            }
        });
    }

    private void checkUnreadMails() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            int unread = getUnreadCount(player.getUniqueId());
            if (unread > 0 && !unreadNotificationSent.contains(player.getUniqueId())) {
                player.sendMessage("§a[邮件系统] §e你有 §c" + unread + " §e封未读邮件，使用 /fmail 查看");
                unreadNotificationSent.add(player.getUniqueId());
            }
        }
    }

    private void cleanExpiredMails() {
        databaseQueue.submit("cleanExpiredMails", conn -> {
            String sql = "DELETE FROM mails WHERE expire_time > 0 AND expire_time < ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                return ps.executeUpdate();
            }
        }, deleted -> {
            if (deleted > 0) {
                plugin.getLogger().info("清理了 " + deleted + " 封过期邮件");
                playerMailCache.clear();
            }
        });
    }

    private Mail resultSetToMail(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID senderUuid = UUID.fromString(rs.getString("sender_uuid"));
        String senderName = rs.getString("sender_name");
        UUID receiverUuid = UUID.fromString(rs.getString("receiver_uuid"));
        String receiverName = rs.getString("receiver_name");
        String title = rs.getString("title");
        String content = rs.getString("content");
        long sentTime = rs.getLong("sent_time");
        long expireTime = rs.getLong("expire_time");
        boolean isRead = rs.getBoolean("is_read");
        boolean isClaimed = rs.getBoolean("is_claimed");
        long readTime = rs.getLong("read_time");
        String serverId = rs.getString("server_id");
        double moneyAttachment = rs.getDouble("money_attachment");

        Mail mail = new Mail(id, senderUuid, senderName, receiverUuid, receiverName,
                title, content, sentTime, expireTime, isRead, isClaimed, readTime, serverId, moneyAttachment);
        mail.setAttachments(deserializeAttachments(rs.getBytes("attachments")));
        return mail;
    }

    private byte[] serializeAttachments(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        // 构建 NBT 字符串
        ReadWriteNBT nbtList = NBT.createNBTObject();
        nbtList.setInteger("size", items.size());
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item != null) {
                ReadWriteNBT itemNbt = NBT.itemStackToNBT(item);
                nbtList.setString("item_" + i, itemNbt.toString());
            }
        }
        String nbtString = nbtList.toString();

        // GZIP 压缩为二进制
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(nbtString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            gos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().warning("附件序列化失败: " + e.getMessage());
            return null;
        }
    }

    private List<ItemStack> deserializeAttachments(byte[] data) {
        List<ItemStack> items = new ArrayList<>();
        if (data == null || data.length == 0) {
            return items;
        }

        try {
            // GZIP 解压
            String nbtString;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 GZIPInputStream gis = new GZIPInputStream(bais)) {
                nbtString = new String(gis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            ReadWriteNBT nbtList = NBT.parseNBT(nbtString);
            int size = nbtList.getInteger("size");
            for (int i = 0; i < size; i++) {
                String itemStr = nbtList.getString("item_" + i);
                if (itemStr != null && !itemStr.isEmpty()) {
                    ReadWriteNBT itemNbt = NBT.parseNBT(itemStr);
                    ItemStack item = NBT.itemStackFromNBT(itemNbt);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("反序列化附件失败: " + e.getMessage());
        }
        return items;
    }

    private String getServerId() {
        String serverId = plugin.getConfig().getString("server.id", "");
        if (serverId.isEmpty()) {
            serverId = Bukkit.getServer().getName();
        }
        return serverId;
    }
}
