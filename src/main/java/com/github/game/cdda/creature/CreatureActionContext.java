package com.github.game.cdda.creature;

import com.github.game.cdda.Player;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.util.Collections;
import java.util.List;

/**
 * 生物行动时的世界上下文。
 * 提供 AI 决策所需的环境信息，避免生物直接依赖 GameWorld。
 */
public class CreatureActionContext {

    /** 玩家实例 */
    private final Player player;

    /** 地图管理器（碰撞检测） */
    private final ChunkManager chunkManager;

    /** 生物管理器（用于 AI 寻找猎物） */
    private CreatureManager creatureManager;

    /** 当前回合的存活生物快照（后台线程计算时使用，避免 O(N) 全列表扫描） */
    private List<Animal> turnSnapshot;

    /** 瓦片像素宽度 */
    private final int tileWidth;

    /** 瓦片像素高度 */
    private final int tileHeight;

    /**
     * 创建行动上下文。
     *
     * @param player      玩家实例
     * @param chunkManager 地图管理器
     * @param tileWidth   瓦片像素宽度
     * @param tileHeight  瓦片像素高度
     */
    public CreatureActionContext(Player player, ChunkManager chunkManager, int tileWidth, int tileHeight) {
        this.player = player;
        this.chunkManager = chunkManager;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    /**
     * 设置当前回合的存活生物快照。
     * 由 CreatureManager 在后台计算前注入。
     */
    void setTurnSnapshot(List<Animal> snapshot) {
        this.turnSnapshot = snapshot;
    }

    /**
     * 获取当前回合的存活生物快照。
     * 如果快照为空，回退到 CreatureManager 的全列表。
     */
    public List<Animal> getTurnSnapshot() {
        if (turnSnapshot != null) return turnSnapshot;
        if (creatureManager != null) {
            return creatureManager.getAliveAnimals();
        }
        return Collections.emptyList();
    }

    public int getPlayerTileX() {
        return player.getTileX();
    }

    public int getPlayerTileY() {
        return player.getTileY();
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public void setCreatureManager(CreatureManager creatureManager) {
        this.creatureManager = creatureManager;
    }

    public CreatureManager getCreatureManager() {
        return creatureManager;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }
}
