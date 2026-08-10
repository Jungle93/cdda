package com.github.game.cdda.world.vegetation;

import java.util.List;

/**
 * 植被定义。
 * 描述一种植被物种的属性、环境适应范围和掉落物。
 *
 * <p>环境适应范围用于世界生成时选择适生物种：
 * <ul>
 *   <li>温度范围 (°C) — 由 WorldMap.getTemperatureAt() 提供</li>
 *   <li>湿度范围 (0-1) — 由 WorldMap.getMoistureAt() 提供</li>
 *   <li>土壤深度 (0-1) — 由 WorldMap.getSoilDepthAt() 提供</li>
 * </ul>
 *
 * <p>掉落物用于砍伐/采集时生成物品。
 */
public class VegetationDefinition {

    /** 植被 ID（如 "oak", "birch"） */
    public String id;

    /** 显示名称（如 "橡树", "桦树"） */
    public String name;

    /** 植被类型 */
    public VegetationType type;

    // ── 环境适应范围 ──────────────────────────────────

    /** 最低温度 (°C) */
    public double tempMin;

    /** 最高温度 (°C) */
    public double tempMax;

    /** 最低湿度 (0-1) */
    public double humidityMin;

    /** 最高湿度 (0-1) */
    public double humidityMax;

    /** 最小土壤深度 (0-1) */
    public double soilMin;

    /** 最大土壤深度 (0-1) */
    public double soilMax;

    // ── 生成参数 ──────────────────────────────────

    /** 基础生成概率 (0-1) */
    public double probability;

    /** 最小簇大小（连续生成数量） */
    public int minClusterSize;

    /** 最大簇大小 */
    public int maxClusterSize;

    // ── 掉落物 ──────────────────────────────────

    /** 砍伐/采集时的掉落物列表 */
    public List<Drop> drops;

    /**
     * 掉落物定义。
     */
    public static class Drop {
        /** 物品 ID（如 "oak_log"） */
        public String itemId;

        /** 最小数量 */
        public int minCount;

        /** 最大数量 */
        public int maxCount;

        /** 掉落概率 (0-1) */
        public double chance;

        public Drop() {}

        public Drop(String itemId, int minCount, int maxCount, double chance) {
            this.itemId = itemId;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.chance = chance;
        }
    }

    public VegetationDefinition() {}

    /**
     * 检查环境条件是否适合此植被生长。
     *
     * @param temperature 温度 (°C)
     * @param humidity    湿度 (0-1)
     * @param soilDepth   土壤深度 (0-1)
     * @return true 如果环境在适应范围内
     */
    public boolean isEnvironmentSuitable(double temperature, double humidity, double soilDepth) {
        return temperature >= tempMin && temperature <= tempMax
                && humidity >= humidityMin && humidity <= humidityMax
                && soilDepth >= soilMin && soilDepth <= soilMax;
    }

    /**
     * 计算环境适应度（0-1）。
     * 用于在多个适生物种中选择最合适的。
     *
     * @param temperature 温度 (°C)
     * @param humidity    湿度 (0-1)
     * @param soilDepth   土壤深度 (0-1)
     * @return 适应度 (0-1)，1 表示完全适应
     */
    public double calculateFitness(double temperature, double humidity, double soilDepth) {
        // 如果不适合，返回 0
        if (!isEnvironmentSuitable(temperature, humidity, soilDepth)) {
            return 0.0;
        }

        // 计算各维度的适应度（越接近范围中心越高）
        double tempFitness = 1.0 - Math.abs(temperature - (tempMin + tempMax) / 2) / ((tempMax - tempMin) / 2);
        double humidityFitness = 1.0 - Math.abs(humidity - (humidityMin + humidityMax) / 2) / ((humidityMax - humidityMin) / 2);
        double soilFitness = 1.0 - Math.abs(soilDepth - (soilMin + soilMax) / 2) / ((soilMax - soilMin) / 2);

        // 综合适应度（加权平均）
        return (tempFitness * 0.4 + humidityFitness * 0.4 + soilFitness * 0.2) * probability;
    }
}
