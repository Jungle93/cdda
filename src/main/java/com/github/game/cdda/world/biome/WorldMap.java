package com.github.game.cdda.world.biome;

import com.github.game.cdda.game.DirectionalGradients;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.engine.core.noise.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 世界地图（大地图）。使用 Perlin 噪声生成全局生物群落分布。
 *
 * <p>每个世界地图单元（cell）对应一个 {@link Chunk}（64×64 瓦片）。
 * 世界地图决定每个区块的"角色"——平原、森林、海洋等，
 * 区块生成时根据自身所在群落的参数放置具体地形。
 *
 * <h3>分层架构</h3>
 * <pre>
 * WorldMap（大地图，低频噪声）
 *   ↓ 每格对应一个 Chunk
 * Chunk（小地图，中频噪声 + 群落参数驱动）
 *   ↓ 64×64 瓦片
 * 游戏渲染
 * </pre>
 *
 * <h3>噪声层</h3>
 * <ul>
 *   <li><b>高程噪声</b>（frequency ~0.0004）— 大尺度大陆/海洋，跨度 ~160 区块</li>
 *   <li><b>湿度噪声</b>（frequency ~0.0003）— 生物群落区域，跨度 ~200 区块</li>
 *   <li><b>温度噪声</b>（frequency ~0.0002）— 辅助分类，跨度 ~250 区块</li>
 * </ul>
 *
 * <p>扩展点：后续可添加河流、道路等线性特征层。
 */
public class WorldMap {

    private static final Logger logger = LoggerFactory.getLogger(WorldMap.class);

    // ── 噪声参数（极低频 → 大陆尺度特征） ──────────────────

    /** 高程噪声频率（特征跨度 ~160 区块 = ~10000 瓦片） */
    private static final double ELEVATION_FREQ = 0.0004;
    /** 湿度噪声频率（特征跨度 ~200 区块 = ~13000 瓦片） */
    private static final double MOISTURE_FREQ = 0.0003;
    /** 温度噪声频率（特征跨度 ~250 区块 = ~16000 瓦片） */
    private static final double TEMPERATURE_FREQ = 0.0002;

    /** fBm 参数（低细节，大尺度） */
    private static final int OCTAVES = 3;
    private static final double PERSISTENCE = 0.5;
    private static final double LACUNARITY = 2.0;

    // ── 水域特征参数（湖泊 + 河流，由大地图统一决定） ──────────────

    /** 湖泊高程阈值（低于此值 + 高湿度 → 湖泊） */
    private static final double LAKE_ELEVATION = -0.15;
    /** 湖泊湿度阈值（高于此值 → 可能形成湖泊） */
    private static final double LAKE_MOISTURE = 0.20;
    /** 河流高程阈值（低于此值 + 中等湿度 → 可能形成河流） */
    private static final double RIVER_ELEVATION = 0.00;
    /** 河流最低湿度（低于此值不生成河流） */
    private static final double RIVER_MIN_MOISTURE = 0.00;
    /** 河流噪声频率（极低频 → 长距离连续河道） */
    private static final double RIVER_FREQ = 0.004;
    /** 河流噪声 fBm 层数 */
    private static final int RIVER_OCTAVES = 2;
    /** 河道半宽阈值（|noise| < 此值视为河道内） */
    private static final double RIVER_HALF_WIDTH = 0.035;

    // ── 生物群落分类阈值 ──────────────────

    /** 海洋高程阈值（低于此值 = 海洋） */
    private static final double OCEAN_THRESHOLD = -0.25;
    /** 浅水/海岸阈值（低于此值 = 沼泽/海滩） */
    private static final double COAST_THRESHOLD = -0.10;
    /** 高地阈值（高于此值 = 丘陵/山地） */
    private static final double HIGHLAND_THRESHOLD = 0.35;
    /** 高山阈值（高于此值 = 山地） */
    private static final double MOUNTAIN_THRESHOLD = 0.55;
    /** 高原阈值（高于此值且低湿度 = 高原） */
    private static final double PLATEAU_THRESHOLD = 0.30;
    /** 干燥阈值（低于此湿度 = 沙漠） */
    private static final double DRY_THRESHOLD = -0.20;
    /** 湿润阈值（高于此湿度 + 低地 = 森林/沼泽） */
    private static final double WET_THRESHOLD = 0.10;
    /** 草原湿度上限（介于此值与干燥阈值之间 = 草原） */
    private static final double GRASSLAND_MOISTURE_MAX = 0.05;

