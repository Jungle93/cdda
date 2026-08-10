package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.drainage.DrainageMap;
import com.github.game.cdda.world.vegetation.VegetationDefinition;
import com.github.game.cdda.world.vegetation.VegetationMap;
import com.github.game.cdda.world.vegetation.VegetationRegistry;
import com.github.game.cdda.world.vegetation.VegetationType;
import com.github.game.engine.core.noise.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

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

    /** 植被地图（存储每个瓦片的植被物种 ID） */
    private VegetationMap vegetationMap;

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
        this.vegetationMap = new VegetationMap(chunkX, chunkY);
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
            placeVegetation(noise, worldMap);
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

                // 水域阈值不再在此使用，水域由第三遍 carveWaterFeatures 根据排水梯度决定

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

        // ── 第二遍：噪声 + 环境适配 → 植被 ──
        placeVegetation(noise, worldMap);

        // ─ 第三遍：排水算法 → 湖泊/河流/海洋 ──
        carveWaterFeatures(worldMap);

        // ── 第四遍：水边放置水生植被（芦苇/香蒲）──
        placeAquaticVegetation(noise, worldMap);

        logger.debug("区块 ({}, {}) 生成完成 — 群落: {}", chunkX, chunkY, biome.getName());
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
     * 在基底地形上放置植被（环境适配版）。
     *
     * <p>使用三层机制：
     * <ol>
     *   <li><b>环境查询</b> — 获取每瓦片的温度、湿度、土壤深度</li>
     *   <li><b>噪声区域层</b>（中频 fbm）— 决定哪些区域"可能"有植被</li>
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

                // ── 噪声区域层：中频 fbm（-1 ~ 1） ──
                double zone = noise.fbm(
                        globalX * VEG_FREQ + 2000.0,
                        globalY * VEG_FREQ + 2000.0,
                        VEG_OCTAVES, PERSISTENCE, LACUNARITY
                );
                double zoneFactor = (zone + 1.0) * 0.5;

                // ── 查询环境参数 ──
                double temperature = worldMap.getTemperatureAt(globalX, globalY);
                double humidity = worldMap.getHumidityAt(globalX, globalY);
                double soilDepth = worldMap.getSoilDepthAt(globalX, globalY);

                // ── 树木 ──
                double treeProb = treeD * (0.3 + 0.7 * zoneFactor);
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
                double shrubProb = treeD * 0.25 * (0.3 + 0.7 * zoneFactor);
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
                double grassProb = grassD * (0.3 + 0.7 * zoneFactor);
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
     * 在水域边缘放置水生植被（芦苇/香蒲）。
     *
     * <p>规则：
     * <ul>
     *   <li>WATER 瓦片且四邻接有非 WATER → 有概率放置（水边芦苇）</li>
     *   <li>SAND 瓦片且四邻接有 WATER → 有概率放置（湿地香蒲）</li>
     * </ul>
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
                                && tiles[nr][nc] != TileType.STONE) {
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

                // 环境查询
                double temperature = worldMap.getTemperatureAt(globalX, globalY);
                double humidity = worldMap.getHumidityAt(globalX, globalY);
                double soilDepth = worldMap.getSoilDepthAt(globalX, globalY);

                // 概率判定（使用偏移哈希区分 WATER/SAND 两种情况）
                int hashOffset = isWaterEdge ? 0 : 50000;
                double hash = tileHash(globalX + hashOffset, globalY + hashOffset);
                double threshold = isWaterEdge ? 0.30 : 0.15;

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

    /**
     * 获取植被地图。
     *
     * @return 植被地图（generate() 调用后可用）
     */
    public VegetationMap getVegetationMap() {
        return vegetationMap;
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
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     */
    public void clearVegetation(int localCol, int localRow) {
        if (vegetationMap != null) {
            vegetationMap.clear(localCol, localRow);
        }
    }
}
