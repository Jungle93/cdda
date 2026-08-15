package com.github.game.cdda.item.model;

import com.github.game.cdda.item.registry.ItemRegistry;

import com.github.game.engine.core.i18n.I18nManager;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
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
    /** 中文显示名（用于 UI 展示） */
    private final String displayName;
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
    /** 功能标签集合（如 "chopping"、"cooking" 等，用于物品动作系统） */
    private final Set<String> tags;
    /** 图标字符（可选，用于 UI 展示；未设置时使用 displayName 首字回退） */
    private final String icon;

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
        this.displayName = b.displayName != null ? b.displayName : b.name;
        this.description = b.description;
        this.weightGrams = b.weightGrams;
        this.volumeMl = b.volumeMl;
        this.maxStackSize = b.maxStackSize;
        this.unique = b.unique;
        this.consumableTypes = b.consumableTypes.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(b.consumableTypes));
        this.tags = b.tags.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(b.tags));
        this.icon = b.icon;
        this.calories = b.calories;
        this.satiety = b.satiety;
        this.waterContent = b.waterContent;
    }

    // ── 访问器 ──────────────────────────────────────────
    public int getId() { return id; }
    public String getName() { return name; }

    /**
     * 获取显示名（优先从 i18n 获取，回退到本地 displayName，最后回退到 name）。
     */
    public String getDisplayName() {
        // 优先通过 i18n 系统获取
        String key = "item." + name + ".name";
        String i18nValue = resolveI18n(key);
        if (i18nValue != null) return i18nValue;
        // 回退到本地存储的 displayName
        if (displayName != null && !displayName.isBlank()) return displayName;
        // 最后回退到技术名称
        return name;
    }

    /**
     * 获取描述（优先从 i18n 获取，回退到本地 description）。
     */
    public String getDescription() {
        String key = "item." + name + ".description";
        String i18nValue = resolveI18n(key);
        if (i18nValue != null) return i18nValue;
        return description;
    }

    /** 尝试通过 I18nManager 解析翻译键，未找到时返回 null */
    private String resolveI18n(String key) {
        try {
            I18nManager i18n = com.github.game.engine.core.EngineServices.i18n;
            if (i18n == null) return null;
            String value = i18n.t(key);
            // 如果返回值等于 key 本身，说明未找到翻译
            return key.equals(value) ? null : value;
        } catch (Exception e) {
            // I18nManager 未初始化时静默回退
            return null;
        }
    }
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

    /** 检查是否包含特定功能标签 */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** 获取所有功能标签（不可变集合） */
    public Set<String> getTags() { return tags; }

    /**
     * 获取图标字符。
     * 优先返回显式设置的 icon；若未设置，回退到 displayName 的首字符。
     *
     * @return 图标字符串（1-2 个字符），不会为 null
     */
    public String getIcon() {
        if (icon != null && !icon.isEmpty()) return icon;
        String dn = getDisplayName();
        if (dn != null && !dn.isEmpty()) {
            // 取第一个字符（兼容 surrogate pair）
            int cp = dn.codePointAt(0);
            return new String(Character.toChars(cp));
        }
        return "?";
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
        private String displayName = "";
        private String description = "";
        private double weightGrams = 0;
        private double volumeMl = 0;
        private int maxStackSize = 1;
        private boolean unique = false;
        private Set<ConsumableType> consumableTypes = EnumSet.noneOf(ConsumableType.class);
        private Set<String> tags = new HashSet<>();
        private double calories = 0;
        private double satiety = 0;
        private double waterContent = 0;
        private String icon = null;

        /** 创建 Builder（id 和 name 必填） */
        public Builder(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * 设置物品描述。
         *
         * @param desc 描述文本
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder description(String desc) { this.description = desc; return this; }
        /** 设置显示名（中文，用于 UI 展示） */
        public Builder displayName(String name) { this.displayName = name; return this; }

        /**
         * 设置单件重量。
         *
         * @param grams 重量（克）
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder weight(double grams) { this.weightGrams = grams; return this; }

        /**
         * 设置单件体积。
         *
         * @param ml 体积（毫升）
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder volume(double ml) { this.volumeMl = ml; return this; }

        /**
         * 设置最大堆叠数。
         *
         * @param max 堆叠上限（至少为 1）
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder maxStackSize(int max) { this.maxStackSize = max; return this; }

        /** 标记为唯一物品（不可堆叠） */
        public Builder unique() { this.unique = true; this.maxStackSize = 1; return this; }

        /** 设置可消耗类型（多标签） */
        public Builder consumable(ConsumableType... types) {
            for (ConsumableType t : types) this.consumableTypes.add(t);
            return this;
        }

        /**
         * 设置可消耗类型（多标签）。
         *
         * @param types 可消耗类型集合
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder consumables(Set<ConsumableType> types) {
            this.consumableTypes = EnumSet.copyOf(types);
            return this;
        }

        /** 添加功能标签（如 "chopping"、"cooking"） */
        public Builder tag(String... tags) {
            for (String t : tags) this.tags.add(t);
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
         * 设置图标字符。
         * 用于 UI 展示（如背包详情面板）。未设置时回退到 displayName 首字。
         *
         * @param icon 图标字符（1-2 个字符的字符串）
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder icon(String icon) { this.icon = icon; return this; }

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
