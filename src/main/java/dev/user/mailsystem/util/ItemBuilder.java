package dev.user.mailsystem.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 物品构建器 - 简化物品创建过程
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    /**
     * 创建一个新的物品构建器
     *
     * @param material 物品材质
     */
    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    /**
     * 创建一个新的物品构建器
     *
     * @param material 物品材质
     * @param amount   物品数量
     */
    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    /**
     * 从现有ItemStack创建构建器
     *
     * @param item 现有物品
     */
    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    /**
     * 设置显示名称
     *
     * @param name 名称（支持颜色代码&）
     * @return ItemBuilder
     */
    public ItemBuilder setName(String name) {
        if (meta != null) {
            meta.displayName(Component.text(colorize(name)).decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    /**
     * 设置显示名称（Component版本）
     *
     * @param component 名称组件
     * @return ItemBuilder
     */
    public ItemBuilder setName(Component component) {
        if (meta != null) {
            meta.displayName(component.decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    /**
     * 设置Lore
     *
     * @param lore Lore列表（支持颜色代码&）
     * @return ItemBuilder
     */
    public ItemBuilder setLore(List<String> lore) {
        if (meta != null) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(Component.text(colorize(line)).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(components);
        }
        return this;
    }

    /**
     * 设置Lore（可变参数版本）
     *
     * @param lore Lore行（支持颜色代码&）
     * @return ItemBuilder
     */
    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }

    /**
     * 添加一行Lore
     *
     * @param line Lore行（支持颜色代码&）
     * @return ItemBuilder
     */
    public ItemBuilder addLore(String line) {
        if (meta != null) {
            List<Component> lore = meta.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(Component.text(colorize(line)).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        return this;
    }

    /**
     * 设置数量
     *
     * @param amount 数量
     * @return ItemBuilder
     */
    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * 添加附魔
     *
     * @param enchantment 附魔类型
     * @param level       等级
     * @return ItemBuilder
     */
    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * 移除附魔
     *
     * @param enchantment 附魔类型
     * @return ItemBuilder
     */
    public ItemBuilder removeEnchantment(Enchantment enchantment) {
        if (meta != null) {
            meta.removeEnchant(enchantment);
        }
        return this;
    }

    /**
     * 添加物品标志
     *
     * @param flags 物品标志
     * @return ItemBuilder
     */
    public ItemBuilder addItemFlags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * 移除物品标志
     *
     * @param flags 物品标志
     * @return ItemBuilder
     */
    public ItemBuilder removeItemFlags(ItemFlag... flags) {
        if (meta != null) {
            meta.removeItemFlags(flags);
        }
        return this;
    }

    /**
     * 设置不可破坏
     *
     * @param unbreakable 是否不可破坏
     * @return ItemBuilder
     */
    public ItemBuilder setUnbreakable(boolean unbreakable) {
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
        }
        return this;
    }

    /**
     * 设置自定义模型数据
     *
     * @param modelData 模型数据
     * @return ItemBuilder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder setCustomModelData(int modelData) {
        if (meta != null) {
            meta.setCustomModelData(modelData);
        }
        return this;
    }

    /**
     * 编辑ItemMeta
     *
     * @param consumer ItemMeta消费者
     * @return ItemBuilder
     */
    public ItemBuilder editMeta(Consumer<ItemMeta> consumer) {
        if (meta != null) {
            consumer.accept(meta);
        }
        return this;
    }

    /**
     * 设置发光效果（添加一个无用的附魔并隐藏附魔提示）
     *
     * @param glow 是否发光
     * @return ItemBuilder
     */
    public ItemBuilder setGlowing(boolean glow) {
        if (meta != null) {
            if (glow) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.removeEnchant(Enchantment.LURE);
                meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        return this;
    }

    /**
     * 构建ItemStack
     *
     * @return ItemStack
     */
    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item.clone();
    }

    /**
     * 颜色代码转换
     *
     * @param text 原始文本
     * @return 转换后的文本
     */
    private String colorize(String text) {
        if (text == null) return "";
        char[] chars = text.toCharArray();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                result.append('§');
            } else {
                result.append(chars[i]);
            }
        }
        return result.toString();
    }

    /**
     * 快速创建按钮物品
     *
     * @param material 材质
     * @param name     名称
     * @param lore     Lore
     * @return ItemStack
     */
    public static ItemStack createButton(Material material, String name, String... lore) {
        return new ItemBuilder(material)
                .setName(name)
                .setLore(lore)
                .build();
    }

    /**
     * 快速创建装饰物品（不可交互）
     *
     * @param material 材质
     * @param name     名称
     * @return ItemStack
     */
    public static ItemStack createDecoration(Material material, String name) {
        return new ItemBuilder(material)
                .setName(name)
                .addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                .build();
    }
}
