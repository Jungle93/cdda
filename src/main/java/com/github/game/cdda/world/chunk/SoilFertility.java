package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.biome.BiomeType;

/**
 * 单个地块（chunk）的土壤肥力地图。
 *
 * <p>每个瓦片有一个 0~100 的肥力值。肥力影响植物生长：
 * <ul>
 *   <li>植物生长消耗肥力</li>
 *   <li>肥力不足时植物枯萎</li>
 *   <li>空闲地块肥力缓慢恢复</li>
 *   <li>肥力达到阈值时野生植物可能定殖</li>
 * </ul>
 *
 * <p>初始肥力由生物群落决定：
 * <ul>
 *   <li>沼泽/森林 → 高肥力（70~85）</li>
 *   <li>平原/草原 → 中肥力（50~65）</li>
 *   <li>丘陵/高原 → 中低肥力（35~50）</li>
 *   <li>沙漠/山地 → 低肥力（10~25）</li>
 * </ul>
 */
public class SoilFertility {

    /** 地块边长（与 Chunk.SIZE 一致） */
    private static final int SIZE = 32;

    /** 每瓦片肥力值 [row][col]，范围 0~100 */
    private final double[][] fertility;

    /** 地块坐标 */
    private final int chunkX;
    private final int chunkY;

    /**
     * 创建土壤肥力地图，根据生物群落初始化肥力。
     *
     * @param chunkX 地块 X 坐标
     * @param chunkY 地块 Y 坐标
     * @param biome  生物群落（决定基础肥力）
     */
    public SoilFertility(int chunkX, int chunkY, BiomeType biome) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.fertility = new double[SIZE][SIZE];
        initializeFromBiome(biome);
    }

    /**
     * 根据生物群落初始化基础肥力（加随机噪声）。
     */
    private void initializeFromBiome(BiomeType biome) {
        double baseFertility = getBaseFertilityForBiome(biome);

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                // 使用坐标哈希添加小幅随机变化（±5）
                double variation = tileVariation(chunkX * SIZE + col, chunkY * SIZE + row) * 10.0 - 5.0;
                fertility[row][col] = Math.max(0.0, Math.min(100.0, baseFertility + variation));
            }
        }
    }

    /**
     * 根据生物群落获取基础肥力。
     */
    private static double getBaseFertilityForBiome(BiomeType biome) {
        String name = biome.getName();
        return switch (name) {
            case "swamp" -> 75.0;       // 沼泽：富含有机质
            case "dense_forest" -> 70.0; // 密林：腐殖质丰富
            case "forest" -> 65.0;       // 森林
            case "plains" -> 55.0;       // 平原
            case "grassland" -> 60.0;    // 草原
            case "hills" -> 40.0;        // 丘陵
            case "plateau" -> 35.0;      // 高原
            case "mountain" -> 20.0;     // 山地：岩石多，土壤少
            case "desert" -> 15.0;       // 沙漠：贫瘠
            case "ocean" -> 10.0;        // 海洋：无土壤
            default -> 50.0;             // 默认
        };
    }

    /**
     * 确定性瓦片变化函数（0~1）。
     */
    private static double tileVariation(int x, int y) {
        long h = (long) x * 374761393L + (long) y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL;
    }

    /**
     * 获取指定瓦片的肥力值。
     *
     * @param localCol 局部列号 [0, SIZE)
     * @param localRow 局部行号 [0, SIZE)
     * @return 肥力值（0~100），越界返回 0
     */
    public double getFertility(int localCol, int localRow) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return 0.0;
        }
        return fertility[localRow][localCol];
    }

    /**
     * 设置指定瓦片的肥力值。
     *
     * @param localCol  局部列号
     * @param localRow  局部行号
     * @param fertility 肥力值（自动钳位到 0~100）
     */
    public void setFertility(int localCol, int localRow, double fertility) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return;
        }
        this.fertility[localRow][localCol] = Math.max(0.0, Math.min(100.0, fertility));
    }

    /**
     * 消耗指定瓦片的肥力。
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     * @param amount   消耗量
     * @return 实际消耗量（不会使肥力低于 0）
     */
    public double consumeFertility(int localCol, int localRow, double amount) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return 0;
        }
        double actualConsume = Math.min(amount, fertility[localRow][localCol]);
        fertility[localRow][localCol] = Math.max(0.0, fertility[localRow][localCol] - actualConsume);
        return actualConsume;
    }

    /**
     * 恢复指定瓦片的肥力（不超过上限）。
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     * @param amount   恢复量
     * @param cap      肥力上限（通常 85）
     */
    public void recoverFertility(int localCol, int localRow, double amount, double cap) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return;
        }
        double current = fertility[localRow][localCol];
        if (current >= cap) return; // 已达上限，不恢复
        fertility[localRow][localCol] = Math.min(cap, current + amount);
    }

    /**
     * 检查指定瓦片肥力是否达到定殖阈值。
     */
    public boolean canColonize(int localCol, int localRow, double threshold) {
        return getFertility(localCol, localRow) >= threshold;
    }

    public int getChunkX() { return chunkX; }
    public int getChunkY() { return chunkY; }
}
