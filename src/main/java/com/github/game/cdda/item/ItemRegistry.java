package com.github.game.cdda.item;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物品类型注册表（遵循 TileType 注册模式）。
 * <p>
 * 所有物品从 {@code resources/items/} 目录下的 JSON 文件加载。
 * Mod 可通过 {@link #registerMod} 在运行时注册新类型。
 * <p>
 * ID 分配约定：
 * <ul>
 *   <li>0–999：内置物品（Base game，通过 JSON 配置）</li>
 *   <li>1000+：Mod 物品</li>
 * </ul>
 */
public final class ItemRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ItemRegistry.class);

    /** 数字 ID → ItemType 注册表（有序） */
    private static final Map<Integer, ItemType> REGISTRY = new LinkedHashMap<>();
    /** 字符串 name → ItemType 注册表（有序） */
    private static final Map<String, ItemType> NAME_REGISTRY = new LinkedHashMap<>();

    /** Gson 实例 */
    private static final Gson GSON = new Gson();

    /** 是否已加载 */
    private static boolean loaded = false;

    private ItemRegistry() {} // 不可实例化

    static {
        loadAll();
    }

    // ── 从 JSON 加载 ────────────────────────────────────

    /**
     * 从 classpath 加载所有物品定义。
     * 扫描 items/ 目录下的 JSON 文件。
     */
    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }

        // 饮品
        loadFromClasspath("items/water_bottle.json");
        loadFromClasspath("items/dirty_water.json");
        // 食物
        loadFromClasspath("items/bread.json");
        loadFromClasspath("items/canned_food.json");
        // 药物
        loadFromClasspath("items/bandage.json");
        loadFromClasspath("items/painkiller.json");
        // 复合消耗类型
        loadFromClasspath("items/herbal_tea.json");
        // 唯一物品
        loadFromClasspath("items/rusty_knife.json");
        loadFromClasspath("items/stone_axe.json");
        // 动物掉落物 — 肉类
        loadFromClasspath("items/venison_raw.json");
        loadFromClasspath("items/rabbit_meat_raw.json");
        loadFromClasspath("items/boar_meat_raw.json");
        loadFromClasspath("items/wolf_meat_raw.json");
        loadFromClasspath("items/badger_meat_raw.json");
        loadFromClasspath("items/roe_venison_raw.json");
        loadFromClasspath("items/hare_meat_raw.json");
        loadFromClasspath("items/mouflon_meat_raw.json");
        loadFromClasspath("items/squirrel_meat_raw.json");
        // 动物掉落物 — 毛皮
        loadFromClasspath("items/deer_hide.json");
        loadFromClasspath("items/rabbit_pelt.json");
        loadFromClasspath("items/fox_pelt.json");
        loadFromClasspath("items/boar_hide.json");
        loadFromClasspath("items/wolf_pelt.json");
        loadFromClasspath("items/badger_fur.json");
        loadFromClasspath("items/hare_pelt.json");
        loadFromClasspath("items/mouflon_hide.json");
        // 动物掉落物 — 特殊材料
        loadFromClasspath("items/antler.json");
        loadFromClasspath("items/bone.json");
        loadFromClasspath("items/boar_tusk.json");
        loadFromClasspath("items/wolf_fang.json");
        // 植物掉落物 — 木材
        loadFromClasspath("items/oak_log.json");
        loadFromClasspath("items/birch_log.json");
        loadFromClasspath("items/pine_log.json");
        loadFromClasspath("items/fir_log.json");
        loadFromClasspath("items/beech_log.json");
        // 植物掉落物 — 植物材料
        loadFromClasspath("items/small_branch.json");
        loadFromClasspath("items/birch_bark.json");
        loadFromClasspath("items/dried_branch.json");
        loadFromClasspath("items/bracken_fern.json");
        loadFromClasspath("items/heather_flower.json");
        loadFromClasspath("items/gorse_branch.json");
        loadFromClasspath("items/blackberry.json");
        loadFromClasspath("items/acorn.json");
        loadFromClasspath("items/pine_cone.json");
        loadFromClasspath("items/grass_bundle.json");
        loadFromClasspath("items/reed_bundle.json");
        loadFromClasspath("items/green_moss.json");
        loadFromClasspath("items/sphagnum_moss.json");
        loadFromClasspath("items/lichen.json");
        loadFromClasspath("items/peat.json");
        // 木材加工品 — 木板
        loadFromClasspath("items/oak_plank.json");
        loadFromClasspath("items/birch_plank.json");
        loadFromClasspath("items/pine_plank.json");
        loadFromClasspath("items/fir_plank.json");
        loadFromClasspath("items/beech_plank.json");
        // 木材加工品 — 木柴
        loadFromClasspath("items/oak_firewood.json");
        loadFromClasspath("items/birch_firewood.json");
        loadFromClasspath("items/pine_firewood.json");
        // 动物尸体
        loadFromClasspath("items/deer_corpse.json");
        loadFromClasspath("items/rabbit_corpse.json");
        loadFromClasspath("items/boar_corpse.json");
        loadFromClasspath("items/wolf_corpse.json");
        loadFromClasspath("items/fox_corpse.json");
        loadFromClasspath("items/badger_corpse.json");
        loadFromClasspath("items/hare_corpse.json");
        loadFromClasspath("items/roe_deer_corpse.json");
        loadFromClasspath("items/mouflon_corpse.json");
        loadFromClasspath("items/squirrel_corpse.json");

        loaded = true;
        logger.info("物品注册表加载完成，共 {} 种物品", REGISTRY.size());
    }

    /**
     * 从 classpath 加载单个物品定义。
     *
     * @param path classpath 路径
     */
    private static void loadFromClasspath(String path) {
        try (InputStream is = ItemRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                logger.warn("物品定义文件未找到: {}", path);
                return;
            }
            ItemDefinition def = GSON.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    ItemDefinition.class
            );
            if (def == null || def.name == null || def.name.isBlank()) {
                logger.warn("物品定义无效: {}", path);
                return;
            }
            ItemType type = def.toItemType();
            validateAndPut(type);
            logger.debug("注册物品定义: {} (id={})", def.name, def.id);
        } catch (Exception e) {
            logger.error("加载物品定义失败: {}", path, e);
        }
    }

    // ── 注册 API ──────────────────────────────────────────

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

    /**
     * 清空注册表（用于测试）。
     */
    public static void clear() {
        REGISTRY.clear();
        NAME_REGISTRY.clear();
        loaded = false;
    }
}
