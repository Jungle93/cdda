package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.drainage.DrainageMap;
import com.github.game.engine.core.noise.PerlinNoise;
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
    public static final int SIZE = 32;

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
    /** waterLevel=0 时，将水域/沙滩阈值额外下移的量（使内陆几乎不生成水域） */
    private static final double DRY_THRESHOLD_OFFSET = -0.40;

    /** 区块坐标 */
    private final int chunkX;
    private final int chunkY;

    /** 此区块的生物群落 */
    private final BiomeType biome;

    /** 瓦片数据 [row][col]（generate() 调用后初始化） */
    private TileType[][] tiles;

    /** 排水图（generate() 时使用，可为 null 表示使用 WorldMap 水域特征） */
    private DrainageMap drainageMap;

    /** 是否已生成 */
    private boolean generated = false;

    /**
     * 创建区块（不立即生成，等待排水计算完成后调用 generate()）。
     *
     * @param chunkX 区块 X 坐标（以区块为单位）
     * @param chunkY 区块 Y 坐标（以区块为单位）
     * @param noise  世界 Perlin 噪声生成器（地形 + 植被）
     * @param biome  此区块的生物群落（由 WorldMap 决定）
     */
    public Chunk(int chunkX, int chunkY, PerlinNoise noise, BiomeType biome) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.biome = biome;
        this.tiles = null;
    }

    // ── 地形生成 ────────────────────────────

    /**
     * 根据生物群落参数生成区块地形。
     *
     * <p>三遍生成：
     * <ol>
     *   <li><b>高程 → 基底地形</b>
     *       局部噪声生成地形起伏，阈值由群落的 {@code waterLevel} 和 {@code rockiness} 偏移。</li>
     *   <li><b>噪声 → 植被放置</b>
     *       植被密度噪声 + 群落参数（{@code treeDensity}, {@code grassDensity}）
     *       控制树木/草/花的密度和分布，形成聚簇效果。</li>
     *   <li><b>大地图水域特征 → 湖泊/河流</b>
     *       查询 {@link WorldMap#getWaterFeature(int, int)} 决定湖泊和河流位置，
     *       水域出现在低洼湿润区域，跨区块连续自然。</li>
     * </ol>
     */
    public void generate(PerlinNoise noise, WorldMap worldMap, DrainageMap drainageMap) {
        if (generated) return;
        this.drainageMap = drainageMap;
        this.tiles = new TileType[SIZE][SIZE];
        generated = true;

        // 群落基数参数（rockiness 全区块统一）
        float wl = biome.getWaterLevel();
        double rockThreshold = BASE_ROCK_LEVEL - biome.getRockiness() * 0.30;

        // ── 特殊处理：海洋群落 → 全区块水域 ──
        if (biome == BiomeType.OCEAN) {
            this.tiles = new TileType[SIZE][SIZE];
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    tiles[row][col] = TileType.WATER;
                }
            }
            placeVegetation(noise);
            logger.debug("区块 ({}, {}) 生成完成 — 群落: {}", chunkX, chunkY, biome.getName());
            return;
        }

        // ── 第一遍：高程 + WorldMap 湿度采样 → 基底地形（不生成水域） ──
        // 每瓦片采样 WorldMap 湿度，调制水域/沙滩阈值，实现群落边界自然过渡。
        // 噪声是全局连续的 → 相邻区块在边界处阈值渐变，而非硬切。
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                // 采样 WorldMap 湿度（tile 级，跨区块连续）
                double tileMoisture = worldMap.getMoistureAt(globalX, globalY);

                // 水域阈值 = 群落基数 + 局部湿度调制（湿度越高 → 水域越多）
                // 干燥群落（waterLevel==0）强制压低阈值，防止生成水域
                double waterThreshold;
                if (wl <= 0.0f) {
                    // 干燥群落：阈值压到极低，几乎不生成水域
                    waterThreshold = BASE_WATER_LEVEL + DRY_THRESHOLD_OFFSET;
                } else {
                    waterThreshold = BASE_WATER_LEVEL + wl * 0.35 + tileMoisture * 0.08;
                }

                // 沙滩阈值：只有有水群落才生成沙滩
                double beachThreshold;
                if (wl > 0.05f) {
                    beachThreshold = BASE_BEACH_LEVEL + wl * 0.10 + tileMoisture * 0.04;
                } else {
                    beachThreshold = BASE_WATER_LEVEL + DRY_THRESHOLD_OFFSET;
                }

                double elevation = noise.fbm(
                        globalX * TERRAIN_FREQ,
                        globalY * TERRAIN_FREQ,
                        TERRAIN_OCTAVES, PERSISTENCE, LACUNARITY
                );

                tiles[row][col] = classifyTerrainWithoutWater(elevation,
                        beachThreshold, rockThreshold);
            }
        }

        // ── 第二遍：噪声 + 群落参数 → 植被 ──
        placeVegetation(noise);

        // ─ 第三遍：排水算法 → 湖泊/河流/海洋 ──
        carveWaterFeatures(worldMap);

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
     * 根据高程和阈值分类基底地形（不生成水域和沙滩，由第三遍统一处理）。
     */
    private TileType classifyTerrainWithoutWater(double elevation,
                                                  double beachThreshold,
                                                  double rockThreshold) {
        // 第一遍只生成陆地地形（GRASS/STONE），水域和沙滩由第三遍根据排水梯度决定
        if (elevation > rockThreshold) {
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
                    tiles[row][col] = (flowerHash < 0.12) ? TileType.FLOWER : TileType.TALL_GRASS;
                    continue;
                }

                // 其余保持 GRASS
            }
        }
    }

    /**
     * 第三遍：放置水域（湖泊/河流/海洋）及过渡带。
     *
     * <p>使用排水算法的梯度值（考虑过渡带衰减）：
     * <ul>
     *   <li><b>深水区</b>：梯度 ≥ 0.5 → WATER</li>
     *   <li><b>浅水/沙滩过渡</b>：梯度 0.2~0.5 → SAND（海滩）</li>
     *   <li><b>陆地</b>：梯度 &lt; 0.2 → 保留原地形（草地/森林等）</li>
     * </ul>
     *
     * <p>这样水域边缘会有自然的沙滩过渡带，而不是硬切。
     */
    private void carveWaterFeatures(WorldMap worldMap) {
        int waterCount = 0, sandCount = 0, grassCount = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                TileType base = tiles[row][col];
                // 只在草地/沙地上放置水域（不覆盖已有水域、岩石、植被）
                if (base != TileType.GRASS && base != TileType.SAND) {
                    continue;
                }
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                double waterGradient;
                if (drainageMap != null) {
                    // 使用排水算法的梯度值（带群落检查）
                    waterGradient = getWaterGradientWithBiomeCheck(globalX, globalY, worldMap);
                } else {
                    // 回退到 WorldMap 水域特征
                    waterGradient = worldMap.getWaterFeature(globalX, globalY);
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
        // 调试输出
        if (waterCount > 0 || sandCount > 0) {
            System.out.printf("Chunk (%d,%d) %s: WATER=%d SAND=%d GRASS=%d drainageMap=%s%n",
                    chunkX, chunkY, biome.getName(), waterCount, sandCount, grassCount,
                    drainageMap != null ? "yes" : "no");
        }
    }

    /**
     * 获取水域梯度值（带群落检查）。
     * 干燥群落强制返回 0，确保大地图与小地图一致。
     */
    private double getWaterGradientWithBiomeCheck(int worldX, int worldY, WorldMap worldMap) {
        // 检查群落类型
        var biome = worldMap.getBiomeAt(worldX, worldY);
        if (biome.getWaterLevel() <= 0.0f) {
            return 0.0; // 干燥群落强制无水
        }
        return drainageMap.getWaterGradient(worldX, worldY);
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
}
