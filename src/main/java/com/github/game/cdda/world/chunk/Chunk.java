package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.noise.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单个区块（chunk）。持有 64×64 瓦片数据。
 *
 * <p>地形生成由世界地图（WorldMap）提供的 {@link BiomeType} 驱动：
 * <ul>
 *   <li><b>大地图</b>（WorldMap）决定每个区块的"角色"——平原、森林、海洋等</li>
 *   <li><b>小地图</b>（Chunk）根据群落参数，用局部噪声生成具体地形细节</li>
 * </ul>
 *
 * <h3>两遍生成（ biome 驱动）：</h3>
 * <ol>
 *   <li><b>高程 → 基底地形</b>
 *       局部噪声生成地形起伏，阈值由群落的 {@code waterLevel} 和 {@code rockiness} 偏移。</li>
 *   <li><b>噪声 → 植被放置</b>
 *       植被密度噪声 + 群落参数（{@code treeDensity}, {@code grassDensity}）
 *       控制树木/草/花的密度和分布，形成聚簇效果。</li>
 * </ol>
 *
 * <h3>扩展点：</h3>
 * <ul>
 *   <li>新生物群落：注册 BiomeType 即可，无需修改 Chunk</li>
 *   <li>地形修饰器：后续可添加 {@code BiomeTerrainModifier} 接口，支持特殊地形规则</li>
 *   <li>群落过渡：区块边界可混合相邻群落参数（待实现）</li>
 * </ul>
 */
public class Chunk {

    private static final Logger logger = LoggerFactory.getLogger(Chunk.class);

    /** 区块边长（瓦片数） */
    public static final int SIZE = 64;

    // ── 噪声参数（局部地形细节） ──────────────────

    /** 局部地形噪声频率（特征跨度 ~43 格，细节丰富但不过碎） */
    private static final double TERRAIN_FREQ = 0.023;
    /** 植被密度噪声频率（特征跨度 ~29 格，形成自然聚簇） */
    private static final double VEG_FREQ = 0.035;
    /** 地形 fBm 参数 */
    private static final int TERRAIN_OCTAVES = 4;
    /** 植被 fBm 参数（少一层，够用） */
    private static final int VEG_OCTAVES = 3;
    private static final double PERSISTENCE = 0.5;
    private static final double LACUNARITY = 2.0;

    // ── 基础高程阈值（会被 biome 参数偏移） ──────────────
    /** 基础水域阈值 */
    private static final double BASE_WATER_LEVEL = -0.15;
    /** 基础沙滩阈值 */
    private static final double BASE_BEACH_LEVEL = -0.05;
    /** 基础岩石阈值 */
    private static final double BASE_ROCK_LEVEL = 0.50;

    /** 区块坐标 */
    private final int chunkX;
    private final int chunkY;

    /** 此区块的生物群落 */
    private final BiomeType biome;

    /** 瓦片数据 [row][col] */
    private final TileType[][] tiles;

