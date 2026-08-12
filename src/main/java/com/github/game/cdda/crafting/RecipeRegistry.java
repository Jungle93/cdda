package com.github.game.cdda.crafting;

import com.github.game.engine.core.data.DataScanner;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加工配方注册表。
 * 管理所有已加载的加工配方，从 data/core/recipes/ 自动扫描加载。
 *
 * <p>配方按输入物品 ID 索引，支持查询某物品的所有可用配方。
 */
public class RecipeRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RecipeRegistry.class);

    /** 按 ID 注册 */
    private static final Map<String, ProcessingRecipe> BY_ID = new LinkedHashMap<>();

    /** 按输入物品 ID 分组 */
    private static final Map<String, List<ProcessingRecipe>> BY_INPUT = new HashMap<>();

    /** Gson 实例 */
    private static final Gson GSON = new Gson();

    /** 是否已加载 */
    private static boolean loaded = false;

    /**
     * 扫描 data/core/recipes/ 目录下所有 JSON 文件并加载。
     */
    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }

        // 扫描并加载所有配方定义（递归扫描子目录）
        int count = 0;
        for (String path : DataScanner.scanClasspathJson("data/core/recipes")) {
            if (loadFromClasspath(path)) {
                count++;
            }
        }

        loaded = true;
        logger.info("配方注册表加载完成，共 {} 个配方", count);
    }

    /**
     * 从 classpath 加载配方。
     *
     * @param path classpath 路径
     * @return 是否加载成功
     */
    private static boolean loadFromClasspath(String path) {
        try (InputStream is = DataScanner.openClasspathStream(path)) {
            if (is == null) {
                return false;
            }
            ProcessingRecipe recipe = GSON.fromJson(
                    new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
                    ProcessingRecipe.class
            );
            if (recipe == null || recipe.id == null) {
                logger.warn("配方定义无效: {}", path);
                return false;
            }
            register(recipe);
            return true;
        } catch (Exception e) {
            logger.error("加载配方失败: {}", path, e);
            return false;
        }
    }

    /**
     * 注册配方。
     *
     * @param recipe 配方定义
     */
    public static void register(ProcessingRecipe recipe) {
        if (recipe == null || recipe.id == null) {
            logger.warn("尝试注册空配方或无 ID 配方");
            return;
        }
        if (BY_ID.containsKey(recipe.id)) {
            logger.warn("配方 ID 重复: {}", recipe.id);
        }
        BY_ID.put(recipe.id, recipe);

        // 按输入物品分组
        BY_INPUT.computeIfAbsent(recipe.inputItemId, k -> new ArrayList<>()).add(recipe);

        logger.debug("注册配方: {} — {}", recipe.id, recipe.getDescription());
    }

    /**
     * 根据 ID 获取配方。
     *
     * @param id 配方 ID
     * @return 配方，未找到返回 null
     */
    public static ProcessingRecipe getById(String id) {
        return BY_ID.get(id);
    }

    /**
     * 获取指定物品的所有可用配方。
     *
     * @param inputItemId 输入物品 ID
     * @return 配方列表（不可变，可能为空）
     */
    public static List<ProcessingRecipe> getRecipesFor(String inputItemId) {
        List<ProcessingRecipe> recipes = BY_INPUT.get(inputItemId);
        if (recipes == null) return Collections.emptyList();
        return Collections.unmodifiableList(recipes);
    }

    /**
     * 获取所有已注册的配方。
     *
     * @return 配方集合（不可变）
     */
    public static Collection<ProcessingRecipe> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * 清空注册表（用于测试）。
     */
    public static void clear() {
        BY_ID.clear();
        BY_INPUT.clear();
        loaded = false;
    }
}
