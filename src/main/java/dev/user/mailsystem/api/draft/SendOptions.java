package dev.user.mailsystem.api.draft;

/**
 * 发送选项 - 控制邮件发送行为的策略配置
 */
public final class SendOptions {

    // 权限覆盖
    private final boolean bypassMailboxLimit;   // 无视邮箱上限
    private final boolean bypassBlacklist;      // 无视黑名单
    private final boolean bypassDailyLimit;     // 无视每日发送限制

    // 费用策略
    private final double customCost;            // 自定义费用（负数表示自动计算）
    private final boolean chargeSender;         // 是否向发送者扣费

    // 行为策略
    private final boolean notifyReceiver;       // 是否通知接收者
    private final boolean clearCache;           // 发送后是否清除缓存
    private final boolean verifyReceiver;       // 是否验证接收者存在

    private SendOptions(Builder builder) {
        this.bypassMailboxLimit = builder.bypassMailboxLimit;
        this.bypassBlacklist = builder.bypassBlacklist;
        this.bypassDailyLimit = builder.bypassDailyLimit;
        this.customCost = builder.customCost;
        this.chargeSender = builder.chargeSender;
        this.notifyReceiver = builder.notifyReceiver;
        this.clearCache = builder.clearCache;
        this.verifyReceiver = builder.verifyReceiver;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 默认选项（普通玩家发送）
     */
    public static SendOptions defaults() {
        return new Builder().build();
    }

    /**
     * 系统邮件选项（免检查、免扣费）
     */
    public static SendOptions systemMail() {
        return new Builder()
                .bypassMailboxLimit(true)
                .bypassBlacklist(true)
                .bypassDailyLimit(true)
                .chargeSender(false)
                .verifyReceiver(false)
                .build();
    }

    /**
     * 管理员发送选项（免限制但仍扣费）
     */
    public static SendOptions admin() {
        return new Builder()
                .bypassMailboxLimit(true)
                .bypassBlacklist(true)
                .bypassDailyLimit(true)
                .build();
    }

    /**
     * 静默发送（不通知接收者）
     */
    public static SendOptions silent() {
        return new Builder()
                .notifyReceiver(false)
                .build();
    }

    // ==================== Getters ====================

    public boolean isBypassMailboxLimit() {
        return bypassMailboxLimit;
    }

    public boolean isBypassBlacklist() {
        return bypassBlacklist;
    }

    public boolean isBypassDailyLimit() {
        return bypassDailyLimit;
    }

    public double getCustomCost() {
        return customCost;
    }

    public boolean hasCustomCost() {
        return customCost >= 0;
    }

    public boolean isChargeSender() {
        return chargeSender;
    }

    public boolean isNotifyReceiver() {
        return notifyReceiver;
    }

    public boolean isClearCache() {
        return clearCache;
    }

    public boolean isVerifyReceiver() {
        return verifyReceiver;
    }

    // ==================== Builder ====================

    public static class Builder {
        private boolean bypassMailboxLimit = false;
        private boolean bypassBlacklist = false;
        private boolean bypassDailyLimit = false;
        private double customCost = -1;
        private boolean chargeSender = true;
        private boolean notifyReceiver = true;
        private boolean clearCache = true;
        private boolean verifyReceiver = true;

        private Builder() {}

        /**
         * 是否无视邮箱容量上限
         */
        public Builder bypassMailboxLimit(boolean value) {
            this.bypassMailboxLimit = value;
            return this;
        }

        /**
         * 是否无视黑名单检查
         */
        public Builder bypassBlacklist(boolean value) {
            this.bypassBlacklist = value;
            return this;
        }

        /**
         * 是否无视每日发送限制
         */
        public Builder bypassDailyLimit(boolean value) {
            this.bypassDailyLimit = value;
            return this;
        }

        /**
         * 设置自定义费用（负数表示自动计算）
         */
        public Builder customCost(double cost) {
            this.customCost = cost;
            return this;
        }

        /**
         * 设置自定义费用（简写，负数表示自动计算）
         */
        public Builder cost(double cost) {
            this.customCost = cost;
            return this;
        }

        /**
         * 是否向发送者扣费
         */
        public Builder chargeSender(boolean value) {
            this.chargeSender = value;
            return this;
        }

        /**
         * 是否通知接收者（在线时）
         */
        public Builder notifyReceiver(boolean value) {
            this.notifyReceiver = value;
            return this;
        }

        /**
         * 发送后是否清除接收者缓存
         */
        public Builder clearCache(boolean value) {
            this.clearCache = value;
            return this;
        }

        /**
         * 发送前是否验证接收者存在
         */
        public Builder verifyReceiver(boolean value) {
            this.verifyReceiver = value;
            return this;
        }

        public SendOptions build() {
            return new SendOptions(this);
        }
    }

    @Override
    public String toString() {
        return "SendOptions{" +
                "bypassLimit=" + bypassMailboxLimit +
                ", bypassBlacklist=" + bypassBlacklist +
                ", bypassDailyLimit=" + bypassDailyLimit +
                ", customCost=" + customCost +
                ", chargeSender=" + chargeSender +
                ", notify=" + notifyReceiver +
                '}';
    }
}
