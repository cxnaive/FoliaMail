package dev.user.mailsystem.mail.pipeline.filters;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.Mail;
import dev.user.mailsystem.mail.pipeline.SendChain;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendFilter;
import org.bukkit.Bukkit;

import dev.user.mailsystem.api.draft.BatchSendResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 持久化过滤器 - 将邮件保存到数据库
 * 统一使用批量逻辑（size==1也是批量的一种）
 */
public class PersistenceFilter implements SendFilter {

    private final MailSystemPlugin plugin;

    public PersistenceFilter(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void filterBatch(List<SendContext> contexts, SendChain chain) {
        // 统一批量逻辑（size==1也是批量的一种）
        List<Mail> mails = new ArrayList<>();
        for (SendContext ctx : contexts) {
            mails.add(buildMail(ctx));
        }

        int total = contexts.size();
        AtomicInteger completedCount = new AtomicInteger(0);

        // 追踪每个context的成功/失败状态
        Map<UUID, Boolean> successMap = new HashMap<>();
        Map<UUID, SendResult.FailReason> failReasonMap = new HashMap<>();
        Map<UUID, Double> costMap = new HashMap<>();

        for (int i = 0; i < contexts.size(); i++) {
            SendContext ctx = contexts.get(i);
            Mail mail = mails.get(i);
            UUID receiverUuid = ctx.getReceiverUuid();

            plugin.getDatabaseQueue().submit("sendMail", conn -> {
                insertMail(conn, mail);
                return null;
            }, result -> {
                // 数据库插入成功
                successMap.put(receiverUuid, true);
                costMap.put(receiverUuid, ctx.getCalculatedCost());

                // 记录发送日志
                if (ctx.getSender() != null) {
                    plugin.getMailManager().logMailSend(ctx.getSenderUuid(), 1);
                }

                // 跨服通知
                if (ctx.getOptions().isNotifyReceiver()) {
                    notifyReceiver(ctx, mail);
                }

                // 清理缓存
                if (ctx.getOptions().isClearCache()) {
                    plugin.getMailManager().clearPlayerCache(ctx.getReceiverUuid());
                }

                checkComplete(ctx, contexts, chain, completedCount, total, successMap, failReasonMap, costMap);
            }, error -> {
                // 数据库错误
                successMap.put(receiverUuid, false);
                SendResult.FailReason reason = analyzeError(error);
                failReasonMap.put(receiverUuid, reason);

                plugin.getLogger().severe("发送邮件失败 (" + ctx.getReceiverName() + "): " + error.getMessage());

                if (reason == SendResult.FailReason.MAILBOX_FULL && ctx.getSender() != null) {
                    ctx.getSender().sendMessage("§c[邮件系统] 收件人 " + ctx.getReceiverName() + " 的邮箱已满，已跳过");
                }

                checkComplete(ctx, contexts, chain, completedCount, total, successMap, failReasonMap, costMap);
            });
        }
    }

    /**
     * 检查是否全部完成并返回结果
     */
    private void checkComplete(SendContext currentCtx, List<SendContext> allContexts, SendChain chain,
                               AtomicInteger completedCount, int total,
                               Map<UUID, Boolean> successMap,
                               Map<UUID, SendResult.FailReason> failReasonMap,
                               Map<UUID, Double> costMap) {
        int completed = completedCount.incrementAndGet();

        if (completed >= total) {
            // 全部完成，构建真实结果
            BatchSendResult.Builder builder = BatchSendResult.builder().totalCount(total);

            for (SendContext ctx : allContexts) {
                UUID receiverUuid = ctx.getReceiverUuid();
                Boolean success = successMap.get(receiverUuid);

                if (Boolean.TRUE.equals(success)) {
                    builder.addSuccess(receiverUuid, costMap.getOrDefault(receiverUuid, 0.0));
                } else {
                    SendResult.FailReason reason = failReasonMap.getOrDefault(receiverUuid, SendResult.FailReason.DATABASE_ERROR);
                    builder.addFailure(receiverUuid, reason);
                }
            }

            BatchSendResult result = builder.build();

            // 根据结果决定调用哪个方法
            if (result.isAllSuccess()) {
                chain.success(allContexts, null);
            } else if (result.isAllFailed()) {
                chain.fail(SendResult.FailReason.DATABASE_ERROR, "所有邮件发送失败");
            } else {
                // 部分成功 - 使用partialSuccess
                chain.partialSuccess(result, null);
            }
        }
    }

    /**
     * 从上下文构建Mail对象
     */
    private Mail buildMail(SendContext ctx) {
        var draft = ctx.getDraft();

        Mail mail = new Mail(
                ctx.getSenderUuid(),
                ctx.getSenderName(),
                ctx.getReceiverUuid(),
                ctx.getReceiverName(),
                draft.getTitle(),
                draft.getContent()
        );

        mail.setId(ctx.getMailId());
        mail.setSentTime(ctx.getSentTime());

        // 设置附件
        if (draft.hasItemAttachments()) {
            mail.setAttachments(draft.getAttachments());
        }
        if (draft.getMoneyAttachment() > 0) {
            mail.setMoneyAttachment(draft.getMoneyAttachment());
        }

        // 设置过期时间
        if (draft.getExpireTime() > 0) {
            mail.setExpireTime(draft.getExpireTime());
        } else if (plugin.getMailConfig().getMailExpirationDays() > 0) {
            long expireTime = System.currentTimeMillis() +
                    (plugin.getMailConfig().getMailExpirationDays() * 24L * 60 * 60 * 1000);
            mail.setExpireTime(expireTime);
        }

        return mail;
    }

    /**
     * 插入邮件到数据库
     */
    private void insertMail(Connection conn, Mail mail) throws SQLException {
        String sql = "INSERT INTO mails (id, sender_uuid, sender_name, receiver_uuid, receiver_name, " +
                "title, content, attachments, money_attachment, sent_time, expire_time, server_id, is_read, is_claimed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mail.getId().toString());
            ps.setString(2, mail.getSenderUuid().toString());
            ps.setString(3, mail.getSenderName());
            ps.setString(4, mail.getReceiverUuid().toString());
            ps.setString(5, mail.getReceiverName());
            ps.setString(6, mail.getTitle());
            ps.setString(7, mail.getContent());

            // 序列化附件
            byte[] attachData = serializeAttachments(mail.getAttachments());
            if (attachData != null) {
                ps.setBytes(8, attachData);
            } else {
                ps.setNull(8, java.sql.Types.BLOB);
            }

            ps.setDouble(9, mail.getMoneyAttachment());
            ps.setLong(10, mail.getSentTime());
            ps.setLong(11, mail.getExpireTime());
            ps.setString(12, getServerId());
            ps.setBoolean(13, mail.isRead());
            ps.setBoolean(14, mail.isClaimed());

            ps.executeUpdate();
        }
    }

