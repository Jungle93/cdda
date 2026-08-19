package com.github.game.cdda.save;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界存档数据。
 * 保存所有区块的地形数据。
 */
public class WorldSaveData {
    /** 区块数据列表 */
    public List<ChunkData> chunks = new ArrayList<>();

    public WorldSaveData() {}
}
