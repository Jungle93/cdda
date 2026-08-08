package com.github.game.cdda.item;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 物品类型定义（不可变模板）。
 * 定义物品的固有属性：名称、描述、重量、体积、堆叠上限、消耗类型、营养值等。
 * 通过 {@link Builder} 构建，通过 {@link ItemRegistry} 注册。
 */
public class ItemType {

    // ── 字段 ──────────────────────────────────────────
    private final int id;
    private final String name;
    private final String description;
    /** 单件重量（克） */
    private final double weightGrams;
    /** 单件体积（毫升） */
    private final double volumeMl;
    /** 最大堆叠数 */
    private final int maxStackSize;
    /** 是否唯一物品（不可堆叠，即使 maxStackSize > 1） */
    private final boolean unique;
    /** 可消耗类型标签集合 */
    private final Set<ConsumableType> consumableTypes;

    // ── 营养值（仅对食物/饮品有意义） ──
    /** 热量（千卡 kcal） */
    private final double calories;
    /** 饱腹度（0-100 相对值） */
    private final double satiety;
    /** 含水量（毫升） */
    private final double waterContent;

    // ── 私有构造 ──
    private ItemType(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.weightGrams = b.weightGrams;
        this.volumeMl = b.volumeMl;
        this.maxStackSize = b.maxStackSize;
        this.unique = b.unique;
        this.consumableTypes = b.consumableTypes.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(b.consumableTypes));
        this.calories = b.calories;
        this.satiety = b.satiety;
        this.waterContent = b.waterContent;
    }

    // ── 访问器 ──────────────────────────────────────────
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getWeightGrams() { return weightGrams; }
    public double getVolumeMl() { return volumeMl; }
    public int getMaxStackSize() { return maxStackSize; }
    public boolean isUnique() { return unique; }
    public Set<ConsumableType> getConsumableTypes() { return consumableTypes; }

    public double getCalories() { return calories; }
    public double getSatiety() { return satiety; }
    public double getWaterContent() { return waterContent; }

    /** 是否可食用/饮用/使用（有任何消耗标签即可） */
    public boolean isConsumable() { return !consumableTypes.isEmpty(); }

    /** 检查是否包含特定消耗类型 */
    public boolean hasConsumableType(ConsumableType type) {
        return consumableTypes.contains(type);
    }

    // ── 显示（带单位转换） ──
    /** 获取格式化重量（按指定单位） */
    public String formatWeight(DisplayUnit unit) {
        return unit.format(weightGrams);
    }

    /** 获取格式化体积（按指定单位） */
    public String formatVolume(DisplayUnit unit) {
        return unit.format(volumeMl);
    }

    @Override
    public String toString() {
        return "ItemType{" + name + "(id=" + id + ")}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemType other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() { return Integer.hashCode(id); }

    // ── Builder ──────────────────────────────────────────
    /**
     * 物品类型构建器。
     * 所有可选属性均有合理默认值，链式调用设置所需属性后 build()。
     */
    public static class Builder {
        private int id = -1;
        private String name = "";
        private String description = "";
        private double weightGrams = 0;
        private double volumeMl = 0;
        private int maxStackSize = 1;
        private boolean unique = false;
        private Set<ConsumableType> consumableTypes = EnumSet.noneOf(ConsumableType.class);
        private double calories = 0;
        private double satiety = 0;
        private double waterContent = 0;

        /** 创建 Builder（id 和 name 必填） */
        public Builder(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder description(String desc) { this.description = desc; return this; }
        public Builder weight(double grams) { this.weightGrams = grams; return this; }
        public Builder volume(double ml) { this.volumeMl = ml; return this; }
        public Builder maxStackSize(int max) { this.maxStackSize = max; return this; }

        /** 标记为唯一物品（不可堆叠） */
        public Builder unique() { this.unique = true; this.maxStackSize = 1; return this; }

        /** 设置可消耗类型（多标签） */
        public Builder consumable(ConsumableType... types) {
            for (ConsumableType t : types) this.consumableTypes.add(t);
            return this;
        }

        public Builder consumables(Set<ConsumableType> types) {
            this.consumableTypes = EnumSet.copyOf(types);
            return this;
        }

        /**
         * 设置营养值。
         * @param calories 热量（千卡 kcal）
         * @param satiety  饱腹度（0-100 相对值）
         * @param waterContent 含水量（毫升）
         */
        public Builder nutrition(double calories, double satiety, double waterContent) {
            this.calories = calories;
            this.satiety = satiety;
            this.waterContent = waterContent;
            return this;
        }

        /**
         * 构建 ItemType 实例（不注册）。
         */
        public ItemType build() {
            if (id < 0) throw new IllegalStateException("id 未设置");
            if (name == null || name.isBlank()) throw new IllegalStateException("name 未设置");
            if (weightGrams < 0) throw new IllegalStateException("重量不能为负");
            if (volumeMl < 0) throw new IllegalStateException("体积不能为负");
            if (maxStackSize < 1) throw new IllegalStateException("堆叠上限至少为1");
            return new ItemType(this);
        }

        /**
         * 构建并注册到 ItemRegistry（便捷方法）。
         */
        public ItemType buildAndRegister() {
            ItemType type = build();
            ItemRegistry.registerMod(type);
            return type;
        }
    }
}
