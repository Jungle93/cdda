package com.github.game.cdda.world.biome;

import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.cdda.world.noise.PerlinNoise;
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

    // ── 生物群落分类阈值 ──────────────────

    /** 海洋高程阈值（低于此值 = 海洋） */
    private static final double OCEAN_THRESHOLD = -0.25;
    /** 浅水/海岸阈值（低于此值 = 沼泽/海滩） */
    private static final double COAST_THRESHOLD = -0.10;
    /** 高地阈值（高于此值 = 丘陵/山地） */
    private static final double HIGHLAND_THRESHOLD = 0.35;
    /** 高山阈值（高于此值 = 山地） */
    private static final double MOUNTAIN_THRESHOLD = 0.55;
    /** 干燥阈值（低于此湿度 = 沙漠） */
    private static final double DRY_THRESHOLD = -0.20;
    /** 湿润阈值（高于此湿度 + 低地 = 森林/沼泽） */
    private static final double WET_THRESHOLD = 0.10;

    /** 世界种子 */
    private final long worldSeed;

    /** 高程噪声生成器 */
    private final PerlinNoise elevationNoise;
    /** 湿度噪声生成器 */
    private final PerlinNoise moistureNoise;
    /** 温度噪声生成器 */
    private final PerlinNoise temperatureNoise;

    /** 生物群落缓存（chunkKey → BiomeType），避免重复计算 */
    private final java.util.Map<Long, BiomeType> biomeCache = new java.util.HashMap<>();

    /**
     * 创建世界地图。
     *
     * @param worldSeed 世界种子（决定地图布局）
     */
    public WorldMap(long worldSeed) {
        this.worldSeed = worldSeed;
        // 每个噪声层使用不同种子偏移，保证独立性
        this.elevationNoise = new PerlinNoise(worldSeed);
        this.moistureNoise = new PerlinNoise(worldSeed + 0x9E3779B97F4A7C15L);
        this.temperatureNoise = new PerlinNoise(worldSeed + 0x517CC1B727220A95L);
        logger.info("世界地图初始化 — 种子: {}", worldSeed);
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
     * <p>分类逻辑：
     * <ol>
     *   <li>低高程 → 海洋</li>
     *   <li>海岸高程 + 高湿度 → 沼泽</li>
     *   <li>海岸高程 + 低湿度 → 平原</li>
     *   <li>低地 + 干燥 → 沙漠</li>
     *   <li>低地 + 适中 → 平原</li>
     *   <li>低地 + 湿润 → 森林/密林</li>
     *   <li>高地 → 丘陵</li>
     *   <li>很高 → 山地</li>
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

        // 高地
        if (elevation > MOUNTAIN_THRESHOLD) {
            return BiomeType.MOUNTAIN;
        }
        if (elevation > HIGHLAND_THRESHOLD) {
            return BiomeType.HILLS;
        }

        // 低地 — 根据湿度划分
        if (moisture < DRY_THRESHOLD) {
            return BiomeType.DESERT;
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

    // ── 访问器 ────────────────────────────

    public long getWorldSeed() { return worldSeed; }

    /** 获取已缓存的生物群落数量 */
    public int getCachedBiomeCount() { return biomeCache.size(); }
}