    /**
     * 通知接收者（如果在线）
     */
    private void notifyReceiver(SendContext ctx, Mail mail) {
        var receiver = Bukkit.getPlayer(ctx.getReceiverUuid());
        if (receiver != null && receiver.isOnline()) {
            receiver.sendMessage("§a[邮件系统] §e你收到了一封新邮件！来自: §f" + ctx.getSenderName());
        }
    }

    /**
     * 序列化附件
     */
    private byte[] serializeAttachments(java.util.List<org.bukkit.inventory.ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        // 委托给MailManager的现有方法
        return plugin.getMailManager().serializeAttachmentsInternal(items);
    }

    private String getServerId() {
        String serverId = plugin.getConfig().getString("server.id", "");
        if (serverId.isEmpty()) {
            serverId = Bukkit.getServer().getName();
        }
        return serverId;
    }

    /**
     * 分析数据库错误类型
     */
    private SendResult.FailReason analyzeError(Throwable error) {
        String msg = error.getMessage();
        if (msg == null) {
            return SendResult.FailReason.DATABASE_ERROR;
        }
        String msgLower = msg.toLowerCase();

        // 检查是否是队列超载
        if (msgLower.contains("队列超载") || msgLower.contains("queue overload") || msgLower.contains("超载")) {
            return SendResult.FailReason.QUEUE_OVERLOAD;
        }

        // 检查是否是邮箱满的错误（需要数据库添加相应约束或触发器）
        if (msgLower.contains("mailbox") || msgLower.contains("full") || msgLower.contains("容量") || msgLower.contains("已满")) {
            return SendResult.FailReason.MAILBOX_FULL;
        }

        // 检查是否是唯一约束冲突
        if (msgLower.contains("unique") || msgLower.contains("duplicate") || msgLower.contains("primary key")) {
            return SendResult.FailReason.DATABASE_ERROR;
        }

        return SendResult.FailReason.DATABASE_ERROR;
    }

    /**
     * 获取错误消息
     */
    private String getErrorMessage(SendResult.FailReason reason, SendContext ctx) {
        return switch (reason) {
            case MAILBOX_FULL -> "收件人 " + ctx.getReceiverName() + " 的邮箱已满，无法发送邮件！";
            case DATABASE_ERROR -> "发送失败，请稍后再试！";
            default -> "发送失败：" + reason.getDefaultMessage();
        };
    }
}
