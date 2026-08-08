package com.github.game.cdda.world.chunk;

import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.noise.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 单个区块（chunk）。持有 64×64 瓦片数据。
 *
 * 使用 Perlin 噪声生成地形，基于全局世界坐标采样噪声，
 * 保证相邻区块边界处地形无缝衔接。
 *
 * 生成采用两遍：
 * 1. 高程噪声 → 基础地形（WATER, SAND, GRASS, STONE）
 * 2. 确定性随机 → 在 GRASS 上放置地表物体（TREE, BUSH, FLOWER, TALL_GRASS）
 */
public class Chunk {

    private static final Logger logger = LoggerFactory.getLogger(Chunk.class);

    /** 区块边长（瓦片数） */
    public static final int SIZE = 64;

    /** 噪声采样频率（控制地形特征大小） */
    private static final double NOISE_SCALE = 0.05;

    /** fBm 参数 */
    private static final int OCTAVES = 4;
    private static final double PERSISTENCE = 0.5;
    private static final double LACUNARITY = 2.0;

    /** 区块坐标 */
    private final int chunkX;
    private final int chunkY;

    /** 世界种子（用于确定性随机放置地表物体） */
    private final long worldSeed;

    /** 瓦片数据 [row][col] */
    private final TileType[][] tiles;

    /**
     * 创建区块并生成地形。
     *
     * @param chunkX   区块 X 坐标（以区块为单位）
     * @param chunkY   区块 Y 坐标（以区块为单位）
     * @param noise    世界 Perlin 噪声生成器
     * @param worldSeed 世界种子（用于地表物体随机放置）
     */
    public Chunk(int chunkX, int chunkY, PerlinNoise noise, long worldSeed) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.worldSeed = worldSeed;
        this.tiles = new TileType[SIZE][SIZE];
        generate(noise);
    }

    /**
     * 使用 Perlin 噪声生成此区块的地形（两遍生成）。
     *
     * 第一遍：高程噪声 → 基础地形
     *   < -0.3  → WATER
     *   -0.3 ~ 0.0 → SAND
     *   0.0 ~ 0.4  → GRASS
     *   > 0.4   → STONE
     *
     * 第二遍：在 GRASS 上随机放置地表物体
     *   8% TREE, 5% BUSH, 5% FLOWER, 10% TALL_GRASS, 72% 保持 GRASS
     */
    private void generate(PerlinNoise noise) {
        // ── 第一遍：高程噪声 → 基础地形 ──
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int globalX = chunkX * SIZE + col;
                int globalY = chunkY * SIZE + row;

                double elevation = noise.fbm(
                        globalX * NOISE_SCALE,
                        globalY * NOISE_SCALE,
                        OCTAVES, PERSISTENCE, LACUNARITY
                );

                if (elevation < -0.3) {
                    tiles[row][col] = TileType.WATER;
                } else if (elevation < 0.0) {
                    tiles[row][col] = TileType.SAND;
                } else if (elevation < 0.4) {
                    tiles[row][col] = TileType.GRASS;
                } else {
                    tiles[row][col] = TileType.STONE;
                }
            }
        }

        // ── 第二遍：在 GRASS 上随机放置地表物体 ──
        // 使用确定性随机种子（worldSeed XOR chunkKey）
        long chunkKeyValue = ((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL);
        Random random = new Random(worldSeed ^ chunkKeyValue);

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (tiles[row][col] == TileType.GRASS) {
                    double r = random.nextDouble();
                    if (r < 0.08) {
                        tiles[row][col] = TileType.TREE;        // 8% 树
                    } else if (r < 0.13) {
                        tiles[row][col] = TileType.BUSH;        // 5% 灌木
                    } else if (r < 0.18) {
                        tiles[row][col] = TileType.FLOWER;      // 5% 花
                    } else if (r < 0.28) {
                        tiles[row][col] = TileType.TALL_GRASS;  // 10% 高草
                    }
                    // 剩余 72% 保持 GRASS
                }
            }
        }

        logger.debug("区块 ({}, {}) 生成完成", chunkX, chunkY);
    }

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
}
