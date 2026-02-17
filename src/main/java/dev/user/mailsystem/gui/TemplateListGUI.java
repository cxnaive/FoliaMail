package dev.user.mailsystem.gui;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.template.TemplateManager;
import dev.user.mailsystem.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 模板列表GUI
 */
public class TemplateListGUI implements InventoryHolder {

    private final MailSystemPlugin plugin;
    private final GUIManager guiManager;
    private Inventory inventory;
    private int currentPage = 1;

    private static final String TITLE = "§8§l选择邮件模板";
    private static final int SIZE = 54;
    private static final int SLOTS_PER_PAGE = 45;

    public TemplateListGUI(MailSystemPlugin plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public void open(Player player, int page) {
        this.currentPage = page;
        inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE));

        // 加载模板列表
        plugin.getTemplateManager().listTemplates(list -> {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                initializeItems(player, list);
                player.openInventory(inventory);
            });
        });
    }

    private void initializeItems(Player player, List<TemplateManager.TemplateInfo> templates) {
        // 填充背景
        ItemStack background = ItemBuilder.createDecoration(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, background);
        }

        int totalTemplates = templates.size();
        int totalPages = (totalTemplates + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (totalPages == 0) totalPages = 1;

        // 计算当前页的起始和结束索引
        int startIndex = (currentPage - 1) * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, totalTemplates);

        // 显示模板
        for (int i = startIndex; i < endIndex; i++) {
            TemplateManager.TemplateInfo info = templates.get(i);
            int slot = i - startIndex;

            ItemStack item = new ItemBuilder(Material.BOOK)
                    .setName("§e§l" + info.getDisplayOrName())
                    .setLore(
                            "§7标识: §f" + info.name(),
                            "§7创建者: §f" + info.creatorName(),
                            "§7使用次数: §f" + info.useCount() + "次",
                            "",
                            "§e左键: §a加载此模板",
                            player.hasPermission("mailsystem.template.delete") ? "§c右键: §4删除模板" : ""
                    )
                    .build();
            inventory.setItem(slot, item);
        }

        // 上一页按钮
        if (currentPage > 1) {
            inventory.setItem(45, new ItemBuilder(Material.ARROW)
                    .setName("§e上一页")
                    .setLore("§7点击切换到第 " + (currentPage - 1) + " 页")
                    .build());
        }

        // 返回按钮
        inventory.setItem(49, new ItemBuilder(Material.BARRIER)
                .setName("§c返回")
                .setLore("§7点击返回写邮件界面")
                .build());

        // 下一页按钮
        if (currentPage < totalPages) {
            inventory.setItem(53, new ItemBuilder(Material.ARROW)
                    .setName("§e下一页")
                    .setLore("§7点击切换到第 " + (currentPage + 1) + " 页")
                    .build());
        }

        // 页码显示
        inventory.setItem(52, new ItemBuilder(Material.PAPER)
                .setName("§e第 " + currentPage + "/" + totalPages + " 页")
                .setLore("§7共 " + totalTemplates + " 个模板")
                .build());
    }

    public boolean handleClick(Player player, int slot, boolean isRightClick) {
        // 上一页
        if (slot == 45 && currentPage > 1) {
            open(player, currentPage - 1);
            return true;
        }

        // 返回
        if (slot == 49) {
            player.closeInventory();
            guiManager.openCompose(player);
            return true;
        }

        // 下一页
        if (slot == 53) {
            plugin.getTemplateManager().listTemplates(list -> {
                int totalPages = (list.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
                if (totalPages == 0) totalPages = 1;
                if (currentPage < totalPages) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> open(player, currentPage + 1));
                }
            });
            return true;
        }

        // 模板点击
        if (slot >= 0 && slot < SLOTS_PER_PAGE) {
            int index = (currentPage - 1) * SLOTS_PER_PAGE + slot;
            plugin.getTemplateManager().listTemplates(list -> {
                if (index >= list.size()) return;

                TemplateManager.TemplateInfo info = list.get(index);

                if (isRightClick && player.hasPermission("mailsystem.template.delete")) {
                    // 右键删除
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        player.closeInventory();
                        player.sendMessage("§c[邮件系统] §e确认删除模板 '" + info.name() + "' 吗？");
                        player.sendMessage("§7输入 'confirm' 确认删除，或 'cancel' 取消");
                        registerDeleteConfirmListener(player, info.name());
                    });
                } else {
                    // 左键加载
                    loadTemplate(player, info.name());
                }
            });
            return true;
        }

        return true;
    }

    private void loadTemplate(Player player, String templateName) {
        plugin.getTemplateManager().getTemplate(templateName, template -> {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                if (template == null) {
                    player.sendMessage("§c[邮件系统] 模板加载失败！");
                    return;
                }

                // 获取或创建 ComposeData
                GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
                if (data == null) {
                    data = new GUIManager.ComposeData();
                }

                // 填充模板数据
                data.setTitle(template.getTitle());
                data.setContent(template.getContent());

                // 检查并加载物品附件（检查权限和数量限制）
                data.clearAttachments();
                boolean hasAttachPermission = player.hasPermission("mailsystem.attach") || player.hasPermission("mailsystem.admin");
                if (template.getAttachments() != null && !template.getAttachments().isEmpty()) {
                    if (hasAttachPermission) {
                        int maxAttachments = plugin.getMailConfig().getMaxAttachments();
                        int loadedCount = 0;
                        for (ItemStack item : template.getAttachments()) {
                            if (data.getAttachmentCount() >= maxAttachments) {
                                player.sendMessage("§e[邮件系统] 模板附件数量超过限制，已截断至 " + maxAttachments + " 个");
                                break;
                            }
                            data.addAttachment(item.clone());
                            loadedCount++;
                        }
                        if (loadedCount > 0) {
                            player.sendMessage("§a[邮件系统] 已加载 " + loadedCount + " 个物品附件");
                        }
                    } else {
                        player.sendMessage("§c[邮件系统] 模板包含物品附件，但你没有权限发送附件，已跳过");
                    }
                }

                // 检查金币附件权限
                boolean hasMoneyPermission = player.hasPermission("mailsystem.attach.money") || player.hasPermission("mailsystem.admin");
                if (template.getMoneyAttachment() > 0) {
                    if (hasMoneyPermission) {
                        data.setMoneyAttachment(template.getMoneyAttachment());
                        player.sendMessage("§a[邮件系统] 已加载金币附件: " + plugin.getEconomyManager().format(template.getMoneyAttachment()));
                    } else {
                        data.setMoneyAttachment(0);
                        player.sendMessage("§c[邮件系统] 模板包含金币附件，但你没有权限发送金币，已跳过");
                    }
                } else {
                    data.setMoneyAttachment(0);
                }

                guiManager.setPlayerComposeData(player.getUniqueId(), data);

                player.sendMessage("§a[邮件系统] 模板 §e" + template.getDisplayOrName() + " §a已加载！");

                // 返回写邮件界面
                player.closeInventory();
                guiManager.openCompose(player);
            });
        });
    }

    private void registerDeleteConfirmListener(Player player, String templateName) {
        org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.viewers().clear();

                String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());

                if (message.equalsIgnoreCase("confirm")) {
                    plugin.getTemplateManager().deleteTemplate(templateName, success -> {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                            guiManager.unregisterChatListener(player.getUniqueId());
                            if (success) {
                                player.sendMessage("§a[邮件系统] 模板 §e" + templateName + " §a已删除！");
                            } else {
                                player.sendMessage("§c[邮件系统] 删除模板失败！");
                            }
                            player.getScheduler().runDelayed(plugin, t -> guiManager.openCompose(player), null, 10);
                        });
                    });
                } else {
                    guiManager.unregisterChatListener(player.getUniqueId());
                    player.sendMessage("§c已取消删除。");
                    player.getScheduler().runDelayed(plugin, t -> guiManager.openCompose(player), null, 10);
                }
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.getRecipients().clear();
            }
        };

        guiManager.registerChatListener(player.getUniqueId(), listener);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
