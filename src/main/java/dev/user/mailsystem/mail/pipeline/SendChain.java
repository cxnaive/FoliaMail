package dev.user.mailsystem.mail.pipeline;

import dev.user.mailsystem.api.draft.BatchSendResult;

import java.util.List;
import java.util.function.Consumer;

/**
 * 责任链接口 - 批量模式
 */
public interface SendChain {

    /**
     * 继续执行下一个过滤器
     *
     * @param contexts 上下文列表
     * @param callback 最终回调
     */
    void next(List<SendContext> contexts, Consumer<BatchSendResult> callback);

    /**
     * 终止链并返回失败结果
     *
     * @param reason 失败原因
     * @param message 错误消息
     */
    void fail(dev.user.mailsystem.api.draft.SendResult.FailReason reason, String message);

    /**
     * 终止链并返回失败结果（使用默认消息）
     *
     * @param reason 失败原因
     */
    default void fail(dev.user.mailsystem.api.draft.SendResult.FailReason reason) {
        fail(reason, reason.getDefaultMessage());
    }

    /**
     * 成功完成
     *
     * @param contexts 上下文列表（包含最终结果）
     * @param callback 回调
     */
    void success(List<SendContext> contexts, Consumer<BatchSendResult> callback);

    /**
     * 部分成功（批量模式下某些成功某些失败）
     *
     * @param result 批量结果
     * @param callback 回调
     */
    void partialSuccess(BatchSendResult result, Consumer<BatchSendResult> callback);
}
