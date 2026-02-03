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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 发件箱GUI
 */
public class SentBoxGUI implements InventoryHolder {

    private final MailSystemPlugin plugin;
    private final GUIManager guiManager;
    private final int currentPage;
    private Inventory inventory;
    private List<Mail> sentMails;

    // GUI配置
    private static final String TITLE = "§8§l邮件系统 - 发件箱";
    private static final int SIZE = 54;
    private static final int MAILS_PER_PAGE = 36;

    // 槽位定义
    private static final int START_SLOT = 0;
    private static final int END_SLOT = 35;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_INFO = 47;

    public SentBoxGUI(MailSystemPlugin plugin, GUIManager guiManager, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.currentPage = Math.max(1, page);
    }

    /**
     * 打开GUI
     */
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE + " (第" + currentPage + "页)"));

        // 从数据库加载已发送邮件（使用 sender_uuid 查询）
        plugin.getMailManager().loadSentMails(player.getUniqueId(), loadedMails -> {
            player.getScheduler().run(plugin, task -> {
                this.sentMails = loadedMails;
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
        int totalPages = Math.max(1, (int) Math.ceil(sentMails.size() / (double) MAILS_PER_PAGE));
        int page = Math.min(currentPage, totalPages);

        int start = (page - 1) * MAILS_PER_PAGE;
        int end = Math.min(start + MAILS_PER_PAGE, sentMails.size());

        // 显示邮件
        for (int i = start; i < end; i++) {
            Mail mail = sentMails.get(i);
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
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .setName("§e发件统计")
                .setLore(
                        "§7总发送数: §f" + sentMails.size(),
                        "§7当前页数: §f" + page + "/" + totalPages,
                        "",
                        "§7显示你发送的所有邮件"
                )
                .build());
    }

    /**
     * 创建邮件物品
     */
    private ItemStack createMailItem(Mail mail) {
        String readStatus = mail.isRead() ? "§a已读" : "§e未读";
        String attachStatus = mail.hasAttachments() ? " §6[有附件]" : "";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String dateStr = sdf.format(new Date(mail.getSentTime()));

        return new ItemBuilder(Material.WRITTEN_BOOK)
                .setName("§e" + mail.getTitle() + attachStatus)
                .setLore(
                        "§7收件人: §f" + mail.getReceiverName(),
                        "§7时间: §f" + dateStr,
                        "§7阅读状态: " + readStatus,
                        "§7附件状态: " + (mail.hasAttachments() ? (mail.isClaimed() ? "§a已领取" : "§e未领取") : "§7无附件"),
                        "§7内容预览:",
                        "§f" + truncateContent(mail.getContent(), 30)
                )
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
        // 邮件区域 - 发件箱只读，不打开详情
        if (slot >= START_SLOT && slot <= END_SLOT) {
            int index = (currentPage - 1) * MAILS_PER_PAGE + slot;
            if (index < sentMails.size()) {
                // 发件箱不打开详情，只是显示信息
                player.sendMessage("§a[邮件系统] §e发件箱仅支持查看，不能操作邮件");
            }
            return true;
        }

        // 功能按钮
        switch (slot) {
            case SLOT_PREV -> {
                if (currentPage > 1) {
                    player.closeInventory();
                    guiManager.openSentBox(player, currentPage - 1);
                }
                return true;
            }
            case SLOT_NEXT -> {
                int totalPages = (int) Math.ceil(sentMails.size() / (double) MAILS_PER_PAGE);
                if (currentPage < totalPages) {
                    player.closeInventory();
                    guiManager.openSentBox(player, currentPage + 1);
                }
                return true;
            }
            case SLOT_BACK -> {
                player.closeInventory();
                guiManager.openMainMenu(player);
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
