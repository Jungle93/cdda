package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.engine.core.noise.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 区块管理器。负责区块的加载、缓存、预加载和卸载。
 *
 * <p>核心职责：
 * <ol>
 *   <li>根据玩家位置自动加载周围区块（可配置预加载半径）</li>
 *   <li>提供世界坐标 → 瓦片的查询接口</li>
 *   <li>卸载远离玩家的区块以释放内存</li>
 * </ol>
 *
 * <p>设计要点：
 * <ul>
 *   <li>仅在玩家跨越区块边界时触发加载/卸载（避免每帧重复操作）</li>
 *   <li>使用 HashMap 缓存已加载区块，O(1) 查找</li>
 *   <li>区块生成由 {@link WorldMap} 的生物群落驱动（大地图→小地图分层架构）</li>
 * </ul>
 */
public class ChunkManager {

    private static final Logger logger = LoggerFactory.getLogger(ChunkManager.class);

    /** 世界种子 */
    private final long worldSeed;

    /** 世界 Perlin 噪声生成器（局部地形细节） */
    private final PerlinNoise noise;

    /** 世界地图（大地图，提供生物群落信息） */
    private final WorldMap worldMap;

    /** 生物管理器（新区块生成后通知其生成生物，可为 null） */
    private CreatureManager creatureManager;

    /** 已加载区块缓存，key = chunkKey(cx, cy) */
    private final Map<Long, Chunk> chunks;

    /** 预加载半径（以区块为单位） */
    private int preloadRadius;

    /** 上次触发加载/卸载时的玩家区块坐标（用于避免重复触发） */
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkY = Integer.MIN_VALUE;

    /**
     * 创建区块管理器。
     *
     * @param worldSeed     世界种子
     * @param preloadRadius 预加载半径（区块数）
     * @param worldMap      世界地图（提供生物群落信息）
     */
    public ChunkManager(long worldSeed, int preloadRadius, WorldMap worldMap) {
        this.worldSeed = worldSeed;
        this.noise = new PerlinNoise(worldSeed);
        this.worldMap = worldMap;
        this.chunks = new HashMap<>();
        this.preloadRadius = preloadRadius;
        logger.info("区块管理器初始化 — 种子: {}, 预加载半径: {}", worldSeed, preloadRadius);
    }

    /**
     * 获取世界瓦片坐标处的地形类型。
     * 自动加载所在区块（如果尚未加载）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 地形类型；区块未加载时返回 null
     */
    public TileType getTile(int worldTileX, int worldTileY) {
        int cx = floorDiv(worldTileX, Chunk.SIZE);
        int cy = floorDiv(worldTileY, Chunk.SIZE);

        Chunk chunk = chunks.get(chunkKey(cx, cy));
        if (chunk == null) {
            // 即时加载（通常不应发生，因为 updateChunks 会预加载）
            chunk = loadChunk(cx, cy);
        }

        // 局部坐标
        int localCol = floorMod(worldTileX, Chunk.SIZE);
        int localRow = floorMod(worldTileY, Chunk.SIZE);
        return chunk.getTile(localCol, localRow);
    }

    /**
     * 设置指定世界瓦片坐标的地形类型。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @param type       新的地形类型
     */
    public void setTile(int worldTileX, int worldTileY, TileType type) {
        int cx = floorDiv(worldTileX, Chunk.SIZE);
        int cy = floorDiv(worldTileY, Chunk.SIZE);

        Chunk chunk = chunks.get(chunkKey(cx, cy));
        if (chunk == null) {
            chunk = loadChunk(cx, cy);
        }

        int localCol = floorMod(worldTileX, Chunk.SIZE);
        int localRow = floorMod(worldTileY, Chunk.SIZE);
        chunk.setTile(localCol, localRow, type);
    }

