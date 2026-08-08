package com.github.game.cdda.creature.config;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生物定义注册表。
 * 管理所有已加载的生物模板，支持从 JSON 文件加载。
 * 采用双注册表模式（按 ID 和按名称），与 TileType/ItemRegistry 一致。
 */
public class CreatureRegistry {

    private static final Logger logger = LoggerFactory.getLogger(CreatureRegistry.class);

    /** 按 ID 注册 */
    private static final Map<String, CreatureDefinition> BY_ID = new LinkedHashMap<>();

    /** 按名称注册 */
    private static final Map<String, CreatureDefinition> BY_NAME = new LinkedHashMap<>();

    /** Gson 实例 */
    private static final Gson GSON = new Gson();

    /** 是否已加载 */
    private static boolean loaded = false;

    /**
     * 注册一个生物定义。
     *
     * @param def 生物定义
     */
    public static void register(CreatureDefinition def) {
        if (def == null || def.id == null) {
            logger.warn("尝试注册空定义或无 ID 定义");
            return;
        }
        if (BY_ID.containsKey(def.id)) {
            logger.warn("生物定义 ID 重复: {}", def.id);
        }
        BY_ID.put(def.id, def);
        if (def.name != null) {
            BY_NAME.put(def.name, def);
        }
        logger.debug("注册生物定义: {} ({})", def.id, def.name);
    }

    /**
     * 按 ID 获取生物定义。
     *
     * @param id 生物 ID
     * @return 生物定义，未找到返回 null
     */
    public static CreatureDefinition get(String id) {
        return BY_ID.get(id);
    }

    /**
     * 按名称获取生物定义。
     *
     * @param name 生物名称
     * @return 生物定义，未找到返回 null
     */
    public static CreatureDefinition getByName(String name) {
        return BY_NAME.get(name);
    }

    /**
     * 获取所有已注册的生物定义。
     *
     * @return 生物定义集合
     */
    public static Collection<CreatureDefinition> getAll() {
        return BY_ID.values();
    }

    /**
     * 从 classpath 加载所有生物定义。
     * 扫描 creatures/ 目录下的所有 JSON 文件。
     */
    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }

        // 加载内置生物
        loadFromClasspath("creatures/deer.json");
        loadFromClasspath("creatures/rabbit.json");

        loaded = true;
        logger.info("生物注册表加载完成，共 {} 种生物", BY_ID.size());
    }

    /**
     * 从 classpath 加载单个生物定义。
     *
     * @param path classpath 路径
     */
    private static void loadFromClasspath(String path) {
        try (InputStream is = CreatureRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                logger.warn("生物定义文件未找到: {}", path);
                return;
            }
            CreatureDefinition def = GSON.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    CreatureDefinition.class
            );
            register(def);
        } catch (Exception e) {
            logger.error("加载生物定义失败: {}", path, e);
        }
    }

    /**
     * 清空注册表（用于测试）。
     */
    public static void clear() {
        BY_ID.clear();
        BY_NAME.clear();
        loaded = false;
    }
}
