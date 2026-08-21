package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.vegetation.VegetationDefinition;
import com.github.game.cdda.world.vegetation.VegetationMap;
import com.github.game.cdda.world.vegetation.VegetationRegistry;
import com.github.game.cdda.world.vegetation.VegetationState;
import com.github.game.cdda.world.vegetation.VegetationType;
import com.github.game.engine.core.noise.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * 单个区块（chunk）。持有 32×32 瓦片数据。
 *
 * <p>地形生成由世界地图（WorldMap）提供的 {@link BiomeType} 驱动：
 * <ul>
 *   <li><b>大地图</b>（WorldMap）决定每个区块的"角色"——平原、森林、海洋等</li>
 *   <li><b>小地图</b>（Chunk）根据群落参数，用局部噪声生成具体地形细节</li>
 * </ul>
 *
 * <p>区块地形分两阶段生成（由 {@link com.github.game.cdda.world.chunk.ChunkManager} 调度）：
 * <ol>
 *   <li><b>阶段1 — generate()</b>：内部地形生成
 *       <ul>
 *         <li>高程 → 基底地形（阈值由群落的 waterLevel/rockiness 偏移）</li>
 *         <li>噪声 → 植被放置（密度噪声 + 群落参数控制聚簇效果）</li>
 *         <li>排水 → 水域处理（查询 WorldMap 决定水域位置）</li>
 *         <li>水边植被（在水域边缘放置芦苇/香蒲）</li>
 *       </ul></li>
 *   <li><b>阶段2 — blendEdges()</b>：边界混合
 *       检查相邻区块的群落类型和边缘瓦片数据，实现跨区块平滑过渡。
 *       混合产生的 TREE/BUSH 会同步分配植被物种。</li>
 * </ol>
 *
 * <h3>线程安全：</h3>
 * <ul>
 *   <li>{@code generated} 标记为 volatile，保证渲染线程可见性</li>
 *   <li>{@code generate()} 和 {@code blendEdges()} 均 synchronized，
 *       防止后台生成线程与 EDT 同步生成路径并发执行</li>
 *   <li>渲染路径通过 {@code isGenerated()} 非阻塞查询，不持锁</li>
 * </ul>
 *
 * <h3>扩展点：</h3>
 * <ul>
 *   <li>新生物群落：注册 BiomeType 即可，无需修改 Chunk</li>
 *   <li>地形修饰器：后续可添加 {@code BiomeTerrainModifier} 接口，支持特殊地形规则</li>
 * </ul>
 */
public class Chunk {

    private static final Logger logger = LoggerFactory.getLogger(Chunk.class);

    /** 区块边长（瓦片数） */
    public static final int SIZE = 32;

    // ── 噪声参数（局部地形细节） ──────────────────

    /** 局部地形噪声频率（特征跨度 ~43 格，细节丰富但不过碎） */
    private static final double TERRAIN_FREQ = 0.023;
    /** 植被密度噪声频率（特征跨度 ~29 格，形成自然聚簇） */
    private static final double VEG_FREQ = 0.035;
    /** 冠层噪声频率（大尺度，定义"哪里有植被"）— VEG_FREQ × 0.6 */
    private static final double CANOPY_FREQ = VEG_FREQ * 0.6;
    /** 间隙噪声频率（小尺度，定义"哪里有林窗"）— VEG_FREQ × 1.8 */
    private static final double CLEARING_FREQ = VEG_FREQ * 1.8;
    /** 地形 fBm 参数 */
    private static final int TERRAIN_OCTAVES = 4;
    /** 植被 fBm 参数（少一层，够用） */
    private static final int VEG_OCTAVES = 3;
    private static final double PERSISTENCE = 0.5;
    private static final double LACUNARITY = 2.0;

    // ── 基础高程阈值 ──────────────────
    /** 基础岩石阈值 */
    private static final double BASE_ROCK_LEVEL = 0.50;

    /** 区块坐标 */
    private final int chunkX;
    private final int chunkY;

    /** 此区块的生物群落 */
    private final BiomeType biome;

    /** 瓦片数据 [row][col]（generate() 调用后初始化） */
    private TileType[][] tiles;

    /** 地面层瓦片 [row][col] — 植被下方的基础地形（用于分层渲染） */
    private TileType[][] groundTiles;

    /** 植被地图（存储每个瓦片的植被物种 ID 和生长状态） */
    private VegetationMap vegetationMap;

    /** 土壤肥力地图（存储每个瓦片的肥力值） */
    private SoilFertility soilFertility;

    /** 是否已生成（volatile 保证跨线程可见性） */
    private volatile boolean generated = false;

    /** 边界混合是否已完成（两阶段生成：generate → blendEdges） */
    private volatile boolean blended = false;

