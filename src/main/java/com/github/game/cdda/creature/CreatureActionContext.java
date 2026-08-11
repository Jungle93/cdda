package com.github.game.cdda.creature;

import com.github.game.cdda.Player;
import com.github.game.cdda.world.chunk.ChunkManager;

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
     * 获取玩家瓦片 X 坐标。
     *
     * @return 玩家瓦片 X
     */
    public int getPlayerTileX() {
        return player.getTileX();
    }

    /**
     * 获取玩家瓦片 Y 坐标。
     *
     * @return 玩家瓦片 Y
     */
    public int getPlayerTileY() {
        return player.getTileY();
    }

    /**
     * 获取地图管理器。
     *
     * @return ChunkManager
     */
    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    /**
     * 设置生物管理器。
     * 由 GameScene 在构造 CreatureActionContext 后调用。
     *
     * @param creatureManager 生物管理器
     */
    public void setCreatureManager(CreatureManager creatureManager) {
        this.creatureManager = creatureManager;
    }

    /**
     * 获取生物管理器。
     * 用于 AI 寻找猎物等查询。
     *
     * @return CreatureManager
     */
    public CreatureManager getCreatureManager() {
        return creatureManager;
    }

    /**
     * 获取瓦片像素宽度。
     *
     * @return 瓦片宽度
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * 获取瓦片像素高度。
     *
     * @return 瓦片高度
     */
    public int getTileHeight() {
        return tileHeight;
    }
}
