package com.github.game.cdda.item.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 可消耗类型枚举。
 * 使用位标志（bit flag）设计，支持物品同时拥有多种消耗类型
 * （如：食物 + 药物 = 药膳）。
 */
public enum ConsumableType {

    /** 食物 — 提供热量和饱腹度 */
    FOOD(1, "食物"),
    /** 饮用水 — 补充水分 */
    WATER(2, "水"),
    /** 药品 — 治疗或缓解症状 */
    MEDICINE(4, "药品");

    /** 位标志值（2的幂次） */
    private final int bit;
    /** 显示名称 */
    private final String displayName;

    ConsumableType(int bit, String displayName) {
        this.bit = bit;
        this.displayName = displayName;
    }

    public int getBit() { return bit; }
    public String getDisplayName() { return displayName; }

    /**
     * 将 Set&lt;ConsumableType&gt; 编码为位掩码（便于序列化）。
     */
    public static int toMask(Set<ConsumableType> types) {
        int mask = 0;
        for (ConsumableType t : types) {
            mask |= t.bit;
        }
        return mask;
    }

    /**
     * 从位掩码解码为 Set&lt;ConsumableType&gt;（便于反序列化）。
     */
    public static Set<ConsumableType> fromMask(int mask) {
        Set<ConsumableType> result = EnumSet.noneOf(ConsumableType.class);
        for (ConsumableType t : values()) {
            if ((mask & t.bit) != 0) {
                result.add(t);
            }
        }
        return result;
    }
}
