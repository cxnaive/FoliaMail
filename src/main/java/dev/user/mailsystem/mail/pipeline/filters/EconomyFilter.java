package dev.user.mailsystem.mail.pipeline.filters;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.api.draft.SendResult;
import dev.user.mailsystem.mail.pipeline.SendChain;
import dev.user.mailsystem.mail.pipeline.SendContext;
import dev.user.mailsystem.mail.pipeline.SendFilter;

import java.util.List;
import java.util.UUID;

/**
 * 经济过滤器 - 计算费用并扣费
 * 统一使用批量逻辑（size==1也是批量的一种）
 */
public class EconomyFilter implements SendFilter {

    private final MailSystemPlugin plugin;

    public EconomyFilter(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void filterBatch(List<SendContext> contexts, SendChain chain) {
        // 检查是否需要扣费
        boolean allSkip = contexts.stream().allMatch(SendContext::isSkipPayment);
        if (allSkip) {
            for (SendContext ctx : contexts) {
                ctx.setCalculatedCost(0);
            }
            chain.next(contexts, null);
            return;
        }

        // 检查经济功能是否启用
        if (!plugin.getMailConfig().isEnableEconomy() || !plugin.getEconomyManager().isEnabled()) {
            for (SendContext ctx : contexts) {
                ctx.setCalculatedCost(0);
            }
            chain.next(contexts, null);
            return;
        }

        // 获取发送者（批量模式下所有context应该有相同的发送者）
        UUID senderUuid = null;
        org.bukkit.entity.Player sender = null;
        for (SendContext ctx : contexts) {
            if (!ctx.isSkipPayment() && ctx.getSender() != null) {
                senderUuid = ctx.getSenderUuid();
                sender = ctx.getSender();
                break;
            }
        }

        // 检查发送者是否存在
        if (senderUuid == null) {
            // 系统发送，不扣费
            for (SendContext ctx : contexts) {
                ctx.setCalculatedCost(0);
            }
            chain.next(contexts, null);
            return;
        }

        // 计算总费用
        double totalCost = 0;
        for (SendContext ctx : contexts) {
            double cost = calculateCost(ctx);
            ctx.setCalculatedCost(cost);
            totalCost += cost;
        }

        if (totalCost <= 0) {
            chain.next(contexts, null);
            return;
        }

        final org.bukkit.entity.Player finalSender = sender;
        final double finalTotalCost = totalCost;

        // 统一批量逻辑（size==1也是批量的一种）
        // 检查余额
        if (!plugin.getEconomyManager().hasEnough(finalSender, finalTotalCost)) {
            String msg = "余额不足！需要 " + plugin.getEconomyManager().format(finalTotalCost) +
                    "，当前余额 " + plugin.getEconomyManager().format(
                            plugin.getEconomyManager().getBalance(finalSender));
            finalSender.sendMessage("§c[邮件系统] §e" + msg);
            chain.fail(SendResult.FailReason.INSUFFICIENT_FUNDS, msg);
            return;
        }

        // 扣除费用（原子操作，依赖经济插件的实现）
        // 注意：如果经济插件不支持原子性，此处存在TOCTOU竞态
        if (!plugin.getEconomyManager().withdraw(finalSender, finalTotalCost)) {
            String msg = "扣费失败！可能在检查余额后，您的余额被其他操作扣减。当前余额 " +
                    plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(finalSender));
            finalSender.sendMessage("§c[邮件系统] §e" + msg);
            chain.fail(SendResult.FailReason.INSUFFICIENT_FUNDS, msg);
            return;
        }

        // 扣费成功提示
        finalSender.getScheduler().run(plugin, task -> {
            String countStr = contexts.size() == 1 ? "" : " (共" + contexts.size() + "封)";
            finalSender.sendMessage("§a[邮件系统] §e已扣费 " + plugin.getEconomyManager().format(finalTotalCost) + countStr);
        }, null);

        chain.next(contexts, null);
    }

    /**
     * 计算发送费用
     */
    private double calculateCost(SendContext ctx) {
        // 检查是否有自定义费用
        if (ctx.getOptions().hasCustomCost()) {
            return ctx.getOptions().getCustomCost();
        }

        var draft = ctx.getDraft();
        double postageFee = plugin.getMailConfig().getMailPostageFee();
        double deliveryFee = draft.getAttachments().size() * plugin.getMailConfig().getAttachmentDeliveryFee();
        double moneyAttach = draft.getMoneyAttachment();

        return postageFee + deliveryFee + moneyAttach;
    }
}