    /** 世界种子 */
    private final long worldSeed;

    /** 方向梯度配置（默认 ~2000 区块尺度） */
    private final DirectionalGradients gradients;

    /** 高程噪声生成器 */
    private final PerlinNoise elevationNoise;
    /** 湿度噪声生成器 */
    private final PerlinNoise moistureNoise;
    /** 温度噪声生成器 */
    private final PerlinNoise temperatureNoise;
    /** 河流噪声生成器（全局河道层，跨区块连续） */
    private final PerlinNoise riverNoise;

    /** 生物群落缓存（chunkKey → BiomeType），避免重复计算 */
    private final java.util.Map<Long, BiomeType> biomeCache = new java.util.HashMap<>();

    /**
     * 创建世界地图。
     *
     * @param worldSeed 世界种子（决定地图布局）
     */
    public WorldMap(long worldSeed) {
        this(worldSeed, DirectionalGradients.DEFAULT);
    }

    /**
     * 创建世界地图。
     *
     * @param worldSeed  世界种子（决定地图布局）
     * @param gradients  方向梯度配置（null 时使用默认梯度）
     */
    public WorldMap(long worldSeed, DirectionalGradients gradients) {
        this.worldSeed = worldSeed;
        this.gradients = gradients != null ? gradients : DirectionalGradients.DEFAULT;
        // 每个噪声层使用不同种子偏移，保证独立性
        this.elevationNoise = new PerlinNoise(worldSeed);
        this.moistureNoise = new PerlinNoise(worldSeed + 0x9E3779B97F4A7C15L);
        this.temperatureNoise = new PerlinNoise(worldSeed + 0x517CC1B727220A95L);
        this.riverNoise = new PerlinNoise(worldSeed + 0x3C6EF372FE94F82BL);
        logger.info("世界地图初始化 — 种子: {}, 梯度: {}", worldSeed, this.gradients);
    }

    /**
     * 获取世界瓦片坐标处的生物群落。
     * 每个区块的所有瓦片返回相同的生物群落（区块级群落分配）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 该位置所属的生物群落
     */
    public BiomeType getBiomeAt(int worldTileX, int worldTileY) {
        // 转换为区块坐标（每个区块对应一个世界地图单元）
        int chunkX = Math.floorDiv(worldTileX, Chunk.SIZE);
        int chunkY = Math.floorDiv(worldTileY, Chunk.SIZE);
        return getBiomeAtChunk(chunkX, chunkY);
    }

    /**
     * 获取指定区块坐标的生物群落。
     * 使用缓存避免重复计算。
     *
     * @param chunkX 区块 X 坐标
     * @param chunkY 区块 Y 坐标
     * @return 该区块的生物群落
     */
    public BiomeType getBiomeAtChunk(int chunkX, int chunkY) {
        long key = chunkKey(chunkX, chunkY);
        BiomeType cached = biomeCache.get(key);
        if (cached != null) return cached;

        BiomeType biome = classifyBiome(chunkX, chunkY);
        biomeCache.put(key, biome);
        return biome;
    }

