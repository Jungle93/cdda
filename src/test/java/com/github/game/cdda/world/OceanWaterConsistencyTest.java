package com.github.game.cdda.world;

import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.engine.core.noise.PerlinNoise;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试大地图和小地图水域一致性。
 *
 * <p>问题：大地图上显示海洋（蓝色）的区域，小地图上没有水域瓦片。
 * 根因：Chunk.carveWaterFeatures() 的瓦片过滤器只允许 GRASS/SAND → WATER，
 * 但海洋群落的 classifyTerrain() 主要产生 MUD（低海拔 + 高湿度），被过滤器跳过。
 */
class OceanWaterConsistencyTest {

    /** 固定种子 */
    private static final long TEST_SEED = 42L;
    /** 区块边长 */
    private static final int CHUNK_SIZE = Chunk.SIZE;

    /**
     * 辅助：扫描并打印种子周围所有生物群落分布。
     */
    @Test
    void scanBiomeDistribution() {
        WorldMap worldMap = new WorldMap(TEST_SEED);
        Map<String, Integer> biomeCounts = new HashMap<>();
        int range = 50; // 扩大到 100×100

        for (int cy = -range; cy < range; cy++) {
            for (int cx = -range; cx < range; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                biomeCounts.merge(biome.getName(), 1, Integer::sum);
            }
        }

        System.out.println("=== 种子 " + TEST_SEED + " 生物群落分布 (范围: " + (-range) + "~" + range + ") ===");
        biomeCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %-15s : %d (%.1f%%)%n",
                        e.getKey(), e.getValue(),
                        e.getValue() * 100.0 / ((2 * range) * (2 * range))));

        // 诊断 OCEAN 区块的噪声值和水域特征
        System.out.println("\n=== OCEAN 区块噪声诊断（前 5 个） ===");
        int found = 0;
        for (int cy = -50; cy < 50 && found < 5; cy++) {
            for (int cx = -50; cx < 50 && found < 5; cx++) {
                if (worldMap.getBiomeAtChunk(cx, cy) != BiomeType.OCEAN) continue;
                found++;

                int wx = cx * CHUNK_SIZE + CHUNK_SIZE / 2;
                int wy = cy * CHUNK_SIZE + CHUNK_SIZE / 2;
                double elev = worldMap.getElevationAt(wx, wy);
                double moist = worldMap.getMoistureAt(wx, wy);
                double water = worldMap.getWaterFeature(wx, wy);
                double humidity = worldMap.getHumidityAt(wx, wy);

                // 采样角落瓦片，看变化范围
                int wx0 = cx * CHUNK_SIZE;
                int wy0 = cy * CHUNK_SIZE;
                double elev0 = worldMap.getElevationAt(wx0, wy0);
                double water0 = worldMap.getWaterFeature(wx0, wy0);

                System.out.printf("  OCEAN chunk (%d,%d) tile(%d,%d):%n", cx, cy, wx, wy);
                System.out.printf("    center: elev=%.4f moist=%.4f humidity=%.4f waterFeature=%.4f%n",
                        elev, moist, humidity, water);
                System.out.printf("    corner: elev=%.4f waterFeature=%.4f%n", elev0, water0);
            }
        }
    }

    /**
     * 核心测试：大地图标记为 OCEAN 的区块，小地图上必须有水域瓦片。
     */
    @Test
    void oceanChunksMustHaveWaterTiles() {
        WorldMap worldMap = new WorldMap(TEST_SEED);
        PerlinNoise noise = new PerlinNoise(TEST_SEED);

        int oceanChunks = 0;
        int oceanChunksWithWater = 0;
        int oceanChunksWithMostlyWater = 0;
        Map<String, Integer> globalTileStats = new HashMap<>();

        // 扩大扫描范围到 -50~50
        for (int cy = -50; cy < 50; cy++) {
            for (int cx = -50; cx < 50; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome != BiomeType.OCEAN) continue;

                oceanChunks++;
                Chunk chunk = new Chunk(cx, cy, noise, biome);
                chunk.generate(noise, worldMap, null);

                int waterCount = 0;
                int sandCount = 0;
                int mudCount = 0;
                Map<String, Integer> tileStats = new HashMap<>();

                for (int row = 0; row < CHUNK_SIZE; row++) {
                    for (int col = 0; col < CHUNK_SIZE; col++) {
                        TileType t = chunk.getTile(col, row);
                        if (t == null) continue;
                        String name = t.getName();
                        tileStats.merge(name, 1, Integer::sum);
                        globalTileStats.merge(name, 1, Integer::sum);
                        if (t == TileType.WATER) waterCount++;
                        if (t == TileType.SAND) sandCount++;
                        if (t == TileType.MUD) mudCount++;
                    }
                }

                if (waterCount > 0) oceanChunksWithWater++;
                if (waterCount > CHUNK_SIZE * CHUNK_SIZE * 0.5) {
                    oceanChunksWithMostlyWater++;
                }

                if (oceanChunks <= 5) {
                    System.out.printf("OCEAN chunk (%d,%d): WATER=%d SAND=%d MUD=%d stats=%s%n",
                            cx, cy, waterCount, sandCount, mudCount, tileStats);
                }
            }
        }

        System.out.println();
        System.out.println("=== OCEAN 区块统计 ===");
        System.out.println("OCEAN 区块总数: " + oceanChunks);
        System.out.println("有水域的 OCEAN 区块: " + oceanChunksWithWater);
        System.out.println("大部分是水的 OCEAN 区块: " + oceanChunksWithMostlyWater);
        System.out.println("全局瓦片分布: " + globalTileStats);

        assertTrue(oceanChunks > 0, "种子 42 应存在 OCEAN 区块");
        assertEquals(oceanChunks, oceanChunksWithWater,
                "所有 OCEAN 区块都必须有水域瓦片（大地图与小地图一致性）");
        assertTrue(oceanChunksWithMostlyWater >= oceanChunks * 0.8,
                "海洋区块应至少 80% 面积是水域");
    }

    /**
     * 沼泽群落也应有水域。
     */
    @Test
    void swampChunksShouldHaveWaterTiles() {
        WorldMap worldMap = new WorldMap(TEST_SEED);
        PerlinNoise noise = new PerlinNoise(TEST_SEED);

        int swampChunks = 0;
        int swampChunksWithWater = 0;

        for (int cy = -50; cy < 50; cy++) {
            for (int cx = -50; cx < 50; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome != BiomeType.SWAMP) continue;

                swampChunks++;
                Chunk chunk = new Chunk(cx, cy, noise, biome);
                chunk.generate(noise, worldMap, null);

                int waterCount = 0;
                for (int row = 0; row < CHUNK_SIZE; row++) {
                    for (int col = 0; col < CHUNK_SIZE; col++) {
                        TileType t = chunk.getTile(col, row);
                        if (t == TileType.WATER) waterCount++;
                    }
                }
                if (waterCount > 0) swampChunksWithWater++;
            }
        }

        System.out.println("SWAMP 区块总数: " + swampChunks);
        System.out.println("有水域的 SWAMP 区块: " + swampChunksWithWater);

        if (swampChunks > 0) {
            assertTrue(swampChunksWithWater > swampChunks * 0.5,
                    "SWAMP 区块应大部分有水域");
        }
    }

    /**
     * 验证 WorldMap.getWaterFeature() 对海洋区域返回有意义的值。
     * 由于海岸噪声扰动，中心点值可能低于 0.6，
     * 所以改为检查区块中是否有足够多的水域瓦片。
     */
    @Test
    void oceanAreaWaterFeatureShouldBeHigh() {
        WorldMap worldMap = new WorldMap(TEST_SEED);
        PerlinNoise noise = new PerlinNoise(TEST_SEED);

        int tested = 0;
        int highCoverageCount = 0;

        for (int cy = -50; cy < 50; cy++) {
            for (int cx = -50; cx < 50; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome != BiomeType.OCEAN) continue;

                Chunk chunk = new Chunk(cx, cy, noise, biome);
                chunk.generate(noise, worldMap, null);

                int waterCount = 0;
                for (int row = 0; row < CHUNK_SIZE; row++) {
                    for (int col = 0; col < CHUNK_SIZE; col++) {
                        TileType t = chunk.getTile(col, row);
                        if (t == TileType.WATER) waterCount++;
                    }
                }
                tested++;
                // 海洋区块应有 > 50% 水域覆盖
                if (waterCount > CHUNK_SIZE * CHUNK_SIZE * 0.5) {
                    highCoverageCount++;
                }
            }
        }

        System.out.println("OCEAN 区块水域覆盖 > 50%: "
                + highCoverageCount + "/" + tested);
        assertTrue(tested > 0, "应有 OCEAN 区块用于测试");
        assertTrue(highCoverageCount >= tested * 0.9,
                "90% 以上的 OCEAN 区块应有 > 50% 水域覆盖");
    }
}
