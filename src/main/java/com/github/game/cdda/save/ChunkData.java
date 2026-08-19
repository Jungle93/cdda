package com.github.game.cdda.save;

/**
 * 单区块存档数据。
 * 保存区块坐标和瓦片数据。
 */
public class ChunkData {
    /** 区块 X 坐标 */
    public int cx;
    /** 区块 Y 坐标 */
    public int cy;
    /** 瓦片名称数组（行优先，size*size 长度） */
    public String[] tiles;
    /** 植被数据（物种 ID，null 表示无植被） */
    public String[] vegetation;

    public ChunkData() {}

    public ChunkData(int cx, int cy, String[] tiles, String[] vegetation) {
        this.cx = cx;
        this.cy = cy;
        this.tiles = tiles;
        this.vegetation = vegetation;
    }
}