    /**
     * 根据噪声值分类生物群落。
     *
     * <p>分类逻辑（从上到下，先命中先返回）：
     * <ol>
     *   <li>低高程 → 海洋</li>
     *   <li>海岸高程 + 高湿度 → 沼泽</li>
     *   <li>海岸高程 + 低湿度 → 平原</li>
     *   <li>很高 → 山地</li>
     *   <li>高海拔 + 低湿度 → 高原</li>
     *   <li>高地 → 丘陵</li>
     *   <li>低地 + 干燥 → 沙漠</li>
     *   <li>低地 + 中等湿度 → 草原</li>
     *   <li>低地 + 湿润 → 森林/密林</li>
     * </ol>
     */
    private BiomeType classifyBiome(int chunkX, int chunkY) {
        // 使用区块中心的世界瓦片坐标采样（保证对称性）
        double centerX = (chunkX + 0.5) * Chunk.SIZE;
        double centerY = (chunkY + 0.5) * Chunk.SIZE;

        double elevation = elevationNoise.fbm(
                centerX * ELEVATION_FREQ,
                centerY * ELEVATION_FREQ,
                OCTAVES, PERSISTENCE, LACUNARITY);
        double moisture = moistureNoise.fbm(
                centerX * MOISTURE_FREQ + 500.0,
                centerY * MOISTURE_FREQ + 500.0,
                OCTAVES, PERSISTENCE, LACUNARITY);
        double temperature = temperatureNoise.fbm(
                centerX * TEMPERATURE_FREQ + 1000.0,
                centerY * TEMPERATURE_FREQ + 1000.0,
                OCTAVES, PERSISTENCE, LACUNARITY);

        // ── 方向梯度偏移 ──
        elevation += gradients.getElevationOffset(chunkY);
        temperature += gradients.getTemperatureOffset(chunkX, chunkY);
        moisture += gradients.getMoistureOffset(chunkX);

        // ── 分类 ──

        // 深水 → 海洋
        if (elevation < OCEAN_THRESHOLD) {
            return BiomeType.OCEAN;
        }

        // 海岸区域（低高程但非深水）
        if (elevation < COAST_THRESHOLD) {
            // 高湿度海岸 → 沼泽
            if (moisture > WET_THRESHOLD) {
                return BiomeType.SWAMP;
            }
            return BiomeType.PLAINS;
        }

        // 高海拔 → 山地
        if (elevation > MOUNTAIN_THRESHOLD) {
            return BiomeType.MOUNTAIN;
        }

        // 高海拔平地 → 高原（中等偏高海拔 + 低湿度）
        if (elevation > PLATEAU_THRESHOLD && moisture < 0.0) {
            return BiomeType.PLATEAU;
        }

        // 高地 → 丘陵
        if (elevation > HIGHLAND_THRESHOLD) {
            return BiomeType.HILLS;
        }

        // 低地 — 根据湿度划分
        if (moisture < DRY_THRESHOLD) {
            return BiomeType.DESERT;
        } else if (moisture <= GRASSLAND_MOISTURE_MAX) {
            return BiomeType.GRASSLAND;
        } else if (moisture < WET_THRESHOLD) {
            return BiomeType.PLAINS;
        } else {
            // 湿润低地：根据温度细分
            if (temperature > 0.1) {
                return BiomeType.DENSE_FOREST;
            } else {
                return BiomeType.FOREST;
            }
        }
    }

    // ── 工具方法 ────────────────────────────

    /**
     * 将两个 int 合并为 long key（与 ChunkManager 一致）。
     */
    private static long chunkKey(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }

    // ── 访问器 ───────────────────────────

    public long getWorldSeed() { return worldSeed; }

    /** 获取河流噪声生成器（供 Chunk 河道 carve 使用） */
    public PerlinNoise getRiverNoise() { return riverNoise; }

    /** 获取已缓存的生物群落数量 */
    public int getCachedBiomeCount() { return biomeCache.size(); }

    // ── 水域特征查询（湖泊 + 河流，由大地图统一决定） ────────────────────