    /**
     * 创建区块并使用生物群落参数生成地形。
     *
     * @param chunkX 区块 X 坐标（以区块为单位）
     * @param chunkY 区块 Y 坐标（以区块为单位）
     * @param noise  世界 Perlin 噪声生成器
     * @param biome  此区块的生物群落（由 WorldMap 决定）
     */
    public Chunk(int chunkX, int chunkY, PerlinNoise noise, BiomeType biome) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.biome = biome;
        this.tiles = new TileType[SIZE][SIZE];
        generate(noise);
    }

    // ── 地形生成 ────────────────────────────

    /**
     * 根据生物群落参数生成区块地形。
     *
     * <p>第一遍：局部高程噪声 → 基底地形。
     * 群落的 {@code waterLevel} 提高水域阈值，{@code rockiness} 降低岩石阈值。
     *
     * <p>第二遍：植被密度噪声 + 群落参数 → 地表物体。
     * 群落的 {@code treeDensity} 和 {@code grassDensity} 控制植被放置阈值。
     */
    private void generate(PerlinNoise noise) {
        // 根据群落计算实际阈值
        // waterLevel 越高 → 水域越多（阈值上移）
        double waterThreshold = BASE_WATER_LEVEL + biome.getWaterLevel() * 0.35;
        double beachThreshold = BASE_BEACH_LEVEL + biome.getWaterLevel() * 0.10;
        // rockiness 越高 → 石头越多（阈值下移）
        double rockThreshold = BASE_ROCK_LEVEL - biome.getRockiness() * 0.30;

        // ── 第一遍：高程 → 基底地形 ──
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                double elevation = noise.fbm(
                        globalX * TERRAIN_FREQ,
                        globalY * TERRAIN_FREQ,
                        TERRAIN_OCTAVES, PERSISTENCE, LACUNARITY
                );

                tiles[row][col] = classifyTerrain(elevation,
                        waterThreshold, beachThreshold, rockThreshold);
            }
        }

        // ── 第二遍：噪声 + 群落参数 → 植被 ──
        placeVegetation(noise);

        logger.debug("区块 ({}, {}) 生成完成 — 群落: {}", chunkX, chunkY, biome.getName());
    }

    /**
     * 根据高程和阈值分类基底地形。
     */
    private TileType classifyTerrain(double elevation,
                                      double waterThreshold,
                                      double beachThreshold,
                                      double rockThreshold) {
        if (elevation < waterThreshold) {
            return TileType.WATER;
        } else if (elevation < beachThreshold) {
            return TileType.SAND;
        } else if (elevation > rockThreshold) {
            return TileType.STONE;
        } else {
            return TileType.GRASS;
        }
    }

    /**
     * 在基底地形上放置植被。
     *
     * <p>使用双层机制避免"大片色块"：
     * <ol>
     *   <li><b>噪声区域层</b>（中频 fbm）— 决定哪些区域"可能"有植被，
     *       形成松散的聚集趋势（不是硬边界）</li>
     *   <li><b>哈希散布层</b>（确定性 hash）— 在"可能有"的区域内，
     *       逐瓦片打散，避免连续平滑色块</li>
     * </ol>
     *
     * <p>效果：树木/花草零零星星，有些地方有几棵聚一起，有些地方完全没有，
     *   但整体趋势跟随生物群落密度。
     */
    private void placeVegetation(PerlinNoise noise) {
        float treeD = biome.getTreeDensity();
        float grassD = biome.getGrassDensity();

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                TileType base = tiles[row][col];
                if (base != TileType.GRASS) continue;

                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                // 确定性哈希（每瓦片独立伪随机，0~1）
                double hash = tileHash(globalX, globalY);

                // ── 噪声区域层：中频 fbm（-1 ~ 1） ──
                double zone = noise.fbm(
                        globalX * VEG_FREQ + 2000.0,
                        globalY * VEG_FREQ + 2000.0,
                        VEG_OCTAVES, PERSISTENCE, LACUNARITY
                );
                // 归一化到 0~1
                double zoneFactor = (zone + 1.0) * 0.5;

                // ── 树木 ──
                // 基础概率 = 群落密度，区域系数 0.3~1.0 调制
                double treeProb = treeD * (0.3 + 0.7 * zoneFactor);
                if (hash < treeProb) {
                    // 在树之间散布少量灌木
                    double subHash = tileHash(globalX + 7919, globalY + 104729);
                    tiles[row][col] = (subHash < 0.25) ? TileType.BUSH : TileType.TREE;
                    continue;
                }

                // ── 高草 / 花 ──
                double grassProb = grassD * (0.3 + 0.7 * zoneFactor);
                if (hash < treeProb + grassProb) {
                    // 花比高草更稀疏
                    double flowerHash = tileHash(globalX + 131, globalY + 523);
                    tiles[row][col] = (flowerHash < 0.35) ? TileType.FLOWER : TileType.TALL_GRASS;
                    continue;
                }

                // 其余保持 GRASS
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
        return tiles[localRow][localCol];
    }

    public int getChunkX() { return chunkX; }
    public int getChunkY() { return chunkY; }
    public BiomeType getBiome() { return biome; }
}