    /**
     * 根据玩家位置更新区块加载状态。
     * 仅在玩家跨越区块边界时触发实际加载/卸载。
     *
     * @param playerWorldPixelX 玩家世界像素 X
     * @param playerWorldPixelY 玩家世界像素 Y
     * @param tileWidth         瓦像素宽度
     * @param tileHeight        瓦素高度
     */
    public void updateChunks(int playerWorldPixelX, int playerWorldPixelY,
                             int tileWidth, int tileHeight) {
        // 像素 → 瓦片坐标 → 区块坐标（使用 floorDiv 正确处理负坐标）
        int playerTileX = Math.floorDiv(playerWorldPixelX, tileWidth);
        int playerTileY = Math.floorDiv(playerWorldPixelY, tileHeight);
        int playerChunkX = floorDiv(playerTileX, Chunk.SIZE);
        int playerChunkY = floorDiv(playerTileY, Chunk.SIZE);

        // 玩家区块未变化则跳过（避免每帧重复操作）
        if (playerChunkX == lastPlayerChunkX && playerChunkY == lastPlayerChunkY) {
            return;
        }
        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkY = playerChunkY;

        // 加载预加载范围内的所有区块（不生成瓦片）
        preloadAround(playerChunkX, playerChunkY);

        // 计算排水并生成瓦片
        computeDrainageAndGenerate(playerChunkX, playerChunkY);

        // 卸载远离玩家的区块
        unloadDistant(playerChunkX, playerChunkY);
    }

    /**
     * 预加载指定区块周围的区块。
     * 加载 [cx-r, cx+r] × [cy-r, cy+r] 范围内的所有区块。
     */
    private void preloadAround(int centerChunkX, int centerChunkY) {
        int loaded = 0;
        for (int dy = -preloadRadius; dy <= preloadRadius; dy++) {
            for (int dx = -preloadRadius; dx <= preloadRadius; dx++) {
                int cx = centerChunkX + dx;
                int cy = centerChunkY + dy;
                long key = chunkKey(cx, cy);
                if (!chunks.containsKey(key)) {
                    loadChunk(cx, cy);
                    loaded++;
                }
            }
        }
        if (loaded > 0) {
            logger.info("预加载 {} 个区块，当前缓存: {}", loaded, chunks.size());
        }
    }

