package dev.user.mailsystem.mail.pipeline.filters;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.pipeline.SendChain;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendFilter;

import java.util.List;
import java.util.UUID;

/**
 * 每日发送限制过滤器 - 检查发送者是否达到日发送上限
 * 统一使用批量验证逻辑（size==1也是批量的一种）
 */
public class DailyLimitFilter implements SendFilter {

    private final MailSystemPlugin plugin;

    public DailyLimitFilter(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void filterBatch(List<SendContext> contexts, SendChain chain) {
        // 检查是否需要跳过
        boolean allSkip = contexts.stream().allMatch(SendContext::isSkipDailyLimitCheck);
        if (allSkip) {
            chain.next(contexts, null);
            return;
        }

        int dailyLimit = plugin.getMailConfig().getDailySendLimit();
        if (dailyLimit <= 0) {
            // 无限制
            chain.next(contexts, null);
            return;
        }

        // 获取发送者（批量模式下所有context应该有相同的发送者）
        UUID senderUuid = null;
        for (SendContext ctx : contexts) {
            if (!ctx.isSkipDailyLimitCheck() && ctx.getSender() != null) {
                senderUuid = ctx.getSenderUuid();
                break;
            }
        }

        // 检查发送者是否存在
        if (senderUuid == null) {
            // 系统发送，不限制
            chain.next(contexts, null);
            return;
        }

        // 统一批量逻辑（size==1也是批量的一种）
        plugin.getMailManager().getTodaySendCountAsync(senderUuid, todayCount -> {
            if (todayCount >= dailyLimit) {
                // 已达上限
                for (SendContext ctx : contexts) {
                    if (!ctx.isSkipDailyLimitCheck() && ctx.getSender() != null) {
                        String msg = "你今日发送邮件已达上限 (" + dailyLimit + "封)，请明天再试！";
                        ctx.getSender().sendMessage("§c[邮件系统] " + msg);
                        break;
                    }
                }
                chain.fail(SendResult.FailReason.DAILY_LIMIT_REACHED, "今日发送邮件已达上限");
            } else {
                // 未达上限，继续
                chain.next(contexts, null);
            }
        });
    }
}
