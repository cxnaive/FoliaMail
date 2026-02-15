package dev.user.mailsystem.mail;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import dev.user.mailsystem.MailSystemPlugin;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 附件管理器 - 处理物品附件的序列化和反序列化
 */
public class AttachmentManager {

    private final MailSystemPlugin plugin;

    public AttachmentManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 序列化物品列表为字节数组（使用NBT+GZIP压缩）
     *
     * @param items 物品列表
     * @return 序列化后的字节数组
     */
    public byte[] serialize(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        // 构建 NBT 字符串
        ReadWriteNBT nbtList = NBT.createNBTObject();
        nbtList.setInteger("size", items.size());
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item != null) {
                ReadWriteNBT itemNbt = NBT.itemStackToNBT(item);
                nbtList.setString("item_" + i, itemNbt.toString());
            }
        }
        String nbtString = nbtList.toString();

        // GZIP 压缩为二进制
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(nbtString.getBytes(StandardCharsets.UTF_8));
            gos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().warning("附件序列化失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化字节数组为物品列表
     *
     * @param data 字节数组
     * @return 物品列表
     */
    public List<ItemStack> deserialize(byte[] data) {
        List<ItemStack> items = new ArrayList<>();
        if (data == null || data.length == 0) {
            return items;
        }

        try {
            // GZIP 解压
            String nbtString;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 GZIPInputStream gis = new GZIPInputStream(bais)) {
                nbtString = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
            }

            ReadWriteNBT nbtList = NBT.parseNBT(nbtString);
            int size = nbtList.getInteger("size");
            for (int i = 0; i < size; i++) {
                String itemStr = nbtList.getString("item_" + i);
                if (itemStr != null && !itemStr.isEmpty()) {
                    ReadWriteNBT itemNbt = NBT.parseNBT(itemStr);
                    ItemStack item = NBT.itemStackFromNBT(itemNbt);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("反序列化附件失败: " + e.getMessage());
        }
        return items;
    }

    /**
     * 检查物品是否有效（非空且不是空气）
     *
     * @param item 物品
     * @return 是否有效
     */
    public boolean isValidItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    /**
     * 克隆物品列表
     *
     * @param items 原列表
     * @return 克隆后的列表
     */
    public List<ItemStack> cloneList(List<ItemStack> items) {
        List<ItemStack> cloned = new ArrayList<>();
        if (items == null) {
            return cloned;
        }
        for (ItemStack item : items) {
            if (isValidItem(item)) {
                cloned.add(item.clone());
            }
        }
        return cloned;
    }
}
