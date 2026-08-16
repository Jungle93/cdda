package com.github.game.cdda.world;

import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.sprite.Sprite;
import com.github.game.engine.core.sprite.SpriteManager;
import com.github.game.cdda.world.chunk.ChunkManager;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * 瓦片地图（渲染层）。
 * 从 ChunkManager 获取瓦片数据，负责可见区域的渲染。
 * 支持两种模式：图形包精灵渲染 和 ASCII 字符渲染。
 *
 * <p>支持可变尺寸精灵渲染：精灵可通过 {@link Sprite#setTileSize(double, double)}
 * 设置占据多格（如松树占 2 格宽 × 2.5 格高），通过 {@link Sprite#setAnchor(double, double)}
 * 设置渲染锚点（默认左下角，精灵向上延伸）。
 *
 * <p>植被精灵优先使用物种 ID 查找（"vegetation.oak"），回退到瓦片类型精灵（"tile.tree"）。
 *
 * <p><b>按 tile 图层循环</b>（参考 Cataclysm-DDA 设计）：
 * 对每个可见 tile 按固定图层顺序渲染 —
 * 地形 → 地面物品 → 生物 → 覆盖层（植被），
 * 使遮挡关系正确：同行内物品在生物之下，生物在植被之下。
 */
public class TileMap {

    /**
     * 瓦片图层渲染回调接口。
     *
     * <p>由 TileMap 在逐 tile 图层循环中调用，将物品和生物的渲染
     * 委托给上层（GameScene），实现关注点分离。
     *
     * <p>图层顺序：地形（TileMap 内部） → 地面物品 → 生物 → 覆盖层（TileMap 内部）。
     * 行优先（从上到下）迭代确保前方（下方行）的图层正确遮挡后方。
     */
    public interface TileLayerRenderer {
        /**
         * 绘制指定瓦片上的地面物品（图层2）。
         *
         * @param renderer    渲染器
         * @param camera      摄像机
         * @param tileCol     瓦片列坐标
         * @param tileRow     瓦片行坐标
         * @param scaledTileW 缩放后单格宽度（像素）
         * @param scaledTileH 缩放后单格高度（像素）
         */
        void drawGroundItems(Renderer renderer, Camera camera,
                             int tileCol, int tileRow,
                             int scaledTileW, int scaledTileH);

        /**
         * 绘制指定瓦片上的生物（图层3）。
         * 实现方应排除玩家（玩家由 GameScene 单独渲染在生物层之上）。
         *
         * @param renderer    渲染器
         * @param camera      摄像机
         * @param tileCol     瓦片列坐标
         * @param tileRow     瓦片行坐标
         * @param scaledTileW 缩放后单格宽度（像素）
         * @param scaledTileH 缩放后单格高度（像素）
         */
        void drawCreatures(Renderer renderer, Camera camera,
                           int tileCol, int tileRow,
                           int scaledTileW, int scaledTileH);
    }

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
     * 通过 Renderer 的 FontMetrics 测量字符宽高（ASCII 模式）。
     */
    public void initTileSize(Renderer renderer) {
        renderer.setFont(font);
        this.tileWidth = renderer.getFontMetrics().charWidth('.');
        this.tileHeight = renderer.getFontMetrics().getHeight();
    }

    /**
     * 初始化瓦片像素尺寸（精灵模式）。
     * 使用图形包的固定瓦片尺寸，不依赖字体度量。
     *
     * @param width  瓦片像素宽度
     * @param height 瓦片像素高度
     */
    public void initTileSize(int width, int height) {
        this.tileWidth = width;
        this.tileHeight = height;
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
     * 渲染摄像机可见范围内的瓦片（仅地形 + 覆盖层）。
     * 不渲染地面物品和生物，适用于不需要完整图层循环的场景。
     */
    public void render(Renderer renderer, Camera camera) {
        render(renderer, camera, null);
    }

    /**
     * 渲染摄像机可见范围内的瓦片（按 tile 图层循环）。
     *
     * <p><b>图层顺序</b>（逐 tile，行优先）：
     * <ol>
     *   <li>地形（地面层）</li>
     *   <li>地面物品（通过 layerRenderer 回调）</li>
     *   <li>生物（通过 layerRenderer 回调）</li>
     * </ol>
     * 之后执行<b>覆盖层 pass</b>（扩展范围，含多瓦片植被精灵去重）。
     *
     * <p><b>遮挡正确性</b>：同一 tile 内物品在生物之下，生物在植被之下；
     * 行优先迭代确保前方（下方行）的元素遮挡后方。
     *
     * @param renderer      渲染器
     * @param camera        摄像机
     * @param layerRenderer 图层回调（null 则仅渲染地形 + 覆盖层）
     */
    public void render(Renderer renderer, Camera camera, TileLayerRenderer layerRenderer) {
        if (tileWidth == 0 || tileHeight == 0) return; // 尚未初始化

        boolean useSprites = SpriteManager.hasActivePack();
        if (!useSprites) {
            renderer.setFont(font);
        }

        // 缩放因子：精灵模式下瓦片绘制尺寸随缩放变化
        double zoom = camera.getZoom();
        int scaledTileW = (int) (tileWidth * zoom);
        int scaledTileH = (int) (tileHeight * zoom);

        // 计算可见瓦片范围（使用缩放后的视口尺寸）
        // 使用 floorDiv 正确处理负坐标
        int zoomedVW = camera.getZoomedViewportWidth();
        int zoomedVH = camera.getZoomedViewportHeight();
        int startCol = Math.floorDiv(camera.getX(), tileWidth);
        int startRow = Math.floorDiv(camera.getY(), tileHeight);
        int endCol = Math.floorDiv(camera.getX() + zoomedVW, tileWidth) + 1;
        int endRow = Math.floorDiv(camera.getY() + zoomedVH, tileHeight) + 1;

        // 扩展可见范围：为多瓦片精灵预留空间（最多扩展 3 格，防止边缘裁剪）
        int margin = 3;
        int renderStartCol = startCol - margin;
        int renderStartRow = startRow - margin;
        int renderEndCol = endCol + margin;
        int renderEndRow = endRow + margin;

        // 记录已渲染的多瓦片精灵位置，避免重复绘制
        Set<String> renderedMultiTile = useSprites ? new HashSet<>() : null;

        // ── 按 tile 图层循环（行优先，从上到下） ──
        // 图层顺序：地形 → 地面物品 → 生物
        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                int screenX = camera.toViewX(c * tileWidth);
                int screenY = camera.toViewY(r * tileHeight);

                // 图层1：地形
                drawGroundTile(renderer, c, r, screenX, screenY,
                        scaledTileW, scaledTileH, useSprites);

                if (layerRenderer != null) {
                    // 图层2：地面物品
                    layerRenderer.drawGroundItems(renderer, camera,
                            c, r, scaledTileW, scaledTileH);
                    // 图层3：生物
                    layerRenderer.drawCreatures(renderer, camera,
                            c, r, scaledTileW, scaledTileH);
                }
            }
        }

        // ── 覆盖层 pass（扩展范围，含多瓦片植被精灵） ──
        for (int r = renderStartRow; r <= renderEndRow; r++) {
            for (int c = renderStartCol; c <= renderEndCol; c++) {
                TileType tile = chunkManager.getTile(c, r);
                if (tile == null || !tile.isOverlay()) continue;

                // 已被多瓦片精灵覆盖 → 跳过
                if (renderedMultiTile != null && renderedMultiTile.contains(c + "," + r)) {
                    continue;
                }

                int screenX = camera.toViewX(c * tileWidth);
                int screenY = camera.toViewY(r * tileHeight);

                if (useSprites) {
                    // 优先使用植被物种精灵（支持物种差异化和多瓦片）
                    Sprite sprite = getVegetationSprite(c, r, tile);
                    if (sprite != null) {
                        drawOverlaySprite(renderer, sprite, screenX, screenY,
                                scaledTileW, scaledTileH, c, r,
                                renderedMultiTile, camera, zoom);
                        continue;
                    }
                    // 回退：使用瓦片类型精灵
                    String spriteId = "tile." + tile.getName();
                    sprite = SpriteManager.getSprite(spriteId);
                    if (sprite != null) {
                        drawSprite(renderer, sprite, screenX, screenY,
                                scaledTileW, scaledTileH);
                        continue;
                    }
                }

                // ASCII 字符渲染覆盖层
                renderer.setColor(tile.getColor());
                int baselineY = screenY + renderer.getFontMetrics().getAscent();
                renderer.drawText(String.valueOf(tile.getChar()), screenX, baselineY);
            }
        }
    }

    // ── 内部渲染辅助方法 ──────────────────────────────────

    /**
     * 绘制单个瓦片的地面层（精灵或 ASCII）。
     * 覆盖层瓦片会渲染其下方的地面层。
     */
    private void drawGroundTile(Renderer renderer, int col, int row,
                                int screenX, int screenY,
                                int scaledTileW, int scaledTileH,
                                boolean useSprites) {
        TileType tile = chunkManager.getTile(col, row);
        if (tile == null) return;

        // 覆盖层瓦片：渲染其下方的地面层
        TileType groundTile = tile.isOverlay()
                ? chunkManager.getGroundTile(col, row)
                : tile;
        if (groundTile == null) groundTile = tile;

        if (useSprites) {
            String spriteId = "tile." + groundTile.getName();
            Sprite sprite = SpriteManager.getSprite(spriteId);
            if (sprite != null) {
                drawSprite(renderer, sprite, screenX, screenY,
                        scaledTileW, scaledTileH);
                return;
            }
        }

        // ASCII 字符渲染（回退）— 字号不缩放，保持可读性
        renderer.setColor(groundTile.getColor());
        int baselineY = screenY + renderer.getFontMetrics().getAscent();
        renderer.drawText(String.valueOf(groundTile.getChar()), screenX, baselineY);
    }

    // ── 精灵渲染辅助方法 ──────────────────────────────────

    /**
     * 获取瓦片处的植被精灵。
     * 优先查找物种精灵（"vegetation.{speciesId}"），无则返回 null。
     *
     * @param tileX 瓦片 X 坐标
     * @param tileY 瓦片 Y 坐标
     * @param tile  瓦片类型
     * @return 植被精灵，无则 null
     */
    private Sprite getVegetationSprite(int tileX, int tileY, TileType tile) {
        String speciesId = chunkManager.getVegetation(tileX, tileY);
        if (speciesId == null) return null;
        return SpriteManager.getSprite("vegetation." + speciesId);
    }

    /**
     * 绘制精灵（支持可变尺寸和锚点）。
     *
     * <p>根据精灵的 tileWidth/tileHeight 计算绘制尺寸，
     * 根据 anchorX/anchorY 计算绘制位置偏移。
     *
     * @param renderer    渲染器
     * @param sprite      精灵
     * @param tileScreenX 瓦片屏幕 X（像素）
     * @param tileScreenY 瓦片屏幕 Y（像素）
     * @param scaledTileW 缩放后单格宽度（像素）
     * @param scaledTileH 缩放后单格高度（像素）
     */
    private void drawSprite(Renderer renderer, Sprite sprite,
                            int tileScreenX, int tileScreenY,
                            int scaledTileW, int scaledTileH) {
        // 计算精灵的绘制尺寸（基于 tileWidth/tileHeight）
        int drawW = (int) (scaledTileW * sprite.getTileWidth());
        int drawH = (int) (scaledTileH * sprite.getTileHeight());

        // 计算锚点偏移（精灵图像上的锚点对齐到瓦片位置）
        int offsetX = (int) (sprite.getAnchorX() * drawW);
        int offsetY = (int) (sprite.getAnchorY() * drawH);

        int drawX = tileScreenX - offsetX;
        int drawY = tileScreenY - offsetY;

        renderer.drawImage(sprite.getImage(), drawX, drawY, drawW, drawH);
    }

    /**
     * 绘制覆盖层精灵（含多瓦片标记）。
     * 对于多瓦片精灵，记录其占据的所有瓦片位置以避免重复绘制。
     */
    private void drawOverlaySprite(Renderer renderer, Sprite sprite,
                                   int tileScreenX, int tileScreenY,
                                   int scaledTileW, int scaledTileH,
                                   int tileCol, int tileRow,
                                   Set<String> renderedMultiTile,
                                   Camera camera, double zoom) {
        // 先正常绘制
        drawSprite(renderer, sprite, tileScreenX, tileScreenY, scaledTileW, scaledTileH);

        // 多瓦片精灵：标记其占据的所有瓦片位置
        if (sprite.isMultiTile() && renderedMultiTile != null) {
            int spanW = (int) Math.ceil(sprite.getTileWidth());
            int spanH = (int) Math.ceil(sprite.getTileHeight());

            // 根据锚点计算精灵占据的瓦片范围
            // 锚点(0,1)=左下角：精灵向右延伸 spanW 格，向上延伸 spanH 格
            int anchorTileColOffset = (int) (sprite.getAnchorX() * spanW);
            int anchorTileRowOffset = (int) (sprite.getAnchorY() * spanH);

            for (int dr = -anchorTileRowOffset; dr < spanH - anchorTileRowOffset; dr++) {
                for (int dc = -anchorTileColOffset; dc < spanW - anchorTileColOffset; dc++) {
                    if (dr == 0 && dc == 0) continue; // 跳过锚点本身
                    renderedMultiTile.add((tileCol + dc) + "," + (tileRow + dr));
                }
            }
        }
    }
}
