package dev.user.mailsystem.api.draft;

import java.util.*;

/**
 * 批量发送邮件结果
 */
public final class BatchSendResult {

    private final int totalCount;
    private final int successCount;
    private final int failCount;
    private final List<UUID> successReceivers;
    private final Map<UUID, SendResult.FailReason> failReasons;
    private final double totalCost;

    private BatchSendResult(Builder builder) {
        this.totalCount = builder.totalCount;
        this.successCount = builder.successCount;
        this.failCount = builder.failCount;
        this.successReceivers = Collections.unmodifiableList(new ArrayList<>(builder.successReceivers));
        this.failReasons = Collections.unmodifiableMap(new HashMap<>(builder.failReasons));
        this.totalCost = builder.totalCost;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ==================== Getters ====================

    public int getTotalCount() {
        return totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public int getFailureCount() {
        return failCount;
    }

    public List<UUID> getSuccessReceivers() {
        return successReceivers;
    }

    public Map<UUID, SendResult.FailReason> getFailReasons() {
        return failReasons;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public boolean isAllSuccess() {
        return successCount == totalCount;
    }

    public boolean isPartialSuccess() {
        return successCount > 0 && successCount < totalCount;
    }

    public boolean isAllFailed() {
        return failCount == totalCount;
    }

    /**
     * 获取指定接收者的失败原因
     */
    public Optional<SendResult.FailReason> getFailReason(UUID receiver) {
        return Optional.ofNullable(failReasons.get(receiver));
    }

    /**
     * 检查指定接收者是否发送成功
     */
    public boolean isSuccess(UUID receiver) {
        return successReceivers.contains(receiver);
    }

    // ==================== Builder ====================

    public static class Builder {
        private int totalCount = 0;
        private int successCount = 0;
        private int failCount = 0;
        private final List<UUID> successReceivers = new ArrayList<>();
        private final Map<UUID, SendResult.FailReason> failReasons = new HashMap<>();
        private double totalCost = 0;

        private Builder() {}

        public Builder totalCount(int count) {
            this.totalCount = count;
            return this;
        }

        public Builder addSuccess(UUID receiver, double cost) {
            this.successCount++;
            this.successReceivers.add(receiver);
            this.totalCost += cost;
            return this;
        }

        public Builder addFailure(UUID receiver, SendResult.FailReason reason) {
            this.failCount++;
            this.failReasons.put(receiver, reason);
            return this;
        }

        public Builder totalCost(double cost) {
            this.totalCost = cost;
            return this;
        }

        public BatchSendResult build() {
            if (totalCount == 0) {
                totalCount = successCount + failCount;
            }
            return new BatchSendResult(this);
        }
    }

    @Override
    public String toString() {
        return "BatchSendResult{" +
                "total=" + totalCount +
                ", success=" + successCount +
                ", fail=" + failCount +
                ", cost=" + totalCost +
                '}';
    }
}
