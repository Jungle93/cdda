package com.github.game.cdda.world.region;

import com.github.game.cdda.world.noise.PerlinNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域管理器。管理区域级逻辑。
 *
 * 区域是区块的逻辑分组，每个区域包含 regionChunkSize × regionChunkSize 个区块。
 * 使用低频 Perlin 噪声为区域分配属性（后续用于大地图渲染）。
 *
 * 当前阶段 Region 仅作为逻辑标识，不持有瓦片数据。
 */
public class RegionManager {

    /** 每个区域包含的区块数（边长） */
    private final int regionChunkSize;

    /** 区块边长（瓦片数） */
    private final int chunkSize;

    /** 低频区域噪声（用于决定区域属性） */
    private final PerlinNoise regionNoise;

    /** 世界种子（用于派生区域种子） */
    private final long worldSeed;

    /**
     * 创建区域管理器。
     *
     * @param worldSeed       世界种子
     * @param regionChunkSize 每个区域包含的区块数（边长）
     * @param chunkSize       区块边长（瓦片数）
     */
    public RegionManager(long worldSeed, int regionChunkSize, int chunkSize) {
        this.worldSeed = worldSeed;
        this.regionChunkSize = regionChunkSize;
        this.chunkSize = chunkSize;
        // 区域噪声使用不同的种子（世界种子 + 偏移），频率更低
        this.regionNoise = new PerlinNoise(worldSeed + 0x9E3779B97F4A7C15L);
    }

    /**
     * 获取世界瓦片坐标所在的区域。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @return 所在区域
     */
    public Region getRegionAt(int worldTileX, int worldTileY) {
        int tilesPerRegion = regionChunkSize * chunkSize;
        int rx = floorDiv(worldTileX, tilesPerRegion);
        int ry = floorDiv(worldTileY, tilesPerRegion);
        return createRegion(rx, ry);
    }

    /**
     * 获取指定世界坐标周围的区域列表。
     *
     * @param worldTileX 世界瓦片 X 坐标
     * @param worldTileY 世界瓦片 Y 坐标
     * @param radius     区域半径（以区域为单位）
     * @return 周围区域列表
     */
    public List<Region> getSurroundingRegions(int worldTileX, int worldTileY, int radius) {
        int tilesPerRegion = regionChunkSize * chunkSize;
        int centerX = floorDiv(worldTileX, tilesPerRegion);
        int centerY = floorDiv(worldTileY, tilesPerRegion);

        List<Region> regions = new ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                regions.add(createRegion(centerX + dx, centerY + dy));
            }
        }
        return regions;
    }

    /**
     * 创建区域对象。区域种子由世界种子和区域坐标派生。
     */
    private Region createRegion(int rx, int ry) {
        long regionSeed = worldSeed ^ ((long) rx * 0x7FFFFFFFL + ry * 0x7FFFFFEL);
        return new Region(rx, ry, regionSeed);
    }

    /**
     * 获取区域的噪声值（低频）。
     * 后续大地图渲染时可用此值决定区域颜色/标签。
     *
     * @param regionX 区域 X 坐标
     * @param regionY 区域 Y 坐标
     * @return 噪声值 [-1, 1]
     */
    public double getRegionNoiseValue(int regionX, int regionY) {
        // 低频采样：每个区域在噪声空间中相距 1.0
        return regionNoise.noise(regionX * 1.0, regionY * 1.0);
    }

    public int getRegionChunkSize() { return regionChunkSize; }
    public int getChunkSize() { return chunkSize; }
    public int getTilesPerRegion() { return regionChunkSize * chunkSize; }

    /** 地板除法（正确处理负数） */
    private static int floorDiv(int x, int y) {
        return Math.floorDiv(x, y);
    }
}
