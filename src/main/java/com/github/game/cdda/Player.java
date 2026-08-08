package com.github.game.cdda;

import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.awt.*;

/**
 * 玩家角色。继承自 {@link Entity}，管理世界坐标位置、移动输入和字符渲染。
 *
 * <p>采用网格式移动：每次按键移动恰好一个瓦片的距离，
 * 位置始终对齐到瓦片边界，保证与地图字符完美对齐。
 *
 * <p>支持碰撞检测：移动前检查目标瓦片通行性。
 * 移动是回合制行动——每次成功移动消耗游戏时间。
 */
public class Player extends Entity {

    /** 世界坐标 X（像素，左上角，始终为 tileWidth 的整数倍） */
    private int worldX;
    /** 世界坐标 Y（像素，左上角，始终为 tileHeight 的整数倍） */
    private int worldY;

    /** 玩家像素宽度（= 一个字符宽度，由外部设置） */
    private int pixelWidth;
    /** 玩家像素高度（= 一个字符高度，由外部设置） */
    private int pixelHeight;

    /** 世界查询接口（碰撞检测用） */
    private ChunkManager chunkManager;
    /** 瓦片像素尺寸（碰撞检测用） */
    private int tileWidth;
    private int tileHeight;

    public Player(int startX, int startY) {
        this.worldX = startX;
        this.worldY = startY;
        // 默认速度 100 = 正常步行（1.2 m/s）
        this.speed = Constants.ENTITY_DEFAULT_SPEED;
    }

    /**
     * 设置玩家像素尺寸。在瓦片尺寸确定后调用一次。
     */
    public void initDimensions(int tileWidth, int tileHeight) {
        this.pixelWidth = tileWidth;
        this.pixelHeight = tileHeight;
        // 对齐到瓦片边界
        this.worldX = Math.floorDiv(worldX, tileWidth) * tileWidth;
        this.worldY = Math.floorDiv(worldY, tileHeight) * tileHeight;
    }

    /**
     * 设置世界查询接口（碰撞检测用）。
     */
    public void initWorld(ChunkManager chunkManager, int tileWidth, int tileHeight) {
        this.chunkManager = chunkManager;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    // ── 移动 ──────────────────────────────────

    /**
     * 尝试向指定方向移动一个瓦片。
     * 移动前检查目标瓦片是否可通过。
     *
     * @param dx 水平方向（-1=左, 0=不动, 1=右）
     * @param dy 垂直方向（-1=上, 0=不动, 1=下）
     * @return 是否成功移动
     */
    public boolean move(int dx, int dy) {
        if (dx == 0 && dy == 0) return false;
        if (chunkManager == null || tileWidth == 0 || tileHeight == 0) return false;

        int newX = worldX + dx * tileWidth;
        int newY = worldY + dy * tileHeight;

        if (!canMoveTo(newX, newY)) {
            return false;
        }

        worldX = newX;
        worldY = newY;
        return true;
    }

    /**
     * 检查目标位置是否可通过。
     * 检查目标瓦片覆盖的四个角的瓦片通行性。
     */
    private boolean canMoveTo(int x, int y) {
        int left   = x;
        int top    = y;
        int right  = x + pixelWidth - 1;
        int bottom = y + pixelHeight - 1;

        int[][] corners = {
            {left,  top},
            {right, top},
            {left,  bottom},
            {right, bottom}
        };

        for (int[] corner : corners) {
            int tileX = Math.floorDiv(corner[0], tileWidth);
            int tileY = Math.floorDiv(corner[1], tileHeight);
            TileType tile = chunkManager.getTile(tileX, tileY);
            if (tile != null && !tile.isPassable()) {
                return false;
            }
        }
        return true;
    }

    // ── 渲染 ──────────────────────────────────

    /**
     * 渲染玩家（字符模式）。通过 Camera 将世界坐标转换为视图局部坐标。
     */
    public void render(Renderer renderer, Camera camera) {
        int viewX = camera.toViewX(worldX);
        int viewY = camera.toViewY(worldY);

        renderer.setColor(Color.WHITE);
        int baselineY = viewY + renderer.getFontMetrics().getAscent();
        renderer.drawText("@", viewX, baselineY);
    }

    // ── 访问器 ──────────────────────────────────

    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }
    public int getPixelWidth() { return pixelWidth; }
    public int getPixelHeight() { return pixelHeight; }
}
