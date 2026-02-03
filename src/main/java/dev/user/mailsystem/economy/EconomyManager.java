package dev.user.mailsystem.economy;

import dev.user.mailsystem.MailSystemPlugin;
import me.yic.xconomy.api.XConomyAPI;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 经济管理器 - XConomy软依赖支持
 */
public class EconomyManager {

    private final MailSystemPlugin plugin;
    private XConomyAPI xconomyAPI;
    private boolean enabled = false;

    public EconomyManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 尝试加载经济插件
     */
    public void init() {
        if (plugin.getServer().getPluginManager().getPlugin("XConomy") != null) {
            try {
                xconomyAPI = new XConomyAPI();
                enabled = true;
                plugin.getLogger().info("已连接到 XConomy 经济系统");
            } catch (Exception e) {
                plugin.getLogger().warning("XConomy 加载失败: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("未检测到经济插件，经济功能已禁用");
        }
    }

    /**
     * 检查经济功能是否可用
     */
    public boolean isEnabled() {
        return enabled && xconomyAPI != null;
    }

    /**
     * 获取玩家余额
     */
    public double getBalance(Player player) {
        if (!isEnabled()) return 0;
        try {
            return xconomyAPI.getPlayerData(player.getUniqueId()).getBalance().doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("获取余额失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取玩家余额
     */
    public double getBalance(UUID uuid) {
        if (!isEnabled()) return 0;
        try {
            return xconomyAPI.getPlayerData(uuid).getBalance().doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("获取余额失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 扣除玩家金钱
     * @param player 玩家
     * @param amount 金额
     * @return 是否成功
     */
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled()) return true;
        if (amount <= 0) return true;

        try {
            // 直接尝试扣除，依赖 XConomyAPI 的原子性保证
            // isadd = false 表示扣除
            int result = xconomyAPI.changePlayerBalance(player.getUniqueId(), player.getName(), BigDecimal.valueOf(amount), false);
            return result == 0; // 0 表示成功，其他值表示失败（如余额不足）
        } catch (Exception e) {
            plugin.getLogger().warning("扣除金钱失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 给玩家增加金钱
     */
    public boolean deposit(Player player, double amount) {
        if (!isEnabled()) return true;
        if (amount <= 0) return true;

        try {
            // isadd = true 表示增加
            int result = xconomyAPI.changePlayerBalance(player.getUniqueId(), player.getName(), BigDecimal.valueOf(amount), true);
            return result == 0; // 0 表示成功
        } catch (Exception e) {
            plugin.getLogger().warning("增加金钱失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 给离线玩家增加金钱
     */
    public boolean deposit(UUID uuid, double amount) {
        if (!isEnabled()) return true;
        if (amount <= 0) return true;

        try {
            // playerName 传 null 表示离线操作
            int result = xconomyAPI.changePlayerBalance(uuid, null, BigDecimal.valueOf(amount), true);
            return result == 0; // 0 表示成功
        } catch (Exception e) {
            plugin.getLogger().warning("增加金钱失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查玩家是否有足够余额
     */
    public boolean hasEnough(Player player, double amount) {
        if (!isEnabled()) return true;
        if (amount <= 0) return true;
        return getBalance(player) >= amount;
    }

    /**
     * 格式化货币显示
     */
    public String format(double amount) {
        String currencyName = plugin.getMailConfig().getCurrencyName();
        return String.format("%.2f %s", amount, currencyName);
    }
}
