package dev.user.mailsystem.mail.pipeline.filters;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.pipeline.SendChain;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 邮箱上限过滤器 - 检查接收者邮箱是否已满
 * 统一使用批量逻辑（size==1也是批量的一种）
 *
 * 注意：存在TOCTOU竞态条件，最终一致性由PersistenceFilter数据库错误处理保证
 */
public class MailboxLimitFilter implements SendFilter {

    private final MailSystemPlugin plugin;

    public MailboxLimitFilter(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void filterBatch(List<SendContext> contexts, SendChain chain) {
        // 检查是否需要跳过
        boolean allSkip = contexts.stream().allMatch(SendContext::isSkipMailboxCheck);
        if (allSkip) {
            chain.next(contexts, null);
            return;
        }

        int maxSize = plugin.getMailConfig().getMaxMailboxSize();
        if (maxSize <= 0) {
            // 无限制
            chain.next(contexts, null);
            return;
        }

        // size==1时走简单逻辑
        if (contexts.size() == 1) {
            SendContext ctx = contexts.get(0);
            if (ctx.isSkipMailboxCheck()) {
                chain.next(contexts, null);
                return;
            }

            // 异步查询邮箱容量
            plugin.getMailManager().getMailCountAsync(ctx.getReceiverUuid(), currentSize -> {
                if (currentSize >= maxSize) {
                    // 邮箱已满
                    String msg = "收件人 " + ctx.getReceiverName() + " 的邮箱已满 (" + currentSize + "/" + maxSize + ")";
                    if (ctx.getSender() != null) {
                        ctx.getSender().sendMessage("§c[邮件系统] " + msg + "，无法发送邮件！");
                    }
                    chain.fail(SendResult.FailReason.MAILBOX_FULL, msg);
                } else {
                    // 未满，继续
                    chain.next(contexts, null);
                }
            });
            return;
        }

        // size>1时走批量逻辑
        // 收集需要检查的接收者（去重）
        Map<UUID, SendContext> uniqueReceivers = new HashMap<>();
        for (SendContext ctx : contexts) {
            if (!ctx.isSkipMailboxCheck()) {
                uniqueReceivers.put(ctx.getReceiverUuid(), ctx);
            }
        }

        if (uniqueReceivers.isEmpty()) {
            chain.next(contexts, null);
            return;
        }

        // 批量查询每个接收者的邮箱容量
        Map<UUID, Integer> receiverSizes = new HashMap<>();
        AtomicInteger pending = new AtomicInteger(uniqueReceivers.size());

        for (UUID receiverUuid : uniqueReceivers.keySet()) {
            plugin.getMailManager().getMailCountAsync(receiverUuid, currentSize -> {
                receiverSizes.put(receiverUuid, currentSize);

                if (pending.decrementAndGet() == 0) {
                    // 所有查询完成，检查容量
                    List<SendContext> passed = new ArrayList<>();
                    for (SendContext ctx : contexts) {
                        if (ctx.isSkipMailboxCheck()) {
                            passed.add(ctx);
                            continue;
                        }

                        int size = receiverSizes.getOrDefault(ctx.getReceiverUuid(), 0);
                        if (size >= maxSize) {
                            // 邮箱已满
                            String msg = "收件人 " + ctx.getReceiverName() + " 的邮箱已满 (" + size + "/" + maxSize + ")";
                            if (ctx.getSender() != null) {
                                ctx.getSender().sendMessage("§c[邮件系统] " + msg + "，无法发送邮件！");
                            }
                            // 批量模式下，一个失败就全部失败（简化处理）
                            chain.fail(SendResult.FailReason.MAILBOX_FULL, msg);
                            return;
                        }
                        passed.add(ctx);
                    }
                    // 全部通过
                    chain.next(passed, null);
                }
            });
        }
    }
}
