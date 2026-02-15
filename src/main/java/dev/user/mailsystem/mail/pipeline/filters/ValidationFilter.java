package dev.user.mailsystem.mail.pipeline.filters;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.pipeline.SendChain;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendFilter;

import java.util.List;

/**
 * 内容验证过滤器 - 检查邮件内容有效性
 * 统一使用批量验证逻辑（size==1也是批量的一种）
 */
public class ValidationFilter implements SendFilter {

    private final MailSystemPlugin plugin;

    public ValidationFilter(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void filterBatch(List<SendContext> contexts, SendChain chain) {
        // 统一批量验证逻辑（size==1也是批量的一种）
        for (SendContext ctx : contexts) {
            var draft = ctx.getDraft();

            // 检查标题
            if (draft.getTitle().isEmpty()) {
                chain.fail(SendResult.FailReason.INVALID_CONTENT, "邮件标题不能为空");
                return;
            }

            int maxTitleLen = plugin.getMailConfig().getMaxMailTitleLength();
            if (draft.getTitle().length() > maxTitleLen) {
                chain.fail(SendResult.FailReason.INVALID_CONTENT, "标题过长，最大长度: " + maxTitleLen);
                return;
            }

            // 检查内容长度
            int maxContentLen = plugin.getMailConfig().getMaxMailContentLength();
            if (draft.getContent().length() > maxContentLen) {
                chain.fail(SendResult.FailReason.INVALID_CONTENT, "内容过长，最大长度: " + maxContentLen);
                return;
            }

            // 检查附件数量
            int maxAttach = plugin.getMailConfig().getMaxAttachments();
            if (draft.hasItemAttachments() && draft.getAttachments().size() > maxAttach) {
                chain.fail(SendResult.FailReason.INVALID_CONTENT, "附件过多，最多: " + maxAttach);
                return;
            }
        }

        // 全部验证通过
        chain.next(contexts, null);
    }
}
