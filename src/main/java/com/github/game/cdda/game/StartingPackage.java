package com.github.game.cdda.game;

/**
 * 起始装备包枚举。
 *
 * <p>每个包包含一组初始物品，玩家在创建角色时选择。
 * 不同的起始包反映不同的生存风格：
 * <ul>
 *   <li>NONE — 无装备，极限挑战</li>
 *   <li>EXPLORER — 探索者：基础工具 + 食物 + 水</li>
 *   <li>FARMER — 农夫：种子 + 锄具 + 食物</li>
 *   <li>HUNTER — 猎人：武器 + 绳索 + 食物</li>
 *   <li>CRAFTER — 工匠：大量基础材料</li>
 * </ul>
 */
public enum StartingPackage {

    /** 无装备 */
    NONE("无", new ItemEntry[0]),

    /** 探索者 — 基础工具 + 应急物资 */
    EXPLORER("探索者", new ItemEntry[]{
            new ItemEntry("rusty_knife", 1),
            new ItemEntry("canned_food", 3),
            new ItemEntry("water_bottle", 2),
            new ItemEntry("small_branch", 3),
            new ItemEntry("stone", 1),
    }),

    /** 农夫 — 种子 + 基础农具 */
    FARMER("农夫", new ItemEntry[]{
            new ItemEntry("rusty_knife", 1),
            new ItemEntry("canned_food", 2),
            new ItemEntry("water_bottle", 1),
            new ItemEntry("barley_seed", 5),
            new ItemEntry("turnip_seed", 5),
            new ItemEntry("bean_seed", 5),
            new ItemEntry("small_branch", 3),
            new ItemEntry("fiber_cord", 2),
    }),

    /** 猎人 — 武器 + 狩猎工具 */
    HUNTER("猎人", new ItemEntry[]{
            new ItemEntry("rusty_knife", 1),
            new ItemEntry("stone_axe", 1),
            new ItemEntry("canned_food", 2),
            new ItemEntry("water_bottle", 1),
            new ItemEntry("fiber_cord", 5),
            new ItemEntry("small_branch", 5),
            new ItemEntry("stone", 3),
    }),

    /** 工匠 — 大量基础材料 */
    CRAFTER("工匠", new ItemEntry[]{
            new ItemEntry("rusty_knife", 1),
            new ItemEntry("stone_axe", 1),
            new ItemEntry("canned_food", 1),
            new ItemEntry("water_bottle", 1),
            new ItemEntry("small_branch", 8),
            new ItemEntry("stone", 5),
            new ItemEntry("fiber_cord", 5),
    });

    /** 物品条目 */
    public static class ItemEntry {
        /** 物品 ID */
        public final String itemId;
        /** 数量 */
        public final int count;

        public ItemEntry(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    /** 显示名称 */
    private final String displayName;
    /** 物品列表 */
    private final ItemEntry[] items;

    StartingPackage(String displayName, ItemEntry[] items) {
        this.displayName = displayName;
        this.items = items;
    }

    /**
     * 获取显示名称。
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取物品列表。
     */
    public ItemEntry[] getItems() {
        return items;
    }

    /**
     * 获取所有可用的起始包（排除 NONE）。
     */
    public static StartingPackage[] getAvailable() {
        StartingPackage[] all = values();
        StartingPackage[] available = new StartingPackage[all.length - 1];
        System.arraycopy(all, 1, available, 0, available.length);
        return available;
    }

    /**
     * 切换到下一个起始包。
     *
     * @param direction 方向（正数向前，负数向后）
     * @return 下一个起始包
     */
    public StartingPackage cycle(int direction) {
        StartingPackage[] all = values();
        int idx = ordinal();
        idx = (idx + direction + all.length) % all.length;
        return all[idx];
    }

    @Override
    public String toString() {
        return displayName;
    }
}
