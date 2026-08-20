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

    // ── 肥力需求（新增：植物生长系统用） ────────────

    /** 发芽所需最低肥力（0~100，参照现实世界） */
    public double germinateFertility;
    /** 幼苗期所需最低肥力 */
    public double seedlingFertility;
    /** 生长期所需最低肥力 */
    public double growingFertility;
    /** 成熟期所需最低肥力 */
    public double matureFertility;

    /** 每日肥力消耗量（0~100，每游戏日） */
    public double dailyFertilityCost;

    /** 幼苗期持续天数（游戏日） */
    public int seedlingDays;
    /** 生长期持续天数（游戏日） */
    public int growingDays;
    /** 成熟期持续天数（游戏日，之后枯萎） */
    public int matureLifespanDays;

    /** 每日传播概率（0~1，向相邻空地扩散） */
    public double spreadProbability;

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
     * 获取本地化的植被显示名称。
     * 优先从 i18n 获取（key: vegetation.{id}.name），未找到时回退到 name 字段。
     */
    public String getLocalizedName() {
        String key = "vegetation." + id + ".name";
        String value = resolveI18n(key);
        return value != null ? value : name;
    }

    /** 尝试通过 I18nManager 解析翻译键，未找到时返回 null */
    private String resolveI18n(String key) {
        try {
            com.github.game.engine.core.i18n.I18nManager i18n =
                    com.github.game.engine.core.EngineServices.i18n;
            if (i18n == null) return null;
            String value = i18n.t(key);
            return key.equals(value) ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

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
