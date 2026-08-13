package com.github.game.engine.core.sprite;

import java.awt.image.BufferedImage;

/**
 * 单个精灵（Sprite）—— 持有图像数据及元信息。
 * <p>
 * 不可变数据对象，由 {@link SpritePack} 管理和提供。
 * </p>
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
}
