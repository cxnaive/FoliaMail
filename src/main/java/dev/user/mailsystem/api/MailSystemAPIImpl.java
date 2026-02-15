package dev.user.mailsystem.api;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.BatchSendResult;
import dev.user.mailsystem.api.draft.MailDraft;
import dev.user.mailsystem.api.draft.SendOptions;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.Mail;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * MailSystemAPI 的实现类
 */
public class MailSystemAPIImpl implements MailSystemAPI {

    private final MailSystemPlugin plugin;
    private final List<MailListener> listeners = new CopyOnWriteArrayList<>();

    public MailSystemAPIImpl(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public void send(MailDraft draft, SendOptions options, Player sender, Consumer<BatchSendResult> callback) {
        plugin.getMailManager().send(List.of(draft), options, sender, callback);
    }

    @Override
    public void send(MailDraft draft, Player sender, Consumer<BatchSendResult> callback) {
        send(draft, SendOptions.defaults(), sender, callback);
    }

    @Override
    public void sendSystemMail(UUID receiver, String receiverName, String title, String content,
                               List<ItemStack> attachments, Consumer<BatchSendResult> callback) {
        plugin.getMailManager().sendSystemMail(receiver, receiverName, title, content, attachments, callback);
    }

    @Override
    public void sendBatch(List<MailDraft> drafts, SendOptions options, Player sender,
                          Consumer<BatchSendResult> callback) {
        plugin.getMailManager().send(drafts, options, sender, callback);
    }

    @Override
    public void getUnreadCount(UUID playerUuid, Consumer<Integer> callback) {
        plugin.getMailManager().getUnreadCount(playerUuid, callback);
    }

    @Override
    public void getMails(UUID playerUuid, Consumer<List<Mail>> callback) {
        plugin.getMailManager().loadPlayerMails(playerUuid, callback);
    }

    @Override
    public void getMail(UUID mailId, Consumer<Mail> callback) {
        plugin.getMailManager().getMail(mailId, callback);
    }

    @Override
    public void markAsRead(UUID mailId) {
        plugin.getMailManager().markAsRead(mailId);
    }

    @Override
    public void claimAttachments(UUID mailId, Player player, Consumer<SendResult> callback) {
        // 先获取邮件验证权限
        plugin.getMailManager().getMail(mailId, mail -> {
            if (callback == null) {
                // callback 为 null 时静默处理
                return;
            }

            if (mail == null) {
                callback.accept(SendResult.failure(SendResult.FailReason.RECEIVER_NOT_FOUND, "邮件不存在"));
                return;
            }

            if (!mail.getReceiverUuid().equals(player.getUniqueId())) {
                callback.accept(SendResult.failure(SendResult.FailReason.UNKNOWN, "无权领取此邮件"));
                return;
            }

            plugin.getMailManager().claimAttachments(mailId, player);
            callback.accept(SendResult.success(mailId, 0));
        });
    }

    @Override
    public void deleteMail(UUID mailId, UUID playerUuid) {
        // 获取邮件验证权限
        plugin.getMailManager().getMail(mailId, mail -> {
            if (mail == null) return;

            boolean isReceiver = mail.getReceiverUuid().equals(playerUuid);
            boolean isAdmin = isAdmin(playerUuid);

            if (!isReceiver && !isAdmin) {
                return; // 无权删除
            }

            if (isAdmin) {
                plugin.getMailManager().deleteMailById(mailId);
            } else {
                plugin.getMailManager().deleteMail(mailId, playerUuid);
            }

            // 触发事件
            notifyListeners(listener -> listener.onMailDelete(
                    new MailListener.MailDeleteEvent(mailId, playerUuid, isAdmin)
            ));
        });
    }

    @Override
    public boolean canSendMail(Player player) {
        return player.hasPermission("mailsystem.send");
    }

    @Override
    public void getMailboxRemainingCapacity(UUID playerUuid, Consumer<Integer> callback) {
        int maxSize = plugin.getMailConfig().getMaxMailboxSize();
        if (maxSize <= 0) {
            callback.accept(-1); // 无限制
            return;
        }

        plugin.getMailManager().getMailCountAsync(playerUuid, count -> {
            int remaining = maxSize - count;
            callback.accept(Math.max(0, remaining));
        });
    }

    @Override
    public void clearMailbox(UUID playerUuid, Consumer<Integer> callback) {
        plugin.getMailManager().clearInbox(playerUuid, callback);
    }

    @Override
    public void registerListener(MailListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void unregisterListener(MailListener listener) {
        listeners.remove(listener);
    }

    // ==================== 内部方法 ====================

    private boolean isAdmin(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            return player.hasPermission("mailsystem.admin");
        }
        return false;
    }

    /**
     * 通知所有监听器
     */
    private void notifyListeners(Consumer<MailListener> action) {
        for (MailListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                plugin.getLogger().warning("邮件监听器执行出错: " + e.getMessage());
            }
        }
    }

    @Override
    public void fireMailSendEvent(Mail mail, boolean isCrossServer) {
        notifyListeners(listener -> listener.onMailSend(
                new MailListener.MailSendEvent(mail, isCrossServer, null)
        ));
    }

    @Override
    public void fireMailReadEvent(Mail mail, Player reader) {
        notifyListeners(listener -> listener.onMailRead(
                new MailListener.MailReadEvent(mail, reader, System.currentTimeMillis())
        ));
    }

    @Override
    public void fireAttachmentClaimEvent(Mail mail, Player claimer) {
        notifyListeners(listener -> listener.onAttachmentClaim(
                new MailListener.AttachmentClaimEvent(mail, claimer, System.currentTimeMillis())
        ));
    }
}
