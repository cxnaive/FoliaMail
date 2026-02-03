package dev.user.mailsystem.mail;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Mail {

    private final UUID id;
    private final UUID senderUuid;
    private String senderName;
    private final UUID receiverUuid;
    private String receiverName;
    private String title;
    private String content;
    private List<ItemStack> attachments;
    private long sentTime;
    private long expireTime;
    private boolean read;
    private boolean claimed;
    private long readTime;
    private String serverId;

    public Mail(UUID senderUuid, String senderName, UUID receiverUuid, String receiverName,
                String title, String content) {
        this.id = UUID.randomUUID();
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.receiverUuid = receiverUuid;
        this.receiverName = receiverName;
        this.title = title;
        this.content = content;
        this.attachments = new ArrayList<>();
        this.sentTime = System.currentTimeMillis();
        this.expireTime = 0;
        this.read = false;
        this.claimed = false;
        this.readTime = 0;
        this.serverId = "";
    }

    public Mail(UUID id, UUID senderUuid, String senderName, UUID receiverUuid, String receiverName,
                String title, String content, long sentTime, long expireTime, boolean read,
                boolean claimed, long readTime, String serverId) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.receiverUuid = receiverUuid;
        this.receiverName = receiverName;
        this.title = title;
        this.content = content;
        this.attachments = new ArrayList<>();
        this.sentTime = sentTime;
        this.expireTime = expireTime;
        this.read = read;
        this.claimed = claimed;
        this.readTime = readTime;
        this.serverId = serverId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public UUID getReceiverUuid() {
        return receiverUuid;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
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

    public void addAttachment(ItemStack item) {
        if (this.attachments == null) {
            this.attachments = new ArrayList<>();
        }
        this.attachments.add(item);
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public long getSentTime() {
        return sentTime;
    }

    public void setSentTime(long sentTime) {
        this.sentTime = sentTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public boolean isExpired() {
        return expireTime > 0 && System.currentTimeMillis() > expireTime;
    }

    public synchronized boolean isRead() {
        return read;
    }

    public synchronized void setRead(boolean read) {
        this.read = read;
    }

    public synchronized void markAsRead() {
        this.read = true;
        this.readTime = System.currentTimeMillis();
    }

    public synchronized boolean isClaimed() {
        return claimed;
    }

    public synchronized void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public long getReadTime() {
        return readTime;
    }

    public void setReadTime(long readTime) {
        this.readTime = readTime;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