    /**
     * 查询指定世界瓦片坐标处的水域特征。
     *
     * <p>水域由大地图的高程 + 湿度噪声统一决定，保证河流/湖泊出现在合理地形的低洼湿润区域，
     * 而非随机位置。Chunk 根据返回值决定瓦片是否为水域及边缘过渡。
     *
     * <p>返回 {@code double} 表示水域强度：
     * <ul>
     *   <li>{@code 0.0} — 无水域</li>
     *   <li>{@code 0.0 ~ 0.5} — 河流边缘过渡区</li>
     *   <li>{@code 0.5 ~ 1.0} — 河流中心 / 湖泊</li>
     *   <li>{@code > 1.0} — 深水区（湖泊中心）</li>
     * </ul>
     *
     * <p>判定逻辑：
     * <ol>
     *   <li><b>湖泊</b>：高程 &lt; LAKE_ELEVATION 且湿度 &gt; LAKE_MOISTURE → 直接返回 1.0+</li>
     *   <li><b>河流</b>：高程 &lt; RIVER_ELEVATION 且湿度 &gt; RIVER_MIN_MOISTURE，
     *       再用河流噪声 carve 窄带河道</li>
     * </ol>
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 水域强度值（0 = 无水，越大水越深）
     */
    public double getWaterFeature(int worldTileX, int worldTileY) {
        double wx = worldTileX * ELEVATION_FREQ;
        double wy = worldTileY * ELEVATION_FREQ;

        // ── 湖泊：低洼 + 高湿 ──
        double elevation = elevationNoise.fbm(wx, wy, OCTAVES, PERSISTENCE, LACUNARITY);
        double moisture = moistureNoise.fbm(
                worldTileX * MOISTURE_FREQ + 500.0,
                worldTileY * MOISTURE_FREQ + 500.0,
                OCTAVES, PERSISTENCE, LACUNARITY);

        if (elevation < LAKE_ELEVATION && moisture > LAKE_MOISTURE) {
            // 湖泊：中心更深，边缘渐浅
            double lakeDepth = 1.0 + (LAKE_ELEVATION - elevation) * 3.0;
            return Math.min(lakeDepth, 2.5);
        }

        // ── 河流：低地 + 湿润 + 噪声河道 ─
        if (elevation < RIVER_ELEVATION && moisture > RIVER_MIN_MOISTURE) {
            double nx = worldTileX * RIVER_FREQ;
            double ny = worldTileY * RIVER_FREQ;
            double riverVal = riverNoise.fbm(nx, ny, RIVER_OCTAVES, PERSISTENCE, LACUNARITY);
            double absNoise = Math.abs(riverVal);
            if (absNoise < RIVER_HALF_WIDTH) {
                // 河道宽度：中心 ~1.0，边缘渐收至 0.3
                return 0.3 + (1.0 - absNoise / RIVER_HALF_WIDTH) * 0.7;
            }
        }

        return 0.0;
    }

    // ── 噪声采样（供 Chunk 逐瓦片查询，实现群落边界平滑过渡） ──────────────

    /**
     * 获取指定世界瓦片坐标的高程噪声值。
     * 使用与 {@link #classifyBiome} 相同的频率和偏移，保证大地图和小地图一致。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 高程噪声值（约 -1 ~ 1）
     */
    public double getElevationAt(int worldTileX, int worldTileY) {
        double wx = worldTileX * ELEVATION_FREQ;
        double wy = worldTileY * ELEVATION_FREQ;
        return elevationNoise.fbm(wx, wy, OCTAVES, PERSISTENCE, LACUNARITY);
    }

    /**
     * 获取指定世界瓦片坐标的湿度噪声值。
     * 使用与 {@link #classifyBiome} 相同的频率和偏移，保证大地图和小地图一致。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 湿度噪声值（约 -1 ~ 1）
     */
    public double getMoistureAt(int worldTileX, int worldTileY) {
        return moistureNoise.fbm(
                worldTileX * MOISTURE_FREQ + 500.0,
                worldTileY * MOISTURE_FREQ + 500.0,
                OCTAVES, PERSISTENCE, LACUNARITY);
    }

