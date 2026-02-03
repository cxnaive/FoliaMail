package dev.user.mailsystem.gui;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.mail.Mail;
import dev.user.mailsystem.util.ItemBuilder;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.UUID;

/**
 * 管理员邮件管理GUI - 查看和管理其他玩家的邮件
 */
public class AdminMailManageGUI implements InventoryHolder {

    private final MailSystemPlugin plugin;
    private final GUIManager guiManager;
    private final UUID targetUuid;
    private final String targetName;
    private final int currentPage;
    private Inventory inventory;
    private List<Mail> mails;

    // GUI配置
    private static final String TITLE = "§8§l邮件管理 - ";
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

    public AdminMailManageGUI(MailSystemPlugin plugin, GUIManager guiManager, UUID targetUuid, String targetName, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.currentPage = Math.max(1, page);
    }

    /**
     * 打开GUI
     */
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE + targetName + " (第" + currentPage + "页)"));

        // 加载邮件数据
        plugin.getMailManager().loadPlayerMails(targetUuid, loadedMails -> {
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
                .setName("§c关闭")
                .setLore("§7点击关闭界面")
                .build());

        // 信息按钮
        int unreadCount = (int) mails.stream().filter(m -> !m.isRead()).count();
        int unclaimedCount = (int) mails.stream().filter(Mail::hasAttachments).filter(m -> !m.isClaimed()).count();
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .setName("§e邮件统计")
                .setLore(
                        "§7目标玩家: §f" + targetName,
                        "§7总邮件数: §f" + mails.size(),
                        "§7未读邮件: §c" + unreadCount,
                        "§7未领取附件: §6" + unclaimedCount,
                        "§7当前页数: §f" + page + "/" + totalPages,
                        "",
                        "§7左键: 查看邮件详情",
                        "§7右键: 管理选项"
                )
                .build());

        // 清空收件箱按钮（仅当有邮件时显示）
        if (!mails.isEmpty()) {
            inventory.setItem(SLOT_CLEAR, new ItemBuilder(Material.LAVA_BUCKET)
                    .setName("§c§l清空该玩家收件箱")
                    .setLore(
                            "§7目标: §f" + targetName,
                            "§7点击清空该玩家的所有邮件",
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
        String mailIdShort = mail.getId().toString().substring(0, 8);

        return new ItemBuilder(material)
                .setName(status + " §f" + mail.getTitle() + attachStatus)
                .setLore(
                        "§7ID: §8" + mailIdShort,
                        "§7发件人: §f" + mail.getSenderName(),
                        "§7时间: §f" + dateStr,
                        "§7已读: " + (mail.isRead() ? "§a是" : "§c否"),
                        "§7已领取: " + (mail.isClaimed() ? "§a是" : "§c否"),
                        "§7内容预览:",
                        "§f" + truncateContent(mail.getContent(), 30),
                        "",
                        "§e左键: 查看详情",
                        "§e右键: 管理选项"
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
     * @param isRightClick 是否右键
     * @return 是否取消事件
     */
    public boolean handleClick(Player player, int slot, boolean isRightClick) {
        // 邮件区域
        if (slot >= START_SLOT && slot <= END_SLOT) {
            int index = (currentPage - 1) * MAILS_PER_PAGE + slot;
            if (index < mails.size()) {
                Mail mail = mails.get(index);
                if (isRightClick) {
                    // 右键打开管理选项
                    openManageOptions(player, mail);
                } else {
                    // 左键查看详情
                    player.closeInventory();
                    guiManager.openMailView(player, mail);
                }
            }
            return true;
        }

        // 功能按钮
        switch (slot) {
            case SLOT_PREV -> {
                if (currentPage > 1) {
                    player.closeInventory();
                    guiManager.openAdminMailManage(player, targetUuid, targetName, currentPage - 1);
                }
                return true;
            }
            case SLOT_NEXT -> {
                int totalPages = (int) Math.ceil(mails.size() / (double) MAILS_PER_PAGE);
                if (currentPage < totalPages) {
                    player.closeInventory();
                    guiManager.openAdminMailManage(player, targetUuid, targetName, currentPage + 1);
                }
                return true;
            }
            case SLOT_BACK -> {
                player.closeInventory();
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
     * 处理清空玩家收件箱
     */
    private void handleClearInbox(Player player) {
        player.closeInventory();
        player.sendMessage("§c§l⚠ 警告：此操作不可恢复！");
        player.sendMessage("§c你确定要清空 §e" + targetName + " §c的收件箱吗？");
        player.sendMessage("§c这将删除 §e" + mails.size() + " §c封邮件。");
        player.sendMessage("§7请在聊天框输入 §e'confirm' §7确认，或输入 §e'cancel' §7取消：");

        org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(AsyncChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.viewers().clear();
                handleClearConfirm(player, PlainTextComponentSerializer.plainText().serialize(event.message()).trim().toLowerCase(), this);
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
                    plugin.getMailManager().clearInbox(targetUuid, deleted -> {
                        player.sendMessage("§a[邮件系统] §e已成功清空 §f" + targetName + " §e的收件箱，删除了 §f" + deleted + " §e封邮件。");
                    });
                } else {
                    player.sendMessage("§c已取消清空操作。");
                    // 重新打开管理界面
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                        player.getScheduler().run(plugin, t -> guiManager.openAdminMailManage(player, targetUuid, targetName, currentPage), null);
                    }, 5);
                }
            }
        };
        guiManager.registerChatListener(player.getUniqueId(), listener);
    }

    /**
     * 打开管理选项
     */
    private void openManageOptions(Player player, Mail mail) {
        player.closeInventory();
        player.sendMessage("§6======== 邮件管理选项 ========");
        player.sendMessage("§7邮件ID: §f" + mail.getId().toString().substring(0, 8));
        player.sendMessage("§7标题: §f" + mail.getTitle());
        player.sendMessage("§7收件人: §f" + targetName);
        player.sendMessage("");
        player.sendMessage("§e1. §f切换已读状态 §7(当前: " + (mail.isRead() ? "§a已读" : "§c未读") + "§7)");
        player.sendMessage("§e2. §f切换领取状态 §7(当前: " + (mail.isClaimed() ? "§a已领取" : "§c未领取") + "§7)");
        player.sendMessage("§e3. §c删除邮件 §7(不可恢复！)");
        player.sendMessage("§c0. §f取消");
        player.sendMessage("§7请在聊天框输入数字选择:");

        org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(AsyncChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.viewers().clear();
                handleManageSelect(player, mail, PlainTextComponentSerializer.plainText().serialize(event.message()).trim(), this);
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.getRecipients().clear();
                // 逻辑在新事件中处理，这里只取消事件
            }

            private void handleManageSelect(Player player, Mail mail, String message, org.bukkit.event.Listener listener) {
                org.bukkit.event.HandlerList.unregisterAll(listener);

                switch (message) {
                    case "1" -> toggleReadStatus(player, mail);
                    case "2" -> toggleClaimStatus(player, mail);
                    case "3" -> deleteMail(player, mail);
                    case "0", "cancel" -> {
                        player.sendMessage("§c已取消。");
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                            player.getScheduler().run(plugin, t -> guiManager.openAdminMailManage(player, targetUuid, targetName, currentPage), null);
                        }, 5);
                    }
                    default -> {
                        player.sendMessage("§c无效选项，请重新输入 (0-3):");
                        // 重新注册自己
                        guiManager.registerChatListener(player.getUniqueId(), listener);
                    }
                }
            }
        };
        guiManager.registerChatListener(player.getUniqueId(), listener);
    }

    /**
     * 切换已读状态
     */
    private void toggleReadStatus(Player player, Mail mail) {
        boolean newStatus = !mail.isRead();
        plugin.getMailManager().markAsReadStatus(mail.getId(), newStatus);
        mail.setRead(newStatus);
        player.sendMessage("§a[邮件系统] §e已将邮件标记为 " + (newStatus ? "§a已读" : "§c未读"));
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            player.getScheduler().run(plugin, t -> guiManager.openAdminMailManage(player, targetUuid, targetName, currentPage), null);
        }, 10);
    }

    /**
     * 切换领取状态
     */
    private void toggleClaimStatus(Player player, Mail mail) {
        boolean newStatus = !mail.isClaimed();
        plugin.getMailManager().markAsClaimedStatus(mail.getId(), newStatus);
        mail.setClaimed(newStatus);
        player.sendMessage("§a[邮件系统] §e已将邮件附件标记为 " + (newStatus ? "§a已领取" : "§c未领取"));
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            player.getScheduler().run(plugin, t -> guiManager.openAdminMailManage(player, targetUuid, targetName, currentPage), null);
        }, 10);
    }

    /**
     * 删除邮件
     */
    private void deleteMail(Player player, Mail mail) {
        plugin.getMailManager().deleteMailById(mail.getId());
        player.sendMessage("§a[邮件系统] §c邮件已删除。");
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            player.getScheduler().run(plugin, t -> guiManager.openAdminMailManage(player, targetUuid, targetName, currentPage), null);
        }, 10);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
