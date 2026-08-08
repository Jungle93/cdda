package com.github.game.cdda.world.region;

/**
 * 区域。一组区块的逻辑分组。
 *
 * 区域是大地图的基本单元，每个区域包含 regionChunkSize × regionChunkSize 个区块。
 * 区域本身不持有瓦片数据，仅作为逻辑标识和种子容器。
 * 后续大地图渲染时，可用区域种子为每个区域分配颜色/标签。
 */
public class Region {

    /** 区域 X 坐标（以区域为单位） */
    private final int regionX;

    /** 区域 Y 坐标（以区域为单位） */
    private final int regionY;

    /** 区域种子（由世界种子和区域坐标派生） */
    private final long regionSeed;

    public Region(int regionX, int regionY, long regionSeed) {
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionSeed = regionSeed;
    }

    /**
     * 获取区域在世界中的瓦片坐标范围（左上角）。
     *
     * @param regionChunkSize 每个区域包含的区块数
     * @param chunkSize       每个区块的边长（瓦片数）
     * @return [worldTileX, worldTileY]
     */
    public int[] getWorldTileOrigin(int regionChunkSize, int chunkSize) {
        int tilesPerRegion = regionChunkSize * chunkSize;
        return new int[]{
                regionX * tilesPerRegion,
                regionY * tilesPerRegion
        };
    }

    public int getRegionX() { return regionX; }
    public int getRegionY() { return regionY; }
    public long getRegionSeed() { return regionSeed; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Region)) return false;
        Region r = (Region) o;
        return regionX == r.regionX && regionY == r.regionY;
    }

    @Override
    public int hashCode() {
        return 31 * regionX + regionY;
    }

    @Override
    public String toString() {
        return String.format("Region(%d,%d,seed=%d)", regionX, regionY, regionSeed);
    }
}
