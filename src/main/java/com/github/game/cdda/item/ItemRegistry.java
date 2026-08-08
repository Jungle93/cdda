package com.github.game.cdda.item;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物品类型注册表（遵循 TileType 注册模式）。
 * <p>
 * 内置物品在静态初始化时注册。Mod 可通过 {@link #registerMod} 在运行时注册新类型。
 * <p>
 * ID 分配约定：
 * <ul>
 *   <li>0–999：内置物品（Base game）</li>
 *   <li>1000+：Mod 物品</li>
 * </ul>
 */
public final class ItemRegistry {

    /** 数字 ID → ItemType 注册表（有序） */
    private static final Map<Integer, ItemType> REGISTRY = new LinkedHashMap<>();
    /** 字符串 name → ItemType 注册表（有序） */
    private static final Map<String, ItemType> NAME_REGISTRY = new LinkedHashMap<>();

    private ItemRegistry() {} // 不可实例化

    // ── 内置物品 ─────────────────────────────────────────

    // ── 饮品 ──
    public static final ItemType WATER_BOTTLE = registerBuiltin(
            new ItemType.Builder(0, "water_bottle")
                    .description("一瓶干净的饮用水")
                    .weight(550).volume(500)
                    .maxStackSize(4)
                    .consumable(ConsumableType.WATER)
                    .nutrition(0, 0, 500)
                    .build()
    );

    public static final ItemType DIRTY_WATER = registerBuiltin(
            new ItemType.Builder(1, "dirty_water")
                    .description("浑浊的水，饮用可能导致不适")
                    .weight(550).volume(500)
                    .maxStackSize(4)
                    .consumable(ConsumableType.WATER)
                    .nutrition(0, 0, 500)
                    .build()
    );

    // ── 食物 ──
    public static final ItemType BREAD = registerBuiltin(
            new ItemType.Builder(2, "bread")
                    .description("一块基本的面包，能填饱肚子")
                    .weight(300).volume(400)
                    .maxStackSize(5)
                    .consumable(ConsumableType.FOOD)
                    .nutrition(350, 60, 30)
                    .build()
    );

    public static final ItemType CANNED_FOOD = registerBuiltin(
            new ItemType.Builder(3, "canned_food")
                    .description("罐头食品，保质期长")
                    .weight(400).volume(350)
                    .maxStackSize(8)
                    .consumable(ConsumableType.FOOD)
                    .nutrition(500, 80, 50)
                    .build()
    );

    // ── 药物 ──
    public static final ItemType BANDAGE = registerBuiltin(
            new ItemType.Builder(4, "bandage")
                    .description("简易绷带，用于处理伤口")
                    .weight(50).volume(30)
                    .maxStackSize(10)
                    .consumable(ConsumableType.MEDICINE)
                    .build()
    );

    public static final ItemType PAINKILLER = registerBuiltin(
            new ItemType.Builder(5, "painkiller")
                    .description("止痛药，缓解疼痛")
                    .weight(20).volume(10)
                    .maxStackSize(20)
                    .consumable(ConsumableType.MEDICINE)
                    .build()
    );

    // ── 复合消耗类型示例 ──
    public static final ItemType HERBAL_TEA = registerBuiltin(
            new ItemType.Builder(6, "herbal_tea")
                    .description("草药茶，既能解渴又有疗效")
                    .weight(250).volume(200)
                    .maxStackSize(3)
                    .consumable(ConsumableType.WATER, ConsumableType.MEDICINE)
                    .nutrition(10, 5, 200)
                    .build()
    );

    // ── 唯一物品示例 ──
    public static final ItemType RUSTY_KNIFE = registerBuiltin(
            new ItemType.Builder(7, "rusty_knife")
                    .description("一把生锈的小刀，聊胜于无")
                    .weight(150).volume(50)
                    .unique()
                    .build()
    );

    // ── 注册 API ──────────────────────────────────────────

    /** 注册内置物品（静态初始化时调用） */
    private static ItemType registerBuiltin(ItemType type) {
        validateAndPut(type);
        return type;
    }

    /**
     * Mod 注册新物品类型（运行时调用）。
     *
     * @throws IllegalArgumentException 如果 ID 或 name 已存在
     */
    public static ItemType registerMod(ItemType type) {
        validateAndPut(type);
        return type;
    }

    /** 核心注册逻辑（ID/name 冲突检查） */
    private static void validateAndPut(ItemType type) {
        if (REGISTRY.containsKey(type.getId())) {
            throw new IllegalArgumentException(
                    "ItemType ID " + type.getId() + " 已被注册: "
                            + REGISTRY.get(type.getId()).getName());
        }
        if (NAME_REGISTRY.containsKey(type.getName())) {
            throw new IllegalArgumentException(
                    "ItemType name '" + type.getName() + "' 已被注册，ID: "
                            + NAME_REGISTRY.get(type.getName()).getId());
        }
        REGISTRY.put(type.getId(), type);
        NAME_REGISTRY.put(type.getName(), type);
    }

    // ── 查询 API ──────────────────────────────────────────

    /** 根据数字 ID 查找物品类型 */
    public static ItemType getById(int id) {
        return REGISTRY.get(id);
    }

    /** 根据字符串 name 查找物品类型 */
    public static ItemType getByName(String name) {
        return NAME_REGISTRY.get(name);
    }

    /** 获取所有已注册的物品类型（有序、不可变） */
    public static Collection<ItemType> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** 获取已注册数量（便于调试 / UI 分页） */
    public static int size() {
        return REGISTRY.size();
    }
}
