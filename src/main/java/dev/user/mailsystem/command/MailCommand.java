package dev.user.mailsystem.command;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.mail.Mail;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MailCommand implements CommandExecutor, TabCompleter {

    private final MailSystemPlugin plugin;

    public MailCommand(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!player.hasPermission("mailsystem.use")) {
            player.sendMessage("§c你没有权限使用邮件系统！");
            return true;
        }

        if (args.length == 0) {
            // 打开GUI主菜单
            plugin.getGuiManager().openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "send" -> {
                if (!checkPermission(player, "mailsystem.send")) return true;
                handleSend(player, args);
            }
            case "write" -> {
                if (!checkPermission(player, "mailsystem.send")) return true;
                handleWrite(player, args);
            }
            case "read" -> {
                if (!checkPermission(player, "mailsystem.read")) return true;
                handleRead(player, args);
            }
            case "list", "inbox" -> {
                if (!checkPermission(player, "mailsystem.read")) return true;
                handleList(player, args);
            }
            case "sent" -> {
                if (!checkPermission(player, "mailsystem.send")) return true;
                handleSent(player, args);
            }
            case "open" -> {
                if (!checkPermission(player, "mailsystem.read")) return true;
                handleOpen(player, args);
            }
            case "delete", "del" -> {
                if (!checkPermission(player, "mailsystem.delete")) return true;
                handleDelete(player, args);
            }
            case "clear", "clearinbox" -> {
                if (!checkPermission(player, "mailsystem.delete")) return true;
                handleClear(player, args);
            }
            case "claim" -> {
                if (!checkPermission(player, "mailsystem.read")) return true;
                handleClaim(player, args);
            }
            case "attach" -> {
                if (!checkPermission(player, "mailsystem.attach")) return true;
                handleAttach(player, args);
            }
            case "broadcast", "bc" -> {
                if (!player.hasPermission("mailsystem.admin")) {
                    player.sendMessage("§c你没有权限使用群发功能！");
                    return true;
                }
                handleBroadcast(player, args);
            }
            case "manage" -> {
                if (!player.hasPermission("mailsystem.admin")) {
                    player.sendMessage("§c你没有权限使用管理功能！");
                    return true;
                }
                handleManage(player, args);
            }
            case "clearcache" -> handleClearCache(player);
            case "reload" -> handleReload(player);
            case "blacklist", "bl" -> handleBlacklist(player, args);
            default -> showHelp(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            // 根据权限添加可补全的命令
            if (player.hasPermission("mailsystem.send")) {
                completions.addAll(Arrays.asList("send", "write", "sent"));
            }
            if (player.hasPermission("mailsystem.read")) {
                completions.addAll(Arrays.asList("read", "list", "inbox", "open", "claim"));
            }
            if (player.hasPermission("mailsystem.delete")) {
                completions.addAll(Arrays.asList("delete", "del", "clear", "clearinbox"));
            }
            if (player.hasPermission("mailsystem.attach")) {
                completions.add("attach");
            }
            // 黑名单命令
            completions.addAll(Arrays.asList("blacklist", "bl"));
            // 管理员命令
            if (player.hasPermission("mailsystem.admin")) {
                completions.addAll(Arrays.asList("reload", "clearcache", "broadcast", "bc", "manage"));
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            // 需要玩家名称补全的命令
            if (sub.equals("send") || sub.equals("write") || sub.equals("attach")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
            // 需要邮件ID补全的命令
            if (sub.equals("open") || sub.equals("delete") || sub.equals("del") || sub.equals("claim")) {
                // 从缓存中获取玩家的邮件ID前缀
                return plugin.getMailManager().getPlayerMails(player.getUniqueId()).stream()
                        .map(mail -> mail.getId().toString().substring(0, 8))
                        .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                        .limit(20)
                        .toList();
            }
            // 群发命令的目标补全
            if (sub.equals("broadcast") || sub.equals("bc")) {
                List<String> targets = Arrays.asList("online", "3days", "7days", "all");
                return targets.stream()
                        .filter(t -> t.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
            // 黑名单命令的子命令补全
            if (sub.equals("blacklist") || sub.equals("bl")) {
                List<String> blCommands = Arrays.asList("add", "remove", "list");
                return blCommands.stream()
                        .filter(c -> c.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
            // 管理命令需要玩家名补全
            if (sub.equals("manage")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            // 黑名单 add/remove 的玩家名补全
            if ((sub.equals("blacklist") || sub.equals("bl")) &&
                (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
        }

        return Collections.emptyList();
    }

    private void handleSend(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /fmail send <玩家> <标题> [内容]");
            return;
        }

        String targetName = args[1];
        String title = args[2];
        if (title.length() > plugin.getMailConfig().getMaxMailTitleLength()) {
            player.sendMessage("§c标题过长，最大长度: " + plugin.getMailConfig().getMaxMailTitleLength());
            return;
        }

        StringBuilder content = new StringBuilder();
        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                content.append(args[i]).append(" ");
            }
        }

        String contentStr = content.toString().trim();
        if (contentStr.length() > plugin.getMailConfig().getMaxMailContentLength()) {
            player.sendMessage("§c内容过长，最大长度: " + plugin.getMailConfig().getMaxMailContentLength());
            return;
        }

        // 先尝试获取在线玩家
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            sendMail(player, onlineTarget.getUniqueId(), onlineTarget.getName(), title, contentStr);
            return;
        }

        // 异步查询离线玩家缓存
        player.sendMessage("§a[邮件系统] §e正在查找玩家...");
        plugin.getPlayerCacheManager().getPlayerUuid(targetName, uuid -> {
            if (uuid == null) {
                player.sendMessage("§c玩家不存在或从未加入过服务器！");
                return;
            }
            // 获取玩家名（使用缓存中的名字）
            plugin.getPlayerCacheManager().getPlayerName(uuid, cachedName -> {
                String finalName = cachedName != null ? cachedName : targetName;
                sendMail(player, uuid, finalName, title, contentStr);
            });
        });
    }

    private void sendMail(Player sender, UUID targetUuid, String targetName, String title, String content) {
        sendMail(sender, targetUuid, targetName, title, content, null);
    }

    private void sendMail(Player sender, UUID targetUuid, String targetName, String title, String content, List<org.bukkit.inventory.ItemStack> attachments) {
        Mail mail = new Mail(sender.getUniqueId(), sender.getName(),
                targetUuid, targetName, title, content);
        if (attachments != null && !attachments.isEmpty()) {
            mail.setAttachments(new ArrayList<>(attachments));
        }

        // 计算费用
        double totalCost = 0;
        if (plugin.getMailConfig().isEnableEconomy() && plugin.getEconomyManager().isEnabled()) {
            double postageFee = plugin.getMailConfig().getMailPostageFee();
            double deliveryFee = (attachments != null ? attachments.size() : 0) * plugin.getMailConfig().getAttachmentDeliveryFee();
            totalCost = postageFee + deliveryFee;
        }

        final double finalCost = totalCost;
        plugin.getMailManager().sendMail(mail, sender, totalCost, success -> {
            if (success) {
                sender.sendMessage("§a邮件已发送给 §e" + targetName);
            }
            // 失败提示和扣费提示已在 sendMail 方法中处理
        });
    }

    private void handleWrite(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /fmail write <玩家> <标题>");
            return;
        }

        String targetName = args[1];
        String title = args[2];
        if (title.length() > plugin.getMailConfig().getMaxMailTitleLength()) {
            player.sendMessage("§c标题过长！");
            return;
        }

        // 先尝试获取在线玩家
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            startWriteChatListener(player, onlineTarget.getUniqueId(), onlineTarget.getName(), title);
            return;
        }

        // 异步查询离线玩家缓存
        player.sendMessage("§a[邮件系统] §e正在查找玩家...");
        plugin.getPlayerCacheManager().getPlayerUuid(targetName, uuid -> {
            if (uuid == null) {
                player.sendMessage("§c玩家不存在或从未加入过服务器！");
                return;
            }
            // 获取玩家名
            plugin.getPlayerCacheManager().getPlayerName(uuid, cachedName -> {
                String finalName = cachedName != null ? cachedName : targetName;
                startWriteChatListener(player, uuid, finalName, title);
            });
        });
    }

    private void startWriteChatListener(Player player, UUID targetUuid, String targetName, String title) {
        player.closeInventory();
        player.sendMessage("§e请输入邮件内容（输入 'cancel' 取消）：");

        plugin.getServer().getPluginManager().registerEvents(
                new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
                    public void onChat(AsyncChatEvent event) {
                        if (!event.getPlayer().equals(player)) return;
                        event.setCancelled(true);
                        event.viewers().clear();
                        handleChatInput(player, targetUuid, targetName, title, PlainTextComponentSerializer.plainText().serialize(event.message()), this);
                    }

                    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
                    public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                        if (!event.getPlayer().equals(player)) return;
                        event.setCancelled(true);
                        event.getRecipients().clear();
                        // 逻辑在新事件中处理，这里只取消事件
                    }

                    private void handleChatInput(Player player, UUID targetUuid, String targetName, String title, String message, org.bukkit.event.Listener listener) {
                        if (message.equalsIgnoreCase("cancel")) {
                            player.sendMessage("§c已取消发送。");
                            org.bukkit.event.HandlerList.unregisterAll(listener);
                            return;
                        }

                        if (message.length() > plugin.getMailConfig().getMaxMailContentLength()) {
                            player.sendMessage("§c内容过长，请重新输入：");
                            return;
                        }

                        // 计算费用（无附件）
                        double totalCost = 0;
                        if (plugin.getMailConfig().isEnableEconomy() && plugin.getEconomyManager().isEnabled()) {
                            totalCost = plugin.getMailConfig().getMailPostageFee();
                        }

                        final double finalCost = totalCost;
                        Mail mail = new Mail(player.getUniqueId(), player.getName(),
                                targetUuid, targetName, title, message);
                        plugin.getMailManager().sendMail(mail, player, totalCost, success -> {
                            if (success) {
                                player.sendMessage("§a邮件已发送给 §e" + targetName);
                            }
                            // 失败提示和扣费提示已在 sendMail 方法中处理
                        });

                        org.bukkit.event.HandlerList.unregisterAll(listener);
                    }
                }, plugin);
    }

    private void handleRead(Player player, String[] args) {
        plugin.getMailManager().loadPlayerMails(player.getUniqueId(), mails -> {
            int unreadCount = (int) mails.stream().filter(m -> !m.isRead() && !m.isExpired()).count();

            if (unreadCount == 0) {
                player.sendMessage("§a你没有未读邮件。");
                return;
            }

            player.sendMessage("§6======== 未读邮件 ========");
            mails.stream()
                    .filter(m -> !m.isRead() && !m.isExpired())
                    .limit(10)
                    .forEach(mail -> {
                        Component line = Component.text("§e[" + mail.getSenderName() + "] §f" + mail.getTitle())
                                .clickEvent(ClickEvent.runCommand("/fmail open " + mail.getId().toString().substring(0, 8)))
                                .hoverEvent(HoverEvent.showText(Component.text("§e点击查看详情\n§7邮件ID: " + mail.getId())));
                        player.sendMessage(line);
                    });

            if (unreadCount > 10) {
                player.sendMessage("§7还有 " + (unreadCount - 10) + " 封未读邮件，使用 /fmail list 查看全部");
            }
        });
    }

    private void handleList(Player player, String[] args) {
        final int pageInput;
        if (args.length > 1) {
            try {
                pageInput = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c页码必须是数字！");
                return;
            }
        } else {
            pageInput = 1;
        }

        plugin.getMailManager().loadPlayerMails(player.getUniqueId(), mails -> {
            mails.removeIf(Mail::isExpired);

            if (mails.isEmpty()) {
                player.sendMessage("§a你的邮箱是空的。");
                return;
            }

            int perPage = 10;
            int totalPages = (int) Math.ceil(mails.size() / (double) perPage);
            final int page = Math.max(1, Math.min(pageInput, totalPages));

            int start = (page - 1) * perPage;
            int end = Math.min(start + perPage, mails.size());

            player.sendMessage("§6======== 收件箱 (" + page + "/" + totalPages + ") ========");

            for (int i = start; i < end; i++) {
                Mail mail = mails.get(i);
                String status = mail.isRead() ? "§7[已读]" : "§a[未读]";
                String attach = mail.hasAttachments() ? " §6[附件]" : "";

                Component line = Component.text(status + " §e[" + mail.getSenderName() + "] §f" + mail.getTitle() + attach)
                        .clickEvent(ClickEvent.runCommand("/fmail open " + mail.getId().toString().substring(0, 8)))
                        .hoverEvent(HoverEvent.showText(Component.text("§e点击查看详情")));
                player.sendMessage(line);
            }

            if (page < totalPages) {
                player.sendMessage(Component.text("§7下一页 >>")
                        .clickEvent(ClickEvent.runCommand("/fmail list " + (page + 1))));
            }
        });
    }

    private void handleSent(Player player, String[] args) {
        plugin.getMailManager().loadSentMails(player.getUniqueId(), mails -> {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                player.sendMessage("§6======== 已发送邮件 ========");

                if (mails.isEmpty()) {
                    player.sendMessage("§a你还没有发送过邮件。");
                    return;
                }

                mails.stream()
                        .sorted(Comparator.comparingLong(Mail::getSentTime).reversed())
                        .limit(10)
                        .forEach(mail -> {
                            String status = mail.isRead() ? "§7已读" : "§a未读";
                            player.sendMessage("§e[" + mail.getReceiverName() + "] §f" + mail.getTitle() + " §7(" + status + ")");
                        });

                if (mails.size() > 10) {
                    player.sendMessage("§7还有 " + (mails.size() - 10) + " 封已发送邮件，使用GUI查看全部");
                }
            });
        });
    }

    private void handleOpen(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fmail open <邮件ID>");
            return;
        }

        String idPrefix = args[1].toLowerCase();

        plugin.getMailManager().loadPlayerMails(player.getUniqueId(), mails -> {
            Mail target = mails.stream()
                    .filter(m -> m.getId().toString().toLowerCase().startsWith(idPrefix))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                player.sendMessage("§c未找到该邮件！");
                return;
            }

            if (!target.isRead()) {
                target.markAsRead();
                plugin.getMailManager().markAsRead(target.getId());
            }

            player.sendMessage("§6======== 邮件详情 ========");
            player.sendMessage("§e发件人: §f" + target.getSenderName());
            player.sendMessage("§e时间: §f" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date(target.getSentTime())));
            player.sendMessage("§e标题: §f" + target.getTitle());
            player.sendMessage("§e内容:");
            player.sendMessage("§f" + target.getContent());

            if (target.hasAttachments()) {
                player.sendMessage("§6-------- 附件 --------");
                for (int i = 0; i < target.getAttachments().size(); i++) {
                    ItemStack item = target.getAttachments().get(i);
                    if (item != null) {
                        String itemName;
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null && meta.hasDisplayName()) {
                            // 使用 Adventure Component API (Paper 1.20.5+)
                            itemName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                    .serialize(meta.displayName());
                        } else {
                            itemName = item.getType().name();
                        }
                        player.sendMessage("§e" + (i + 1) + ". §f" + item.getAmount() + "x " + itemName);
                    }
                }

                if (!target.isClaimed()) {
                    player.sendMessage(Component.text("§a[点击领取附件]")
                            .clickEvent(ClickEvent.runCommand("/fmail claim " + idPrefix)));
                } else {
                    player.sendMessage("§7附件已领取");
                }
            }
        });
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fmail delete <邮件ID>");
            return;
        }

        String idPrefix = args[1].toLowerCase();

        plugin.getMailManager().loadPlayerMails(player.getUniqueId(), mails -> {
            Mail target = mails.stream()
                    .filter(m -> m.getId().toString().toLowerCase().startsWith(idPrefix))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                player.sendMessage("§c未找到该邮件！");
                return;
            }

            plugin.getMailManager().deleteMail(target.getId(), player.getUniqueId());
            player.sendMessage("§a邮件已删除。");
        });
    }

    private void handleClear(Player player, String[] args) {
        // 先获取邮件数量
        plugin.getMailManager().loadPlayerMails(player.getUniqueId(), mails -> {
            int count = mails.size();
            if (count == 0) {
                player.sendMessage("§a你的收件箱已经是空的。");
                return;
            }

            player.closeInventory();
            player.sendMessage("§c§l⚠ 警告：此操作不可恢复！");
            player.sendMessage("§c你确定要清空收件箱吗？这将删除 §e" + count + " §c封邮件。");
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
                    plugin.getGuiManager().unregisterChatListener(player.getUniqueId());

                    if (message.equals("confirm")) {
                        plugin.getMailManager().clearInbox(player.getUniqueId(), deleted -> {
                            player.sendMessage("§a[邮件系统] §e已成功清空收件箱，删除了 §f" + deleted + " §e封邮件。");
                        });
                    } else {
                        player.sendMessage("§c已取消清空操作。");
                    }
                }
            };
            plugin.getGuiManager().registerChatListener(player.getUniqueId(), listener);
        });
    }

    private void handleClaim(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fmail claim <邮件ID>");
            return;
        }

        String idPrefix = args[1].toLowerCase();

        plugin.getMailManager().loadPlayerMails(player.getUniqueId(), mails -> {
            Mail target = mails.stream()
                    .filter(m -> m.getId().toString().toLowerCase().startsWith(idPrefix))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                player.sendMessage("§c未找到该邮件！");
                return;
            }

            if (!target.hasAttachments()) {
                player.sendMessage("§c该邮件没有附件！");
                return;
            }

            if (target.isClaimed()) {
                player.sendMessage("§c附件已被领取！");
                return;
            }

            if (target.isExpired()) {
                player.sendMessage("§c邮件已过期！");
                return;
            }

            plugin.getMailManager().claimAttachments(target.getId(), player);
            target.setClaimed(true);
        });
    }

    private void handleAttach(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fmail attach <玩家> [标题]");
            return;
        }

        String targetName = args[1];
        String title = args.length > 2 ? args[2] : "物品邮件";

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage("§c请手持要发送的物品！");
            return;
        }

        // 先尝试获取在线玩家
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            sendAttachMail(player, onlineTarget.getUniqueId(), onlineTarget.getName(), title, item);
            return;
        }

        // 异步查询离线玩家缓存
        player.sendMessage("§a[邮件系统] §e正在查找玩家...");
        plugin.getPlayerCacheManager().getPlayerUuid(targetName, uuid -> {
            if (uuid == null) {
                player.sendMessage("§c玩家不存在或从未加入过服务器！");
                return;
            }
            // 获取玩家名
            plugin.getPlayerCacheManager().getPlayerName(uuid, cachedName -> {
                String finalName = cachedName != null ? cachedName : targetName;
                sendAttachMail(player, uuid, finalName, title, item);
            });
        });
    }

    private void sendAttachMail(Player sender, UUID targetUuid, String targetName, String title, ItemStack item) {
        List<ItemStack> attachments = new ArrayList<>();
        attachments.add(item.clone());

        // 计算费用（1个附件）
        double totalCost = 0;
        if (plugin.getMailConfig().isEnableEconomy() && plugin.getEconomyManager().isEnabled()) {
            double postageFee = plugin.getMailConfig().getMailPostageFee();
            double deliveryFee = plugin.getMailConfig().getAttachmentDeliveryFee();
            totalCost = postageFee + deliveryFee;
        }

        final double finalCost = totalCost;
        Mail mail = new Mail(sender.getUniqueId(), sender.getName(),
                targetUuid, targetName, title, "");
        mail.setAttachments(attachments);

        plugin.getMailManager().sendMail(mail, sender, totalCost, success -> {
            if (success) {
                sender.getInventory().setItemInMainHand(null);
                sender.sendMessage("§a物品已发送给 §e" + targetName);
            }
            // 失败提示和扣费提示已在 sendMail 方法中处理
        });
    }

    private void handleClearCache(Player player) {
        if (!player.hasPermission("mailsystem.admin")) {
            player.sendMessage("§c你没有权限！");
            return;
        }

        plugin.getMailManager().getPlayerMailCache().clear();
        player.sendMessage("§a邮件缓存已清除。");
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("mailsystem.admin")) {
            player.sendMessage("§c你没有权限！");
            return;
        }

        plugin.reload();
        player.sendMessage("§a配置文件已重载！");
    }

    private void handleBlacklist(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fmail blacklist <add|remove|list> [玩家]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /fmail blacklist add <玩家>");
                    return;
                }
                String targetName = args[2];

                // 不能屏蔽自己
                if (targetName.equalsIgnoreCase(player.getName())) {
                    player.sendMessage("§c你不能屏蔽自己！");
                    return;
                }

                // 获取目标玩家UUID
                plugin.getPlayerCacheManager().getPlayerUuid(targetName, targetUuid -> {
                    if (targetUuid == null) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            player.sendMessage("§c玩家不存在或从未加入过服务器！"));
                        return;
                    }

                    // 检查是否已在黑名单
                    plugin.getMailManager().getBlacklist(player.getUniqueId(), blacklist -> {
                        if (blacklist.contains(targetUuid)) {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                player.sendMessage("§c该玩家已在你的黑名单中！"));
                            return;
                        }

                        // 添加到黑名单
                        plugin.getMailManager().addToBlacklist(player.getUniqueId(), targetUuid, success -> {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                if (success) {
                                    player.sendMessage("§a已将 §e" + targetName + " §a加入黑名单，对方将无法给你发送邮件。");
                                } else {
                                    player.sendMessage("§c添加失败，请稍后重试！");
                                }
                            });
                        });
                    });
                });
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /fmail blacklist remove <玩家>");
                    return;
                }
                String targetName = args[2];

                // 获取目标玩家UUID
                plugin.getPlayerCacheManager().getPlayerUuid(targetName, targetUuid -> {
                    if (targetUuid == null) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            player.sendMessage("§c玩家不存在或从未加入过服务器！"));
                        return;
                    }

                    // 从黑名单移除
                    plugin.getMailManager().removeFromBlacklist(player.getUniqueId(), targetUuid, success -> {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                            if (success) {
                                player.sendMessage("§a已将 §e" + targetName + " §a从黑名单中移除。");
                            } else {
                                player.sendMessage("§c该玩家不在你的黑名单中！");
                            }
                        });
                    });
                });
            }
            case "list" -> {
                plugin.getMailManager().getBlacklist(player.getUniqueId(), blacklist -> {
                    if (blacklist.isEmpty()) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            player.sendMessage("§a你的黑名单是空的。"));
                        return;
                    }

                    // 获取黑名单玩家名称
                    List<String> names = new ArrayList<>();
                    AtomicInteger pending = new AtomicInteger(blacklist.size());

                    for (UUID uuid : blacklist) {
                        plugin.getPlayerCacheManager().getPlayerName(uuid, name -> {
                            if (name != null) {
                                names.add(name);
                            } else {
                                names.add(uuid.toString().substring(0, 8));
                            }

                            if (pending.decrementAndGet() == 0) {
                                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                    player.sendMessage("§6======== 黑名单列表 ========");
                                    for (int i = 0; i < names.size(); i++) {
                                        player.sendMessage("§e" + (i + 1) + ". §f" + names.get(i));
                                    }
                                    player.sendMessage("§7共 §f" + names.size() + " §7人");
                                });
                            }
                        });
                    }
                });
            }
            default -> player.sendMessage("§c未知操作: " + action + "，可用: add, remove, list");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("§6======== 邮件系统帮助 ========");
        player.sendMessage("§e/fmail §f- 打开邮件系统GUI");
        player.sendMessage("§e/fmail send <玩家> <标题> [内容] §f- 发送邮件");
        player.sendMessage("§e/fmail write <玩家> <标题> §f- 交互式编写邮件");
        player.sendMessage("§e/fmail attach <玩家> [标题] §f- 发送手持物品");
        player.sendMessage("§e/fmail read §f- 查看未读邮件");
        player.sendMessage("§e/fmail list [页码] §f- 查看收件箱");
        player.sendMessage("§e/fmail sent §f- 查看已发送");
        player.sendMessage("§e/fmail open <邮件ID> §f- 打开邮件");
        player.sendMessage("§e/fmail claim <邮件ID> §f- 领取附件");
        player.sendMessage("§e/fmail delete <邮件ID> §f- 删除邮件");
        player.sendMessage("§e/fmail clear §f- 清空收件箱");
        player.sendMessage("§e/fmail blacklist <add|remove|list> [玩家] §f- 管理黑名单");

        // 显示费用信息
        if (plugin.getMailConfig().isEnableEconomy() && plugin.getEconomyManager().isEnabled()) {
            double postageFee = plugin.getMailConfig().getMailPostageFee();
            double deliveryFee = plugin.getMailConfig().getAttachmentDeliveryFee();
            if (postageFee > 0 || deliveryFee > 0) {
                player.sendMessage("§6--- 邮件费用 ---");
                if (postageFee > 0) {
                    player.sendMessage("§7基础邮费: §f" + plugin.getEconomyManager().format(postageFee));
                }
                if (deliveryFee > 0) {
                    player.sendMessage("§7快递费: §f" + plugin.getEconomyManager().format(deliveryFee) + "/个附件");
                }
            }
        }

        if (player.hasPermission("mailsystem.admin")) {
            player.sendMessage("§6--- 管理员命令 ---");
            player.sendMessage("§e/fmail broadcast <目标> <标题> [内容] §f- 群发邮件");
            player.sendMessage("§7  目标: online(在线), 3days(3天内), 7days(7天内), all(全部)");
            player.sendMessage("§e/fmail manage <玩家> §f- 管理指定玩家的邮件");
            player.sendMessage("§7  可删除/修改已读状态/修改领取状态");
            player.sendMessage("§e/fmail reload §f- 重载配置");
            player.sendMessage("§e/fmail clearcache §f- 清除缓存");
        }
    }

    private void handleBroadcast(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /fmail broadcast <目标> <标题> [内容]");
            player.sendMessage("§7目标类型:");
            player.sendMessage("§7  online - 所有在线玩家");
            player.sendMessage("§7  3days  - 3天内登录过的玩家");
            player.sendMessage("§7  7days  - 7天内登录过的玩家");
            player.sendMessage("§7  all    - 所有曾经登录过的玩家");
            return;
        }

        String target = args[1].toLowerCase();
        String title = args[2];

        if (title.length() > plugin.getMailConfig().getMaxMailTitleLength()) {
            player.sendMessage("§c标题过长，最大长度: " + plugin.getMailConfig().getMaxMailTitleLength());
            return;
        }

        StringBuilder content = new StringBuilder();
        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                content.append(args[i]).append(" ");
            }
        }
        String contentStr = content.toString().trim();
        if (contentStr.length() > plugin.getMailConfig().getMaxMailContentLength()) {
            player.sendMessage("§c内容过长，最大长度: " + plugin.getMailConfig().getMaxMailContentLength());
            return;
        }

        // 根据目标类型获取收件人列表
        switch (target) {
            case "online" -> broadcastToOnline(player, title, contentStr);
            case "3days" -> broadcastToRecent(player, title, contentStr, 3);
            case "7days" -> broadcastToRecent(player, title, contentStr, 7);
            case "all" -> broadcastToAll(player, title, contentStr);
            default -> player.sendMessage("§c未知的目标类型: " + target + "，可选: online, 3days, 7days, all");
        }
    }

    private void broadcastToOnline(Player sender, String title, String content) {
        Map<UUID, String> targets = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            targets.put(player.getUniqueId(), player.getName());
        }
        broadcastToTargets(sender, targets, title, content, "在线玩家");
    }

    private void broadcastToRecent(Player sender, String title, String content, int days) {
        long sinceTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        plugin.getPlayerCacheManager().getPlayersLoggedInSince(sinceTime, players ->
            broadcastToTargets(sender, players, title, content, days + "天内登录的玩家"));
    }

    private void broadcastToAll(Player sender, String title, String content) {
        plugin.getPlayerCacheManager().getAllCachedPlayers(players ->
            broadcastToTargets(sender, players, title, content, "玩家"));
    }

    private void broadcastToTargets(Player sender, Map<UUID, String> targets, String title, String content, String targetDesc) {
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger timeout = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<UUID, String> entry : targets.entrySet()) {
            Mail mail = new Mail(sender.getUniqueId(), sender.getName(),
                    entry.getKey(), entry.getValue(), title, content);
            CompletableFuture<Void> future = new CompletableFuture<>();
            plugin.getMailManager().sendMail(mail, sender, success -> {
                if (success) {
                    count.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
                future.complete(null);
            });
            futures.add(future);
        }

        // 等待所有发送完成，带超时
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(plugin.getMailConfig().getBroadcastTimeout(), java.util.concurrent.TimeUnit.SECONDS)
            .whenComplete((result, throwable) -> {
                int sent = count.get();
                int skip = skipped.get();
                int to = 0;
                if (throwable != null) {
                    // 超时发生，计算超时数量
                    to = (int) futures.stream().filter(f -> !f.isDone()).count();
                    timeout.set(to);
                    // 取消未完成的 future
                    futures.forEach(f -> f.cancel(true));
                }
                final int finalTimeout = to;
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    StringBuilder msg = new StringBuilder("§a[邮件系统] §e群发邮件已发送给 §f" + sent + " §e个" + targetDesc);
                    if (skip > 0) {
                        msg.append("，§c跳过 " + skip + " 个邮箱已满的玩家");
                    }
                    if (finalTimeout > 0) {
                        msg.append("，§c" + finalTimeout + " 个发送超时");
                    }
                    sender.sendMessage(msg.toString());
                });
            });
    }

    private void handleManage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fmail manage <玩家名>");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target != null && target.isOnline()) {
            // 在线玩家直接打开
            plugin.getGuiManager().openAdminMailManage(player, target.getUniqueId(), target.getName(), 1);
            return;
        }

        // 离线玩家查询缓存
        player.sendMessage("§a[邮件系统] §e正在查找玩家...");
        plugin.getPlayerCacheManager().getPlayerUuid(targetName, uuid -> {
            if (uuid == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    player.sendMessage("§c玩家不存在或从未加入过服务器！");
                });
                return;
            }

            plugin.getPlayerCacheManager().getPlayerName(uuid, cachedName -> {
                String nameToUse = (cachedName != null) ? cachedName : targetName;
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    plugin.getGuiManager().openAdminMailManage(player, uuid, nameToUse, 1);
                });
            });
        });
    }

    private boolean checkPermission(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            player.sendMessage("§c你没有权限执行此操作！");
            return false;
        }
        return true;
    }
}
