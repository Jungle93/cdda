package com.github.game.cdda.item;

import java.util.Set;

/**
 * 物品类型定义（JSON 可序列化 POJO）。
 * <p>
 * 对应 {@link ItemType} 的所有字段，用于从 JSON 文件反序列化物品模板。
 * 通过 {@link #toItemType()} 转换为不可变的 {@link ItemType} 实例。
 * <p>
 * JSON 示例：
 * <pre>
 * {
 *   "id": 2,
 *   "name": "bread",
 *   "description": "一块基本的面包，能填饱肚子",
 *   "weightGrams": 300,
 *   "volumeMl": 400,
 *   "maxStackSize": 5,
 *   "consumableTypes": 1,
 *   "tags": ["cooking"],
 *   "calories": 350,
 *   "satiety": 60,
 *   "waterContent": 30
 * }
 * </pre>
 *
 * @see ConsumableType#toMask(Set)
 * @see ConsumableType#fromMask(int)
 */
public class ItemDefinition {

    /** 物品数字 ID（必填） */
    public int id;
    /** 物品字符串名称（必填） */
    public String name;
    /** 物品描述 */
    public String description = "";
    /** 单件重量（克） */
    public double weightGrams = 0;
    /** 单件体积（毫升） */
    public double volumeMl = 0;
    /** 最大堆叠数 */
    public int maxStackSize = 1;
    /** 是否唯一物品 */
    public boolean unique = false;
    /** 可消耗类型位掩码（FOOD=1, WATER=2, MEDICINE=4） */
    public int consumableTypes = 0;
    /** 功能标签（如 "chopping", "cooking", "crafting"） */
    public String[] tags = new String[0];
    /** 热量（千卡 kcal） */
    public double calories = 0;
    /** 饱腹度（0-100 相对值） */
    public double satiety = 0;
    /** 含水量（毫升） */
    public double waterContent = 0;

    /**
     * 将定义转换为不可变的 {@link ItemType} 实例。
     *
     * @return 转换后的 ItemType
     */
    public ItemType toItemType() {
        ItemType.Builder builder = new ItemType.Builder(id, name);

        builder.description(description);
        builder.weight(weightGrams);
        builder.volume(volumeMl);

        if (unique) {
            builder.unique();
        } else {
            builder.maxStackSize(maxStackSize);
        }

        Set<ConsumableType> types = ConsumableType.fromMask(consumableTypes);
        if (!types.isEmpty()) {
            builder.consumables(types);
        }

        if (tags != null && tags.length > 0) {
            builder.tag(tags);
        }

        if (calories > 0 || satiety > 0 || waterContent > 0) {
            builder.nutrition(calories, satiety, waterContent);
        }

        return builder.build();
    }
}
