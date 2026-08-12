package com.github.game.cdda.item.registry;

import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 战利品表。
 * 定义生物死亡时可能掉落的物品及其概率。
 * 通过 Gson 从 JSON 加载，与 {@link com.github.game.cdda.creature.config.CreatureDefinition} 配合使用。
 *
 * <p>JSON 格式示例：
 * <pre>
 * "lootTable": {
 *   "entries": [
 *     { "itemId": 2, "chance": 0.6, "minCount": 1, "maxCount": 3 },
 *     { "itemId": 4, "chance": 0.3, "minCount": 1, "maxCount": 2 }
 *   ]
 * }
 * </pre>
 */
public class LootTable {

    /** 掉落条目列表 */
    public List<Entry> entries;

    /**
     * 掉落条目。
     * 每个条目定义一种可能的掉落物。
     */
    public static class Entry {
        /** 物品 ID（对应 ItemRegistry） */
        public int itemId;
        /** 掉落概率（0.0~1.0） */
        public double chance;
        /** 最小数量 */
        public int minCount;
        /** 最大数量 */
        public int maxCount;
    }

    /**
     * 随机掷骰，生成本次掉落物品列表。
     * 对每个条目独立判定，可能返回空列表。
     *
     * @return 掉落物品列表（可能为空）
     */
    public List<ItemStack> roll() {
        return roll(new Random());
    }

    /**
     * 使用指定随机数生成器掷骰。
     *
     * @param random 随机数生成器
     * @return 掉落物品列表
     */
    public List<ItemStack> roll(Random random) {
        List<ItemStack> drops = new ArrayList<>();
        if (entries == null) return drops;

        for (Entry entry : entries) {
            if (random.nextDouble() < entry.chance) {
                ItemType type = ItemRegistry.getById(entry.itemId);
                if (type == null) continue;

                int count;
                if (entry.minCount >= entry.maxCount) {
                    count = entry.minCount;
                } else {
                    count = entry.minCount + random.nextInt(entry.maxCount - entry.minCount + 1);
                }

                // 考虑 maxStackSize，可能需要拆分多个 ItemStack
                int maxStack = type.getMaxStackSize();
                while (count > 0) {
                    int stackSize = Math.min(count, maxStack);
                    drops.add(new ItemStack(type, stackSize));
                    count -= stackSize;
                }
            }
        }

        return drops;
    }
}
