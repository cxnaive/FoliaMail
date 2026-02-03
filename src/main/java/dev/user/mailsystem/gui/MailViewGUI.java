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
 * 邮件详情GUI
 */
public class MailViewGUI implements InventoryHolder {

    private final MailSystemPlugin plugin;
    private final GUIManager guiManager;
    private final Mail mail;
    private Inventory inventory;

    // GUI配置
    private static final String TITLE = "§8§l邮件详情";
    private static final int SIZE = 54;

    // 槽位定义
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLAIM = 49;
    private static final int SLOT_DELETE = 53;
    private static final int ATTACHMENT_START = 28;
    private static final int ATTACHMENT_END = 34;

    public MailViewGUI(MailSystemPlugin plugin, GUIManager guiManager, Mail mail) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.mail = mail;
    }

    /**
     * 打开GUI
     */
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE));
        initializeItems(player);

        // 标记为已读
        if (!mail.isRead()) {
            mail.markAsRead();
            plugin.getMailManager().markAsRead(mail.getId());
        }

        player.openInventory(inventory);
    }

    /**
     * 初始化物品
     */
    private void initializeItems(Player player) {
        // 填充背景
        ItemStack background = ItemBuilder.createDecoration(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, background);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = sdf.format(new Date(mail.getSentTime()));

        // 邮件信息
        inventory.setItem(10, new ItemBuilder(Material.PAPER)
                .setName("§e邮件信息")
                .setLore(
                        "§7发件人: §f" + mail.getSenderName(),
                        "§7收件人: §f" + mail.getReceiverName(),
                        "§7发送时间: §f" + dateStr,
                        "§7状态: " + (mail.isRead() ? "§7已读" : "§a未读"),
                        "§7附件: " + (mail.hasAttachments() ? (mail.isClaimed() ? "§7已领取" : "§6未领取") : "§7无")
                )
                .build());

        // 邮件标题
        inventory.setItem(12, new ItemBuilder(Material.NAME_TAG)
                .setName("§e标题")
                .setLore("§f" + mail.getTitle())
                .build());

        // 邮件内容
        List<String> contentLore = new ArrayList<>();
        contentLore.add("§7内容:");
        contentLore.addAll(splitContent(mail.getContent(), 40));

        inventory.setItem(14, new ItemBuilder(Material.BOOK)
                .setName("§e内容")
                .setLore(contentLore.toArray(new String[0]))
                .build());

        // 附件区域
        if (mail.hasAttachments()) {
            List<ItemStack> attachments = mail.getAttachments();
            for (int i = 0; i < attachments.size() && i < 7; i++) {
                int slot = ATTACHMENT_START + i;
                ItemStack item = attachments.get(i);
                if (item != null) {
                    inventory.setItem(slot, item.clone());
                }
            }

            // 领取附件按钮
            if (!mail.isClaimed()) {
                inventory.setItem(SLOT_CLAIM, new ItemBuilder(Material.CHEST)
                        .setName("§a§l领取附件")
                        .setLore(
                                "§7点击领取所有附件",
                                "§7附件将放入你的背包",
                                "",
                                "§e点击领取"
                        )
                        .setGlowing(true)
                        .build());
            } else {
                inventory.setItem(SLOT_CLAIM, new ItemBuilder(Material.CHEST_MINECART)
                        .setName("§7附件已领取")
                        .setLore("§7你已经领取过这些附件了")
                        .build());
            }
        } else {
            inventory.setItem(SLOT_CLAIM, new ItemBuilder(Material.BARRIER)
                    .setName("§c没有附件")
                    .setLore("§7这封邮件没有附件")
                    .build());
        }

        // 返回按钮
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .setName("§e返回收件箱")
                .setLore("§7点击返回收件箱")
                .build());

        // 删除按钮
        inventory.setItem(SLOT_DELETE, new ItemBuilder(Material.LAVA_BUCKET)
                .setName("§c§l删除邮件")
                .setLore(
                        "§7点击删除这封邮件",
                        "§c警告: 此操作不可恢复！",
                        "",
                        "§e点击删除"
                )
                .build());
    }

    /**
     * 分割内容到多行
     */
    private List<String> splitContent(String content, int maxLength) {
        List<String> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            lines.add("§7(无内容)");
            return lines;
        }

        String[] words = content.split(" ");
        StringBuilder currentLine = new StringBuilder("§f");

        for (String word : words) {
            if (currentLine.length() + word.length() > maxLength) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder("§f").append(word).append(" ");
            } else {
                currentLine.append(word).append(" ");
            }
        }

        if (currentLine.length() > 2) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * 处理点击事件
     *
     * @param player 玩家
     * @param slot   点击的槽位
     * @return 是否取消事件
     */
    public boolean handleClick(Player player, int slot) {
        // 附件区域 - 不允许取出
        if (slot >= ATTACHMENT_START && slot <= ATTACHMENT_END) {
            return true;
        }

        switch (slot) {
            case SLOT_BACK -> {
                player.closeInventory();
                guiManager.openInbox(player, 1);
                return true;
            }
            case SLOT_DELETE -> {
                if (!player.hasPermission("mailsystem.delete")) {
                    player.sendMessage("§c你没有权限删除邮件！");
                    return true;
                }
                player.closeInventory();
                plugin.getMailManager().deleteMail(mail.getId(), player.getUniqueId());
                player.sendMessage("§a[邮件系统] §e邮件已删除");
                // 延迟打开收件箱
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    player.getScheduler().run(plugin, t -> guiManager.openInbox(player, 1), null);
                }, 5);
                return true;
            }
            case SLOT_CLAIM -> {
                if (!player.hasPermission("mailsystem.read")) {
                    player.sendMessage("§c你没有权限领取附件！");
                    return true;
                }
                if (mail.hasAttachments() && !mail.isClaimed()) {
                    claimAttachments(player);
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /**
     * 领取附件
     */
    private void claimAttachments(Player player) {
        if (mail.isExpired()) {
            player.sendMessage("§c[邮件系统] 邮件已过期，无法领取附件！");
            return;
        }

        plugin.getMailManager().claimAttachments(mail.getId(), player);
        mail.setClaimed(true);

        // 刷新界面
        player.closeInventory();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            player.getScheduler().run(plugin, t -> guiManager.openMailView(player, mail), null);
        }, 5);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
