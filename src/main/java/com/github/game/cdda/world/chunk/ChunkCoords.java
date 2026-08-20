package com.github.game.cdda.world.chunk;

/**
 * 区块坐标工具类。
 *
 * <p>集中管理区块键计算、坐标转换等通用操作，
 * 消除 CreatureGrid / CreatureManager / EnergyFlowManager / AnimalAI
 * 中重复的位运算代码。
 */
public final class ChunkCoords {

    private ChunkCoords() {}

    /**
     * 将瓦片坐标转换为区块坐标（向下取整）。
     *
     * @param tileX 瓦片 X 坐标
     * @return 区块 X 坐标 = tileX / 32
     */
    public static int toChunkX(int tileX) {
        return tileX >> 5; // /32
    }

    /**
     * 将瓦片坐标转换为区块坐标（向下取整）。
     *
     * @param tileY 瓦片 Y 坐标
     * @return 区块 Y 坐标 = tileY / 32
     */
    public static int toChunkY(int tileY) {
        return tileY >> 5; // /32
    }

    /**
     * 计算区块唯一键。
     *
     * <p>将两个 32 位整数打包为一个 64 位 long：
     * 高 32 位 = chunkX，低 32 位 = chunkY（无符号）。
     * 支持负数坐标（算术右移 + 无符号掩码）。
     *
     * @param chunkX 区块 X 坐标
     * @param chunkY 区块 Y 坐标
     * @return 唯一区块键
     */
    public static long key(int chunkX, int chunkY) {
        return ((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL);
    }

    /**
     * 从瓦片坐标直接计算区块键。
     *
     * @param tileX 瓦片 X 坐标
     * @param tileY 瓦片 Y 坐标
     * @return 区块键
     */
    public static long keyFromTile(int tileX, int tileY) {
        return key(toChunkX(tileX), toChunkY(tileY));
    }

    /**
     * 从区块键提取 chunkX（有符号）。
     *
     * @param key 区块键
     * @return chunkX
     */
    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    /**
     * 从区块键提取 chunkY（有符号）。
     *
     * @param key 区块键
     * @return chunkY
     */
    public static int chunkY(long key) {
        return (int) key; // 低 32 位，强制转换保留符号
    }
}
