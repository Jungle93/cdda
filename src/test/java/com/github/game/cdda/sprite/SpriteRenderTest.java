package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 精灵渲染坐标测试 - 验证各图层渲染位置是否一致。
 */
public class SpriteRenderTest {

    private Sprite creatureSprite;
    private int tileWidth = 32;
    private int tileHeight = 32;

    @BeforeEach
    void setUp() {
        // 创建测试精灵（32x32）
        BufferedImage creatureImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = creatureImage.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        creatureSprite = new Sprite("creature.test", creatureImage);
    }

    /**
     * 测试默认锚点 (0, 1) - 左下角对齐。
     */
    @Test
    void testDefaultAnchor() {
        creatureSprite.setAnchor(0.0, 1.0);  // 默认锚点

        int tileX = 5;
        int tileY = 5;
        int worldX = tileX * tileWidth;
        int worldY = tileY * tileHeight;

        // 简化：假设摄像机在 (0, 0)，缩放为 1.0
        int viewX = worldX;
        int viewY = worldY;

        // 计算精灵渲染位置（使用当前公式）
        int drawW = (int) (tileWidth * creatureSprite.getTileWidth());
        int drawH = (int) (tileHeight * creatureSprite.getTileHeight());
        int offsetX = (int) (creatureSprite.getAnchorX() * drawW);
        int offsetY = (int) (creatureSprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 默认锚点 (0, 1) ===");
        System.out.println("瓦片位置: (" + tileX + ", " + tileY + ")");
        System.out.println("世界坐标: (" + worldX + ", " + worldY + ")");
        System.out.println("视图坐标: (" + viewX + ", " + viewY + ")");
        System.out.println("偏移量: (" + offsetX + ", " + offsetY + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (0, 1) 时，精灵底部应该在瓦片顶部
        assertEquals(viewX, drawX, "X坐标应该对齐");
        assertEquals(viewY - drawH, drawY, "Y坐标：精灵底部应该在瓦片顶部");
        System.out.println("✓ 精灵底部对齐到瓦片顶部（精灵在瓦片上方）\n");
    }

    /**
     * 测试锚点 (0, 0) - 左上角对齐。
     */
    @Test
    void testTopLeftAnchor() {
        creatureSprite.setAnchor(0.0, 0.0);

        int tileX = 5;
        int tileY = 5;
        int worldX = tileX * tileWidth;
        int worldY = tileY * tileHeight;

        // 简化：假设摄像机在 (0, 0)，缩放为 1.0
        int viewX = worldX;
        int viewY = worldY;

        int drawW = (int) (tileWidth * creatureSprite.getTileWidth());
        int drawH = (int) (tileHeight * creatureSprite.getTileHeight());
        int offsetX = (int) (creatureSprite.getAnchorX() * drawW);
        int offsetY = (int) (creatureSprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 锚点 (0, 0) ===");
        System.out.println("瓦片位置: (" + tileX + ", " + tileY + ")");
        System.out.println("视图坐标: (" + viewX + ", " + viewY + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (0, 0) 时，精灵顶部应该在瓦片顶部
        assertEquals(viewX, drawX, "X坐标应该对齐");
        assertEquals(viewY, drawY, "Y坐标：精灵顶部应该在瓦片顶部");
        System.out.println("✓ 精灵顶部对齐到瓦片顶部（精灵覆盖瓦片）\n");
    }

    /**
     * 测试锚点 (1, 0) - 右上角对齐。
     */
    @Test
    void testTopRightAnchor() {
        creatureSprite.setAnchor(1.0, 0.0);

        int tileX = 5;
        int tileY = 5;
        int worldX = tileX * tileWidth;
        int worldY = tileY * tileHeight;

        // 简化：假设摄像机在 (0, 0)，缩放为 1.0
        int viewX = worldX;
        int viewY = worldY;

        int drawW = (int) (tileWidth * creatureSprite.getTileWidth());
        int drawH = (int) (tileHeight * creatureSprite.getTileHeight());
        int offsetX = (int) (creatureSprite.getAnchorX() * drawW);
        int offsetY = (int) (creatureSprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 锚点 (1, 0) ===");
        System.out.println("瓦片位置: (" + tileX + ", " + tileY + ")");
        System.out.println("视图坐标: (" + viewX + ", " + viewY + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (1, 0) 时，精灵右上角应该在瓦片左上角
        assertEquals(viewX - drawW, drawX, "X坐标：精灵右边应该在瓦片左边");
        assertEquals(viewY, drawY, "Y坐标：精灵顶部应该在瓦片顶部");
        System.out.println("✓ 精灵右上角对齐到瓦片左上角（精灵在瓦片左方）\n");
    }

    /**
     * 测试锚点 (0.5, 0.5) - 中心对齐。
     */
    @Test
    void testCenterAnchor() {
        creatureSprite.setAnchor(0.5, 0.5);

        int tileX = 5;
        int tileY = 5;
        int worldX = tileX * tileWidth;
        int worldY = tileY * tileHeight;

        // 简化：假设摄像机在 (0, 0)，缩放为 1.0
        int viewX = worldX;
        int viewY = worldY;

        int drawW = (int) (tileWidth * creatureSprite.getTileWidth());
        int drawH = (int) (tileHeight * creatureSprite.getTileHeight());
        int offsetX = (int) (creatureSprite.getAnchorX() * drawW);
        int offsetY = (int) (creatureSprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 锚点 (0.5, 0.5) ===");
        System.out.println("瓦片位置: (" + tileX + ", " + tileY + ")");
        System.out.println("视图坐标: (" + viewX + ", " + viewY + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (0.5, 0.5) 时，精灵中心应该在瓦片左上角
        assertEquals(viewX - drawW / 2, drawX, "X坐标：精灵中心应该在瓦片左边");
        assertEquals(viewY - drawH / 2, drawY, "Y坐标：精灵中心应该在瓦片上边");
        System.out.println("✓ 精灵中心对齐到瓦片左上角\n");
    }

    /**
     * 对比不同锚点的渲染位置。
     */
    @Test
    void compareAnchorPositions() {
        int tileX = 5;
        int tileY = 5;
        int worldX = tileX * tileWidth;
        int worldY = tileY * tileHeight;

        // 简化：假设摄像机在 (0, 0)，缩放为 1.0
        int viewX = worldX;
        int viewY = worldY;

        System.out.println("\n=== 各锚点渲染位置对比 ===");
        System.out.println("瓦片位置: (" + viewX + ", " + viewY + ")");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]\n");

        double[][] anchors = {
            {0.0, 0.0},  // 左上
            {0.5, 0.0},  // 上中
            {1.0, 0.0},  // 右上
            {0.0, 0.5},  // 左中
            {0.5, 0.5},  // 中心
            {1.0, 0.5},  // 右中
            {0.0, 1.0},  // 左下
            {0.5, 1.0},  // 下中
            {1.0, 1.0},  // 右下
        };

        for (double[] anchor : anchors) {
            creatureSprite.setAnchor(anchor[0], anchor[1]);

            int drawW = (int) (tileWidth * creatureSprite.getTileWidth());
            int drawH = (int) (tileHeight * creatureSprite.getTileHeight());
            int offsetX = (int) (creatureSprite.getAnchorX() * drawW);
            int offsetY = (int) (creatureSprite.getAnchorY() * drawH);
            int drawX = viewX - offsetX;
            int drawY = viewY - offsetY;

            System.out.printf("锚点 (%.1f, %.1f): 精灵 [%3d, %3d] 到 [%3d, %3d]",
                anchor[0], anchor[1], drawX, drawY, drawX + drawW, drawY + drawH);

            // 判断精灵与瓦片的关系
            if (drawY + drawH <= viewY) {
                System.out.print(" (瓦片上方)");
            } else if (drawY >= viewY + tileHeight) {
                System.out.print(" (瓦片下方)");
            } else if (drawX + drawW <= viewX) {
                System.out.print(" (瓦片左方)");
            } else if (drawX >= viewX + tileWidth) {
                System.out.print(" (瓦片右方)");
            } else {
                System.out.print(" (与瓦片重叠)");
            }
            System.out.println();
        }
    }
}
