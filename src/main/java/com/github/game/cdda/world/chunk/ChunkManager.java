package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.save.ChunkData;
import com.github.game.engine.core.noise.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
 *   <li>使用 {@link ConcurrentHashMap} 缓存已加载区块，O(1) 查找</li>
 *   <li>区块生成由 {@link WorldMap} 的生物群落驱动（大地图→小地图分层架构）</li>
 *   <li>区块地形在后台线程异步生成，不阻塞 EDT 渲染</li>
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

    /** 已加载区块缓存，key = chunkKey(cx, cy)。线程安全。 */
    private final ConcurrentHashMap<Long, Chunk> chunks;

    /** 后台区块生成线程池（单线程队列执行） */
    private final ExecutorService generationExecutor;

    /** 待生成区块数量（用于日志） */
    private final AtomicInteger pendingGeneration = new AtomicInteger(0);

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
        this.chunks = new ConcurrentHashMap<>();
        this.preloadRadius = preloadRadius;
        this.generationExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chunk-generator");
            t.setDaemon(true);
            return t;
        });
        logger.info("区块管理器初始化 — 种子: {}, 预加载半径: {}", worldSeed, preloadRadius);
    }

    /**
     * 获取世界瓦片坐标处的地形类型。
     * 自动加载所在区块（如果尚未加载）。
     * 如果区块已加载但未生成地形，会在 EDT 同步生成（异步队列来不及时）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 地形类型；区块未加载返回 null
     */
    public TileType getTile(int worldTileX, int worldTileY) {
        Chunk chunk = getOrLoadChunk(worldTileX, worldTileY);
        ensureGenerated(chunk, worldTileX, worldTileY);
        return chunk.getTile(localCol(worldTileX), localRow(worldTileY));
    }

    /**
     * 获取世界瓦片坐标处的地面层地形类型。
     * 地面层是植被放置前的基底地形，用于分层渲染（植被在地面之上）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 地面层地形类型；区块未加载返回 null
     */
    public TileType getGroundTile(int worldTileX, int worldTileY) {
        Chunk chunk = getOrLoadChunk(worldTileX, worldTileY);
        ensureGenerated(chunk, worldTileX, worldTileY);
        return chunk.getGroundTile(localCol(worldTileX), localRow(worldTileY));
    }

    /**
     * 设置指定世界瓦片坐标的地形类型。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @param type       新的地形类型
     */
    public void setTile(int worldTileX, int worldTileY, TileType type) {
        Chunk chunk = getOrLoadChunk(worldTileX, worldTileY);
        chunk.setTile(localCol(worldTileX), localRow(worldTileY), type);
    }

    /**
     * 根据玩家位置更新区块加载状态。
     * 仅在玩家跨越区块边界时触发实际加载/卸载。
     * 区块地形生成提交到后台线程，不阻塞当前帧。
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

        // 1. 加载预加载范围内的所有区块（仅创建对象，很快）
        preloadAround(playerChunkX, playerChunkY);

        // 2. 提交后台生成任务（异步，不阻塞 EDT）
        submitGeneration(playerChunkX, playerChunkY);

        // 3. 卸载远离玩家的区块
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
        BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
        Chunk chunk = new Chunk(cx, cy, biome);
        chunks.put(chunkKey(cx, cy), chunk);
        return chunk;
    }

    /**
     * 从存档数据加载区块（跳过正常生成流程）。
     * 直接恢复存档中保存的地形和植被数据。
     *
     * @param cx        区块 X 坐标
     * @param cy        区块 Y 坐标
     * @param chunkData 存档数据
     */
    public void loadChunkFromSave(int cx, int cy, ChunkData chunkData) {
        long key = chunkKey(cx, cy);
        BiomeType biome = worldMap.getBiomeAtChunk(cx, cy);
        Chunk chunk = new Chunk(cx, cy, biome);
        chunk.loadFromSave(chunkData.tiles, chunkData.vegetation);
        chunks.put(key, chunk);
        logger.debug("从存档恢复区块 ({}, {})", cx, cy);
    }

    /**
     * 提交预加载区域内所有区块的后台生成任务。
     *
     * <p><b>两阶段生成</b>（解决边界混合时序依赖）：
     * <ol>
     *   <li><b>Phase 1 — 生成</b>：所有区块独立生成内部地形（高程/植被/水域），
     *       不做边界混合（因为邻居可能尚未生成）。</li>
     *   <li><b>Phase 2 — 混合</b>：所有区块生成完成后，统一执行边界混合。
     *       此时邻居都已生成，混合不会被跳过。</li>
     * </ol>
     *
     * <p>修复了此前单阶段生成中，先生成的区块因邻居未就绪而跳过边界混合、
     * 导致区块边界永久缺少树木的问题。
     */
    private void submitGeneration(int playerChunkX, int playerChunkY) {
        // 收集待生成区块
        int totalToGenerate = 0;
        for (int dy = -preloadRadius; dy <= preloadRadius; dy++) {
            for (int dx = -preloadRadius; dx <= preloadRadius; dx++) {
                int cx = playerChunkX + dx;
                int cy = playerChunkY + dy;
                Chunk chunk = chunks.get(chunkKey(cx, cy));
                if (chunk != null && !chunk.isGenerated()) {
                    totalToGenerate++;
                }
            }
        }
        if (totalToGenerate == 0) return;

        pendingGeneration.set(totalToGenerate);
        logger.debug("提交 {} 个区块后台生成（两阶段）", totalToGenerate);

        // 提交到后台线程（单线程队列执行，保证阶段顺序）
        generationExecutor.submit(() -> {
            long start = System.currentTimeMillis();
            int generatedCount = 0;

            // Phase 1: 生成所有区块的内部地形（不做边界混合）
            for (int dy = -preloadRadius; dy <= preloadRadius; dy++) {
                for (int dx = -preloadRadius; dx <= preloadRadius; dx++) {
                    int cx = playerChunkX + dx;
                    int cy = playerChunkY + dy;
                    Chunk chunk = chunks.get(chunkKey(cx, cy));
                    if (chunk != null && !chunk.isGenerated()) {
                        chunk.generate(noise, worldMap);
                        generatedCount++;
                    }
                }
            }

            // Phase 2: 所有区块生成完成后，统一执行边界混合
            // 此时邻居均已生成，blendChunkEdges 不会再因邻居未就绪而跳过
            int blendedCount = 0;
            for (int dy = -preloadRadius; dy <= preloadRadius; dy++) {
                for (int dx = -preloadRadius; dx <= preloadRadius; dx++) {
                    int cx = playerChunkX + dx;
                    int cy = playerChunkY + dy;
                    Chunk chunk = chunks.get(chunkKey(cx, cy));
                    if (chunk != null) {
                        Chunk[][] neighbors = snapshotNeighbors(cx, cy);
                        chunk.blendEdges(neighbors, worldMap);
                        blendedCount++;
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            logger.debug("区块生成完成：生成 {} 个，混合 {} 个，耗时 {}ms",
                    generatedCount, blendedCount, elapsed);

            // 通知生物管理器在新生成的区块中按概率生成生物
            if (creatureManager != null) {
                int spawnMinX = playerChunkX - preloadRadius;
                int spawnMinY = playerChunkY - preloadRadius;
                int spawnMaxX = playerChunkX + preloadRadius;
                int spawnMaxY = playerChunkY + preloadRadius;
                creatureManager.onChunksGenerated(spawnMinX, spawnMinY, spawnMaxX, spawnMaxY);
            }
            pendingGeneration.set(0);
        });
    }

    /**
     * 同步生成单个区块（EDT 回退路径 + 后台线程主路径）。
     * 对区块加锁，防止与后台线程同时 generate()。
     *
     * <p>注意：仅执行阶段1（内部地形生成），不含边界混合。
     * 边界混合由后台线程的 Phase 2 统一处理。
     */
    private void generateChunkSync(Chunk chunk, int cx, int cy) {
        synchronized (chunk) {
            if (chunk.isGenerated()) return;
            chunk.generate(noise, worldMap);
        }
    }

    /**
     * 查找指定世界坐标对应的区块（不检查生成状态）。
     *
     * @param worldTileX 世界瓦片 X
     * @param worldTileY 世界瓦片 Y
     * @return 区块对象；未加载时返回 null
     */
    private Chunk getChunkForTile(int worldTileX, int worldTileY) {
        int cx = floorDiv(worldTileX, Chunk.SIZE);
        int cy = floorDiv(worldTileY, Chunk.SIZE);
        return chunks.get(chunkKey(cx, cy));
    }

    /**
     * 查找指定世界坐标对应的区块，仅在区块已生成完成时返回（非阻塞）。
     *
     * @param worldTileX 世界瓦片 X
     * @param worldTileY 世界瓦片 Y
     * @return 已生成的区块；未加载或未完成生成时返回 null
     */
    private Chunk getGeneratedChunkIfReady(int worldTileX, int worldTileY) {
        Chunk chunk = getChunkForTile(worldTileX, worldTileY);
        return (chunk != null && chunk.isGenerated()) ? chunk : null;
    }

    /**
     * 获取指定世界坐标对应的区块，未加载时自动加载。
     *
     * @param worldTileX 世界瓦片 X
     * @param worldTileY 世界瓦片 Y
     * @return 区块对象（不会返回 null）
     */
    private Chunk getOrLoadChunk(int worldTileX, int worldTileY) {
        int cx = floorDiv(worldTileX, Chunk.SIZE);
        int cy = floorDiv(worldTileY, Chunk.SIZE);
        Chunk chunk = chunks.get(chunkKey(cx, cy));
        return chunk != null ? chunk : loadChunk(cx, cy);
    }

    /**
     * 确保区块已生成（未生成时同步生成，异步队列来不及时回退）。
     *
     * @param chunk      目标区块
     * @param worldTileX 世界瓦片 X（用于计算区块坐标）
     * @param worldTileY 世界瓦片 Y
     */
    private void ensureGenerated(Chunk chunk, int worldTileX, int worldTileY) {
        if (!chunk.isGenerated()) {
            int cx = floorDiv(worldTileX, Chunk.SIZE);
            int cy = floorDiv(worldTileY, Chunk.SIZE);
            generateChunkSync(chunk, cx, cy);
        }
    }

    /** 计算世界坐标在区块内的局部列号 */
    private static int localCol(int worldTileX) {
        return floorMod(worldTileX, Chunk.SIZE);
    }

    /** 计算世界坐标在区块内的局部行号 */
    private static int localRow(int worldTileY) {
        return floorMod(worldTileY, Chunk.SIZE);
    }

    /**
     * 尝试获取瓦片（渲染专用，非阻塞）。
     * 区块未生成完成时返回 null，避免渲染线程被后台生成阻塞。
     *
     * @param worldTileX 世界瓦片 X
     * @param worldTileY 世界瓦片 Y
     * @return 瓦片类型；区块未加载或未完成生成时返回 null
     */
    public TileType getTileIfReady(int worldTileX, int worldTileY) {
        Chunk chunk = getGeneratedChunkIfReady(worldTileX, worldTileY);
        return chunk != null ? chunk.getTile(localCol(worldTileX), localRow(worldTileY)) : null;
    }

    /**
     * 尝试获取地面层瓦片（渲染专用，非阻塞）。
     *
     * @param worldTileX 世界瓦片 X
     * @param worldTileY 世界瓦片 Y
     * @return 地面层瓦片类型；区块未加载或未完成生成时返回 null
     */
    public TileType getGroundTileIfReady(int worldTileX, int worldTileY) {
        Chunk chunk = getGeneratedChunkIfReady(worldTileX, worldTileY);
        return chunk != null ? chunk.getGroundTile(localCol(worldTileX), localRow(worldTileY)) : null;
    }

    /**
     * 尝试获取植被信息（渲染专用，非阻塞）。
     *
     * @param worldTileX 世界瓦片 X
     * @param worldTileY 世界瓦片 Y
     * @return 植被物种 ID；区块未加载或未完成生成时返回 null
     */
    public String getVegetationIfReady(int worldTileX, int worldTileY) {
        Chunk chunk = getGeneratedChunkIfReady(worldTileX, worldTileY);
        return chunk != null ? chunk.getVegetation(localCol(worldTileX), localRow(worldTileY)) : null;
    }

    /**
     * 快照指定区块周围的 5×5 邻居引用。
     * 用于异步生成时获取最新的邻居状态。
     */
    private Chunk[][] snapshotNeighbors(int centerChunkX, int centerChunkY) {
        Chunk[][] neighbors = new Chunk[5][5];
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int cx = centerChunkX + dx;
                int cy = centerChunkY + dy;
                neighbors[dy + 2][dx + 2] = chunks.get(chunkKey(cx, cy));
            }
        }
        return neighbors;
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
     * 获取生物管理器。
     */
    public CreatureManager getCreatureManager() {
        return creatureManager;
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
        return getChunkForTile(worldTileX, worldTileY) != null;
    }

    /** 获取预加载半径 */
    public int getPreloadRadius() { return preloadRadius; }
    /** 获取世界种子 */
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
        Chunk chunk = getChunkForTile(worldTileX, worldTileY);
        return chunk != null ? chunk.getVegetation(localCol(worldTileX), localRow(worldTileY)) : null;
    }

    /**
     * 获取指定世界瓦片坐标的生长状态。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 生长状态，无植被或未加载返回 null
     */
    public com.github.game.cdda.world.vegetation.VegetationState getGrowthState(int worldTileX, int worldTileY) {
        Chunk chunk = getChunkForTile(worldTileX, worldTileY);
        return chunk != null ? chunk.getGrowthState(localCol(worldTileX), localRow(worldTileY)) : null;
    }

    /**
     * 设置指定世界瓦片坐标的植被物种（播种时调用）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @param speciesId  植被物种 ID
     * @param state      生长状态
     */
    public void setVegetation(int worldTileX, int worldTileY, String speciesId,
                              com.github.game.cdda.world.vegetation.VegetationState state) {
        Chunk chunk = getChunkForTile(worldTileX, worldTileY);
        if (chunk == null) return;
        int col = localCol(worldTileX), row = localRow(worldTileY);
        chunk.setVegetation(col, row, speciesId);
        if (state != null) {
            chunk.setGrowthState(col, row, state);
        }
    }

    /**
     * 清除指定世界瓦片坐标的植被（砍伐后调用）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     */
    public void clearVegetation(int worldTileX, int worldTileY) {
        Chunk chunk = getChunkForTile(worldTileX, worldTileY);
        if (chunk != null) {
            chunk.clearVegetation(localCol(worldTileX), localRow(worldTileY));
        }
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

    /**
     * 获取指定世界瓦片坐标的土壤肥力值。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 肥力值（0~100），区块未加载返回 0
     */
    public double getSoilFertility(int worldTileX, int worldTileY) {
        Chunk chunk = getChunkForTile(worldTileX, worldTileY);
        if (chunk == null || chunk.getSoilFertility() == null) return 0.0;
        return chunk.getSoilFertility().getFertility(localCol(worldTileX), localRow(worldTileY));
    }

    /**
     * 获取待生成区块数（调试用）。
     */
    public int getPendingGenerationCount() {
        return pendingGeneration.get();
    }

    /**
     * 关闭后台生成线程池。
     * 由 GameWorld 清理时调用。
     */
    public void shutdown() {
        generationExecutor.shutdownNow();
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
