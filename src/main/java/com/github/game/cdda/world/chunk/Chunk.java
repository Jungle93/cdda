package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
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

    /** 植被地图（存储每个瓦片的植被物种 ID） */
    private VegetationMap vegetationMap;

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
     * <p>四遍生成：
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
     *   <li><b>边界混合</b>
     *       检查相邻区块的群落类型和边缘瓦片数据，实现跨区块平滑过渡。</li>
     * </ol>
     *
     * @param noise       Perlin 噪声生成器
     * @param worldMap    世界地图（提供环境数据）
     * @param neighbors   周围 5×5 邻居区块（可为 null）
     */
    public void generate(PerlinNoise noise, WorldMap worldMap, Chunk[][] neighbors) {
        if (generated) return;
        this.tiles = new TileType[SIZE][SIZE];
        this.vegetationMap = new VegetationMap(chunkX, chunkY);
        generated = true;

        // 群落基数参数
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
                        biome, rockThreshold);
            }
        }

        // ── 第二遍：噪声 + 环境适配 → 植被 ──
        placeVegetation(noise, worldMap);

        // ─ 第三遍：排水算法 → 湖泊/河流/海洋 ──
        carveWaterFeatures(worldMap);

        // ── 第四遍：水边放置水生植被（芦苇/香蒲）──
        placeAquaticVegetation(noise, worldMap);

        // ── 第五遍：区块边界混合（双层级检查）──
        blendChunkEdges(neighbors, worldMap);

        logger.debug("区块 ({}, {}) 生成完成 — 群落: {}", chunkX, chunkY, biome.getName());
    }

    /**
     * 根据高程、温度、湿度和群落参数分类基底地形。
     *
     * <p>判定优先级：
     * <ol>
     *   <li>高海拔 + 高岩石率 → 石头</li>
     *   <li>低海拔 + 高湿度 → 泥地</li>
     *   <li>低海拔 + 中等湿度 → 泥土</li>
     *   <li>干燥 + 低湿度噪声 → 沙地</li>
     *   <li>高海拔 + 低温 → 石头（高原冻土）</li>
     *   <li>默认 → 草地</li>
     * </ol>
     */
    private TileType classifyTerrain(double elevation, double temperature,
                                      double humidity, double moisture,
                                      BiomeType biome, double rockThreshold) {
        // 岩石优先（高海拔 + 群落岩石率）
        if (elevation > rockThreshold) {
            return TileType.STONE;
        }

        // 低海拔 + 高湿度 → 泥地（沼泽边缘）
        if (elevation < -0.10 && humidity > 0.6) {
            return TileType.MUD;
        }

        // 低海拔 + 中等湿度 → 泥土地
        if (elevation < -0.05 && humidity > 0.3) {
            return humidity > 0.5 ? TileType.MUD : TileType.DIRT;
        }

        // 干燥环境 → 沙地
        if (humidity < 0.2 && moisture < -0.15) {
            return TileType.SAND;
        }

        // 高海拔 + 低温 → 高原冻土（石头）
        if (elevation > 0.30 && temperature < 0) {
            return TileType.STONE;
        }

        // 默认 → 草地
        return TileType.GRASS;
    }

    // ── 边界混合（第五遍） ────────────────────

    /** 方向偏移：上、下、左、右 */
    private static final int[] BLEND_DIR_DX = {0, 0, -1, 1};
    private static final int[] BLEND_DIR_DY = {-1, 1, 0, 0};

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

        int blendWidth = 2;

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
            blendWithNeighborTiles(dir, neighbor, blendWidth);
        }
    }

    /**
     * 与相邻区块边缘瓦片进行混合。
     *
     * @param dir        方向：0=上, 1=下, 2=左, 3=右
     * @param neighbor   相邻区块
     * @param blendWidth 混合宽度（格数）
     */
    private void blendWithNeighborTiles(int dir, Chunk neighbor, int blendWidth) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 1; j <= blendWidth; j++) {
                // 从邻居区块获取对应边缘瓦片
                TileType neighborTile = neighbor.getTileAtEdge(dir, i);
                if (neighborTile == null) continue;

                // 混合因子：距离边缘越近，受邻居影响越大
                double blendFactor = (double) (blendWidth + 1 - j) / (blendWidth + 1);

                // 确定当前区块中对应边缘的瓦片坐标
                int[] selfPos = getEdgeTilePos(dir, i, j - 1);
                int selfRow = selfPos[0];
                int selfCol = selfPos[1];

                TileType selfTile = tiles[selfRow][selfCol];

                // 根据邻居瓦片类型决定混合策略
                TileType blended = blendTile(selfTile, neighborTile, blendFactor, dir);
                if (blended != null) {
                    tiles[selfRow][selfCol] = blended;
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
     * 根据邻居瓦片类型和混合因子决定当前瓦片的混合结果。
     *
     * @param selfTile    当前瓦片
     * @param neighborTile 邻居瓦片
     * @param blendFactor  混合因子 (0~1，越大越倾向于采纳邻居类型)
     * @param dir          方向
     * @return 混合后的瓦片类型（null 表示不改变）
     */
    private TileType blendTile(TileType selfTile, TileType neighborTile,
                                double blendFactor, int dir) {
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

        // 邻居是石头 → 只在自身也是石头时保持，否则渐变
        if (neighborTile == TileType.STONE) {
            if (selfTile == TileType.GRASS && randomVal < blendFactor * 0.3) {
                return TileType.STONE;
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
                // 只在草地/沙地上放置水域（不覆盖已有水域、岩石、植被）
                if (base != TileType.GRASS && base != TileType.SAND) {
                    continue;
                }
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                double waterGradient = worldMap.getWaterFeature(globalX, globalY);

                // 水边高频扰动（让边界曲折，不是平滑直线）
                // 仅在过渡区（0.15 ~ 0.65）施加扰动，深水区和纯陆地不变
                if (waterGradient > 0.15 && waterGradient < 0.65) {
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
