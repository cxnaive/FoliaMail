package dev.user.mailsystem.api;

import dev.user.mailsystem.api.draft.BatchSendResult;
import dev.user.mailsystem.api.draft.MailDraft;
import dev.user.mailsystem.api.draft.SendOptions;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.Mail;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * FoliaMail 对外API接口
 *
 * 其他插件可通过以下方式获取实例:
 * <pre>
 * MailSystemAPI api = Bukkit.getServicesManager().load(MailSystemAPI.class);
 * </pre>
 */
public interface MailSystemAPI {

    /**
     * API版本号
     * 版本变更规则:
     * - 主版本: 不兼容的API变更
     * - 次版本: 向下兼容的功能添加
     */
    int API_VERSION = 1;

    /**
     * 获取API版本
     */
    int getApiVersion();

    // ==================== 邮件发送 ====================

    /**
     * 发送邮件（完整选项）
     *
     * @param draft    邮件草稿（必填）
     * @param options  发送选项（null则使用默认）
     * @param sender   发送者（用于权限检查和扣费，可为null）
     * @param callback 结果回调（在主线程执行）
     *
     * @example
     * <pre>
     * MailDraft draft = MailDraft.builder()
     *     .sender(player)
     *     .receiver(targetUuid, targetName)
     *     .title("活动奖励")
     *     .content("恭喜你获得奖励！")
     *     .addAttachment(rewardItem)
     *     .build();
     *
     * api.send(draft, SendOptions.defaults(), player, result -> {
     *     if (result.isSuccess()) {
     *         player.sendMessage("发送成功！");
     *     } else {
     *         player.sendMessage("发送失败: " + result.getMessage());
     *     }
     * });
     * </pre>
     */
    void send(MailDraft draft, SendOptions options, Player sender, Consumer<BatchSendResult> callback);

    /**
     * 发送邮件（简化版，使用默认选项）
     */
    void send(MailDraft draft, Player sender, Consumer<BatchSendResult> callback);

    /**
     * 发送系统邮件（免检查、免扣费）
     *
     * @param receiver    接收者UUID
     * @param receiverName 接收者名称（可为空）
     * @param title       标题
     * @param content     内容
     * @param attachments 附件物品列表（可为null）
     * @param callback    结果回调
     */
    void sendSystemMail(UUID receiver, String receiverName, String title, String content,
                        List<ItemStack> attachments, Consumer<BatchSendResult> callback);

    /**
     * 批量发送邮件
     *
     * @param drafts   邮件草稿列表
     * @param options  发送选项
     * @param sender   发送者（用于权限检查和扣费）
     * @param callback 批量结果回调
     */
    void sendBatch(List<MailDraft> drafts, SendOptions options, Player sender,
                   Consumer<BatchSendResult> callback);

    // ==================== 邮件查询 ====================

    /**
     * 获取玩家未读邮件数量（同步，从缓存读取）
     *
     * @param playerUuid 玩家UUID
     * @return 未读邮件数量
     */
    int getUnreadCount(UUID playerUuid);

    /**
     * 异步获取玩家邮件列表
     *
     * @param playerUuid 玩家UUID
     * @param callback   结果回调
     */
    void getMails(UUID playerUuid, Consumer<List<Mail>> callback);

    /**
     * 根据ID获取邮件
     *
     * @param mailId   邮件ID
     * @param callback 结果回调（邮件不存在则返回null）
     */
    void getMail(UUID mailId, Consumer<Mail> callback);

    // ==================== 邮件操作 ====================

    /**
     * 标记邮件为已读
     *
     * @param mailId 邮件ID
     */
    void markAsRead(UUID mailId);

    /**
     * 领取附件
     *
     * @param mailId   邮件ID
     * @param player   领取者（必须是邮件接收者）
     * @param callback 结果回调
     */
    void claimAttachments(UUID mailId, Player player, Consumer<SendResult> callback);

    /**
     * 删除邮件
     *
     * @param mailId     邮件ID
     * @param playerUuid 操作者UUID（必须是接收者或管理员）
     */
    void deleteMail(UUID mailId, UUID playerUuid);

    // ==================== 系统功能 ====================

    /**
     * 检查玩家是否可以发送邮件（权限检查）
     *
     * @param player 玩家
     * @return 是否有发送权限
     */
    boolean canSendMail(Player player);

    /**
     * 检查玩家是否可以接收邮件
     *
     * @param playerUuid 玩家UUID
     * @param callback   结果回调（返回剩余容量，-1表示无限制）
     */
    void getMailboxRemainingCapacity(UUID playerUuid, Consumer<Integer> callback);

    /**
     * 清空玩家收件箱
     *
     * @param playerUuid 玩家UUID
     * @param callback   回调（参数为删除的邮件数量）
     */
    void clearMailbox(UUID playerUuid, Consumer<Integer> callback);

    // ==================== 事件监听 ====================

    /**
     * 注册邮件监听器
     *
     * @param listener 监听器
     */
    void registerListener(MailListener listener);

    /**
     * 注销邮件监听器
     *
     * @param listener 监听器
     */
    void unregisterListener(MailListener listener);

    // ==================== 事件触发（内部使用） ====================

    /**
     * 触发邮件发送事件
     *
     * @param mail 邮件
     * @param isSystemMail 是否为系统邮件
     */
    void fireMailSendEvent(Mail mail, boolean isSystemMail);

    /**
     * 触发邮件读取事件
     *
     * @param mail 邮件
     * @param reader 阅读者
     */
    void fireMailReadEvent(Mail mail, Player reader);

    /**
     * 触发附件领取事件
     *
     * @param mail 邮件
     * @param player 领取者
     */
    void fireAttachmentClaimEvent(Mail mail, Player player);
}