    /**
     * 卸载距离玩家超过预加载半径的区块。
     * 额外保留 1 个区块的缓冲（避免频繁加载/卸载边界区块）。
     */
    private void unloadDistant(int playerChunkX, int playerChunkY) {
        int unloadThreshold = preloadRadius + 1;
        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        int unloaded = 0;
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> entry = it.next();
            Chunk chunk = entry.getValue();
            int dx = Math.abs(chunk.getChunkX() - playerChunkX);
            int dy = Math.abs(chunk.getChunkY() - playerChunkY);
            if (dx > unloadThreshold || dy > unloadThreshold) {
                it.remove();
                unloaded++;
            }
        }
        if (unloaded > 0) {
            logger.debug("卸载 {} 个区块，剩余: {}", unloaded, chunks.size());
        }
    }

    /**
     * 加载指定区块（仅创建对象，不生成瓦片）。
     * 从世界地图获取该位置的生物群落。
     */
    private Chunk loadChunk(int cx, int cy) {
        // 从世界地图获取此区块的生物群落
        BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
        Chunk chunk = new Chunk(cx, cy, noise, biome);
        chunks.put(chunkKey(cx, cy), chunk);
        return chunk;
    }

    /**
     * 生成预加载区域内的所有区块。
     *
     * <p>流程：
     * <ol>
     *   <li>构建 5×5 邻居区块引用数组</li>
     *   <li>对区域内每个区块调用 generate()，传入邻居引用</li>
     * </ol>
     *
     * <p>注意：不再生成 DrainageMap（性能考虑），水域由 WorldMap 直接提供。
     */
    private void computeDrainageAndGenerate(int playerChunkX, int playerChunkY) {
        // 构建 5×5 邻居区块引用（用于区块边界混合）
        Chunk[][] neighbors = new Chunk[5][5];
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int cx = playerChunkX + dx;
                int cy = playerChunkY + dy;
                neighbors[dy + 2][dx + 2] = chunks.get(chunkKey(cx, cy));
            }
        }

        // 生成预加载区域内的所有区块
        for (int dy = -preloadRadius; dy <= preloadRadius; dy++) {
            for (int dx = -preloadRadius; dx <= preloadRadius; dx++) {
                int cx = playerChunkX + dx;
                int cy = playerChunkY + dy;
                Chunk chunk = chunks.get(chunkKey(cx, cy));
                if (chunk != null) {
                    chunk.generate(noise, worldMap, neighbors);
                }
            }
        }

        // 通知生物管理器在新生成的区块中按概率生成生物
        if (creatureManager != null) {
            int spawnMinX = playerChunkX - preloadRadius;
            int spawnMinY = playerChunkY - preloadRadius;
            int spawnMaxX = playerChunkX + preloadRadius;
            int spawnMaxY = playerChunkY + preloadRadius;
            creatureManager.onChunksGenerated(spawnMinX, spawnMinY, spawnMaxX, spawnMaxY);
        }
    }

    /**
     * 将两个 int 合并为 long key。
     * 高 32 位存 cx，低 32 位存 cy。
     */
    private static long chunkKey(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }

    /** 设置预加载半径 */
    public void setPreloadRadius(int radius) {
        this.preloadRadius = radius;
    }

    /**
     * 设置生物管理器（区块生成后回调）。
     * 由 GameWorld 在构造时调用。
     *
     * @param creatureManager 生物管理器
     */
    public void setCreatureManager(CreatureManager creatureManager) {
        this.creatureManager = creatureManager;
    }

    /**
     * 检查指定区块是否已加载（不触发自动加载）。
     * 用于世界地图等需要大范围渲染但不想触发卡顿的场景。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 该瓦片所在区块是否已缓存
     */
    public boolean isChunkLoaded(int worldTileX, int worldTileY) {
        int cx = floorDiv(worldTileX, Chunk.SIZE);
        int cy = floorDiv(worldTileY, Chunk.SIZE);
        return chunks.containsKey(chunkKey(cx, cy));
    }

    public int getPreloadRadius() { return preloadRadius; }
    public long getWorldSeed() { return worldSeed; }
    public WorldMap getWorldMap() { return worldMap; }

    /** 获取当前已加载区块数 */
    public int getLoadedChunkCount() { return chunks.size(); }

    /**
     * 获取指定世界瓦片坐标的植被物种 ID。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 物种 ID，无植被或区块未加载返回 null
     */
    public String getVegetation(int worldTileX, int worldTileY) {
        int cx = floorDiv(worldTileX, Chunk.SIZE);
        int cy = floorDiv(worldTileY, Chunk.SIZE);

        Chunk chunk = chunks.get(chunkKey(cx, cy));
        if (chunk == null) return null;

        int localCol = floorMod(worldTileX, Chunk.SIZE);
        int localRow = floorMod(worldTileY, Chunk.SIZE);
        return chunk.getVegetation(localCol, localRow);
    }

    /**
     * 清除指定世界瓦片坐标的植被（砍伐后调用）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     */
    public void clearVegetation(int worldTileX, int worldTileY) {
        int cx = floorDiv(worldTileX, Chunk.SIZE);
        int cy = floorDiv(worldTileY, Chunk.SIZE);

        Chunk chunk = chunks.get(chunkKey(cx, cy));
        if (chunk == null) return;

        int localCol = floorMod(worldTileX, Chunk.SIZE);
        int localRow = floorMod(worldTileY, Chunk.SIZE);
        chunk.clearVegetation(localCol, localRow);
    }

    /**
     * 获取指定区块（如果已加载）。
     *
     * @param chunkX 区块 X 坐标
     * @param chunkY 区块 Y 坐标
     * @return 区块对象，未加载时返回 null
     */
    public Chunk getChunk(int chunkX, int chunkY) {
        return chunks.get(chunkKey(chunkX, chunkY));
    }

    // ── 整数除法工具（正确处理负数） ──────────────

    /** 地板除法（向负无穷取整） */
    private static int floorDiv(int x, int y) {
        return Math.floorDiv(x, y);
    }

    /** 地板取模（结果始终 >= 0） */
    private static int floorMod(int x, int y) {
        return Math.floorMod(x, y);
    }
}
