package dev.user.mailsystem.template;

import dev.user.mailsystem.api.draft.MailDraft;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 邮件模板数据模型
 */
public class MailTemplate {

    private int id;
    private String name;              // 英文标识（唯一）
    private String displayName;       // 显示名称（可选）

    private String title;             // 标题（支持变量）
    private String content;           // 内容（支持变量）
    private List<ItemStack> attachments;      // 物品附件
    private double moneyAttachment;           // 金币附件

    private UUID creatorUuid;         // 创建者
    private String creatorName;
    private long createdAt;
    private long updatedAt;
    private int useCount;             // 使用次数

    public MailTemplate() {
    }

    public MailTemplate(String name, String displayName, String title, String content,
                        List<ItemStack> attachments, double moneyAttachment,
                        UUID creatorUuid, String creatorName) {
        this.name = name;
        this.displayName = displayName;
        this.title = title;
        this.content = content;
        this.attachments = attachments;
        this.moneyAttachment = moneyAttachment;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.useCount = 0;
    }

    /**
     * 变量替换（支持离线玩家）
     *
     * @param text         原文本
     * @param sender       发送者
     * @param receiverName 接收者名字
     * @param serverName   服务器名称
     * @return 替换后的文本
     */
    public static String replaceVariables(String text, Player sender, String receiverName, String serverName) {
        if (text == null) return "";

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();

        return text
                .replace("{sender}", sender != null ? sender.getName() : "系统")
                .replace("{receiver}", receiverName != null && !receiverName.isEmpty() ? receiverName : "玩家")
                .replace("{time}", timeFormat.format(now))
                .replace("{date}", dateFormat.format(now))
                .replace("{server}", serverName != null ? serverName : "服务器");
    }

    /**
     * 生成邮件草稿（在线玩家）
     *
     * @param sender     发送者
     * @param receiver   接收者（在线玩家）
     * @param serverName 服务器名称
     * @return MailDraft
     */
    public MailDraft toDraft(Player sender, Player receiver, String serverName) {
        return toDraft(sender, receiver.getUniqueId(), receiver.getName(), serverName);
    }

    /**
     * 生成邮件草稿（支持离线玩家）
     *
     * @param sender       发送者
     * @param receiverUuid 接收者UUID
     * @param receiverName 接收者名字
     * @param serverName   服务器名称
     * @return MailDraft
     */
    public MailDraft toDraft(Player sender, UUID receiverUuid, String receiverName, String serverName) {
        String finalTitle = replaceVariables(title, sender, receiverName, serverName);
        String finalContent = replaceVariables(content, sender, receiverName, serverName);

        return MailDraft.builder()
                .sender(sender != null ? sender.getUniqueId() : new UUID(0, 0),
                        sender != null ? sender.getName() : "系统")
                .receiver(receiverUuid, receiverName)
                .title(finalTitle)
                .content(finalContent)
                .attachments(attachments)
                .moneyAttachment(moneyAttachment)
                .build();
    }

    /**
     * 获取用于显示的名称（displayName 或 name）
     */
    public String getDisplayOrName() {
        return displayName != null && !displayName.isEmpty() ? displayName : name;
    }

    // ==================== Getters & Setters ====================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ItemStack> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ItemStack> attachments) {
        this.attachments = attachments;
    }

    public double getMoneyAttachment() {
        return moneyAttachment;
    }

    public void setMoneyAttachment(double moneyAttachment) {
        this.moneyAttachment = moneyAttachment;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public void setCreatorUuid(UUID creatorUuid) {
        this.creatorUuid = creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getUseCount() {
        return useCount;
    }

    public void setUseCount(int useCount) {
        this.useCount = useCount;
    }
}
