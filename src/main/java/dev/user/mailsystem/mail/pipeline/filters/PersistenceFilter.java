package dev.user.mailsystem.mail.pipeline.filters;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.Mail;
import dev.user.mailsystem.mail.pipeline.SendChain;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendFilter;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
        // size==1时走简单逻辑
        if (contexts.size() == 1) {
            SendContext ctx = contexts.get(0);
            // 构建Mail对象
            Mail mail = buildMail(ctx);

            // 提交到数据库队列
            plugin.getDatabaseQueue().submit("sendMail", conn -> {
                insertMail(conn, mail);
                return null;
            }, result -> {
                // 数据库插入成功
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

                chain.success(contexts, null);
            }, error -> {
                // 数据库错误
                plugin.getLogger().severe("发送邮件失败: " + error.getMessage());

                // 分析错误类型
                SendResult.FailReason reason = analyzeError(error);
                String msg = getErrorMessage(reason, ctx);

                if (ctx.getSender() != null) {
                    ctx.getSender().sendMessage("§c[邮件系统] " + msg);
                }

                // 注意：如果已扣费，这里应该退款，但为简化逻辑先不处理
                chain.fail(reason, msg);
            });
            return;
        }

        // size>1时走批量逻辑
        List<Mail> mails = new ArrayList<>();
        for (SendContext ctx : contexts) {
            mails.add(buildMail(ctx));
        }

        // 批量提交到数据库
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < contexts.size(); i++) {
            SendContext ctx = contexts.get(i);
            Mail mail = mails.get(i);

            plugin.getDatabaseQueue().submit("sendMail", conn -> {
                insertMail(conn, mail);
                return null;
            }, result -> {
                // 数据库插入成功
                successCount.incrementAndGet();

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

                // 检查是否全部完成
                if (successCount.get() + failCount.get() >= contexts.size()) {
                    chain.success(contexts, null);
                }
            }, error -> {
                // 数据库错误
                failCount.incrementAndGet();
                plugin.getLogger().severe("批量发送邮件失败 (" + ctx.getReceiverName() + "): " + error.getMessage());

                // 分析错误类型
                SendResult.FailReason reason = analyzeError(error);
                if (reason == SendResult.FailReason.MAILBOX_FULL && ctx.getSender() != null) {
                    ctx.getSender().sendMessage("§c[邮件系统] 收件人 " + ctx.getReceiverName() + " 的邮箱已满，已跳过");
                }

                // 检查是否全部完成
                if (successCount.get() + failCount.get() >= contexts.size()) {
                    if (failCount.get() == contexts.size()) {
                        chain.fail(SendResult.FailReason.DATABASE_ERROR, "数据库错误");
                    } else {
                        // 部分成功
                        chain.success(contexts, null);
                    }
                }
            });
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
        msg = msg.toLowerCase();

        // 检查是否是邮箱满的错误（需要数据库添加相应约束或触发器）
        if (msg.contains("mailbox") || msg.contains("full") || msg.contains("容量") || msg.contains("已满")) {
            return SendResult.FailReason.MAILBOX_FULL;
        }

        // 检查是否是唯一约束冲突
        if (msg.contains("unique") || msg.contains("duplicate") || msg.contains("primary key")) {
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
