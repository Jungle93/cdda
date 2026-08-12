package com.github.game.cdda.item.registry;

import com.github.game.cdda.item.model.ItemDefinition;
import com.github.game.cdda.item.model.ItemType;

import com.github.game.engine.core.data.DataScanner;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物品类型注册表。
 * <p>
 * 所有物品从 {@code data/core/items/} 目录下自动扫描加载。
 * Mod 可通过 {@link #registerMod} 在运行时注册新类型。
 * <p>
 * ID 分配约定：
 * <ul>
 *   <li>0–999：内置物品（Base game）</li>
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
     * 扫描 data/core/items/ 目录下所有 JSON 文件并加载。
     */
    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }

        // 扫描并加载所有物品定义（递归扫描子目录）
        int count = 0;
        for (String path : DataScanner.scanClasspathJson("data/core/items")) {
            if (loadFromClasspath(path)) {
                count++;
            }
        }

        loaded = true;
        logger.info("物品注册表加载完成，共 {} 种物品", count);
    }

    /**
     * 从 classpath 加载单个物品定义。
     *
     * @param path classpath 路径
     * @return 是否加载成功
     */
    private static boolean loadFromClasspath(String path) {
        try (InputStream is = DataScanner.openClasspathStream(path)) {
            if (is == null) {
                return false;
            }
            ItemDefinition def = GSON.fromJson(
                    new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
                    ItemDefinition.class
            );
            if (def == null || def.name == null || def.name.isBlank()) {
                logger.warn("物品定义无效: {}", path);
                return false;
            }
            ItemType type = def.toItemType();
            validateAndPut(type);
            logger.debug("注册物品: {} (id={})", def.name, def.id);
            return true;
        } catch (Exception e) {
            logger.error("加载物品定义失败: {}", path, e);
            return false;
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

    /**
     * 根据数字 ID 查找物品类型。
     *
     * @param id 物品数字 ID
     * @return 对应的 ItemType，未找到返回 null
     */
    public static ItemType getById(int id) {
        return REGISTRY.get(id);
    }

    /**
     * 根据字符串 name 查找物品类型。
     *
     * @param name 物品字符串名称（英文技术标识符）
     * @return 对应的 ItemType，未找到返回 null
     */
    public static ItemType getByName(String name) {
        return NAME_REGISTRY.get(name);
    }

    /**
     * 获取所有已注册的物品类型（有序、不可变）。
     *
     * @return 所有 ItemType 的不可变集合
     */
    public static Collection<ItemType> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * 获取已注册数量（便于调试 / UI 分页）。
     *
     * @return 已注册的物品类型总数
     */
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
