package dev.user.mailsystem.gui;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.mail.Mail;
import dev.user.mailsystem.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 收件箱GUI
 */
public class InboxGUI implements InventoryHolder {

    private final MailSystemPlugin plugin;
    private final GUIManager guiManager;
    private final int currentPage;
    private Inventory inventory;
    private List<Mail> mails;

    // GUI配置
    private static final String TITLE = "§8§l邮件系统 - 收件箱";
    private static final int SIZE = 54;
    private static final int MAILS_PER_PAGE = 36;

    // 槽位定义
    private static final int START_SLOT = 0;
    private static final int END_SLOT = 35;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_INFO = 47;
    private static final int SLOT_CLEAR = 51;

    public InboxGUI(MailSystemPlugin plugin, GUIManager guiManager, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.currentPage = Math.max(1, page);
    }

    /**
     * 打开GUI
     */
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE + " (第" + currentPage + "页)"));

        // 加载邮件数据
        plugin.getMailManager().loadPlayerMails(player.getUniqueId(), loadedMails -> {
            player.getScheduler().run(plugin, task -> {
                this.mails = loadedMails.stream()
                        .filter(mail -> !mail.isExpired())
                        .toList();
                initializeItems(player);
                player.openInventory(inventory);
            }, null);
        });
    }

    /**
     * 初始化物品
     */
    private void initializeItems(Player player) {
        // 填充背景
        ItemStack background = ItemBuilder.createDecoration(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < SIZE; i++) {
            inventory.setItem(i, background);
        }

        // 计算分页
        int totalPages = Math.max(1, (int) Math.ceil(mails.size() / (double) MAILS_PER_PAGE));
        int page = Math.min(currentPage, totalPages);

        int start = (page - 1) * MAILS_PER_PAGE;
        int end = Math.min(start + MAILS_PER_PAGE, mails.size());

        // 显示邮件
        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            int slot = i - start;
            inventory.setItem(slot, createMailItem(mail));
        }

        // 上一页按钮
        if (page > 1) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .setName("§e上一页")
                    .setLore("§7点击切换到第 " + (page - 1) + " 页")
                    .build());
        } else {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                    .setName("§7已经是第一页")
                    .build());
        }

        // 下一页按钮
        if (page < totalPages) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .setName("§e下一页")
                    .setLore("§7点击切换到第 " + (page + 1) + " 页")
                    .build());
        } else {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                    .setName("§7已经是最后一页")
                    .build());
        }

        // 返回按钮
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .setName("§c返回主菜单")
                .setLore("§7点击返回主菜单")
                .build());

        // 信息按钮
        int unreadCount = (int) mails.stream().filter(m -> !m.isRead()).count();
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .setName("§e邮件统计")
                .setLore(
                        "§7总邮件数: §f" + mails.size(),
                        "§7未读邮件: §c" + unreadCount,
                        "§7当前页数: §f" + page + "/" + totalPages,
                        "",
                        "§7点击邮件查看详情"
                )
                .build());

        // 清空收件箱按钮
        if (!mails.isEmpty()) {
            inventory.setItem(SLOT_CLEAR, new ItemBuilder(Material.LAVA_BUCKET)
                    .setName("§c§l清空收件箱")
                    .setLore(
                            "§7点击清空所有邮件",
                            "§c⚠ 此操作不可恢复！",
                            "§7需要聊天框输入确认"
                    )
                    .setGlowing(true)
                    .build());
        }
    }

    /**
     * 创建邮件物品
     */
    private ItemStack createMailItem(Mail mail) {
        Material material = mail.isRead() ? Material.PAPER : Material.MAP;
        String status = mail.isRead() ? "§7[已读]" : "§a[未读]";
        String attachStatus = mail.hasAttachments() ? (mail.isClaimed() ? " §7[已领取]" : " §6[有附件]") : "";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String dateStr = sdf.format(new Date(mail.getSentTime()));

        return new ItemBuilder(material)
                .setName(status + " §f" + mail.getTitle() + attachStatus)
                .setLore(
                        "§7发件人: §f" + mail.getSenderName(),
                        "§7时间: §f" + dateStr,
                        "§7内容预览:",
                        "§f" + truncateContent(mail.getContent(), 30),
                        "",
                        "§e点击查看详情"
                )
                .setGlowing(!mail.isRead())
                .build();
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "§7(无内容)";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 处理点击事件
     *
     * @param player 玩家
     * @param slot   点击的槽位
     * @return 是否取消事件
     */
    public boolean handleClick(Player player, int slot) {
        // 邮件区域
        if (slot >= START_SLOT && slot <= END_SLOT) {
            int index = (currentPage - 1) * MAILS_PER_PAGE + slot;
            if (index < mails.size()) {
                Mail mail = mails.get(index);
                player.closeInventory();
                guiManager.openMailView(player, mail);
            }
            return true;
        }

        // 功能按钮
        switch (slot) {
            case SLOT_PREV -> {
                if (currentPage > 1) {
                    player.closeInventory();
                    guiManager.openInbox(player, currentPage - 1);
                }
                return true;
            }
            case SLOT_NEXT -> {
                int totalPages = (int) Math.ceil(mails.size() / (double) MAILS_PER_PAGE);
                if (currentPage < totalPages) {
                    player.closeInventory();
                    guiManager.openInbox(player, currentPage + 1);
                }
                return true;
            }
            case SLOT_BACK -> {
                player.closeInventory();
                guiManager.openMainMenu(player);
                return true;
            }
            case SLOT_CLEAR -> {
                if (!mails.isEmpty()) {
                    handleClearInbox(player);
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /**
     * 处理清空收件箱
     */
    private void handleClearInbox(Player player) {
        player.closeInventory();
        player.sendMessage("§c§l⚠ 警告：此操作不可恢复！");
        player.sendMessage("§c你确定要清空收件箱吗？这将删除 §e" + mails.size() + " §c封邮件。");
        player.sendMessage("§7请在聊天框输入 §e'confirm' §7确认，或输入 §e'cancel' §7取消：");

        org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.viewers().clear();
                handleClearConfirm(player, net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()).trim().toLowerCase(), this);
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.getRecipients().clear();
                // 逻辑在新事件中处理，这里只取消事件
            }

            private void handleClearConfirm(Player player, String message, org.bukkit.event.Listener listener) {
                guiManager.unregisterChatListener(player.getUniqueId());

                if (message.equals("confirm")) {
                    plugin.getMailManager().clearInbox(player.getUniqueId(), deleted -> {
                        player.sendMessage("§a[邮件系统] §e已成功清空收件箱，删除了 §f" + deleted + " §e封邮件。");
                    });
                } else {
                    player.sendMessage("§c已取消清空操作。");
                    // 重新打开收件箱
                    org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                        player.getScheduler().run(plugin, t -> guiManager.openInbox(player, currentPage), null);
                    }, 5);
                }
            }
        };
        guiManager.registerChatListener(player.getUniqueId(), listener);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
