package dev.user.mailsystem.api.draft;

import java.util.Objects;
import java.util.UUID;

/**
 * 邮件发送结果
 * 使用密封类表示成功或失败
 */
public abstract sealed class SendResult {

    /**
     * 发送成功
     */
    public static final class Success extends SendResult {
        private final UUID mailId;
        private final double cost;
        private final long sentTime;

        public Success(UUID mailId, double cost, long sentTime) {
            this.mailId = Objects.requireNonNull(mailId, "mailId cannot be null");
            this.cost = Math.max(0, cost);
            this.sentTime = sentTime;
        }

        public UUID getMailId() {
            return mailId;
        }

        public double getCost() {
            return cost;
        }

        public long getSentTime() {
            return sentTime;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String toString() {
            return "SendResult.Success{mailId=" + mailId + ", cost=" + cost + "}";
        }
    }

    /**
     * 发送失败
     */
    public static final class Failure extends SendResult {
        private final FailReason reason;
        private final String message;

        public Failure(FailReason reason, String message) {
            this.reason = Objects.requireNonNull(reason, "reason cannot be null");
            this.message = message != null ? message : reason.getDefaultMessage();
        }

        public FailReason getReason() {
            return reason;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public String toString() {
            return "SendResult.Failure{reason=" + reason + ", message='" + message + "'}";
        }
    }

    /**
     * 失败原因枚举
     */
    public enum FailReason {
        MAILBOX_FULL("收件人邮箱已满"),
        BLACKLISTED("你已被对方加入黑名单"),
        DAILY_LIMIT_REACHED("今日发送邮件已达上限"),
        INSUFFICIENT_FUNDS("余额不足"),
        RECEIVER_NOT_FOUND("收件人不存在"),
        INVALID_CONTENT("邮件内容无效"),
        DATABASE_ERROR("数据库错误"),
        QUEUE_OVERLOAD("服务器繁忙，请稍后重试"),
        UNKNOWN("发送失败");

        private final String defaultMessage;

        FailReason(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    /**
     * 是否发送成功
     */
    public abstract boolean isSuccess();

    /**
     * 创建成功结果
     */
    public static Success success(UUID mailId, double cost, long sentTime) {
        return new Success(mailId, cost, sentTime);
    }

    /**
     * 创建成功结果（使用当前时间）
     */
    public static Success success(UUID mailId, double cost) {
        return new Success(mailId, cost, System.currentTimeMillis());
    }

    /**
     * 创建失败结果
     */
    public static Failure failure(FailReason reason, String message) {
        return new Failure(reason, message);
    }

    /**
     * 创建失败结果（使用默认消息）
     */
    public static Failure failure(FailReason reason) {
        return new Failure(reason, reason.getDefaultMessage());
    }
}