    /**
     * 使用自定义参数采样高程噪声（供排水算法等需要不同分辨率的场景）。
     *
     * @param worldTileX  世界瓦片 X 坐标
     * @param worldTileY  世界瓦片 Y 坐标
     * @param frequency   噪声频率
     * @param octaves     fBm 层数
     * @param persistence 振幅衰减
     * @param lacunarity  频率倍增
     * @return 噪声值（约 -1 ~ 1）
     */
    public double sampleElevationNoise(int worldTileX, int worldTileY,
                                       double frequency, int octaves,
                                       double persistence, double lacunarity) {
        return elevationNoise.fbm(
                worldTileX * frequency, worldTileY * frequency,
                octaves, persistence, lacunarity);
    }

    // ── 环境参数查询（供植被系统使用） ──────────────

    /**
     * 获取指定世界瓦片坐标的环境温度 (°C)。
     * 基于温度噪声 + 海拔修正（每升高 1 单位高程降温约 6.5°C）
     * + 方向梯度偏移（北冷南热、西暖东凉）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 估算温度 (°C)，约 -20 ~ 30°C
     */
    public double getTemperatureAt(int worldTileX, int worldTileY) {
        // 基础温度（温度噪声映射到 -5 ~ 25°C 范围）
        double tempNoise = temperatureNoise.fbm(
                worldTileX * TEMPERATURE_FREQ + 1000.0,
                worldTileY * TEMPERATURE_FREQ + 1000.0,
                OCTAVES, PERSISTENCE, LACUNARITY);
        double baseTemp = 10.0 + tempNoise * 15.0; // -5 ~ 25°C

        // 海拔修正（高程越高温度越低）
        double elevation = getElevationAt(worldTileX, worldTileY);
        double elevationPenalty = Math.max(0, elevation) * 6.5; // 每单位高程降 6.5°C

        // 方向梯度偏移
        int chunkX = Math.floorDiv(worldTileX, Chunk.SIZE);
        int chunkY = Math.floorDiv(worldTileY, Chunk.SIZE);
        double gradientOffset = gradients.getTemperatureOffset(chunkX, chunkY) * 100.0;

        return baseTemp - elevationPenalty + gradientOffset;
    }

    /**
     * 获取指定世界瓦片坐标的土壤深度 (0-1)。
     * 基于高程和生物群落估算：低地土壤深，高地土壤浅。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 土壤深度 (0-1)，0 = 裸岩/无土壤，1 = 深土
     */
    public double getSoilDepthAt(int worldTileX, int worldTileY) {
        double elevation = getElevationAt(worldTileX, worldTileY);
        BiomeType biome = getBiomeAt(worldTileX, worldTileY);

        // 基础土壤深度：低地深，高地浅
        double baseDepth = Math.max(0.0, 0.7 - elevation * 0.8);

        // 生物群落修正
        if (biome == BiomeType.SWAMP) {
            return Math.min(1.0, baseDepth + 0.2); // 沼泽土壤较深
        } else if (biome == BiomeType.DESERT) {
            return Math.max(0.0, baseDepth - 0.2); // 沙漠土壤较浅
        } else if (biome == BiomeType.MOUNTAIN) {
            return Math.max(0.0, baseDepth - 0.3); // 山地多岩石
        }
        return baseDepth;
    }

    /**
     * 获取指定世界瓦片坐标的湿度 (0-1)。
     * 将原始湿度噪声值 (-1 ~ 1) 归一化到 0-1 范围，
     * + 方向梯度偏移（东湿西干）。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 湿度 (0-1)
     */
    public double getHumidityAt(int worldTileX, int worldTileY) {
        double moisture = getMoistureAt(worldTileX, worldTileY);

        // 方向梯度偏移
        int chunkX = Math.floorDiv(worldTileX, Chunk.SIZE);
        moisture += gradients.getMoistureOffset(chunkX);

        // 噪声值约 -1 ~ 1，归一化到 0 ~ 1
        return Math.max(0.0, Math.min(1.0, (moisture + 1.0) * 0.5));
    }
}
