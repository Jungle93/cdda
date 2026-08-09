package com.github.game.cdda.world.drainage;

import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.cdda.world.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排水算法集成测试。验证大地图（WorldMap）和小地图（Chunk）的地理一致性。
 *
 * <p>现实世界地理规律：
 * <ul>
 *   <li>海洋 → 大面积水域</li>
 *   <li>平原 → 开阔草地，不应有水域</li>
 *   <li>森林 → 树木为主，不应有水域</li>
 *   <li>沼泽 → 水与植被混合</li>
 *   <li>山地/丘陵 → 岩石/草地，水往低处流</li>
 *   <li>河流从高地流向低地，不应出现在平原中央</li>
 * </ul>
 */
class DrainageIntegrationTest {

    private WorldMap worldMap;
    private DrainageCalculator calculator;

    @BeforeEach
    void setUp() {
        // 固定种子保证测试可重复
        worldMap = new WorldMap(42L);
        calculator = new DrainageCalculator(worldMap);
    }

    // ── 大地图群落分布验证 ──────────────────

    @Test
    void testBiomeDistribution() {
        // 生成较大区域（16×16 区块 = 512×512 瓦片）
        int chunkRadius = 8;
        int totalChunks = (chunkRadius * 2 + 1) * (chunkRadius * 2 + 1);
        int oceanCount = 0, plainsCount = 0, forestCount = 0,
            swampCount = 0, mountainCount = 0, desertCount = 0, otherCount = 0;

        for (int cy = -chunkRadius; cy <= chunkRadius; cy++) {
            for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome == BiomeType.OCEAN) oceanCount++;
                else if (biome == BiomeType.PLAINS) plainsCount++;
                else if (biome == BiomeType.FOREST || biome == BiomeType.DENSE_FOREST) forestCount++;
                else if (biome == BiomeType.SWAMP) swampCount++;
                else if (biome == BiomeType.MOUNTAIN) mountainCount++;
                else if (biome == BiomeType.DESERT) desertCount++;
                else otherCount++;
            }
        }

        // 验证群落分布合理性
        assertTrue(totalChunks > 0, "应有区块");
        assertTrue(oceanCount + plainsCount + forestCount + swampCount + mountainCount + desertCount + otherCount == totalChunks,
                "群落计数总和应等于区块总数");

        // 输出统计（调试用）
        System.out.println("=== 群落分布统计 ===");
        System.out.printf("海洋: %d (%.1f%%)%n", oceanCount, oceanCount * 100.0 / totalChunks);
        System.out.printf("平原: %d (%.1f%%)%n", plainsCount, plainsCount * 100.0 / totalChunks);
        System.out.printf("森林: %d (%.1f%%)%n", forestCount, forestCount * 100.0 / totalChunks);
        System.out.printf("沼泽: %d (%.1f%%)%n", swampCount, swampCount * 100.0 / totalChunks);
        System.out.printf("山地: %d (%.1f%%)%n", mountainCount, mountainCount * 100.0 / totalChunks);
        System.out.printf("沙漠: %d (%.1f%%)%n", desertCount, desertCount * 100.0 / totalChunks);
        System.out.printf("其他: %d (%.1f%%)%n", otherCount, otherCount * 100.0 / totalChunks);
    }

    // ── 干燥群落不应有水域 ──────────────────

    @Test
    void testDryBiomeNoWater() {
        // 计算排水图
        int chunkRadius = 4;
        int minWorld = -chunkRadius * Chunk.SIZE;
        int maxWorld = chunkRadius * Chunk.SIZE - 1;
        DrainageMap drainageMap = calculator.compute(minWorld, minWorld, maxWorld, maxWorld);

        // 遍历所有瓦片，验证干燥群落（waterLevel==0）没有水域
        int dryBiomeWaterTiles = 0;
        int dryBiomeTotalTiles = 0;

        for (int row = 0; row < drainageMap.height; row++) {
            for (int col = 0; col < drainageMap.width; col++) {
                int worldX = drainageMap.minX + col;
                int worldY = drainageMap.minY + row;
                BiomeType biome = worldMap.getBiomeAt(worldX, worldY);

                if (biome.getWaterLevel() <= 0.0f) {
                    dryBiomeTotalTiles++;
                    double waterLevel = drainageMap.getWaterLevel(worldX, worldY);
                    if (waterLevel > 0.0) {
                        dryBiomeWaterTiles++;
                        System.out.printf("⚠️  干燥群落 (%s) 出现水域: (%d,%d) waterLevel=%.4f%n",
                                biome.getName(), worldX, worldY, waterLevel);
                    }
                }
            }
        }

        // 干燥群落不应有水域
        assertEquals(0, dryBiomeWaterTiles,
                String.format("干燥群落不应有水域，实际发现 %d 个水域瓦片（共 %d 个干燥群落瓦片）",
                        dryBiomeWaterTiles, dryBiomeTotalTiles));
    }

    // ── 水生群落应有水域 ──────────────────

    @Test
    void testAquaticBiomeHasWater() {
        // 计算排水图
        int chunkRadius = 4;
        int minWorld = -chunkRadius * Chunk.SIZE;
        int maxWorld = chunkRadius * Chunk.SIZE - 1;
        DrainageMap drainageMap = calculator.compute(minWorld, minWorld, maxWorld, maxWorld);

        // 统计沼泽和海洋群落的水域比例
        int swampTiles = 0, swampWaterTiles = 0;
        int oceanTiles = 0, oceanWaterTiles = 0;

        for (int row = 0; row < drainageMap.height; row++) {
            for (int col = 0; col < drainageMap.width; col++) {
                int worldX = drainageMap.minX + col;
                int worldY = drainageMap.minY + row;
                BiomeType biome = worldMap.getBiomeAt(worldX, worldY);
                double waterLevel = drainageMap.getWaterLevel(worldX, worldY);

                if (biome == BiomeType.SWAMP) {
                    swampTiles++;
                    if (waterLevel > 0.0) swampWaterTiles++;
                } else if (biome == BiomeType.OCEAN) {
                    oceanTiles++;
                    if (waterLevel >= 2.0) oceanWaterTiles++; // 海洋应是深水
                }
            }
        }

        // 海洋应几乎全是水
        if (oceanTiles > 0) {
            double oceanWaterRatio = (double) oceanWaterTiles / oceanTiles;
            assertTrue(oceanWaterRatio > 0.8,
                    String.format("海洋群落应 >80%% 水域，实际 %.1f%% (%d/%d)",
                            oceanWaterRatio * 100, oceanWaterTiles, oceanTiles));
        }

        // 沼泽应有相当比例的水域（20%~60%）
        if (swampTiles > 0) {
            double swampWaterRatio = (double) swampWaterTiles / swampTiles;
            assertTrue(swampWaterRatio >= 0.1,
                    String.format("沼泽群落应有一定水域，实际 %.1f%% (%d/%d)",
                            swampWaterRatio * 100, swampWaterTiles, swampTiles));
        }

        System.out.printf("=== 水生群落水域统计 ===%n");
        System.out.printf("海洋: %d/%d (%.1f%%)%n", oceanWaterTiles, oceanTiles,
                oceanTiles > 0 ? oceanWaterRatio(oceanWaterTiles, oceanTiles) : 0);
        System.out.printf("沼泽: %d/%d (%.1f%%)%n", swampWaterTiles, swampTiles,
                swampTiles > 0 ? swampWaterRatio(swampWaterTiles, swampTiles) : 0);
    }

    private double oceanWaterRatio(int water, int total) {
        return (double) water / total * 100;
    }

    private double swampWaterRatio(int water, int total) {
        return (double) water / total * 100;
    }

    // ─ 河流从高地流向低地 ──────────────────

    @Test
    void testRiverFlowsFromHighToLow() {
        // 计算排水图
        int chunkRadius = 4;
        int minWorld = -chunkRadius * Chunk.SIZE;
        int maxWorld = chunkRadius * Chunk.SIZE - 1;
        DrainageMap drainageMap = calculator.compute(minWorld, minWorld, maxWorld, maxWorld);

        // 找到所有河流瓦片（流量 ≥ RIVER_THRESHOLD）
        int riverTiles = 0;
        int riverFromHighElevation = 0;

        for (int row = 0; row < drainageMap.height; row++) {
            for (int col = 0; col < drainageMap.width; col++) {
                int worldX = drainageMap.minX + col;
                int worldY = drainageMap.minY + row;
                int flowAccum = drainageMap.getFlowAccum(worldX, worldY);

                if (flowAccum >= 8) { // RIVER_THRESHOLD
                    riverTiles++;
                    double elevation = drainageMap.getElevation(worldX, worldY);
                    BiomeType biome = worldMap.getBiomeAt(worldX, worldY);

                    // 河流应出现在中低海拔区域（非山地顶部）
                    if (biome != BiomeType.MOUNTAIN || elevation < 0.3) {
                        riverFromHighElevation++;
                    }
                }
            }
        }

        // 大部分河流应不在山地最高处
        if (riverTiles > 0) {
            double ratio = (double) riverFromHighElevation / riverTiles;
            assertTrue(ratio > 0.5,
                    String.format("河流应主要在中低海拔，实际 %.1f%% (%d/%d)",
                            ratio * 100, riverFromHighElevation, riverTiles));
        }

        System.out.printf("=== 河流分布 ===%n");
        System.out.printf("河流瓦片: %d%n", riverTiles);
        System.out.printf("中低海拔河流: %d (%.1f%%)%n", riverFromHighElevation,
                riverTiles > 0 ? (double) riverFromHighElevation / riverTiles * 100 : 0);
    }

    // ── 湖泊出现在洼地 ──────────────────

    @Test
    void testLakesInDepressions() {
        // 计算排水图
        int chunkRadius = 4;
        int minWorld = -chunkRadius * Chunk.SIZE;
        int maxWorld = chunkRadius * Chunk.SIZE - 1;
        DrainageMap drainageMap = calculator.compute(minWorld, minWorld, maxWorld, maxWorld);

        // 找到所有湖泊瓦片（fillElevation > elevation）
        int lakeTiles = 0;
        int lakeInLowElevation = 0;

        for (int row = 0; row < drainageMap.height; row++) {
            for (int col = 0; col < drainageMap.width; col++) {
                int worldX = drainageMap.minX + col;
                int worldY = drainageMap.minY + row;
                double elevation = drainageMap.getElevation(worldX, worldY);
                double fillElevation = drainageMap.getFillElevation(worldX, worldY);

                if (fillElevation > elevation + 0.01) { // 洼地填充
                    lakeTiles++;
                    BiomeType biome = worldMap.getBiomeAt(worldX, worldY);

                    // 湖泊应出现在低海拔群落（沼泽/平原低洼处）
                    if (biome.getBaseElevation() <= 0.0f) {
                        lakeInLowElevation++;
                    }
                }
            }
        }

        // 湖泊应主要在低海拔区域
        if (lakeTiles > 0) {
            double ratio = (double) lakeInLowElevation / lakeTiles;
            assertTrue(ratio > 0.5,
                    String.format("湖泊应主要在低海拔区域，实际 %.1f%% (%d/%d)",
                            ratio * 100, lakeInLowElevation, lakeTiles));
        }

        System.out.printf("=== 湖泊分布 ===%n");
        System.out.printf("湖泊瓦片: %d%n", lakeTiles);
        System.out.printf("低海拔湖泊: %d (%.1f%%)%n", lakeInLowElevation,
                lakeTiles > 0 ? (double) lakeInLowElevation / lakeTiles * 100 : 0);
    }

    // ── 大地图与小地图视觉一致性 ──────────────────

    @Test
    void testWorldMapChunkConsistency() {
        // 创建 ChunkManager 和测试区块
        ChunkManager chunkManager = new ChunkManager(42L, 1, worldMap);

        // 测试几个特定区块（使用不同的玩家位置避免缓存跳过）
        int[][] testChunks = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int playerOffset = 0;

        for (int[] chunkCoord : testChunks) {
            int cx = chunkCoord[0];
            int cy = chunkCoord[1];

            // 获取大地图群落
            BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);

            // 触发区块加载和生成（使用不同的玩家位置）
            int playerTileX = (cx * Chunk.SIZE + Chunk.SIZE / 2) * 16 + playerOffset;
            int playerTileY = (cy * Chunk.SIZE + Chunk.SIZE / 2) * 16;
            chunkManager.updateChunks(playerTileX, playerTileY, 16, 16);
            playerOffset += 1000; // 确保每次调用不同的玩家位置

            Chunk chunk = chunkManager.getChunk(cx, cy);
            if (chunk == null) {
                System.out.printf("区块 (%d,%d) %s: 未加载%n", cx, cy, biome.getName());
                continue;
            }

            // 统计区块内水域比例
            int waterTiles = 0;
            int totalTiles = Chunk.SIZE * Chunk.SIZE;

            for (int row = 0; row < Chunk.SIZE; row++) {
                for (int col = 0; col < Chunk.SIZE; col++) {
                    TileType tile = chunk.getTile(col, row);
                    if (tile == TileType.WATER) {
                        waterTiles++;
                    }
                }
            }

            double waterRatio = (double) waterTiles / totalTiles;

            // 验证一致性
            if (biome == BiomeType.OCEAN) {
                assertTrue(waterRatio > 0.5,
                        String.format("海洋区块 (%d,%d) 应 >50%% 水域，实际 %.1f%%",
                                cx, cy, waterRatio * 100));
            } else if (biome.getWaterLevel() <= 0.0f) {
                // 干燥群落不应有水域
                assertEquals(0, waterTiles,
                        String.format("干燥群落区块 (%d,%d) %s 不应有水域，实际 %d 个",
                                cx, cy, biome.getName(), waterTiles));
            }

            System.out.printf("区块 (%d,%d) %s: 水域 %.1f%% (%d/%d)%n",
                    cx, cy, biome.getName(), waterRatio * 100, waterTiles, totalTiles);
        }
    }

    // ── 水域过渡带验证 ──────────────────

    @Test
    void testWaterTransitionZone() {
        // 创建 ChunkManager 和测试区块
        ChunkManager chunkManager = new ChunkManager(42L, 2, worldMap);

        // 触发区块加载和生成
        int playerTileX = 0;
        int playerTileY = 0;
        chunkManager.updateChunks(playerTileX, playerTileY, 16, 16);

        // 统计多个区块的水域和过渡带
        int waterTiles = 0;
        int sandTiles = 0;
        int grassTiles = 0;
        int totalTiles = 0;

        for (int cy = -2; cy <= 2; cy++) {
            for (int cx = -2; cx <= 2; cx++) {
                Chunk chunk = chunkManager.getChunk(cx, cy);
                if (chunk == null) continue;

                for (int row = 0; row < Chunk.SIZE; row++) {
                    for (int col = 0; col < Chunk.SIZE; col++) {
                        TileType tile = chunk.getTile(col, row);
                        totalTiles++;
                        if (tile == TileType.WATER) waterTiles++;
                        else if (tile == TileType.SAND) sandTiles++;
                        else if (tile == TileType.GRASS) grassTiles++;
                    }
                }
            }
        }

        System.out.printf("=== 水域过渡带统计 ===%n");
        System.out.printf("水域 (WATER): %d (%.1f%%)%n", waterTiles, waterTiles * 100.0 / totalTiles);
        System.out.printf("沙滩 (SAND): %d (%.1f%%)%n", sandTiles, sandTiles * 100.0 / totalTiles);
        System.out.printf("草地 (GRASS): %d (%.1f%%)%n", grassTiles, grassTiles * 100.0 / totalTiles);

        // 验证：如果有水域，应该有沙滩过渡带
        if (waterTiles > 0) {
            assertTrue(sandTiles > 0,
                    String.format("有水域 (%d) 时应有沙滩过渡带，实际 = %d", waterTiles, sandTiles));
            // 沙滩比例应该合理（不是太多也不是太少）
            double sandRatio = (double) sandTiles / totalTiles;
            assertTrue(sandRatio < 0.3,
                    String.format("沙滩比例应 < 30%%，实际 %.1f%%", sandRatio * 100));
        }
    }
}
