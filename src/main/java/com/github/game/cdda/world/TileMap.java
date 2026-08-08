package com.github.game.cdda.world;

import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.world.chunk.ChunkManager;
import java.awt.*;

/**
 * 瓦片地图（渲染层）。
 * 从 ChunkManager 获取瓦片数据，负责可见区域的字符渲染。
 *
 * 职责：
 * - 管理瓦片像素尺寸（由字体度量决定）
 * - 根据摄像机视口范围渲染可见瓦片
 * - 不持有瓦片数组（数据源为 ChunkManager）
 */
public class TileMap {

    private final ChunkManager chunkManager;

    /** 瓦片像素宽度（由字体度量决定） */
    private int tileWidth;
    /** 瓦片像素高度（由字体度量决定） */
    private int tileHeight;

    /** 渲染字体 */
    private final Font font;

    /**
     * 创建瓦片地图（使用 ChunkManager 提供数据）。
     *
     * @param chunkManager 区块管理器（瓦片数据源）
     * @param fontSize     字体大小（pt），决定瓦片像素尺寸
     */
    public TileMap(ChunkManager chunkManager, int fontSize) {
        this.chunkManager = chunkManager;
        this.font = new Font("Monospaced", Font.PLAIN, fontSize);
    }

    /**
     * 初始化瓦片像素尺寸。必须在渲染前调用一次。
     * 通过 Renderer 的 FontMetrics 测量字符宽高。
     */
    public void initTileSize(Renderer renderer) {
        renderer.setFont(font);
        this.tileWidth = renderer.getFontMetrics().charWidth('.');
        this.tileHeight = renderer.getFontMetrics().getHeight();
    }

    // ── 查询 ──────────────────────────────────────

    /**
     * 获取世界瓦片坐标处的地形。
     * 委托给 ChunkManager。
     */
    public TileType getTileAtWorldTile(int worldTileX, int worldTileY) {
        return chunkManager.getTile(worldTileX, worldTileY);
    }

    public int getTileWidth() { return tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public ChunkManager getChunkManager() { return chunkManager; }

    // ── 渲染 ──────────────────────────────────────

    /**
     * 渲染摄像机可见范围内的瓦片（字符模式）。
     * 从 ChunkManager 获取瓦片数据，仅渲染视口覆盖的瓦片。
     *
     * 无限世界：不再钳制到固定边界，直接按视口范围遍历。
     */
    public void render(Renderer renderer, Camera camera) {
        if (tileWidth == 0 || tileHeight == 0) return; // 尚未初始化

        renderer.setFont(font);

        // 计算可见瓦片范围（世界像素 → 瓦片索引）
        // 使用 floorDiv 正确处理负坐标（整数除法向零截断会导致负坐标偏移）
        // 使用 Camera 的视口尺寸（而非全屏），支持分屏布局
        int startCol = Math.floorDiv(camera.getX(), tileWidth);
        int startRow = Math.floorDiv(camera.getY(), tileHeight);
        int endCol = Math.floorDiv(camera.getX() + camera.getViewportWidth(), tileWidth) + 1;
        int endRow = Math.floorDiv(camera.getY() + camera.getViewportHeight(), tileHeight) + 1;

        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                TileType tile = chunkManager.getTile(c, r);
                if (tile == null) continue;

                // 世界 → 屏幕
                int screenX = c * tileWidth - camera.getX();
                int screenY = r * tileHeight - camera.getY();

                renderer.setColor(tile.getColor());
                // drawText 的 y 是基线位置，需要用 ascent 调整
                int baselineY = screenY + renderer.getFontMetrics().getAscent();
                renderer.drawText(String.valueOf(tile.getChar()), screenX, baselineY);
            }
        }
    }
}
