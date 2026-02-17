package dev.user.mailsystem.listener;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.gui.*;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.function.Consumer;

public class MailListener implements Listener, Consumer<ScheduledTask> {

    private final MailSystemPlugin plugin;

    public MailListener(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 更新玩家缓存
        plugin.getPlayerCacheManager().updatePlayerCache(player.getUniqueId(), player.getName());

        int delay = 20 * 3;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getMailManager().getUnreadCount(player.getUniqueId(), unreadCount -> {
                if (unreadCount > 0) {
                    player.sendMessage("§a[邮件系统] §e欢迎回来！你有 §c" + unreadCount + " §e封未读邮件");
                    player.sendMessage("§e使用 §f/fmail §e打开邮件系统GUI");
                }
            });
        }, delay);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 清理邮件缓存
        plugin.getMailManager().clearPlayerCache(player.getUniqueId());
        // 清理GUI数据和聊天监听器
        if (plugin.getGuiManager() != null) {
            // 注销聊天监听器，防止内存泄漏
            plugin.getGuiManager().unregisterChatListener(player.getUniqueId());
            plugin.getGuiManager().closeGUI(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return;
        }

        // 检查是否是邮件系统的GUI
        boolean handled = false;

        if (holder instanceof MainMenuGUI gui) {
            event.setCancelled(true);
            handled = gui.handleClick(player, event.getSlot());
        } else if (holder instanceof InboxGUI gui) {
            event.setCancelled(true);
            handled = gui.handleClick(player, event.getSlot());
        } else if (holder instanceof SentBoxGUI gui) {
            event.setCancelled(true);
            handled = gui.handleClick(player, event.getSlot());
        } else if (holder instanceof MailViewGUI gui) {
            event.setCancelled(true);
            handled = gui.handleClick(player, event.getSlot());
        } else if (holder instanceof ComposeGUI gui) {
            // ComposeGUI允许在附件区域放入物品
            handled = gui.handleClick(player, event.getSlot());
            if (handled) {
                event.setCancelled(true);
            }
            // 如果handled为false，说明是在附件区域，不取消事件
        } else if (holder instanceof AdminMailManageGUI gui) {
            event.setCancelled(true);
            handled = gui.handleClick(player, event.getSlot(), event.isRightClick());
        } else if (holder instanceof TemplateListGUI gui) {
            event.setCancelled(true);
            handled = gui.handleClick(player, event.getSlot(), event.isRightClick());
        }

        // 如果处理了点击，确保取消事件
        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();

        // 如果是ComposeGUI关闭，检查是否需要归还附件
        if (holder instanceof ComposeGUI gui) {
            gui.returnAttachmentsOnClose(player);
            // ComposeGUI关闭时暂时保留数据，因为可能是去输入收件人/标题/内容
            GUIManager.ComposeData data = plugin.getGuiManager().getPlayerComposeData(player.getUniqueId());
            if (data == null || (data.getReceiver() == null && data.getTitle() == null
                    && (data.getContent() == null || data.getContent().isEmpty())
                    && (data.getAttachments() == null || data.getAttachments().isEmpty())
                    && data.getMoneyAttachment() <= 0)) {
                // 如果什么都没填（包括没有附件和金币），直接清理
                plugin.getGuiManager().closeGUI(player);
            }
            // 否则保留数据，等待玩家重新打开或发送
        }
    }

    @Override
    public void accept(ScheduledTask task) {
    }
}
