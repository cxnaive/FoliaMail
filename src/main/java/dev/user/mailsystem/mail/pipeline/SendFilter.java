package dev.user.mailsystem.mail.pipeline;

import java.util.List;

/**
 * 发送过滤器接口 - 责任链中的每个检查步骤
 * 统一批量接口：单发 = List.of(draft) 的批量
 */
public interface SendFilter {

    /**
     * 执行批量过滤检查
     *
     * @param contexts 发送上下文列表（size>=1）
     * @param chain 责任链，用于继续或终止
     */
    void filterBatch(List<SendContext> contexts, SendChain chain);

    /**
     * 获取过滤器名称（用于日志）
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 是否异步执行（默认true，大部分检查需要数据库查询）
     */
    default boolean isAsync() {
        return true;
    }
}
