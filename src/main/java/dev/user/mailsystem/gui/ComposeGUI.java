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

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 写邮件界面GUI
 */
public class ComposeGUI implements InventoryHolder {

    private final MailSystemPlugin plugin;
    private final GUIManager guiManager;
    private Inventory inventory;

    // GUI配置
    private static final String TITLE = "§8§l写邮件";
    private static final int SIZE = 54;

    // 槽位定义
    private static final int SLOT_RECEIVER = 10;
    private static final int SLOT_TITLE = 12;
    private static final int SLOT_CONTENT = 14;
    private static final int SLOT_MONEY = 16;      // 金币附件按钮
    private static final int SLOT_BROADCAST = 25;  // 群发模式按钮（移动到25）
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLEAR = 47;
    private static final int SLOT_SEND = 49;
    private static final int ATTACHMENT_START = 28;
    private static final int ATTACHMENT_END = 34;

    public ComposeGUI(MailSystemPlugin plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * 打开GUI
     */
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE));

        // 获取或创建ComposeData
        GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
        if (data == null) {
            data = new GUIManager.ComposeData();
            guiManager.setPlayerComposeData(player.getUniqueId(), data);
        }

        data.setReturned(false);
        initializeItems(player);

        // 从ComposeData恢复附件到界面
        int slot = ATTACHMENT_START;
        for (ItemStack item : data.getAttachments()) {
            if (slot <= ATTACHMENT_END) {
                inventory.setItem(slot++, item);
            }
        }

        player.openInventory(inventory);
    }

    /**
     * 初始化物品
     */
    private void initializeItems(Player player) {
        // 填充背景（跳过附件槽位）
        ItemStack background = ItemBuilder.createDecoration(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            // 跳过附件区域，保留玩家放入的物品
            if (i >= ATTACHMENT_START && i <= ATTACHMENT_END) {
                continue;
            }
            inventory.setItem(i, background);
        }

        GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
        if (data == null) {
            data = new GUIManager.ComposeData();
            guiManager.setPlayerComposeData(player.getUniqueId(), data);
        }

        // 收件人设置
        String receiver = data.getReceiver();
        inventory.setItem(SLOT_RECEIVER, new ItemBuilder(Material.PLAYER_HEAD)
                .setName("§e设置收件人")
                .setLore(
                        "§7当前: " + (receiver != null ? "§f" + receiver : "§c未设置"),
                        "",
                        "§7点击输入收件人名称",
                        "§7或在聊天框输入: /fmail send <玩家>"
                )
                .build());

        // 标题设置
        String title = data.getTitle();
        inventory.setItem(SLOT_TITLE, new ItemBuilder(Material.NAME_TAG)
                .setName("§e设置标题")
                .setLore(
                        "§7当前: " + (title != null ? "§f" + title : "§c未设置"),
                        "",
                        "§7点击输入邮件标题",
                        "§7最大长度: " + plugin.getMailConfig().getMaxMailTitleLength()
                )
                .build());

        // 内容设置
        String content = data.getContent();
        List<String> contentLore = new ArrayList<>();
        contentLore.add("§7当前内容:");
        if (content != null && !content.isEmpty()) {
            contentLore.addAll(splitContent(content, 30));
        } else {
            contentLore.add("§c未设置");
        }
        contentLore.add("");
        contentLore.add("§7点击输入邮件内容");
        contentLore.add("§7最大长度: " + plugin.getMailConfig().getMaxMailContentLength());

        inventory.setItem(SLOT_CONTENT, new ItemBuilder(Material.WRITABLE_BOOK)
                .setName("§e设置内容")
                .setLore(contentLore.toArray(new String[0]))
                .build());

        // 金币附件设置（仅管理员或有权限玩家可见）
        boolean canAttachMoney = player.hasPermission("mailsystem.attach.money") || player.hasPermission("mailsystem.admin");
        double moneyAttachment = data.getMoneyAttachment();
        if (canAttachMoney) {
            inventory.setItem(SLOT_MONEY, new ItemBuilder(Material.GOLD_INGOT)
                    .setName("§6§l设置金币附件")
                    .setLore(
                            "§7当前: " + (moneyAttachment > 0 ? "§f" + plugin.getEconomyManager().format(moneyAttachment) : "§c未设置"),
                            "",
                            "§7点击输入要附加的金币数量",
                            "§7收件人领取邮件时将获得这些金币",
                            "",
                            "§c注意: 发送时会立即扣除你的金币"
                    )
                    .build());
        } else {
            inventory.setItem(SLOT_MONEY, new ItemBuilder(Material.GOLD_INGOT)
                    .setName("§7§l金币附件")
                    .setLore(
                            "§7你没有权限发送金币附件",
                            "§7需要权限: §fmailsystem.attach.money"
                    )
                    .build());
        }

        // 附件区域说明（槽位19不是附件区域，只是说明）
        double deliveryFee = plugin.getMailConfig().getAttachmentDeliveryFee();
        boolean economyEnabled = plugin.getMailConfig().isEnableEconomy() && plugin.getEconomyManager().isEnabled();
        inventory.setItem(19, new ItemBuilder(Material.CHEST)
                .setName("§e附件区域")
                .setLore(
                        "§7将物品放入下方格子 (28-34)",
                        "§7作为邮件附件发送",
                        "§7最多 " + plugin.getMailConfig().getMaxAttachments() + " 个物品",
                        (economyEnabled && deliveryFee > 0) ? "§7快递费: §f" + plugin.getEconomyManager().format(deliveryFee) + "/个" : "",
                        "",
                        "§7当前附件: " + data.getAttachmentCount() + "/" + plugin.getMailConfig().getMaxAttachments()
                )
                .build());

        // 返回按钮
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .setName("§c返回")
                .setLore("§7点击返回主菜单")
                .build());

        // 清空按钮
        inventory.setItem(SLOT_CLEAR, new ItemBuilder(Material.LAVA_BUCKET)
                .setName("§c清空")
                .setLore("§7点击清空所有输入")
                .build());

        // 群发按钮（仅管理员可见）
        if (player.hasPermission("mailsystem.admin")) {
            boolean isBroadcast = data.isBroadcastMode();
            inventory.setItem(SLOT_BROADCAST, new ItemBuilder(isBroadcast ? Material.NETHER_STAR : Material.FIREWORK_STAR)
                    .setName(isBroadcast ? "§6§l群发模式: 开启" : "§7群发模式: 关闭")
                    .setLore(
                            "§7点击切换群发模式",
                            "",
                            isBroadcast ? "§a当前: 群发邮件给多个玩家" : "§7当前: 发送给单个玩家",
                            "",
                            "§e群发目标可在点击后选择:",
                            "§7- 所有在线玩家",
                            "§7- 3天内登录的玩家",
                            "§7- 7天内登录的玩家",
                            "§7- 所有曾经登录的玩家"
                    )
                    .setGlowing(isBroadcast)
                    .build());
        }

        // 发送按钮
        boolean isBroadcast = data.isBroadcastMode();
        boolean canSend;
        if (isBroadcast) {
            canSend = title != null; // 群发不需要收件人
        } else {
            canSend = receiver != null && title != null;
        }

        String sendButtonName;
        String[] sendButtonLore;
        if (isBroadcast) {
            sendButtonName = canSend ? "§6§l群发邮件" : "§c§l无法群发";
            sendButtonLore = canSend ?
                    new String[]{
                            "§7模式: §6群发",
                            "§7标题: §f" + title,
                            "",
                            "§e点击选择群发目标并发送"
                    } :
                    new String[]{
                            "§c请设置标题后再群发",
                            ""
                    };
        } else {
            sendButtonName = canSend ? "§a§l发送邮件" : "§c§l无法发送";
            // 添加费用信息到lore
            double postageFee = plugin.getMailConfig().getMailPostageFee();
            double moneyAttach = data.getMoneyAttachment();
            String feeInfo = "";
            if (plugin.getMailConfig().isEnableEconomy() && plugin.getEconomyManager().isEnabled()) {
                if (postageFee > 0 || deliveryFee > 0 || moneyAttach > 0) {
                    feeInfo = "§7费用: §f基础邮费 " + plugin.getEconomyManager().format(postageFee);
                    if (deliveryFee > 0) {
                        feeInfo += " + 快递费 " + plugin.getEconomyManager().format(deliveryFee) + "/个";
                    }
                    if (moneyAttach > 0) {
                        feeInfo += " + 金币附件 " + plugin.getEconomyManager().format(moneyAttach);
                    }
                }
            }
            String finalFeeInfo = feeInfo;
            String moneyInfo = moneyAttach > 0 ? "§7金币附件: §f" + plugin.getEconomyManager().format(moneyAttach) : "";
            sendButtonLore = canSend ?
                    new String[]{
                            "§7收件人: §f" + receiver,
                            "§7标题: §f" + title,
                            moneyInfo.isEmpty() ? "" : moneyInfo,
                            "",
                            finalFeeInfo.isEmpty() ? "" : finalFeeInfo,
                            finalFeeInfo.isEmpty() ? "" : "",
                            "§e点击发送邮件"
                    } :
                    new String[]{
                            "§c请完善以下信息:",
                            receiver == null ? "§c- 收件人" : "§a- 收件人 ✓",
                            title == null ? "§c- 标题" : "§a- 标题 ✓",
                            ""
                    };
        }

        Material sendMaterial = canSend ? (isBroadcast ? Material.NETHER_STAR : Material.EMERALD_BLOCK) : Material.REDSTONE_BLOCK;
        inventory.setItem(SLOT_SEND, new ItemBuilder(sendMaterial)
                .setName(sendButtonName)
                .setLore(sendButtonLore)
                .setGlowing(canSend)
                .build());
    }

    /**
     * 分割内容到多行
     */
    private List<String> splitContent(String content, int maxLength) {
        List<String> lines = new ArrayList<>();
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
     * 将界面中的附件同步到ComposeData
     */
    public void syncAttachmentsToData(Player player) {
        GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
        if (data == null) {
            data = new GUIManager.ComposeData();
            guiManager.setPlayerComposeData(player.getUniqueId(), data);
        }
        data.clearAttachments();
        for (int i = ATTACHMENT_START; i <= ATTACHMENT_END; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                data.addAttachment(item);
            }
        }
    }

    /**
     * 获取所有附件（从ComposeData）
     */
    public List<ItemStack> getAttachments(Player player) {
        GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
        if (data != null) {
            return new ArrayList<>(data.getAttachments());
        }
        return new ArrayList<>();
    }

    /**
     * 处理点击事件
     *
     * @param player 玩家
     * @param slot   点击的槽位
     * @return 是否取消事件
     */
    public boolean handleClick(Player player, int slot) {
        // 附件区域 - 允许放入/取出，权限检查在发送时进行
        if (slot >= ATTACHMENT_START && slot <= ATTACHMENT_END) {
            return false; // 不取消事件，允许玩家操作物品
        }

        switch (slot) {
            case SLOT_RECEIVER -> {
                player.closeInventory();
                player.sendMessage("§a[邮件系统] §e请在聊天框输入收件人名称:");
                player.sendMessage("§7输入 'cancel' 取消");

                // 注册临时聊天监听器
                registerChatListener(player, "receiver");
                return true;
            }
            case SLOT_TITLE -> {
                player.closeInventory();
                player.sendMessage("§a[邮件系统] §e请在聊天框输入邮件标题:");
                player.sendMessage("§7输入 'cancel' 取消");
                player.sendMessage("§7最大长度: " + plugin.getMailConfig().getMaxMailTitleLength());

                registerChatListener(player, "title");
                return true;
            }
            case SLOT_CONTENT -> {
                player.closeInventory();
                player.sendMessage("§a[邮件系统] §e请在聊天框输入邮件内容:");
                player.sendMessage("§7输入 'cancel' 取消");
                player.sendMessage("§7最大长度: " + plugin.getMailConfig().getMaxMailContentLength());

                registerChatListener(player, "content");
                return true;
            }
            case SLOT_MONEY -> {
                // 检查权限
                if (!player.hasPermission("mailsystem.attach.money") && !player.hasPermission("mailsystem.admin")) {
                    player.sendMessage("§c你没有权限发送金币附件！");
                    return true;
                }
                // 检查经济功能是否启用
                if (!plugin.getMailConfig().isEnableEconomy() || !plugin.getEconomyManager().isEnabled()) {
                    player.sendMessage("§c经济功能未启用，无法发送金币附件！");
                    return true;
                }
                player.closeInventory();
                player.sendMessage("§a[邮件系统] §e请在聊天框输入要附加的金币数量:");
                player.sendMessage("§7输入 'cancel' 取消");
                player.sendMessage("§7输入 '0' 或 'clear' 清除金币附件");
                player.sendMessage("§7当前余额: §f" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)));

                registerChatListener(player, "money");
                return true;
            }
            case SLOT_BROADCAST -> {
                if (!player.hasPermission("mailsystem.admin")) {
                    return true;
                }
                // 切换群发模式
                GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
                if (data != null) {
                    data.setBroadcastMode(!data.isBroadcastMode());
                    // 重新初始化界面
                    initializeItems(player);
                    player.sendMessage(data.isBroadcastMode() ? "§a[邮件系统] §e已切换到群发模式" : "§a[邮件系统] §e已切换到单发模式");
                }
                return true;
            }
            case SLOT_BACK -> {
                // 归还附件
                returnAttachmentsOnClose(player);
                player.closeInventory();
                guiManager.openMainMenu(player);
                return true;
            }
            case SLOT_CLEAR -> {
                // 先归还附件
                returnAttachmentsOnClose(player);
                // 清空数据
                guiManager.setPlayerComposeData(player.getUniqueId(), new GUIManager.ComposeData());
                // 清空界面
                for (int i = ATTACHMENT_START; i <= ATTACHMENT_END; i++) {
                    inventory.setItem(i, null);
                }
                initializeItems(player);
                player.sendMessage("§a[邮件系统] §e已清空所有输入");
                return true;
            }
            case SLOT_SEND -> {
                if (!player.hasPermission("mailsystem.send")) {
                    player.sendMessage("§c你没有权限发送邮件！");
                    return true;
                }
                GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
                // 检查是否正在处理中，防止快速点击重复发送
                if (data != null && !data.getProcessing().compareAndSet(false, true)) {
                    player.sendMessage("§c[邮件系统] §e邮件正在发送中，请稍后...");
                    return true;
                }
                if (data != null && data.isBroadcastMode()) {
                    if (!player.hasPermission("mailsystem.admin")) {
                        player.sendMessage("§c你没有权限使用群发功能！");
                        data.getProcessing().set(false); // 重置状态
                        return true;
                    }
                    showBroadcastTargetSelector(player);
                } else {
                    sendMail(player);
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /**
     * 注册聊天监听器
     */
    private void registerChatListener(Player player, String type) {
        org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(AsyncChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.viewers().clear();
                handleChatEvent(player, type, PlainTextComponentSerializer.plainText().serialize(event.message()), this);
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.getRecipients().clear();
                // 逻辑在新事件中处理，这里只取消事件
            }

            private void handleChatEvent(Player player, String type, String message, org.bukkit.event.Listener listener) {
                if (message.equalsIgnoreCase("cancel")) {
                    player.sendMessage("§c已取消输入。");
                    org.bukkit.event.HandlerList.unregisterAll(listener);
                    // 重新打开界面
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                        player.getScheduler().run(plugin, t -> guiManager.openCompose(player), null);
                    }, 5);
                    return;
                }

                GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
                if (data == null) {
                    data = new GUIManager.ComposeData();
                }

                switch (type) {
                    case "receiver" -> {
                        // 先检查在线玩家
                        Player onlineTarget = Bukkit.getPlayer(message);
                        if (onlineTarget != null) {
                            data.setReceiver(onlineTarget.getName());
                            data.setReceiverUuid(onlineTarget.getUniqueId());
                            player.sendMessage("§a收件人已设置为: §e" + onlineTarget.getName());
                            break; // 使用 break 继续下面的保存和界面重开
                        }
                        // 异步查询离线玩家
                        player.sendMessage("§a[邮件系统] §e正在查找玩家...");
                        GUIManager.ComposeData finalData = data;
                        plugin.getPlayerCacheManager().getPlayerUuid(message, uuid -> {
                            if (uuid == null) {
                                player.sendMessage("§c玩家不存在或从未加入过服务器！请重新输入:");
                                return;
                            }
                            plugin.getPlayerCacheManager().getPlayerName(uuid, cachedName -> {
                                String finalName = cachedName != null ? cachedName : message;
                                finalData.setReceiver(finalName);
                                finalData.setReceiverUuid(uuid);
                                guiManager.setPlayerComposeData(player.getUniqueId(), finalData);
                                player.sendMessage("§a收件人已设置为: §e" + finalName);
                                org.bukkit.event.HandlerList.unregisterAll(listener);
                                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                                    player.getScheduler().run(plugin, t -> guiManager.openCompose(player), null);
                                }, 5);
                            });
                        });
                        return; // 异步处理，直接返回不执行下面的保存代码
                    }
                    case "title" -> {
                        if (message.length() > plugin.getMailConfig().getMaxMailTitleLength()) {
                            player.sendMessage("§c标题过长！最大长度: " + plugin.getMailConfig().getMaxMailTitleLength() + "，请重新输入:");
                            return;
                        }
                        data.setTitle(message);
                        player.sendMessage("§a标题已设置为: §e" + message);
                    }
                    case "content" -> {
                        if (message.length() > plugin.getMailConfig().getMaxMailContentLength()) {
                            player.sendMessage("§c内容过长！最大长度: " + plugin.getMailConfig().getMaxMailContentLength() + "，请重新输入:");
                            return;
                        }
                        data.setContent(message);
                        player.sendMessage("§a内容已设置");
                    }
                    case "money" -> {
                        // 处理清除命令
                        if (message.equalsIgnoreCase("clear") || message.equals("0")) {
                            data.setMoneyAttachment(0);
                            player.sendMessage("§a金币附件已清除");
                            break;
                        }
                        // 解析金额
                        double amount;
                        try {
                            amount = Double.parseDouble(message);
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c无效的数字格式！请输入有效的金币数量:");
                            return;
                        }
                        // 检查金额有效性
                        if (amount < 0) {
                            player.sendMessage("§c金币数量不能为负数！请重新输入:");
                            return;
                        }
                        if (amount == 0) {
                            data.setMoneyAttachment(0);
                            player.sendMessage("§a金币附件已清除");
                            break;
                        }
                        // 检查余额
                        double balance = plugin.getEconomyManager().getBalance(player);
                        if (balance < amount) {
                            player.sendMessage("§c余额不足！当前余额: §f" + plugin.getEconomyManager().format(balance) + "§c，请重新输入:");
                            return;
                        }
                        // 设置金币附件
                        data.setMoneyAttachment(amount);
                        player.sendMessage("§a金币附件已设置为: §e" + plugin.getEconomyManager().format(amount));
                    }
                }

                guiManager.setPlayerComposeData(player.getUniqueId(), data);
                org.bukkit.event.HandlerList.unregisterAll(listener);

                // 重新打开界面
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    player.getScheduler().run(plugin, t -> guiManager.openCompose(player), null);
                }, 5);
            }
        };

        guiManager.registerChatListener(player.getUniqueId(), listener);
    }

    /**
     * 发送邮件
     */
    private void sendMail(Player player) {
        // 先同步附件到数据
        syncAttachmentsToData(player);

        GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
        if (data == null || data.getReceiver() == null || data.getTitle() == null) {
            player.sendMessage("§c请完善邮件信息！");
            if (data != null) {
                data.getProcessing().set(false);
            }
            return;
        }

        // 检查是否有收件人UUID（离线玩家设置时会存储）
        UUID receiverUuid = data.getReceiverUuid();
        String receiverName = data.getReceiver();

        // 如果是在线玩家但没有存储UUID（旧逻辑兼容），尝试获取
        if (receiverUuid == null) {
            Player onlineTarget = Bukkit.getPlayer(receiverName);
            if (onlineTarget != null) {
                receiverUuid = onlineTarget.getUniqueId();
                receiverName = onlineTarget.getName();
            } else {
                player.sendMessage("§c无法找到收件人信息，请重新设置收件人！");
                data.getProcessing().set(false);
                return;
            }
        }

        String content = data.getContent() != null ? data.getContent() : "";
        final String finalReceiverName = receiverName;
        final UUID finalReceiverUuid = receiverUuid;
        Mail mail = new Mail(player.getUniqueId(), player.getName(),
                finalReceiverUuid, finalReceiverName, data.getTitle(), content);

        // 添加附件（从ComposeData）
        List<ItemStack> attachments = data.getAttachments();
        double moneyAttachment = data.getMoneyAttachment();
        if (!attachments.isEmpty()) {
            // 检查附件权限
            if (!player.hasPermission("mailsystem.attach")) {
                player.sendMessage("§c你没有权限发送物品附件！请先移除附件后再发送。");
                data.getProcessing().set(false);
                return;
            }
            if (attachments.size() > plugin.getMailConfig().getMaxAttachments()) {
                player.sendMessage("§c附件数量超过限制！最多 " + plugin.getMailConfig().getMaxAttachments() + " 个");
                data.getProcessing().set(false);
                return;
            }
            mail.setAttachments(new ArrayList<>(attachments));
        }

        // 检查金币附件权限
        if (moneyAttachment > 0) {
            if (!player.hasPermission("mailsystem.attach.money") && !player.hasPermission("mailsystem.admin")) {
                player.sendMessage("§c你没有权限发送金币附件！");
                data.getProcessing().set(false);
                return;
            }
            // 检查余额
            if (!plugin.getEconomyManager().hasEnough(player, moneyAttachment)) {
                player.sendMessage("§c余额不足！无法发送金币附件。当前余额: §f" +
                    plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)));
                data.getProcessing().set(false);
                return;
            }
            mail.setMoneyAttachment(moneyAttachment);
        }

        // 计算费用（群发不扣费，因为属于管理员功能）
        double totalCost = 0;
        boolean shouldCharge = !data.isBroadcastMode();
        if (shouldCharge && plugin.getMailConfig().isEnableEconomy() &&
            plugin.getEconomyManager().isEnabled()) {
            double postageFee = plugin.getMailConfig().getMailPostageFee();
            double deliveryFee = attachments.size() * plugin.getMailConfig().getAttachmentDeliveryFee();
            totalCost = postageFee + deliveryFee + moneyAttachment; // 金币附件也计入总费用（需要扣除）
        }

        // 异步发送邮件（传入 player 和费用，在 MailManager 中处理扣费逻辑）
        plugin.getMailManager().sendMail(mail, player, totalCost, success -> {
            // 在主线程执行后续操作
            player.getScheduler().run(plugin, task -> {
                if (!success) {
                    // 发送失败（邮箱已满或余额不足），重置处理状态允许重试
                    data.getProcessing().set(false);
                    return;
                }

                // 清空界面中的附件槽位，防止 closeInventory 时触发 returnAttachmentsOnClose 归还附件
                for (int i = ATTACHMENT_START; i <= ATTACHMENT_END; i++) {
                    inventory.setItem(i, null);
                }
                // 标记附件已处理，防止重复归还
                data.setReturned(true);

                player.closeInventory();
                player.sendMessage("§a[邮件系统] §e邮件已发送给 §f" + finalReceiverName);
                if (!attachments.isEmpty()) {
                    player.sendMessage("§a附件: §f" + attachments.size() + " 个物品");
                }
                if (moneyAttachment > 0) {
                    player.sendMessage("§a金币附件: §f" + plugin.getEconomyManager().format(moneyAttachment));
                }

                // 清理数据（附件已发送，无需归还）
                guiManager.setPlayerComposeData(player.getUniqueId(), null);
            }, null);
        });
    }

    /**
     * 显示群发目标选择器
     */
    private void showBroadcastTargetSelector(Player player) {
        GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
        if (data == null || data.getTitle() == null) {
            player.sendMessage("§c请先设置邮件标题！");
            return;
        }

        // 先同步附件到数据，防止关闭时丢失
        syncAttachmentsToData(player);
        // 标记附件已处理，防止 closeInventory 时归还附件
        data.setReturned(true);
        player.closeInventory();
        player.sendMessage("§6======== 选择群发目标 ========");
        player.sendMessage("§e1. §f当前子服在线玩家 §7- 所有当前在线的玩家");
        player.sendMessage("§e2. §f3天内 §7- 3天内登录过的玩家");
        player.sendMessage("§e3. §f7天内 §7- 7天内登录过的玩家");
        player.sendMessage("§e4. §f所有玩家 §7- 所有曾经登录过的玩家");
        player.sendMessage("§c0. §f取消群发");
        player.sendMessage("§7请在聊天框输入数字选择 (0-4):");

        // 使用GUIManager注册聊天监听器，确保玩家退出时自动清理
        org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(AsyncChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.viewers().clear();
                handleBroadcastSelect(player, data, PlainTextComponentSerializer.plainText().serialize(event.message()).trim(), this);
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                if (!event.getPlayer().equals(player)) return;
                event.setCancelled(true);
                event.getRecipients().clear();
                // 逻辑在新事件中处理，这里只取消事件
            }

            private void handleBroadcastSelect(Player player, GUIManager.ComposeData data, String message, org.bukkit.event.Listener listener) {
                // 使用GUIManager注销监听器
                guiManager.unregisterChatListener(player.getUniqueId());

                switch (message) {
                    case "1" -> broadcastToOnline(player);
                    case "2" -> broadcastToRecent(player, 3);
                    case "3" -> broadcastToRecent(player, 7);
                    case "4" -> broadcastToAll(player);
                    case "0", "cancel" -> {
                        player.sendMessage("§c已取消群发。");
                        // 重置处理状态
                        data.getProcessing().set(false);
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                            player.getScheduler().run(plugin, t -> guiManager.openCompose(player), null);
                        }, 5);
                    }
                    default -> {
                        player.sendMessage("§c无效选项，请重新输入 (0-4):");
                        // 重新注册监听器
                        guiManager.registerChatListener(player.getUniqueId(), listener);
                    }
                }
            }
        };
        guiManager.registerChatListener(player.getUniqueId(), listener);
    }

    /**
     * 群发邮件给在线玩家
     */
    private void broadcastToOnline(Player sender) {
        Map<UUID, String> targets = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            targets.put(player.getUniqueId(), player.getName());
        }
        broadcastToTargets(sender, targets, "当前子服在线玩家", false);
    }

    /**
     * 群发邮件给近期登录玩家
     */
    private void broadcastToRecent(Player sender, int days) {
        long sinceTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        plugin.getPlayerCacheManager().getPlayersLoggedInSince(sinceTime, players ->
            broadcastToTargets(sender, players, days + "天内登录的玩家", true));
    }

    /**
     * 群发邮件给所有玩家
     */
    private void broadcastToAll(Player sender) {
        plugin.getPlayerCacheManager().getAllCachedPlayers(players ->
            broadcastToTargets(sender, players, "所有玩家", true));
    }

    /**
     * 通用群发邮件方法
     * @param sender 发送者
     * @param targets 目标玩家 Map<UUID, 玩家名>
     * @param targetDesc 目标描述（用于提示消息）
     * @param checkAttach 是否检查附件权限（在线玩家群发时已经在界面检查过）
     */
    private void broadcastToTargets(Player sender, Map<UUID, String> targets, String targetDesc, boolean checkAttach) {
        syncAttachmentsToData(sender);
        GUIManager.ComposeData data = guiManager.getPlayerComposeData(sender.getUniqueId());

        String title = data.getTitle();
        String content = data.getContent() != null ? data.getContent() : "";
        List<ItemStack> attachments = data.getAttachments();
        double moneyAttachment = data.getMoneyAttachment();

        // 检查附件权限
        if (checkAttach && !attachments.isEmpty()) {
            if (!sender.hasPermission("mailsystem.attach")) {
                sender.sendMessage("§c你没有权限发送物品附件！");
                returnAttachmentsOnClose(sender);
                data.getProcessing().set(false);
                return;
            }
            if (attachments.size() > plugin.getMailConfig().getMaxAttachments()) {
                sender.sendMessage("§c附件数量超过限制！");
                returnAttachmentsOnClose(sender);
                data.getProcessing().set(false);
                return;
            }
        }

        // 检查金币附件权限和余额
        if (moneyAttachment > 0) {
            if (!sender.hasPermission("mailsystem.attach.money") && !sender.hasPermission("mailsystem.admin")) {
                sender.sendMessage("§c你没有权限发送金币附件！");
                data.getProcessing().set(false);
                return;
            }
            double totalMoneyNeeded = moneyAttachment * targets.size();
            if (!plugin.getEconomyManager().hasEnough(sender, totalMoneyNeeded)) {
                sender.sendMessage("§c余额不足！群发金币附件需要 §f" +
                    plugin.getEconomyManager().format(totalMoneyNeeded) +
                    "§c，当前余额: §f" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(sender)));
                data.getProcessing().set(false);
                return;
            }
        }

        // 构建邮件列表
        List<Mail> mails = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : targets.entrySet()) {
            Mail mail = new Mail(sender.getUniqueId(), sender.getName(),
                    entry.getKey(), entry.getValue(), title, content);
            if (!attachments.isEmpty()) {
                mail.setAttachments(new ArrayList<>(attachments));
            }
            if (moneyAttachment > 0) {
                mail.setMoneyAttachment(moneyAttachment);
            }
            mails.add(mail);
        }

        // 使用批量发送（优化版本，使用数据库批量插入）
        plugin.getMailManager().sendMailsBatch(mails, sender, sentUuids -> {
            int sent = sentUuids.size();
            int skipped = targets.size() - sent;
            boolean hasAttachments = !attachments.isEmpty() || moneyAttachment > 0;
            int attachCount = attachments.size();
            boolean showSkipped = skipped > 0 && !sender.hasPermission("mailsystem.admin");

            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                if (showSkipped) {
                    sender.sendMessage("§a[邮件系统] §e群发邮件已发送给 §f" + sent + " §e个" + targetDesc + "，§c跳过 " + skipped + " 个邮箱已满的玩家");
                } else {
                    sender.sendMessage("§a[邮件系统] §e群发邮件已发送给 §f" + sent + " §e个" + targetDesc);
                }
                if (hasAttachments) {
                    if (!attachments.isEmpty()) {
                        sender.sendMessage("§a物品附件: §f" + attachCount + " 个/每人");
                    }
                    if (moneyAttachment > 0) {
                        sender.sendMessage("§a金币附件: §f" + plugin.getEconomyManager().format(moneyAttachment) + "/每人");
                    }
                }
                guiManager.setPlayerComposeData(sender.getUniqueId(), null);
            });
        });
    }

    /**
     * 归还附件到玩家背包（用于返回按钮或关闭界面）
     */
    public void returnAttachmentsOnClose(Player player) {
        GUIManager.ComposeData data = guiManager.getPlayerComposeData(player.getUniqueId());
        if (data == null || data.isReturned()) {
            return; // 已经返还过了
        }

        // 先同步附件到数据
        syncAttachmentsToData(player);

        if (!data.getAttachments().isEmpty()) {
            List<ItemStack> attachments = new ArrayList<>(data.getAttachments());
            player.getScheduler().run(plugin, task -> {
                for (ItemStack item : attachments) {
                    player.getInventory().addItem(item).values()
                            .forEach(drop -> player.getWorld().dropItem(player.getLocation(), drop));
                }
                if (!attachments.isEmpty()) {
                    player.sendMessage("§a[邮件系统] §e附件已归还到背包");
                }
            }, null);
            // 清空附件数据并标记已返还
            data.clearAttachments();
            data.setReturned(true);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
