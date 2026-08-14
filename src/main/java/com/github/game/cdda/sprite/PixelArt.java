package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * 点阵像素画工具 —— 从字符串模式和调色板生成 {@link Sprite}。
 * <p>
 * 使用方式：
 * <pre>
 * String[] pattern = {
 *     "................",
 *     "....aaa.........",
 *     "...abba.........",
 *     // ... 共 16 行
 * };
 * Map&lt;Character, Color&gt; palette = Map.of(
 *     'a', new Color(100, 100, 100),
 *     'b', new Color(200, 200, 200)
 * );
 * Sprite sprite = PixelArt.createSprite("creature.wolf", pattern, palette);
 * </pre>
 * 其中 {@code '.'} 表示透明像素，其他字符映射到调色板中的颜色。
 * </p>
 */
public final class PixelArt {

    /** 默认精灵尺寸 */
    public static final int DEFAULT_SIZE = 32;

    /** 透明像素标记字符 */
    public static final char TRANSPARENT = '.';

    private PixelArt() {}

    /**
     * 从字符串模式和调色板创建精灵。
     * <p>
     * 模式数组的每行长度应一致（推荐 16 字符），行数决定图像高度（推荐 16 行）。
     * 字符 {@code '.'} 表示透明，其他字符在调色板中查找对应颜色。
     * 若字符未在调色板中定义，该像素视为透明。
     * </p>
     *
     * @param id       精灵唯一标识
     * @param pattern  字符串模式数组，每行为一行像素
     * @param palette  字符到颜色的映射
     * @return 生成的精灵对象
     */
    public static Sprite createSprite(String id, String[] pattern, Map<Character, Color> palette) {
        int height = pattern.length;
        int width = 0;
        for (String row : pattern) {
            width = Math.max(width, row.length());
        }
        if (width == 0 || height == 0) {
            throw new IllegalArgumentException("精灵模式不能为空: " + id);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            String row = pattern[y];
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == TRANSPARENT) {
                    continue;
                }
                Color color = palette.get(c);
                if (color != null) {
                    image.setRGB(x, y, color.getRGB());
                }
                // 未在调色板中定义的字符视为透明
            }
        }

        return new Sprite(id, image);
    }

    /**
     * 使用指定尺寸从模式创建精灵，自动填充或截断到目标尺寸。
     *
     * @param id       精灵唯一标识
     * @param pattern  字符串模式数组
     * @param palette  字符到颜色的映射
     * @param size     目标尺寸（宽=高）
     * @return 生成的精灵对象
     */
    public static Sprite createSprite(String id, String[] pattern, Map<Character, Color> palette, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < size && y < pattern.length; y++) {
            String row = pattern[y];
            for (int x = 0; x < size && x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == TRANSPARENT) {
                    continue;
                }
                Color color = palette.get(c);
                if (color != null) {
                    image.setRGB(x, y, color.getRGB());
                }
            }
        }

        return new Sprite(id, image);
    }

    /**
     * 创建纯色填充的精灵（用于地形等简单纹理）。
     *
     * @param id     精灵唯一标识
     * @param color  填充颜色
     * @param size   尺寸（宽=高）
     * @return 生成的精灵对象
     */
    public static Sprite createSolid(String id, Color color, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int rgb = color.getRGB();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return new Sprite(id, image);
    }

    /**
     * 从纹理模式创建精灵，支持多层叠加。
     * <p>
     * 基础层为纯色填充，图案层叠加在上面（非透明像素覆盖）。
     * </p>
     *
     * @param id         精灵唯一标识
     * @param baseColor  基础底色
     * @param overlay    叠加图案（同 createSprite 格式）
     * @param palette    叠加图案的调色板
     * @param size       尺寸（宽=高）
     * @return 生成的精灵对象
     */
    public static Sprite createTextured(String id, Color baseColor, String[] overlay,
                                         Map<Character, Color> palette, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        // 基础填充
        int baseRgb = baseColor.getRGB();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, baseRgb);
            }
        }

        // 叠加图案
        for (int y = 0; y < size && y < overlay.length; y++) {
            String row = overlay[y];
            for (int x = 0; x < size && x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == TRANSPARENT) {
                    continue;
                }
                Color color = palette.get(c);
                if (color != null) {
                    image.setRGB(x, y, color.getRGB());
                }
            }
        }

        return new Sprite(id, image);
    }
}
