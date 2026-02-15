package dev.user.mailsystem.gui;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 主菜单GUI
 */
public class MainMenuGUI implements InventoryHolder {

    private final MailSystemPlugin plugin;
    private final GUIManager guiManager;
    private Inventory inventory;

    // GUI配置
    private static final String TITLE = "§8§l邮件系统 - 主菜单";
    private static final int SIZE = 27;

    // 槽位定义
    private static final int SLOT_COMPOSE = 11;
    private static final int SLOT_INBOX = 13;
    private static final int SLOT_SENT = 15;
    private static final int SLOT_HELP = 26;

    public MainMenuGUI(MailSystemPlugin plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * 打开GUI
     */
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE));
        // 先加载未读数量，再初始化并打开GUI
        plugin.getMailManager().getUnreadCount(player.getUniqueId(), unreadCount ->
            player.getScheduler().run(plugin, task -> {
                initializeItems(player, unreadCount);
                player.openInventory(inventory);
            }, null)
        );
    }

    /**
     * 初始化物品
     */
    private void initializeItems(Player player, int unreadCount) {
        // 填充背景
        ItemStack background = ItemBuilder.createDecoration(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, background);
        }

        // 写邮件按钮 - 检查发送权限
        if (player.hasPermission("mailsystem.send")) {
            inventory.setItem(SLOT_COMPOSE, new ItemBuilder(Material.WRITABLE_BOOK)
                    .setName("§a§l写邮件")
                    .setLore(
                            "§7点击编写新邮件",
                            "§7可以添加物品作为附件",
                            "",
                            "§e点击打开"
                    )
                    .setGlowing(true)
                    .build());
        }

        // 收件箱按钮 - 检查读取权限
        if (player.hasPermission("mailsystem.read")) {
            String inboxName = unreadCount > 0 ? "§e§l收件箱 §c(" + unreadCount + ")" : "§e§l收件箱";
            inventory.setItem(SLOT_INBOX, new ItemBuilder(Material.CHEST)
                    .setName(inboxName)
                    .setLore(
                            "§7查看收到的邮件",
                            "§7未读邮件: §c" + unreadCount,
                            "",
                            "§e点击查看"
                    )
                    .setGlowing(unreadCount > 0)
                    .build());
        }

        // 发件箱按钮 - 检查发送权限
        if (player.hasPermission("mailsystem.send")) {
            inventory.setItem(SLOT_SENT, new ItemBuilder(Material.BOOK)
                    .setName("§b§l发件箱")
                    .setLore(
                            "§7查看已发送的邮件",
                            "",
                            "§e点击查看"
                    )
                    .build());
        }

        // 帮助按钮
        inventory.setItem(SLOT_HELP, new ItemBuilder(Material.BOOKSHELF)
                .setName("§d§l帮助")
                .setLore(
                        "§7邮件系统使用帮助",
                        "",
                        "§7/fmail send <玩家> <标题> [内容]",
                        "§7  §f发送邮件",
                        "§7/fmail attach <玩家> [标题]",
                        "§7  §f发送手持物品",
                        "§7/fmail read",
                        "§7  §f查看未读邮件",
                        "§7/fmail list [页码]",
                        "§7  §f查看收件箱",
                        "§7/fmail blacklist <add|remove|list> [玩家]",
                        "§7  §f管理黑名单",
                        "",
                        "§e点击关闭帮助"
                )
                .build());
    }

    /**
     * 处理点击事件
     *
     * @param player 玩家
     * @param slot   点击的槽位
     * @return 是否取消事件
     */
    public boolean handleClick(Player player, int slot) {
        switch (slot) {
            case SLOT_COMPOSE -> {
                if (!player.hasPermission("mailsystem.send")) {
                    player.sendMessage("§c你没有权限发送邮件！");
                    player.closeInventory();
                    return true;
                }
                player.closeInventory();
                guiManager.openCompose(player);
                return true;
            }
            case SLOT_INBOX -> {
                if (!player.hasPermission("mailsystem.read")) {
                    player.sendMessage("§c你没有权限查看收件箱！");
                    player.closeInventory();
                    return true;
                }
                player.closeInventory();
                guiManager.openInbox(player, 1);
                return true;
            }
            case SLOT_SENT -> {
                if (!player.hasPermission("mailsystem.send")) {
                    player.sendMessage("§c你没有权限查看发件箱！");
                    player.closeInventory();
                    return true;
                }
                player.closeInventory();
                guiManager.openSentBox(player, 1);
                return true;
            }
            case SLOT_HELP -> {
                // 帮助按钮只是显示信息，点击关闭
                player.closeInventory();
                player.sendMessage("§a[邮件系统] §e使用 /fmail 查看命令帮助");
                return true;
            }
            default -> {
                // 点击其他位置，取消事件
                return true;
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