    /**
     * 创建区块（不立即生成，等待排水计算完成后调用 generate()）。
     *
     * @param chunkX 区块 X 坐标（以区块为单位）
     * @param chunkY 区块 Y 坐标（以区块为单位）
     * @param biome  此区块的生物群落（由 WorldMap 决定）
     */
    public Chunk(int chunkX, int chunkY, BiomeType biome) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.biome = biome;
        this.tiles = null;
    }

    // ── 地形生成 ────────────────────────────

    /**
     * 根据生物群落参数生成区块地形（阶段1）。
     *
     * <p>四遍生成（不含边界混合）：
     * <ol>
     *   <li><b>高程 + 环境 → 基底地形</b>
     *       局部噪声生成地形起伏，海拔/湿度/温度共同决定地形类型。</li>
     *   <li><b>噪声 → 植被放置</b>
     *       植被密度噪声 + 群落参数（{@code treeDensity}, {@code grassDensity}）
     *       控制树木/草/花的密度和分布，形成聚簇效果。</li>
     *   <li><b>WorldMap 水域特征 → 湖泊/河流</b>
     *       查询 WorldMap 决定水域位置。</li>
     *   <li><b>水边植被</b>
     *       在水域边缘放置芦苇/香蒲。</li>
     * </ol>
     *
     * <p><b>边界混合</b>已拆分为独立的 {@link #blendEdges(Chunk[][], WorldMap)} 方法（阶段2），
     * 由 {@link ChunkManager} 在所有区块生成完成后统一调用，解决生成时序导致的边界混合遗漏。
     *
     * @param noise       Perlin 噪声生成器
     * @param worldMap    世界地图（提供环境数据）
     */
    public synchronized void generate(PerlinNoise noise, WorldMap worldMap) {
        if (generated) return;
        this.tiles = new TileType[SIZE][SIZE];
        this.groundTiles = new TileType[SIZE][SIZE];
        this.vegetationMap = new VegetationMap(chunkX, chunkY);
        this.soilFertility = new SoilFertility(chunkX, chunkY, biome);

        // 群落基数参数
        double rockThreshold = BASE_ROCK_LEVEL - biome.getRockiness() * 0.30;
        
        // ── 第一遍：高程 + 环境查询 → 基底地形 ──
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                // 采样 WorldMap 湿度（tile 级，跨区块连续）
                double tileMoisture = worldMap.getMoistureAt(globalX, globalY);
                double elevation = noise.fbm(
                        globalX * TERRAIN_FREQ,
                        globalY * TERRAIN_FREQ,
                        TERRAIN_OCTAVES, PERSISTENCE, LACUNARITY
                );

                // 环境参数
                double temperature = worldMap.getTemperatureAt(globalX, globalY);
                double humidity = worldMap.getHumidityAt(globalX, globalY);

                tiles[row][col] = classifyTerrain(
                        elevation, temperature, humidity, tileMoisture,
                        biome, rockThreshold, biome.getWaterLevel(),
                        globalX, globalY);
            }
        }

        // ── 第一遍半：排水算法 → 湖泊/河流/海洋 ──
        // 必须在植被放置之前执行，否则水域无法覆盖已放置的树木/泥土
        carveWaterFeatures(worldMap);

        // 保存地面层（水域雕刻后、植被放置前，所有瓦片都是地面）
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                groundTiles[row][col] = tiles[row][col];
            }
        }

        // ── 第二遍：噪声 + 环境适配 → 植被 ──
        // 此时水域已确定，植被不会放置在水域上
        placeVegetation(noise, worldMap);

        // ── 第二遍半：群落岩石率 → 散布岩石覆盖物 ──
        placeRocks(noise);

        // ── 第三遍：水边放置水生植被（芦苇/香蒲）──
        placeAquaticVegetation(noise, worldMap);

        // 边界混合已拆分到 blendEdges()（阶段2，由 ChunkManager 统一调度）

        // 所有瓦片数据填充完毕后才标记完成（volatile 保证渲染线程可见性）
        generated = true;

        logger.debug("区块 ({}, {}) 生成完成 — 群落: {}", chunkX, chunkY, biome.getName());
    }

    /**
     * 根据高程、温度、湿度和群落参数分类基底地形。
     *
     * <p>判定优先级：
     * <ol>
     *   <li>低海拔 + 高湿度 → 泥地</li>
     *   <li>低海拔 + 中等湿度 → 泥土</li>
     *   <li>干燥 + 低湿度噪声 → 沙地</li>
     *   <li>高海拔 + 低温 → 泥土（高原冻土，不再产生 STONE 地面）</li>
     *   <li>默认 → 通过 biome 地面覆盖池加权随机解析（pseudo-terrain indirection）</li>
     * </ol>
     *
     * <p>注意：STONE 不再由地形生成（改为 ROCK 覆盖物散布）。
     * <p>群落 waterLevel 会降低低地阈值，使海洋/沼泽群落产生更多泥地，
     * 便于后续 {@link #carveWaterFeatures} 在这些区域放置水域。
     *
     * @param globalX 全局瓦片 X（地面覆盖池解析用）
     * @param globalY 全局瓦片 Y（地面覆盖池解析用）
     */
    private TileType classifyTerrain(double elevation, double temperature,
                                      double humidity, double moisture,
                                      BiomeType biome, double rockThreshold,
                                      float waterLevel,
                                      int globalX, int globalY) {
        // waterLevel 越高，低地阈值越宽松（海洋 waterLevel=1.0 → 阈值 +0.30）
        // 使海洋/沼泽群落更容易产生泥地，便于后续水域雕刻
        double waterBonus = waterLevel * 0.25;

        // 低海拔 + 高湿度 → 泥地（沼泽边缘/海底）
        // 阈值收紧，减少泥地产生
        if (elevation < -0.15 + waterBonus && humidity > 0.8) {
            return TileType.MUD;
        }

        // 低海拔 + 中等湿度 → 泥土地
        // 阈值收紧，减少泥土产生
        if (elevation < -0.10 + waterBonus && humidity > 0.5) {
            return humidity > 0.8 ? TileType.MUD : TileType.DIRT;
        }

        // 干燥环境 → 沙地
        if (humidity < 0.2 && moisture < -0.15) {
            return TileType.SAND;
        }

        // 高海拔 + 低温 → 高原冻土（改为泥土，不再产生 STONE 地面）
        // 提高阈值，减少冻土产生
        if (elevation > 0.40 && temperature < 0) {
            return TileType.DIRT;
        }

        // 默认 → biome 地面覆盖池加权随机解析
        // 使不同 biome 的"普通地面"视觉不同（森林有泥土/泥地斑块，沙漠以沙为主）
        return resolveGroundCover(biome, globalX, globalY);
    }

    /**
     * 通过 biome 地面覆盖池加权随机解析地面类型。
     *
     * <p>设计借鉴 pseudo-terrain indirection 模式：
     * 每个 biome 定义一个地形类型加权池，解析时根据确定性哈希做加权随机选取。
     *
     * <p>使用 {@link #tileHash(int, int)} 保证相同坐标始终返回相同结果（确定性生成）。
     *
     * @param biome   当前 biome
     * @param globalX 全局瓦片 X（哈希输入）
     * @param globalY 全局瓦片 Y（哈希输入）
     * @return 解析后的地面地形类型；池为空时回退到 GRASS
     */
    private TileType resolveGroundCover(BiomeType biome, int globalX, int globalY) {
        List<BiomeType.GroundCoverEntry> pool = biome.getGroundCover();
        if (pool == null || pool.isEmpty()) {
            return TileType.GRASS;
        }

        // 计算总权重
        int totalWeight = 0;
        for (BiomeType.GroundCoverEntry entry : pool) {
            totalWeight += entry.weight;
        }
        if (totalWeight <= 0) {
            return TileType.GRASS;
        }

        // 确定性加权随机（使用偏移哈希，避免与 tileHash(col, row) 产生相关性）
        double roll = tileHash(globalX + 1000, globalY + 2000) * totalWeight;
        int cumulative = 0;
        for (BiomeType.GroundCoverEntry entry : pool) {
            cumulative += entry.weight;
            if (roll < cumulative) {
                return entry.type;
            }
        }
        return pool.get(pool.size() - 1).type;
    }

    // ── 边界混合（阶段2，独立于 generate） ────────────────────

    /** 方向偏移：上、下、左、右 */
    private static final int[] BLEND_DIR_DX = {0, 0, -1, 1};
    private static final int[] BLEND_DIR_DY = {-1, 1, 0, 0};

    /**
     * 区块边界混合（阶段2）。
     *
     * <p>在所有邻居区块均完成 {@link #generate} 后调用，
     * 对四个方向的相邻区块进行边缘瓦片混合，实现跨区块平滑过渡。
     *
     * <p>幂等性：首次调用后标记 {@code blended=true}，后续调用直接返回（不重复混合）。
     * 混合时产生的 TREE/BUSH 瓦片会同步分配植被物种（修复无贴图问题）。
     *
     * @param neighbors 周围 5×5 邻居区块
     * @param worldMap  世界地图（提供环境数据，用于植被物种选择）
     */
    public synchronized void blendEdges(Chunk[][] neighbors, WorldMap worldMap) {
        if (blended) return;
        blendChunkEdges(neighbors, worldMap);
        blended = true;
    }

    /**
     * 区块边界混合（双层级检查）。
     *
     * <p>第一层：检查相邻区块的群落类型（大地图）—— 群落相同则无需混合。
     * <p>第二层：读取相邻区块边缘瓦片数据（小地图）—— 根据邻居实际瓦片类型决定混合。
     *
     * <p>混合宽度 2 格，距离边缘越近受邻居影响越大。
     * 混合策略：
     * <ul>
     *   <li>邻居是 WATER → 本区块边缘概率变为 SAND 过渡</li>
     *   <li>邻居是 SAND → 本区块边缘概率变为 SAND</li>
     *   <li>邻居是 STONE → 只在自身也是石头时保持</li>
     *   <li>邻居是 TREE/BUSH → 如果自身是 GRASS，概率变植被</li>
     *   <li>邻居是 GRASS/MUD/DIRT → 概率混合为邻居类型</li>
     * </ul>
     */
    private void blendChunkEdges(Chunk[][] neighbors, WorldMap worldMap) {
        if (neighbors == null) return;

        // 混合宽度 5 格（原为 2 格），更宽的过渡区使 biome 边界更自然
        int blendWidth = 5;

        // 4 个方向：上(-Y)、下(+Y)、左(-X)、右(+X)
        for (int dir = 0; dir < 4; dir++) {
            int ny = 2 + BLEND_DIR_DY[dir];
            int nx = 2 + BLEND_DIR_DX[dir];
            if (nx < 0 || nx >= 5 || ny < 0 || ny >= 5) continue;

            Chunk neighbor = neighbors[ny][nx];

            // 第一层：检查群落类型（大地图）
            if (neighbor == null || !neighbor.generated || neighbor.getBiome() == this.biome) {
                continue;
            }

            // 第二层：读取邻居边缘瓦片数据（小地图），进行混合
            // 传入邻居的 biome 用于 biome 感知规则，传入 worldMap 用于植被物种选择
            blendWithNeighborTiles(dir, neighbor, blendWidth, worldMap);
        }
    }

    /**
     * 与相邻区块边缘瓦片进行混合。
     *
     * <p>使用指数衰减的混合因子：越靠近边界受邻居影响越大。
     * 公式：{@code blendFactor = pow(distance_ratio, 1.5)}，
     * 产生比线性更自然的过渡（边界处急剧，向内部迅速衰减）。
     *
     * @param dir        方向：0=上, 1=下, 2=左, 3=右
     * @param neighbor   相邻区块
     * @param blendWidth 混合宽度（格数）
     */
    private void blendWithNeighborTiles(int dir, Chunk neighbor, int blendWidth,
                                         WorldMap worldMap) {
        BiomeType neighborBiome = neighbor.getBiome();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 1; j <= blendWidth; j++) {
                // 从邻居区块获取对应边缘瓦片
                TileType neighborTile = neighbor.getTileAtEdge(dir, i);
                if (neighborTile == null) continue;

                // 指数衰减混合因子：边界处急剧，向内部迅速衰减
                // pow(x, 1.5) 使过渡比线性更自然（CDDA 设计模式）
                double linearRatio = (double) (blendWidth + 1 - j) / (blendWidth + 1);
                double blendFactor = Math.pow(linearRatio, 1.5);

                // 确定当前区块中对应边缘的瓦片坐标
                int[] selfPos = getEdgeTilePos(dir, i, j - 1);
                int selfRow = selfPos[0];
                int selfCol = selfPos[1];

                TileType selfTile = tiles[selfRow][selfCol];

                // 根据邻居瓦片类型 + biome 决定混合策略
                TileType blended = blendTile(selfTile, neighborTile, blendFactor, dir, neighborBiome);
                if (blended != null) {
                    tiles[selfRow][selfCol] = blended;

                    // 混合产生的 TREE/BUSH 需要分配植被物种（修复无贴图问题）
                    // 原 placeVegetation 中 TREE 总是附带 vegetationMap 条目，
                    // 但 blend 路径只设置了 tiles 未设置 vegetationMap，导致渲染时无精灵
                    if ((blended == TileType.TREE || blended == TileType.BUSH)
                            && vegetationMap.getVegetation(selfCol, selfRow) == null) {
                        int globalX = chunkX * SIZE + selfCol;
                        int globalY = chunkY * SIZE + selfRow;
                        assignBlendedVegetation(selfCol, selfRow, globalX, globalY,
                                blended, worldMap);
                    }
                }
            }
        }
    }

    /**
     * 获取当前区块在指定方向上、距离边缘 offset 格的瓦片。
     *
     * @param dir    方向
     * @param along  沿边缘的位置 (0 ~ SIZE-1)
     * @param offset 距离边缘的格数（0 = 边缘本身）
     * @return [row, col]
     */
    private int[] getEdgeTilePos(int dir, int along, int offset) {
        switch (dir) {
            case 0: return new int[]{offset, along};           // 上边缘
            case 1: return new int[]{SIZE - 1 - offset, along}; // 下边缘
            case 2: return new int[]{along, offset};            // 左边缘
            case 3: return new int[]{along, SIZE - 1 - offset}; // 右边缘
            default: return new int[]{0, 0};
        }
    }

    /**
     * 根据邻居瓦片类型、混合因子和邻居 biome 决定当前瓦片的混合结果。
     *
     * <p>混合策略分两层：
     * <ol>
     *   <li><b>tile-based 规则</b>：根据邻居瓦片类型（WATER→SAND，SAND→扩展等）</li>
     *   <li><b>biome-aware 规则</b>：根据邻居 biome 类型增强/补充转换
     *       （FOREST→TREE 蔓延，DESERT→SAND 蔓延，MOUNTAIN→ROCK 蔓延）</li>
     * </ol>
     *
     * @param selfTile      当前瓦片
     * @param neighborTile  邻居瓦片
     * @param blendFactor   混合因子 (0~1，越大越倾向于采纳邻居类型)
     * @param dir           方向
     * @param neighborBiome 邻居生物群落（biome-aware 规则用）
     * @return 混合后的瓦片类型（null 表示不改变）
     */
    private TileType blendTile(TileType selfTile, TileType neighborTile,
                                double blendFactor, int dir, BiomeType neighborBiome) {
        // 使用确定性哈希决定是否采纳混合
        int globalX = chunkX * SIZE;
        int globalY = chunkY * SIZE;

        // 根据混合方向计算实际瓦片的世界坐标
        int[] pos = getEdgeTilePos(dir, 0, 0); // 简化哈希，用边缘行/列
        long hash = (long) (globalX + pos[1]) * 374761393L
                  + (long) (globalY + pos[0]) * 668265263L;
        hash = (hash ^ (hash >> 13)) * 1274126177L;
        hash = hash ^ (hash >> 16);
        double randomVal = (hash & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL;

        // 邻居是水 → 本区块边缘有概率变成沙地过渡
        if (neighborTile == TileType.WATER) {
            if (randomVal < blendFactor * 0.6 && selfTile == TileType.GRASS) {
                return TileType.SAND;
            }
            return null;
        }

        // 邻居是沙滩 → 本区块边缘有概率变成沙滩
        if (neighborTile == TileType.SAND) {
            if (randomVal < blendFactor * 0.5 && (selfTile == TileType.GRASS || selfTile == TileType.DIRT)) {
                return TileType.SAND;
            }
            return null;
        }

        // 邻居是岩石覆盖物 → 如果自身是草地/沙地，概率变为岩石
        if (neighborTile == TileType.ROCK
                && (selfTile == TileType.GRASS || selfTile == TileType.SAND)) {
            if (randomVal < blendFactor * 0.3) {
                return TileType.ROCK;
            }
            return null;
        }

        // 邻居是树木/灌木 → 如果自身是草地，概率变为植被
        if ((neighborTile == TileType.TREE || neighborTile == TileType.BUSH)
                && selfTile == TileType.GRASS) {
            if (randomVal < blendFactor * 0.4) {
                return neighborTile;
            }
            return null;
        }

        // 邻居是芦苇 → 本区块边缘如果是草地，概率变芦苇
        if (neighborTile == TileType.REEDS && selfTile == TileType.GRASS) {
            if (randomVal < blendFactor * 0.2) {
                return TileType.SAND; // 水边过渡先变沙地
            }
            return null;
        }

        // 邻居是草地/泥地/泥土 → 概率混合为邻居类型
        if (neighborTile == TileType.MUD || neighborTile == TileType.DIRT) {
            if (selfTile == TileType.GRASS && randomVal < blendFactor * 0.4) {
                return neighborTile;
            }
            return null;
        }

        // ── biome-aware 规则（补充 tile-based 规则的不足） ──

        // 邻居 biome 是森林 + 自身是 GRASS → 森林边缘树木蔓延
        // 即使邻居边缘瓦片不是 TREE（可能是 GRASS），森林 biome 也会使我们的边缘长出树
        if (neighborBiome != null && neighborBiome.isWooded() && selfTile == TileType.GRASS) {
            double treeEncroach = neighborBiome.getTreeDensity() * blendFactor * 0.3;
            if (randomVal < treeEncroach) {
                return TileType.TREE;
            }
        }

        // 邻居 biome 是沙漠 + 自身是 GRASS → 沙地蔓延
        if (neighborBiome == BiomeType.DESERT && selfTile == TileType.GRASS) {
            if (randomVal < blendFactor * 0.25) {
                return TileType.SAND;
            }
        }

        // 邻居 biome 是山地 + 自身是 GRASS → 岩石/泥土蔓延
        if (neighborBiome == BiomeType.MOUNTAIN && selfTile == TileType.GRASS) {
            if (randomVal < blendFactor * 0.15) {
                return randomVal < blendFactor * 0.08 ? TileType.ROCK : TileType.DIRT;
            }
        }

        return null;
    }

    /**
     * 为边界混合产生的 TREE/BUSH 瓦片分配植被物种。
     *
     * <p>边界混合（{@link #blendTile}）只修改 {@code tiles[][]}，不同步设置 {@code vegetationMap}，
     * 导致渲染时 {@code getVegetationIfReady()} 返回 null → 无精灵贴图。
     * 本方法根据环境查询适生物种，补全植被数据。
     *
     * @param localCol      局部列号
     * @param localRow      局部行号
     * @param globalX       全局瓦片 X（环境查询 + 确定性哈希）
     * @param globalY       全局瓦片 Y
     * @param tileType      混合后的瓦片类型（TREE 或 BUSH）
     * @param worldMap      世界地图（提供温度/湿度/土壤深度）
     */
    private void assignBlendedVegetation(int localCol, int localRow,
                                          int globalX, int globalY,
                                          TileType tileType,
                                          WorldMap worldMap) {
        VegetationType vegType = getBlendVegetationType(tileType);
        if (vegType == null) return;

        // 环境查询（与 placeVegetation 一致，使用 WorldMap 的实际环境值）
        double temperature = worldMap.getTemperatureAt(globalX, globalY);
        double humidity = worldMap.getHumidityAt(globalX, globalY);
        double soilDepth = worldMap.getSoilDepthAt(globalX, globalY);

        // 使用确定性哈希作为随机种子（与 tileHash 保持一致的分布）
        double hash = tileHash(globalX + 31415, globalY + 27183);
        VegetationDefinition def = VegetationRegistry.selectForEnvironment(
                temperature, humidity, soilDepth,
                vegType,
                new Random((long)(hash * Long.MAX_VALUE)));
        if (def != null) {
            vegetationMap.setVegetation(localCol, localRow, def.id);
        }
    }

    /**
     * 根据混合产生的瓦片类型和邻居 biome 推断植被类型。
     *
     * <p>映射规则：TREE → TREE，BUSH → SHRUB，其他 → null。
     *
     * @return 植被类型，无法推断时返回 null
     */
    private VegetationType getBlendVegetationType(TileType tileType) {
        if (tileType == TileType.TREE) return VegetationType.TREE;
        if (tileType == TileType.BUSH) return VegetationType.SHRUB;
        return null;
    }

    /**
     * 获取当前区块在指定方向上的边缘瓦片（供邻居区块混合调用）。
     *
     * @param dir 方向：0=上, 1=下, 2=左, 3=右
     * @param i   沿边缘的位置 (0 ~ SIZE-1)
     * @return 边缘瓦片类型
     */
    private TileType getTileAtEdge(int dir, int i) {
        if (i < 0 || i >= SIZE || tiles == null) return null;
        switch (dir) {
            case 0: return tiles[0][i];              // 上边缘
            case 1: return tiles[SIZE - 1][i];       // 下边缘
            case 2: return tiles[i][0];               // 左边缘
            case 3: return tiles[i][SIZE - 1];        // 右边缘
            default: return null;
        }
    }

    /**
     * 在基底地形上放置植被（环境适配 + 双噪声层聚类）。
     *
     * <p>使用四层机制：
     * <ol>
     *   <li><b>环境查询</b> — 获取每瓦片的温度、湿度、土壤深度</li>
     *   <li><b>冠层噪声</b>（低频 fbm）— 大尺度定义"哪里有植被区域"</li>
     *   <li><b>间隙噪声</b>（高频 fbm）— 小尺度定义"哪里有林间空地"，
     *       通过噪声减法（{@code max(0, canopy² - clearing³×0.5)}）产生聚簇效果</li>
     *   <li><b>物种选择</b> — 根据环境从 VegetationRegistry 选择适生物种</li>
     * </ol>
     *
     * <p>植被类型映射到 TileType：
     * <ul>
     *   <li>TREE → TileType.TREE</li>
     *   <li>SHRUB → TileType.BUSH</li>
     *   <li>GRASS → TileType.TALL_GRASS（部分 FLOWER）</li>
     *   <li>MOSS → TileType.TALL_GRASS（潮湿区域）</li>
     * </ul>
     *
     * @param noise    Perlin 噪声生成器
     * @param worldMap 世界地图（提供环境数据）
     */
    private void placeVegetation(PerlinNoise noise, WorldMap worldMap) {
        float treeD = biome.getTreeDensity();
        float grassD = biome.getGrassDensity();

        // 方向性植被密度梯度修正（CDDA forest_increase 模式）
        // 北方森林更密，东方轻微增密
        double densityModifier = worldMap.getForestDensityModifier(chunkX, chunkY);
        treeD = (float) Math.max(0.0, treeD + densityModifier);
        grassD = (float) Math.max(0.0, grassD + densityModifier * 0.5); // 草密度受影响较小

        // 使用区块坐标作为随机种子（确定性生成）
        Random vegRandom = new Random(chunkX * 374761393L + chunkY * 668265263L);

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                TileType base = tiles[row][col];
                if (base != TileType.GRASS) continue;

                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                // 确定性哈希（每瓦片独立伪随机，0~1）
                double hash = tileHash(globalX, globalY);

                // ── 双噪声层聚类（noise subtraction 模式） ──
                // 冠层噪声：大尺度 fbm，定义"哪里有植被区域"
                double canopy = (noise.fbm(
                        globalX * CANOPY_FREQ + 2000.0,
                        globalY * CANOPY_FREQ + 2000.0,
                        3, PERSISTENCE, LACUNARITY) + 1.0) * 0.5;
                canopy = canopy * canopy; // 锐化峰值（pow(2)）

                // 间隙噪声：小尺度 fbm，定义"哪里有林间空地"
                double clearing = (noise.fbm(
                        globalX * CLEARING_FREQ + 5000.0,
                        globalY * CLEARING_FREQ + 5000.0,
                        2, PERSISTENCE, LACUNARITY) + 1.0) * 0.5;
                clearing = clearing * clearing * clearing; // 锐化（pow(3)）

                // 减法 → 聚簇效果：冠层定义森林区域，间隙噪声在其中挖出空地
                double densityFactor = Math.max(0.0, canopy - clearing * 0.5);

                // ── 查询环境参数 ──
                double temperature = worldMap.getTemperatureAt(globalX, globalY);
                double humidity = worldMap.getHumidityAt(globalX, globalY);
                double soilDepth = worldMap.getSoilDepthAt(globalX, globalY);

                // ── 树木 ──
                double treeProb = treeD * (0.3 + 0.7 * densityFactor);
                if (hash < treeProb) {
                    // 从注册表选择适生树种
                    VegetationDefinition treeDef = VegetationRegistry.selectForEnvironment(
                            temperature, humidity, soilDepth, VegetationType.TREE, vegRandom);
                    if (treeDef != null) {
                        tiles[row][col] = TileType.TREE;
                        vegetationMap.setVegetation(col, row, treeDef.id);
                    } else {
                        // 无适生树种，尝试灌木
                        VegetationDefinition shrubDef = VegetationRegistry.selectForEnvironment(
                                temperature, humidity, soilDepth, VegetationType.SHRUB, vegRandom);
                        if (shrubDef != null) {
                            tiles[row][col] = TileType.BUSH;
                            vegetationMap.setVegetation(col, row, shrubDef.id);
                        }
                    }
                    continue;
                }

                // ── 灌木 ──
                double shrubProb = treeD * 0.25 * (0.3 + 0.7 * densityFactor);
                double subHash = tileHash(globalX + 7919, globalY + 104729);
                if (subHash < shrubProb) {
                    VegetationDefinition shrubDef = VegetationRegistry.selectForEnvironment(
                            temperature, humidity, soilDepth, VegetationType.SHRUB, vegRandom);
                    if (shrubDef != null) {
                        tiles[row][col] = TileType.BUSH;
                        vegetationMap.setVegetation(col, row, shrubDef.id);
                        continue;
                    }
                }

                // ── 草 / 花 / 苔藓 ──
                // 降低高草生成概率（乘法因子从 0.7 降到 0.35）
                double grassProb = grassD * (0.3 + 0.35 * densityFactor);
                if (hash < treeProb + grassProb) {
                    double flowerHash = tileHash(globalX + 131, globalY + 523);

                    // 高湿度 + 浅土壤 → 尝试苔藓
                    if (humidity > 0.6 && soilDepth < 0.4 && flowerHash < 0.2) {
                        VegetationDefinition mossDef = VegetationRegistry.selectForEnvironment(
                                temperature, humidity, soilDepth, VegetationType.MOSS, vegRandom);
                        if (mossDef != null) {
                            tiles[row][col] = TileType.TALL_GRASS;
                            vegetationMap.setVegetation(col, row, mossDef.id);
                            continue;
                        }
                    }

                    // 普通草地：花 vs 高草
                    if (flowerHash < 0.12) {
                        tiles[row][col] = TileType.FLOWER;
                    } else {
                        tiles[row][col] = TileType.TALL_GRASS;
                        // 选择草种
                        VegetationDefinition grassDef = VegetationRegistry.selectForEnvironment(
                                temperature, humidity, soilDepth, VegetationType.GRASS, vegRandom);
                        if (grassDef != null) {
                            vegetationMap.setVegetation(col, row, grassDef.id);
                        }
                    }
                    continue;
                }

                // 其余保持 GRASS
            }
        }
    }

    /**
     * 第二遍半：在沙地和草地上散布岩石覆盖物。
     *
     * <p>利用群落 rockiness 参数控制岩石密度：
     * <ul>
     *   <li>MOUNTAIN (rockiness=0.50) → 岩石最密集</li>
     *   <li>DESERT (rockiness=0.05) → 少量岩石</li>
     *   <li>其他 (rockiness=0.00) → 无岩石</li>
     * </ul>
     *
     * <p>岩石作为覆盖物（ROCK overlay）放置，不改变底层地面类型。
     * 使用区域噪声使岩石分布呈簇状（非均匀随机）。
     */
    private void placeRocks(PerlinNoise noise) {
        float rockiness = biome.getRockiness();
        if (rockiness <= 0) return;

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                TileType ground = groundTiles[row][col];
                // 仅在沙地和草地上放置岩石
                if (ground != TileType.SAND && ground != TileType.GRASS) continue;
                // 跳过已有覆盖物（植被等）的瓦片
                if (tiles[row][col] != ground) continue;

                int gx = chunkX * SIZE + col;
                int gy = chunkY * SIZE + row;

                // 确定性哈希
                double hash = tileHash(gx + 99991, gy + 77777);

                // 区域噪声：使岩石呈簇状分布
                double zoneNoise = noise.fbm(
                        gx * 0.05 + 5000.0, gy * 0.05 + 5000.0,
                        2, PERSISTENCE, LACUNARITY);
                double factor = (zoneNoise + 1.0) * 0.5; // 归一化到 0~1

                // 岩石概率 = rockiness * 基础系数 * 噪声因子
                double rockProb = rockiness * 0.15 * (0.3 + 0.7 * factor);

                if (hash < rockProb) {
                    tiles[row][col] = TileType.ROCK;
                }
            }
        }
    }

    /**
     * 第三遍：放置水域（湖泊/河流/海洋）及过渡带。
     *
     * <p>使用 WorldMap 水域特征 + 高频扰动：
     * <ul>
     *   <li><b>深水区</b>：梯度 ≥ 0.6 → WATER</li>
     *   <li><b>浅水/沙滩过渡</b>：梯度 0.3~0.6 → SAND（海滩）</li>
     *   <li><b>陆地</b>：梯度 &lt; 0.3 → 保留原地形（草地/森林等）</li>
     * </ul>
     *
     * <p>水边高频噪声扰动使边缘曲折自然，不是平滑直线。
     */
    private void carveWaterFeatures(WorldMap worldMap) {
        int waterCount = 0, sandCount = 0, grassCount = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                TileType base = tiles[row][col];
                // 允许在草地/沙地/泥地/泥土上放置水域（不覆盖岩石、植被）
                // MUD 是海洋/沼泽群落的主要地面类型，必须允许水域覆盖
                if (base != TileType.GRASS && base != TileType.SAND
                        && base != TileType.MUD && base != TileType.DIRT) {
                    continue;
                }
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                double waterGradient = worldMap.getWaterFeature(globalX, globalY);

                // 水边高频扰动（让边界曲折，不是平滑直线）
                // 仅在过渡区（0.15 ~ 0.6）施加扰动，深水（≥0.6）和纯陆地不变
                // 注意：扰动后的值不能低于 0.6 当原始值 ≥ 0.6 时（防止深水变沙滩）
                if (waterGradient > 0.15 && waterGradient < 0.6) {
                    double edgeJitter = tileHash(globalX * 7 + 31, globalY * 11 + 17) * 0.2 - 0.1;
                    // 四邻接水体检查：邻居有水 → 提高当前瓦片水位倾向
                    if (hasWaterNeighbor(row, col)) {
                        edgeJitter += 0.08; // 邻水瓦片更易成水，形成曲折边缘
                    }
                    waterGradient += edgeJitter;
                }

                // 根据梯度值决定地形
                if (waterGradient >= 0.6) {
                    tiles[row][col] = TileType.WATER;
                    waterCount++;
                } else if (waterGradient >= 0.3) {
                    tiles[row][col] = TileType.SAND;
                    sandCount++;
                } else {
                    grassCount++;
                }
            }
        }
        if (waterCount > 0 || sandCount > 0) {
            logger.debug("Chunk ({},{}) {}: WATER={} SAND={} GRASS={}",
                    chunkX, chunkY, biome.getName(), waterCount, sandCount, grassCount);
        }
    }

    /**
     * 检查指定瓦片四邻接是否有水域。
     */
    private boolean hasWaterNeighbor(int row, int col) {
        final int[] dx = {0, 0, -1, 1};
        final int[] dy = {-1, 1, 0, 0};
        for (int d = 0; d < 4; d++) {
            int nr = row + dy[d];
            int nc = col + dx[d];
            if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE) {
                TileType t = tiles[nr][nc];
                if (t == TileType.WATER || t == TileType.SAND) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 在水域边缘放置水生植被（芦苇/香蒲）。
     *
     * <p>规则：
     * <ul>
     *   <li>WATER 瓦片且四邻接有非 WATER → 仅在湖泊区域有概率放置（湖边芦苇）</li>
     *   <li>SAND 瓦片且四邻接有 WATER → 仅在湖泊区域有概率放置（湿地香蒲）</li>
     * </ul>
     *
     * <p>利用 WorldMap.getWaterFeature() 区分湖泊（>1.0）和其他水域。
     * 非湖泊水域（河流、海洋）不生成芦苇，使芦苇仅出现在湖边浅水区。
     *
     * <p>使用确定性哈希控制概率，相同坐标总是产生相同结果。
     */
    private void placeAquaticVegetation(PerlinNoise noise, WorldMap worldMap) {
        // 四邻接偏移
        final int[] dx = {0, 0, -1, 1};
        final int[] dy = {-1, 1, 0, 0};

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                TileType tile = tiles[row][col];
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                boolean isWaterEdge = false;  // WATER 邻接陆地
                boolean isSandEdge = false;   // SAND 邻接水

                if (tile == TileType.WATER) {
                    // 检查是否有非 WATER 邻接（陆地边缘）
                    for (int d = 0; d < 4; d++) {
                        int nr = row + dy[d];
                        int nc = col + dx[d];
                        if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE
                                && tiles[nr][nc] != TileType.WATER
                                && tiles[nr][nc] != TileType.ROCK) {
                            isWaterEdge = true;
                            break;
                        }
                    }
                } else if (tile == TileType.SAND) {
                    // 检查是否有 WATER 邻接（水边沙滩）
                    for (int d = 0; d < 4; d++) {
                        int nr = row + dy[d];
                        int nc = col + dx[d];
                        if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE
                                && tiles[nr][nc] == TileType.WATER) {
                            isSandEdge = true;
                            break;
                        }
                    }
                }

                if (!isWaterEdge && !isSandEdge) continue;

                // 湖泊检测：仅在湖泊区域（waterFeature > 1.0）生成芦苇
                double waterFeature = worldMap.getWaterFeature(globalX, globalY);
                boolean isLake = waterFeature > 1.0;

                if (!isLake) {
                    // 非湖泊水域：大幅降低概率（仅极少数芦苇出现在河口等区域）
                    // 概率从 0.30/0.15 降至 0.03/0.015
                }

                // 环境查询
                double temperature = worldMap.getTemperatureAt(globalX, globalY);
                double humidity = worldMap.getHumidityAt(globalX, globalY);
                double soilDepth = worldMap.getSoilDepthAt(globalX, globalY);

                // 概率判定（使用偏移哈希区分 WATER/SAND 两种情况）
                int hashOffset = isWaterEdge ? 0 : 50000;
                double hash = tileHash(globalX + hashOffset, globalY + hashOffset);
                // 湖泊边缘高概率，非湖泊极低概率
                double baseThreshold = isWaterEdge ? 0.30 : 0.15;
                double threshold = isLake ? baseThreshold : baseThreshold * 0.1;

                if (hash < threshold) {
                    VegetationDefinition aquDef = VegetationRegistry.selectForEnvironment(
                            temperature, humidity, soilDepth, VegetationType.AQUATIC,
                            new Random(globalX * 7391L + globalY * 27413L));
                    if (aquDef != null) {
                        tiles[row][col] = TileType.REEDS;
                        vegetationMap.setVegetation(col, row, aquDef.id);
                    }
                }
            }
        }
    }

    /**
     * 确定性瓦片哈希函数。
     * 给定世界坐标，返回 0~1 的伪随机值。
     * 相同坐标总是返回相同值（跨区块一致）。
     *
     * <p>使用大素数混合，分布均匀，避免 Perlin 噪声的平滑特性。
     */
    private static double tileHash(int x, int y) {
        long h = (long) x * 374761393L + (long) y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL;
    }

    // ── 查询 ────────────────────────────

    /**
     * 获取区块内局部坐标的地形类型。
     *
     * @param localCol 局部列号 [0, SIZE)
     * @param localRow 局部行号 [0, SIZE)
     * @return 地形类型；越界返回 null
     */
    public TileType getTile(int localCol, int localRow) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return null;
        }
        if (tiles == null) return null;
        return tiles[localRow][localCol];
    }

    /**
     * 获取区块内局部坐标处的地面层瓦片。
     * 地面层是植被放置前的基底地形（草地、泥土、沙地等），
     * 用于分层渲染：植被精灵渲染在地面之上。
     *
     * @param localCol 局部列号 [0, SIZE)
     * @param localRow 局部行号 [0, SIZE)
     * @return 地面层地形类型；越界返回 null
     */
    public TileType getGroundTile(int localCol, int localRow) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return null;
        }
        if (groundTiles == null) return null;
        return groundTiles[localRow][localCol];
    }

    /**
     * 设置局部瓦片地形类型。
     *
     * @param localCol 局部列坐标（0 ~ SIZE-1）
     * @param localRow 局部行坐标（0 ~ SIZE-1）
     * @param type     新的地形类型
     */
    public void setTile(int localCol, int localRow, TileType type) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return;
        }
        tiles[localRow][localCol] = type;
    }

    public int getChunkX() { return chunkX; }
    public int getChunkY() { return chunkY; }
    public BiomeType getBiome() { return biome; }

    /** 区块地形是否已生成 */
    public boolean isGenerated() { return generated; }

    /**
     * 获取植被地图。
     *
     * @return 植被地图（generate() 调用后可用）
     */
    public VegetationMap getVegetationMap() {
        return vegetationMap;
    }

    /**
     * 获取土壤肥力地图。
     *
     * @return 土壤肥力地图（generate() 调用后可用）
     */
    public SoilFertility getSoilFertility() {
        return soilFertility;
    }

    /**
     * 获取局部坐标处的植被物种 ID。
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     * @return 物种 ID，无植被返回 null
     */
    public String getVegetation(int localCol, int localRow) {
        if (vegetationMap == null) return null;
        return vegetationMap.getVegetation(localCol, localRow);
    }

    /**
     * 清除局部坐标处的植被（砍伐后调用）。
     * 同时清除物种 ID 和生长状态。
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     */
    public void clearVegetation(int localCol, int localRow) {
        if (vegetationMap != null) {
            vegetationMap.clear(localCol, localRow);
        }
    }

    /**
     * 设置局部坐标处的植被物种 ID。
     */
    public void setVegetation(int localCol, int localRow, String speciesId) {
        if (vegetationMap != null) {
            vegetationMap.setVegetation(localCol, localRow, speciesId);
        }
    }

    /**
     * 设置局部坐标处的生长状态。
     */
    public void setGrowthState(int localCol, int localRow, VegetationState state) {
        if (vegetationMap != null) {
            vegetationMap.setGrowthState(localCol, localRow, state);
        }
    }

    /**
     * 获取局部坐标处的生长状态。
     */
    public VegetationState getGrowthState(int localCol, int localRow) {
        if (vegetationMap == null) return null;
        return vegetationMap.getGrowthState(localCol, localRow);
    }

    // ── 存档加载 ────────────────────────────

    /**
     * 从存档数据恢复区块地形和植被。
     * 跳过正常的噪声生成流程，直接使用存档中的瓦片数据。
     *
     * @param tiles      地形名称数组（行优先，size*size）
     * @param vegetation 植被物种 ID 数组（行优先，size*size，null 表示无植被）
     */
    public void loadFromSave(String[] tiles, String[] vegetation) {
        this.tiles = new TileType[SIZE][SIZE];
        this.groundTiles = new TileType[SIZE][SIZE];
        if (this.vegetationMap == null) {
            this.vegetationMap = new VegetationMap(chunkX, chunkY);
        }

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int index = row * SIZE + col;

                // 恢复地形
                String tileName = (index < tiles.length && tiles[index] != null)
                        ? tiles[index] : "grass";
                TileType type = TileType.getByName(tileName);
                this.tiles[row][col] = type != null ? type : TileType.GRASS;

                // 地面层与地形层相同（存档中的地形已包含水域雕刻结果）
                this.groundTiles[row][col] = this.tiles[row][col];

                // 恢复植被
                if (vegetation != null && index < vegetation.length) {
                    String vegId = vegetation[index]; // null 表示无植被
                    if (vegId != null && !vegId.isEmpty()) {
                        vegetationMap.setVegetation(col, row, vegId);
                    }
                }
            }
        }

        // 标记为已生成
        generated = true;
    }
}
