package com.github.game.engine.core.sprite;

import java.awt.image.BufferedImage;

/**
 * 单个精灵（Sprite）—— 持有图像数据及元信息。
 * <p>
 * 支持可变尺寸渲染：通过 {@link #setTileSize(double, double)} 设置精灵占据的瓦片数，
 * 通过 {@link #setAnchor(double, double)} 设置渲染锚点。
 * </p>
 *
 * <p>默认行为（tileWidth=1, tileHeight=1, anchor=left-bottom）等价于旧的单瓦片渲染，
 * 完全向后兼容。
 *
 * <p>示例：一棵松树贴图 64×80 像素，基准瓦片 32×32：
 * <pre>
 * Sprite sprite = new Sprite("vegetation.pine", image);
 * sprite.setTileSize(2.0, 2.5);  // 占 2 格宽 × 2.5 格高
 * sprite.setAnchor(0, 1);         // 锚定左下角（默认）
 * </pre>
 *
 * @see SpritePack
 */
public class Sprite {

    /** 精灵唯一标识 */
    private final String id;

    /** 精灵图像（ARGB 格式） */
    private final BufferedImage image;

    /** 精灵宽度（像素） */
    private final int width;

    /** 精灵高度（像素） */
    private final int height;

    // ── 可变尺寸渲染参数 ──

    /** 精灵占据的瓦片宽度（以基准瓦片为单位，默认 1.0） */
    private double tileWidth = 1.0;

    /** 精灵占据的瓦片高度（以基准瓦片为单位，默认 1.0） */
    private double tileHeight = 1.0;

    /**
     * 水平锚点（0.0=左边缘, 0.5=中心, 1.0=右边缘）。
     * 渲染时该点对齐到瓦片的左边缘。默认 0（左对齐）。
     */
    private double anchorX = 0;

    /**
     * 垂直锚点（0.0=上边缘, 0.5=中心, 1.0=下边缘）。
     * 渲染时该点对齐到瓦片的上边缘。默认 1（底部对齐，精灵向上延伸）。
     */
    private double anchorY = 1.0;

    /**
     * 构造精灵对象。
     *
     * @param id     唯一标识（如 "creature.wolf"、"tile.grass"）
     * @param image  精灵图像，不可为 null
     */
    public Sprite(String id, BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("精灵图像不能为空: " + id);
        }
        this.id = id;
        this.image = image;
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    // ── 基本属性 ──

    /** 获取精灵唯一标识 */
    public String getId() {
        return id;
    }

    /** 获取精灵图像 */
    public BufferedImage getImage() {
        return image;
    }

    /** 获取精灵宽度（像素） */
    public int getWidth() {
        return width;
    }

    /** 获取精灵高度（像素） */
    public int getHeight() {
        return height;
    }

    // ── 可变尺寸 ──

    /**
     * 设置精灵占据的瓦片数。
     * 渲染时将按此尺寸绘制，而非固定的 1×1 瓦片。
     *
     * @param tileWidth  瓦片宽度（如 2.0 表示占 2 格宽）
     * @param tileHeight 瓦片高度（如 2.5 表示占 2.5 格高）
     */
    public void setTileSize(double tileWidth, double tileHeight) {
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    /**
     * 设置渲染锚点。
     * 锚点定义精灵图像上的哪个位置对齐到瓦片的左上角。
     *
     * <p>常用锚点：
     * <ul>
     *   <li>(0, 1) — 左下角：精灵从瓦片位置向上向右延伸（树木、建筑）</li>
     *   <li>(0.5, 1) — 底部中心：精灵从瓦片中心向上延伸（角色、柱子）</li>
     *   <li>(0.5, 0.5) — 中心：精灵以瓦片中心为基准（物品、特效）</li>
     *   <li>(0, 0) — 左上角：精灵从瓦片位置向下向右延伸（当前默认行为）</li>
     * </ul>
     *
     * @param anchorX 水平锚点（0.0~1.0）
     * @param anchorY 垂直锚点（0.0~1.0）
     */
    public void setAnchor(double anchorX, double anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    /** 获取精灵占据的瓦片宽度 */
    public double getTileWidth() { return tileWidth; }

    /** 获取精灵占据的瓦片高度 */
    public double getTileHeight() { return tileHeight; }

    /** 获取水平锚点（0.0~1.0） */
    public double getAnchorX() { return anchorX; }

    /** 获取垂直锚点（0.0~1.0） */
    public double getAnchorY() { return anchorY; }

    /**
     * 是否为多瓦片精灵（占据超过 1×1 瓦片）。
     *
     * @return true 如果 tileWidth > 1 或 tileHeight > 1
     */
    public boolean isMultiTile() {
        return tileWidth > 1.0 || tileHeight > 1.0;
    }
}
