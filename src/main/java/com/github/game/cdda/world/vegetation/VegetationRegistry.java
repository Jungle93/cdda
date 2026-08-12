package com.github.game.cdda.world.vegetation;

import com.github.game.engine.core.data.DataScanner;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 植被定义注册表。
 * 管理所有已加载的植被模板，从 data/core/vegetation/ 自动扫描加载。
 *
 * <p>与 CreatureRegistry 采用相同模式：
 * <ul>
 *   <li>按 ID 注册查询</li>
 *   <li>支持 {@code loadAll()} 从 classpath JSON 自动扫描加载</li>
 *   <li>支持环境适配查询 {@code getForEnvironment()}</li>
 * </ul>
 */
public class VegetationRegistry {

    private static final Logger logger = LoggerFactory.getLogger(VegetationRegistry.class);

    /** 按 ID 注册 */
    private static final Map<String, VegetationDefinition> BY_ID = new LinkedHashMap<>();

    /** 按类型分组 */
    private static final Map<VegetationType, List<VegetationDefinition>> BY_TYPE = new EnumMap<>(VegetationType.class);

    /** Gson 实例 */
    private static final Gson GSON = new Gson();

    /** 是否已加载 */
    private static boolean loaded = false;

    /**
     * 扫描 data/core/vegetation/ 目录下所有 JSON 文件并加载。
     */
    public static synchronized void loadAll() {
        if (loaded) {
            return;
        }

        // 初始化类型分组
        for (VegetationType type : VegetationType.values()) {
            BY_TYPE.put(type, new ArrayList<>());
        }

        // 扫描并加载所有植被定义（递归扫描子目录）
        int count = 0;
        for (String path : DataScanner.scanClasspathJson("data/core/vegetation")) {
            if (loadFromClasspath(path)) {
                count++;
            }
        }

        loaded = true;
        logger.info("植被注册表加载完成，共 {} 种植被", count);
    }

    /**
     * 从 classpath 加载植被定义。
     *
     * @param path classpath 路径
     * @return 是否加载成功
     */
    private static boolean loadFromClasspath(String path) {
        try (InputStream is = DataScanner.openClasspathStream(path)) {
            if (is == null) {
                return false;
            }
            VegetationDefinition def = GSON.fromJson(
                    new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
                    VegetationDefinition.class
            );
            if (def == null || def.id == null) {
                logger.warn("植被定义无效: {}", path);
                return false;
            }
            register(def);
            return true;
        } catch (Exception e) {
            logger.error("加载植被定义失败: {}", path, e);
            return false;
        }
    }

    /**
     * 注册植被定义。
     *
     * @param def 植被定义
     */
    public static void register(VegetationDefinition def) {
        if (def == null || def.id == null) {
            logger.warn("尝试注册空植被定义或无 ID 定义");
            return;
        }
        if (BY_ID.containsKey(def.id)) {
            logger.warn("植被定义 ID 重复: {}", def.id);
        }
        BY_ID.put(def.id, def);
        if (def.type != null) {
            BY_TYPE.get(def.type).add(def);
        }
        logger.debug("注册植被定义: {} ({})", def.id, def.name);
    }

    /**
     * 根据 ID 获取植被定义。
     *
     * @param id 植被 ID
     * @return 植被定义，未找到返回 null
     */
    public static VegetationDefinition getById(String id) {
        return BY_ID.get(id);
    }

    /**
     * 获取所有已注册的植被定义。
     *
     * @return 植被定义集合（不可变）
     */
    public static Collection<VegetationDefinition> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * 获取指定类型的所有植被定义。
     *
     * @param type 植被类型
     * @return 该类型的植被列表
     */
    public static List<VegetationDefinition> getByType(VegetationType type) {
        return Collections.unmodifiableList(BY_TYPE.getOrDefault(type, Collections.emptyList()));
    }

    /**
     * 根据环境条件获取适生物种列表。
     * 返回适应度大于 0 的所有物种，按适应度降序排序。
     *
     * @param temperature 温度 (°C)
     * @param humidity    湿度 (0-1)
     * @param soilDepth   土壤深度 (0-1)
     * @param type        植被类型（null 表示不限类型）
     * @return 适生物种列表（按适应度降序）
     */
    public static List<VegetationDefinition> getForEnvironment(
            double temperature, double humidity, double soilDepth, VegetationType type) {
        List<VegetationDefinition> candidates = new ArrayList<>();
        Collection<VegetationDefinition> pool = (type != null)
                ? BY_TYPE.getOrDefault(type, Collections.emptyList())
                : BY_ID.values();

        for (VegetationDefinition def : pool) {
            double fitness = def.calculateFitness(temperature, humidity, soilDepth);
            if (fitness > 0) {
                candidates.add(def);
            }
        }

        // 按适应度降序排序
        candidates.sort((a, b) -> Double.compare(
                b.calculateFitness(temperature, humidity, soilDepth),
                a.calculateFitness(temperature, humidity, soilDepth)));

        return candidates;
    }

    /**
     * 根据环境条件随机选择一个适生物种。
     * 适应度越高，被选中的概率越大。
     *
     * @param temperature 温度 (°C)
     * @param humidity    湿度 (0-1)
     * @param soilDepth   土壤深度 (0-1)
     * @param type        植被类型（null 表示不限类型）
     * @param random      随机数生成器
     * @return 选中的物种，无适生物种返回 null
     */
    public static VegetationDefinition selectForEnvironment(
            double temperature, double humidity, double soilDepth,
            VegetationType type, Random random) {
        List<VegetationDefinition> candidates = getForEnvironment(temperature, humidity, soilDepth, type);
        if (candidates.isEmpty()) return null;

        // 计算总适应度
        double totalFitness = 0;
        for (VegetationDefinition def : candidates) {
            totalFitness += def.calculateFitness(temperature, humidity, soilDepth);
        }

        // 按适应度加权随机选择
        double roll = random.nextDouble() * totalFitness;
        double cumulative = 0;
        for (VegetationDefinition def : candidates) {
            cumulative += def.calculateFitness(temperature, humidity, soilDepth);
            if (roll <= cumulative) {
                return def;
            }
        }

        return candidates.get(0); // 兜底
    }

    /**
     * 清空注册表（用于测试）。
     */
    public static void clear() {
        BY_ID.clear();
        for (List<VegetationDefinition> list : BY_TYPE.values()) {
            list.clear();
        }
        loaded = false;
    }
}
