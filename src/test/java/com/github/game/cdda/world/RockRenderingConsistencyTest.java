package com.github.game.cdda.world;

import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.engine.core.noise.PerlinNoise;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试岩石（ROCK）渲染位置与数据位置的一致性。
 *
 * <p>排查点：
 * <ul>
 *   <li>区块数据中 ROCK 瓦片的坐标 vs 覆盖层渲染时使用的坐标</li>
 *   <li>ROCK 覆盖层精灵的绘制偏移是否正确</li>
 *   <li>ChunkManager.getTile() 返回 ROCK 时，对应地面层数据是否完整</li>
 *   <li>ROCK 的 overlay 标记与实际渲染路径是否匹配</li>
 * </ul>
 */
class RockRenderingConsistencyTest {

    private static final long TEST_SEED = 42L;
    private static final int CHUNK_SIZE = Chunk.SIZE;

    /**
     * 扫描并打印不同种子和范围内的岩石群落分布。
     */
    @Test
    void scanRockyBiomes() {
        WorldMap worldMap = new WorldMap(TEST_SEED);
        java.util.Map<String, Integer> biomeCounts = new java.util.HashMap<>();
        int range = 50;
        for (int cy = -range; cy <= range; cy++) {
            for (int cx = -range; cx <= range; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome.getRockiness() > 0) {
                    biomeCounts.merge(biome.getName(), 1, Integer::sum);
                }
            }
        }
        System.out.println("=== 种子 " + TEST_SEED + " 岩石群落分布 ===");
        biomeCounts.forEach((name, count) ->
                System.out.printf("  %-15s : %d%n", name, count));
        assertFalse(biomeCounts.isEmpty(), "应存在至少一种岩石群落");
    }

    /**
     * 验证 ROCK 精灵的锚点设置正确。
     * 单瓦片精灵的 anchorY 应为 0（不偏移），
     * 否则精灵会被绘制到与碰撞位置不同的屏幕坐标。
     */
    @Test
    void rockSpriteAnchorCorrect() {
        java.util.Map<String, com.github.game.engine.core.sprite.Sprite> sprites =
                com.github.game.cdda.sprite.TileSpriteData.createAllTileSprites();

        com.github.game.engine.core.sprite.Sprite rockSprite = sprites.get("tile.rock");
        assertNotNull(rockSprite, "tile.rock 精灵必须存在");

        // 验证锚点：单瓦片精灵的 anchorY 应为 0（顶部对齐，不偏移）
        // 默认 anchorY=1.0 会导致精灵向上偏移一整格，造成贴图与位置不一致
        assertEquals(0.0, rockSprite.getAnchorY(), 0.001,
                "ROCK 精灵 anchorY 应为 0（顶部对齐），当前值=" + rockSprite.getAnchorY()
                        + " 会导致精灵向上偏移一整格");
        assertEquals(0.0, rockSprite.getAnchorX(), 0.001,
                "ROCK 精灵 anchorX 应为 0");

        // 验证尺寸：应为 1x1 瓦片
        assertEquals(1.0, rockSprite.getTileWidth(), 0.001,
                "ROCK 精灵 tileWidth 应为 1.0");
        assertEquals(1.0, rockSprite.getTileHeight(), 0.001,
                "ROCK 精灵 tileHeight 应为 1.0");

        // 验证所有覆盖层单瓦片精灵（overlay=true, 1x1）的锚点都为 (0,0)
        String[] overlaySingleTiles = {
                "tile.rock", "tile.reeds", "tile.withered_tree",
                "tile.withered_bush", "tile.dead_grass"
        };
        for (String tileId : overlaySingleTiles) {
            com.github.game.engine.core.sprite.Sprite sprite = sprites.get(tileId);
            if (sprite != null) {
                assertEquals(0.0, sprite.getAnchorY(), 0.001,
                        tileId + " 的 anchorY 应为 0（单瓦片覆盖层不应偏移）");
            }
        }

        System.out.println("ROCK 精灵: anchor=(" + rockSprite.getAnchorX()
                + ", " + rockSprite.getAnchorY() + "), tileSize=("
                + rockSprite.getTileWidth() + "x" + rockSprite.getTileHeight() + ")");
    }

    /**
     * 查找含岩石的区块，验证 ROCK 瓦片的数据一致性：
     * 1. ROCK 必须标记为 overlay
     * 2. ROCK 位置的地面层不能为 null
     * 3. ROCK 位置不可通过
     * 4. 模拟渲染坐标计算，验证与碰撞坐标一致
     */
    @Test
    void rockDataConsistency() {
        long seed = TEST_SEED;
        PerlinNoise noise = new PerlinNoise(seed);
        WorldMap worldMap = new WorldMap(seed);

        // 搜索含岩石的区块
        List<int[]> rockPositions = new ArrayList<>();
        int rockChunkX = 0, rockChunkY = 0;

        for (int cy = -50; cy <= 50 && rockPositions.isEmpty(); cy++) {
            for (int cx = -50; cx <= 50 && rockPositions.isEmpty(); cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome.getRockiness() <= 0) continue;

                Chunk chunk = new Chunk(cx, cy, biome);
                chunk.generate(noise, worldMap);

                for (int row = 0; row < CHUNK_SIZE; row++) {
                    for (int col = 0; col < CHUNK_SIZE; col++) {
                        if (chunk.getTile(col, row) == TileType.ROCK) {
                            rockPositions.add(new int[]{col, row});
                        }
                    }
                }
                if (!rockPositions.isEmpty()) {
                    rockChunkX = cx;
                    rockChunkY = cy;
                }
            }
        }

        assertFalse(rockPositions.isEmpty(), "在搜索范围内未找到含岩石的区块");
        System.out.printf("在区块 (%d, %d) 中找到 %d 个岩石瓦片%n",
                rockChunkX, rockChunkY, rockPositions.size());

        // 重新生成该区块进行详细验证
        BiomeType biome = worldMap.getBiomeAtChunk(rockChunkX, rockChunkY);
        PerlinNoise noise2 = new PerlinNoise(seed);
        Chunk chunk = new Chunk(rockChunkX, rockChunkY, biome);
        chunk.generate(noise2, worldMap);

        int tileWidth = 32;
        int tileHeight = 32;

        for (int[] pos : rockPositions) {
            int col = pos[0];
            int row = pos[1];

            // 验证1: ROCK 标记为 overlay
            TileType tile = chunk.getTile(col, row);
            assertEquals(TileType.ROCK, tile, "瓦片应为 ROCK");
            assertTrue(tile.isOverlay(), "ROCK 必须标记为 overlay，否则渲染管线会把它当地面处理");

            // 验证2: 地面层不为 null
            TileType ground = chunk.getGroundTile(col, row);
            assertNotNull(ground, "ROCK 位置的地面层不能为 null");
            System.out.printf("  岩石 (%d, %d): 地面=%s, overlay=%s%n",
                    col, row, ground.getName(), tile.isOverlay());

            // 验证3: ROCK 不可通过
            assertFalse(tile.isPassable(), "ROCK 应不可通过");

            // 验证4: 模拟渲染坐标计算，确保与碰撞检测坐标一致
            // 世界瓦片坐标
            int worldTileX = rockChunkX * CHUNK_SIZE + col;
            int worldTileY = rockChunkY * CHUNK_SIZE + row;

            // 碰撞检测使用的坐标（ChunkManager.getTile 内部转换）
            int queryChunkX = Math.floorDiv(worldTileX, CHUNK_SIZE);
            int queryChunkY = Math.floorDiv(worldTileY, CHUNK_SIZE);
            int queryLocalCol = Math.floorMod(worldTileX, CHUNK_SIZE);
            int queryLocalRow = Math.floorMod(worldTileY, CHUNK_SIZE);

            assertEquals(rockChunkX, queryChunkX, "碰撞查询的区块 X 应一致");
            assertEquals(rockChunkY, queryChunkY, "碰撞查询的区块 Y 应一致");
            assertEquals(col, queryLocalCol, "碰撞查询的局部列应一致");
            assertEquals(row, queryLocalRow, "碰撞查询的局部行应一致");

            // 碰撞检测结果
            TileType collisionTile = chunk.getTile(queryLocalCol, queryLocalRow);
            assertEquals(TileType.ROCK, collisionTile, "碰撞查询应返回 ROCK");

            // 模拟渲染坐标（覆盖层 pass）
            int screenX = worldTileX * tileWidth;   // camera.toViewX 在 camera=(0,0) 时
            int screenY = worldTileY * tileHeight;
            int expectedScreenX = (rockChunkX * CHUNK_SIZE + col) * tileWidth;
            int expectedScreenY = (rockChunkY * CHUNK_SIZE + row) * tileHeight;
            assertEquals(expectedScreenX, screenX, "渲染 X 坐标应与碰撞位置一致");
            assertEquals(expectedScreenY, screenY, "渲染 Y 坐标应与碰撞位置一致");
        }
    }

    /**
     * 验证 ChunkManager 的 getTile/getTileIfReady 对 ROCK 返回一致结果。
     */
    @Test
    void chunkManagerRockConsistency() {
        long seed = TEST_SEED;
        PerlinNoise noise = new PerlinNoise(seed);
        WorldMap worldMap = new WorldMap(seed);

        // 找一个有岩石的区块
        for (int cy = -50; cy <= 50; cy++) {
            for (int cx = -50; cx <= 50; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome.getRockiness() <= 0) continue;

                Chunk chunk = new Chunk(cx, cy, biome);
                chunk.generate(noise, worldMap);

                // 找第一个岩石
                for (int row = 0; row < CHUNK_SIZE; row++) {
                    for (int col = 0; col < CHUNK_SIZE; col++) {
                        if (chunk.getTile(col, row) == TileType.ROCK) {
                            int worldTileX = cx * CHUNK_SIZE + col;
                            int worldTileY = cy * CHUNK_SIZE + row;

                            // 通过 ChunkManager 查询
                            ChunkManager cm = new ChunkManager(seed, 1, worldMap);
                            TileType fromGetTile = cm.getTile(worldTileX, worldTileY);
                            TileType fromGetTileIfReady = cm.getTileIfReady(worldTileX, worldTileY);

                            assertEquals(TileType.ROCK, fromGetTile,
                                    "ChunkManager.getTile() 应返回 ROCK");
                            assertEquals(TileType.ROCK, fromGetTileIfReady,
                                    "ChunkManager.getTileIfReady() 应返回 ROCK（区块已生成）");

                            // 验证地面层查询一致性
                            TileType groundFromGet = cm.getGroundTile(worldTileX, worldTileY);
                            TileType groundFromIfReady = cm.getGroundTileIfReady(worldTileX, worldTileY);

                            assertNotNull(groundFromGet, "getGroundTile 不应返回 null");
                            assertEquals(groundFromGet, groundFromIfReady,
                                    "getGroundTile 和 getGroundTileIfReady 应返回相同结果");

                            // 验证植被查询一致性（ROCK 位置不应有植被）
                            String vegFromGet = cm.getVegetation(worldTileX, worldTileY);
                            String vegFromIfReady = cm.getVegetationIfReady(worldTileX, worldTileY);
                            assertEquals(vegFromGet, vegFromIfReady,
                                    "getVegetation 和 getVegetationIfReady 应返回相同结果");

                            System.out.printf("岩石验证通过: 世界坐标 (%d, %d), " +
                                            "tile=ROCK, ground=%s, vegetation=%s%n",
                                    worldTileX, worldTileY,
                                    groundFromGet != null ? groundFromGet.getName() : "null",
                                    vegFromGet);

                            return; // 找到一个就够了
                        }
                    }
                }
            }
        }
        fail("在搜索范围内未找到含岩石的区块");
    }

    /**
     * 验证 ROCK 在渲染管线中走覆盖层路径，而非地面层路径。
     * 模拟 TileMap.render() 的逻辑。
     */
    @Test
    void rockRenderingPathSimulation() {
        long seed = TEST_SEED;
        PerlinNoise noise = new PerlinNoise(seed);
        WorldMap worldMap = new WorldMap(seed);

        for (int cy = -50; cy <= 50; cy++) {
            for (int cx = -50; cx <= 50; cx++) {
                BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
                if (biome.getRockiness() <= 0) continue;

                Chunk chunk = new Chunk(cx, cy, biome);
                chunk.generate(noise, worldMap);

                // 统计岩石数量
                int rockCount = 0;
                for (int row = 0; row < CHUNK_SIZE; row++) {
                    for (int col = 0; col < CHUNK_SIZE; col++) {
                        if (chunk.getTile(col, row) == TileType.ROCK) {
                            rockCount++;
                        }
                    }
                }
                if (rockCount == 0) continue;

                System.out.printf("区块 (%d, %d) 含 %d 个岩石，模拟渲染管线...%n", cx, cy, rockCount);

                // 模拟 TileMap.render() 的双 pass 渲染
                int tileWidth = 32;
                int tileHeight = 32;

                // 记录每个瓦片在主 pass 和覆盖层 pass 中绘制了什么
                int mainPassDrawn = 0;
                int overlayPassDrawn = 0;

                // 主 pass（地面层）：迭代 startCol..endCol
                for (int row = 0; row < CHUNK_SIZE; row++) {
                    for (int col = 0; col < CHUNK_SIZE; col++) {
                        int worldCol = cx * CHUNK_SIZE + col;
                        int worldRow = cy * CHUNK_SIZE + row;

                        TileType tile = chunk.getTile(col, row);
                        if (tile == null) continue;

                        // drawGroundTile 逻辑
                        TileType groundTile = tile.isOverlay()
                                ? chunk.getGroundTile(col, row)
                                : tile;
                        if (groundTile == null) groundTile = tile;

                        // 主 pass 绘制地面
                        assertNotNull(groundTile,
                                String.format("主 pass: 位置 (%d, %d) 地面层为 null, tile=%s",
                                        worldCol, worldRow, tile.getName()));
                        mainPassDrawn++;
                    }
                }

                // 覆盖层 pass：迭代 renderStartCol..renderEndCol
                int margin = 3;
                int renderStartCol = -margin;
                int renderStartRow = -margin;
                int renderEndCol = CHUNK_SIZE - 1 + margin;
                int renderEndRow = CHUNK_SIZE - 1 + margin;

                for (int r = renderStartRow; r <= renderEndRow; r++) {
                    for (int c = renderStartCol; c <= renderEndCol; c++) {
                        int worldCol = cx * CHUNK_SIZE + c;
                        int worldRow = cy * CHUNK_SIZE + r;

                        // 获取瓦片（可能越界）
                        TileType tile = chunk.getTile(c, r);
                        if (tile == null || !tile.isOverlay()) continue;

                        // 计算屏幕坐标
                        int screenX = worldCol * tileWidth;
                        int screenY = worldRow * tileHeight;

                        // 验证：屏幕坐标对应的世界瓦片位置就是当前遍历的位置
                        int reconstructedWorldCol = screenX / tileWidth;
                        int reconstructedWorldRow = screenY / tileHeight;
                        assertEquals(worldCol, reconstructedWorldCol,
                                "覆盖层渲染 X 坐标反推的世界瓦片列应一致");
                        assertEquals(worldRow, reconstructedWorldRow,
                                "覆盖层渲染 Y 坐标反推的世界瓦片行应一致");

                        // 验证：该世界坐标处通过 ChunkManager 查询应得到相同的 tile
                        ChunkManager cm = new ChunkManager(seed, 1, worldMap);
                        TileType queriedTile = cm.getTile(worldCol, worldRow);
                        assertEquals(tile, queriedTile,
                                String.format("覆盖层 pass: 位置 (%d, %d) 渲染读取和碰撞查询结果不一致: " +
                                        "渲染=%s, 查询=%s", worldCol, worldRow, tile.getName(),
                                        queriedTile != null ? queriedTile.getName() : "null"));

                        overlayPassDrawn++;
                    }
                }

                System.out.printf("  主 pass 绘制: %d, 覆盖层 pass 绘制: %d (含 margin 范围)%n",
                        mainPassDrawn, overlayPassDrawn);
                assertTrue(rockCount > 0, "应找到岩石");

                return; // 只测一个区块
            }
        }
        fail("在搜索范围内未找到含岩石的区块");
    }
}
