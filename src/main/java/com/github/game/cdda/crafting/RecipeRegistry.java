package com.github.game.cdda.crafting;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 加工配方注册表。
 * 管理所有已加载的加工配方，支持从 JSON 文件加载。
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
     * 加载所有配方。
     */
    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }

        // ── 原木 → 木板 ──
        loadFromClasspath("recipes/oak_log_to_planks.json");
        loadFromClasspath("recipes/birch_log_to_planks.json");
        loadFromClasspath("recipes/pine_log_to_planks.json");
        loadFromClasspath("recipes/fir_log_to_planks.json");
        loadFromClasspath("recipes/beech_log_to_planks.json");

        // ── 原木 → 木柴 ──
        loadFromClasspath("recipes/oak_log_to_firewood.json");
        loadFromClasspath("recipes/birch_log_to_firewood.json");
        loadFromClasspath("recipes/pine_log_to_firewood.json");

        // ── 树枝 → 木柴 ──
        loadFromClasspath("recipes/branch_to_firewood.json");

        loaded = true;
        logger.info("配方注册表加载完成，共 {} 个配方", BY_ID.size());
    }

    /**
     * 从 classpath 加载配方。
     */
    private static void loadFromClasspath(String path) {
        try (InputStream is = RecipeRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                logger.warn("配方文件未找到: {}", path);
                return;
            }
            ProcessingRecipe recipe = GSON.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    ProcessingRecipe.class
            );
            register(recipe);
        } catch (Exception e) {
            logger.error("加载配方失败: {}", path, e);
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
