package dev.user.mailsystem.api;

import dev.user.mailsystem.mail.Mail;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 邮件事件监听器
 *
 * 其他插件可通过实现此接口来监听邮件相关事件
 */
public interface MailListener {

    /**
     * 邮件发送成功时触发
     *
     * @param event 事件信息
     */
    default void onMailSend(MailSendEvent event) {}

    /**
     * 邮件被阅读时触发
     *
     * @param event 事件信息
     */
    default void onMailRead(MailReadEvent event) {}

    /**
     * 附件被领取时触发
     *
     * @param event 事件信息
     */
    default void onAttachmentClaim(AttachmentClaimEvent event) {}

    /**
     * 邮件被删除时触发
     *
     * @param event 事件信息
     */
    default void onMailDelete(MailDeleteEvent event) {}

    // ==================== 事件类 ====================

    /**
     * 邮件发送事件
     */
    class MailSendEvent {
        private final Mail mail;
        private final boolean isCrossServer;
        private final UUID serverId;

        public MailSendEvent(Mail mail, boolean isCrossServer, UUID serverId) {
            this.mail = mail;
            this.isCrossServer = isCrossServer;
            this.serverId = serverId;
        }

        public Mail getMail() {
            return mail;
        }

        public boolean isCrossServer() {
            return isCrossServer;
        }

        public UUID getServerId() {
            return serverId;
        }
    }

    /**
     * 邮件阅读事件
     */
    class MailReadEvent {
        private final Mail mail;
        private final Player reader;
        private final long readTime;

        public MailReadEvent(Mail mail, Player reader, long readTime) {
            this.mail = mail;
            this.reader = reader;
            this.readTime = readTime;
        }

        public Mail getMail() {
            return mail;
        }

        public Player getReader() {
            return reader;
        }

        public long getReadTime() {
            return readTime;
        }
    }

    /**
     * 附件领取事件
     */
    class AttachmentClaimEvent {
        private final Mail mail;
        private final Player claimer;
        private final long claimTime;

        public AttachmentClaimEvent(Mail mail, Player claimer, long claimTime) {
            this.mail = mail;
            this.claimer = claimer;
            this.claimTime = claimTime;
        }

        public Mail getMail() {
            return mail;
        }

        public Player getClaimer() {
            return claimer;
        }

        public long getClaimTime() {
            return claimTime;
        }
    }

    /**
     * 邮件删除事件
     */
    class MailDeleteEvent {
        private final UUID mailId;
        private final UUID deleterUuid;
        private final boolean isAdmin;

        public MailDeleteEvent(UUID mailId, UUID deleterUuid, boolean isAdmin) {
            this.mailId = mailId;
            this.deleterUuid = deleterUuid;
            this.isAdmin = isAdmin;
        }

        public UUID getMailId() {
            return mailId;
        }

        public UUID getDeleterUuid() {
            return deleterUuid;
        }

        public boolean isAdmin() {
            return isAdmin;
        }
    }
}
