package dev.user.mailsystem.api.draft;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 邮件草稿 - 用于构建邮件的不可变DTO
 * 使用Builder模式创建
 */
public final class MailDraft {

    private final UUID senderUuid;
    private final String senderName;
    private final UUID receiverUuid;
    private final String receiverName;
    private final String title;
    private final String content;
    private final List<ItemStack> attachments;
    private final double moneyAttachment;
    private final long expireTime;

    private MailDraft(Builder builder) {
        this.senderUuid = builder.senderUuid;
        this.senderName = builder.senderName;
        this.receiverUuid = builder.receiverUuid;
        this.receiverName = builder.receiverName;
        this.title = builder.title;
        this.content = builder.content;
        this.attachments = Collections.unmodifiableList(new ArrayList<>(builder.attachments));
        this.moneyAttachment = builder.moneyAttachment;
        this.expireTime = builder.expireTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ==================== Getters ====================

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public String getSenderName() {
        return senderName;
    }

    public UUID getReceiverUuid() {
        return receiverUuid;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public List<ItemStack> getAttachments() {
        return attachments;
    }

    public double getMoneyAttachment() {
        return moneyAttachment;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty() || moneyAttachment > 0;
    }

    public boolean hasItemAttachments() {
        return !attachments.isEmpty();
    }

    // ==================== Builder ====================

    public static class Builder {
        // 必填字段
        private UUID senderUuid;
        private String senderName;
        private UUID receiverUuid;
        private String receiverName;

        // 选填字段（有默认值）
        private String title = "";
        private String content = "";
        private List<ItemStack> attachments = new ArrayList<>();
        private double moneyAttachment = 0;
        private long expireTime = 0;

        private Builder() {}

        /**
         * 设置发送者（通过Player对象）
         */
        public Builder sender(Player player) {
            Objects.requireNonNull(player, "player cannot be null");
            this.senderUuid = player.getUniqueId();
            this.senderName = player.getName();
            return this;
        }

        /**
         * 设置发送者
         */
        public Builder sender(UUID uuid, String name) {
            Objects.requireNonNull(uuid, "sender uuid cannot be null");
            this.senderUuid = uuid;
            this.senderName = name != null ? name : "";
            return this;
        }

        /**
         * 设置接收者（通过Player对象）
         */
        public Builder receiver(Player player) {
            Objects.requireNonNull(player, "player cannot be null");
            this.receiverUuid = player.getUniqueId();
            this.receiverName = player.getName();
            return this;
        }

        /**
         * 设置接收者
         */
        public Builder receiver(UUID uuid, String name) {
            Objects.requireNonNull(uuid, "receiver uuid cannot be null");
            this.receiverUuid = uuid;
            this.receiverName = name != null ? name : "";
            return this;
        }

        /**
         * 设置标题
         */
        public Builder title(String title) {
            this.title = title != null ? title : "";
            return this;
        }

        /**
         * 设置内容
         */
        public Builder content(String content) {
            this.content = content != null ? content : "";
            return this;
        }

        /**
         * 设置附件列表（会复制列表）
         */
        public Builder attachments(List<ItemStack> items) {
            this.attachments.clear();
            if (items != null) {
                for (ItemStack item : items) {
                    addAttachment(item);
                }
            }
            return this;
        }

        /**
         * 添加单个附件
         */
        public Builder addAttachment(ItemStack item) {
            if (item != null && !item.getType().isAir()) {
                this.attachments.add(item.clone());
            }
            return this;
        }

        /**
         * 设置金币附件
         */
        public Builder moneyAttachment(double amount) {
            this.moneyAttachment = Math.max(0, amount);
            return this;
        }

        /**
         * 设置过期天数（从当前时间计算）
         */
        public Builder expireDays(int days) {
            if (days > 0) {
                this.expireTime = System.currentTimeMillis() + (days * 24L * 60 * 60 * 1000);
            } else {
                this.expireTime = 0;
            }
            return this;
        }

        /**
         * 设置过期时间戳（毫秒）
         */
        public Builder expireTime(long timestamp) {
            this.expireTime = timestamp > 0 ? timestamp : 0;
            return this;
        }

        /**
         * 构建MailDraft
         * @throws IllegalStateException 如果必填字段未设置
         */
        public MailDraft build() {
            // 验证必填字段
            if (senderUuid == null) {
                throw new IllegalStateException("Sender is required");
            }
            if (receiverUuid == null) {
                throw new IllegalStateException("Receiver is required");
            }
            if (title.isEmpty()) {
                throw new IllegalStateException("Title is required");
            }

            // 截断超长内容（防止数据库错误）
            if (title.length() > 100) {
                this.title = title.substring(0, 100);
            }
            if (content.length() > 5000) {
                this.content = content.substring(0, 5000);
            }

            // 限制附件数量（防止滥用）
            if (attachments.size() > 27) { // 最多27个物品（3行）
                this.attachments = new ArrayList<>(attachments.subList(0, 27));
            }

            return new MailDraft(this);
        }
    }

    @Override
    public String toString() {
        return "MailDraft{" +
                "sender=" + senderName + "(" + senderUuid + "), " +
                "receiver=" + receiverName + "(" + receiverUuid + "), " +
                "title='" + title + "', " +
                "attachments=" + attachments.size() + ", " +
                "money=" + moneyAttachment +
                '}';
    }
}
