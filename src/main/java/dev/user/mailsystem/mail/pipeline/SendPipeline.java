package dev.user.mailsystem.mail.pipeline;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.BatchSendResult;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.pipeline.filters.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * 邮件发送责任链 - 组装并执行所有过滤器（统一批量模式）
 * 单发 = List.of(context) 的批量
 */
public class SendPipeline {

    private final MailSystemPlugin plugin;
    private final List<SendFilter> filters;

    public SendPipeline(MailSystemPlugin plugin) {
        this.plugin = plugin;
        // 按顺序组装过滤器
        this.filters = List.of(
            new ValidationFilter(plugin),      // 1. 内容验证
            new MailboxLimitFilter(plugin),    // 2. 邮箱上限
            new DailyLimitFilter(plugin),      // 3. 日发送限制
            new BlacklistFilter(plugin),       // 4. 黑名单检查
            new EconomyFilter(plugin),         // 5. 扣费
            new PersistenceFilter(plugin)      // 6. 持久化到数据库
        );
    }

    /**
     * 执行批量发送流程（统一入口）
     *
     * @param contexts 发送上下文列表
     * @param callback 结果回调
     */
    public void execute(List<SendContext> contexts, Consumer<BatchSendResult> callback) {
        if (contexts.isEmpty()) {
            callback.accept(BatchSendResult.builder().totalCount(0).build());
            return;
        }

        if (filters.isEmpty()) {
            // 无过滤器，直接成功
            BatchSendResult.Builder builder = BatchSendResult.builder().totalCount(contexts.size());
            for (SendContext ctx : contexts) {
                builder.addSuccess(ctx.getReceiverUuid(), ctx.getCalculatedCost());
            }
            callback.accept(builder.build());
            return;
        }

        // 创建链并执行
        SendChainImpl chain = new SendChainImpl(filters.iterator(), callback, contexts.size());
        chain.next(contexts, callback);
    }

    /**
     * 执行单发（转换为批量）
     */
    public void execute(SendContext ctx, Consumer<SendResult> callback) {
        execute(List.of(ctx), batchResult -> {
            // 将批量结果转换为单发结果
            if (batchResult.isAllSuccess()) {
                callback.accept(SendResult.success(ctx.getMailId(), ctx.getCalculatedCost(), ctx.getSentTime()));
            } else if (batchResult.isAllFailed() && !batchResult.getFailReasons().isEmpty()) {
                SendResult.FailReason reason = batchResult.getFailReasons().get(ctx.getReceiverUuid());
                if (reason == null) {
                    reason = SendResult.FailReason.UNKNOWN;
                }
                callback.accept(SendResult.failure(reason));
            } else {
                // 部分成功情况，检查当前是否成功
                if (batchResult.isSuccess(ctx.getReceiverUuid())) {
                    callback.accept(SendResult.success(ctx.getMailId(), ctx.getCalculatedCost(), ctx.getSentTime()));
                } else {
                    SendResult.FailReason reason = batchResult.getFailReasons().getOrDefault(
                            ctx.getReceiverUuid(), SendResult.FailReason.UNKNOWN);
                    callback.accept(SendResult.failure(reason));
                }
            }
        });
    }

    /**
     * 责任链实现类 - 批量模式
     */
    private class SendChainImpl implements SendChain {

        private final Iterator<SendFilter> iterator;
        private final Consumer<BatchSendResult> finalCallback;
        private final int totalCount;
        private final BatchSendResult.Builder resultBuilder;

        public SendChainImpl(Iterator<SendFilter> iterator, Consumer<BatchSendResult> finalCallback, int totalCount) {
            this.iterator = iterator;
            this.finalCallback = finalCallback;
            this.totalCount = totalCount;
            this.resultBuilder = BatchSendResult.builder().totalCount(totalCount);
        }

        @Override
        public void next(List<SendContext> contexts, Consumer<BatchSendResult> callback) {
            if (iterator.hasNext()) {
                SendFilter filter = iterator.next();
                try {
                    filter.filterBatch(contexts, this);
                } catch (Exception e) {
                    plugin.getLogger().warning("过滤器 " + filter.getName() + " 执行出错: " + e.getMessage());
                    e.printStackTrace();
                    fail(SendResult.FailReason.UNKNOWN, "发送过程中发生错误");
                }
            } else {
                // 所有过滤器通过，返回成功
                success(contexts, callback);
            }
        }

        @Override
        public void fail(SendResult.FailReason reason, String message) {
            // 全部失败
            finalCallback.accept(BatchSendResult.builder()
                    .totalCount(totalCount)
                    .build());
        }

        @Override
        public void success(List<SendContext> contexts, Consumer<BatchSendResult> callback) {
            // 全部成功，构建结果
            BatchSendResult.Builder builder = BatchSendResult.builder().totalCount(totalCount);
            for (SendContext ctx : contexts) {
                builder.addSuccess(ctx.getReceiverUuid(), ctx.getCalculatedCost());
            }
            finalCallback.accept(builder.build());
        }

        @Override
        public void partialSuccess(BatchSendResult result, Consumer<BatchSendResult> callback) {
            finalCallback.accept(result);
        }
    }
}
