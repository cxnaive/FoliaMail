package dev.user.mailsystem.mail.pipeline;

import dev.user.mailsystem.api.draft.MailDraft;
import dev.user.mailsystem.api.draft.SendOptions;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 邮件发送上下文 - 在责任链中传递的共享数据
 */
public class SendContext {

    private final MailDraft draft;
    private final SendOptions options;
    private final Player sender;
    private final boolean batchMode;

    // 链内计算/生成的数据
    private double calculatedCost;
    private UUID mailId;
    private long sentTime;
    private boolean skipMailboxCheck;
    private boolean skipBlacklistCheck;
    private boolean skipDailyLimitCheck;
    private boolean skipPayment;

    public SendContext(MailDraft draft, SendOptions options, Player sender) {
        this(draft, options, sender, false);
    }

    public SendContext(MailDraft draft, SendOptions options, Player sender, boolean batchMode) {
        this.draft = draft;
        this.options = options != null ? options : dev.user.mailsystem.api.draft.SendOptions.defaults();
        this.sender = sender;
        this.batchMode = batchMode;
        this.mailId = UUID.randomUUID();
        this.sentTime = System.currentTimeMillis();

        // 从options初始化跳过标志
        this.skipMailboxCheck = this.options.isBypassMailboxLimit();
        this.skipBlacklistCheck = this.options.isBypassBlacklist();
        this.skipDailyLimitCheck = this.options.isBypassDailyLimit();
        this.skipPayment = !this.options.isChargeSender();
    }

    // ==================== 输入数据 Getters ====================

    public MailDraft getDraft() {
        return draft;
    }

    public SendOptions getOptions() {
        return options;
    }

    public Player getSender() {
        return sender;
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public boolean isAdmin() {
        return sender != null && sender.hasPermission("mailsystem.admin");
    }

    // ==================== 链内数据 Getters/Setters ====================

    public double getCalculatedCost() {
        return calculatedCost;
    }

    public void setCalculatedCost(double cost) {
        this.calculatedCost = cost;
    }

    public UUID getMailId() {
        return mailId;
    }

    public void setMailId(UUID mailId) {
        this.mailId = mailId;
    }

    public long getSentTime() {
        return sentTime;
    }

    public void setSentTime(long sentTime) {
        this.sentTime = sentTime;
    }

    // ==================== 跳过检查标志 ====================

    public boolean isSkipMailboxCheck() {
        return skipMailboxCheck || isAdmin();
    }

    public void setSkipMailboxCheck(boolean skip) {
        this.skipMailboxCheck = skip;
    }

    public boolean isSkipBlacklistCheck() {
        return skipBlacklistCheck || isAdmin();
    }

    public void setSkipBlacklistCheck(boolean skip) {
        this.skipBlacklistCheck = skip;
    }

    public boolean isSkipDailyLimitCheck() {
        return skipDailyLimitCheck || isAdmin();
    }

    public void setSkipDailyLimitCheck(boolean skip) {
        this.skipDailyLimitCheck = skip;
    }

    public boolean isSkipPayment() {
        return skipPayment;
    }

    public void setSkipPayment(boolean skip) {
        this.skipPayment = skip;
    }

    // ==================== 便捷方法 ====================

    public UUID getReceiverUuid() {
        return draft.getReceiverUuid();
    }

    public String getReceiverName() {
        return draft.getReceiverName();
    }

    public UUID getSenderUuid() {
        return draft.getSenderUuid();
    }

    public String getSenderName() {
        return draft.getSenderName();
    }

    @Override
    public String toString() {
        return "SendContext{" +
                "sender=" + (sender != null ? sender.getName() : "system") +
                ", receiver=" + getReceiverName() +
                ", title='" + draft.getTitle() + "'" +
                ", cost=" + calculatedCost +
                '}';
    }
}
