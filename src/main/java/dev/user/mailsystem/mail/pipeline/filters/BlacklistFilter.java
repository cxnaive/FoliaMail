package dev.user.mailsystem.mail.pipeline.filters;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.pipeline.SendChain;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 黑名单过滤器 - 检查发送者是否被接收者屏蔽
 * 统一使用批量逻辑（size==1也是批量的一种）
 */
public class BlacklistFilter implements SendFilter {

    private final MailSystemPlugin plugin;

    public BlacklistFilter(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void filterBatch(List<SendContext> contexts, SendChain chain) {
        // 检查是否需要跳过
        boolean allSkip = contexts.stream().allMatch(SendContext::isSkipBlacklistCheck);
        if (allSkip) {
            chain.next(contexts, null);
            return;
        }

        // 统一批量逻辑（size==1也是批量的一种）
        // 过滤出需要检查的黑名单
        List<SendContext> toCheck = new ArrayList<>();
        for (SendContext ctx : contexts) {
            if (!ctx.isSkipBlacklistCheck() && ctx.getSender() != null) {
                toCheck.add(ctx);
            }
        }

        if (toCheck.isEmpty()) {
            chain.next(contexts, null);
            return;
        }

        // 批量检查黑名单
        AtomicInteger pending = new AtomicInteger(toCheck.size());
        AtomicInteger blockedCount = new AtomicInteger(0);

        for (SendContext ctx : toCheck) {
            plugin.getMailManager().isInBlacklist(
                    ctx.getSenderUuid(),
                    ctx.getReceiverUuid(),
                    isBlocked -> {
                        if (isBlocked) {
                            blockedCount.incrementAndGet();
                            if (ctx.getSender() != null) {
                                String msg = "你已被 " + ctx.getReceiverName() + " 加入黑名单，无法发送邮件！";
                                ctx.getSender().sendMessage("§c[邮件系统] " + msg);
                            }
                        }

                        if (pending.decrementAndGet() == 0) {
                            // 所有检查完成
                            if (blockedCount.get() > 0) {
                                chain.fail(SendResult.FailReason.BLACKLISTED, "你已被部分玩家加入黑名单");
                            } else {
                                chain.next(contexts, null);
                            }
                        }
                    }
            );
        }
    }
}
