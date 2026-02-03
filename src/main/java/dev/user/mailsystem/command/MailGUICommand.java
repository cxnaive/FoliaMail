package dev.user.mailsystem.command;

import dev.user.mailsystem.MailSystemPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /fmail 命令 - 无参数时打开GUI
 */
public class MailGUICommand extends Command {

    private final MailSystemPlugin plugin;

    public MailGUICommand(MailSystemPlugin plugin) {
        super("fmailgui", "打开邮件系统GUI", "/fmailgui", Collections.emptyList());
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (!player.hasPermission("mailsystem.use")) {
            player.sendMessage("§c你没有权限使用邮件系统！");
            return true;
        }

        // 打开主菜单GUI
        plugin.getGuiManager().openMainMenu(player);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return Collections.emptyList();
    }
}
