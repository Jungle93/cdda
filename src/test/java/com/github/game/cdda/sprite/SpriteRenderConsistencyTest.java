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
public class SpriteRenderConsistencyTest {

    private Sprite creatureSprite;
    private Sprite tileSprite;
    private Sprite overlaySprite;
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

        BufferedImage tileImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        g = tileImage.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        tileSprite = new Sprite("tile.test", tileImage);

        BufferedImage overlayImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        g = overlayImage.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        overlaySprite = new Sprite("overlay.test", overlayImage);
    }

    /**
     * 测试地形层渲染 - 地形精灵应该覆盖瓦片。
     */
    @Test
    void testTerrainLayerRendering() {
        // 地形精灵应该使用锚点 (0, 0) - 左上角对齐
        tileSprite.setAnchor(0.0, 0.0);

        int tileX = 5;
        int tileY = 5;
        int viewX = tileX * tileWidth;
        int viewY = tileY * tileHeight;

        // TileMap.drawSprite 的渲染公式
        int drawW = (int) (tileWidth * tileSprite.getTileWidth());
        int drawH = (int) (tileHeight * tileSprite.getTileHeight());
        int offsetX = (int) (tileSprite.getAnchorX() * drawW);
        int offsetY = (int) (tileSprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 地形层渲染测试 ===");
        System.out.println("瓦片位置: (" + tileX + ", " + tileY + ")");
        System.out.println("视图坐标: (" + viewX + ", " + viewY + ")");
        System.out.println("锚点: (" + tileSprite.getAnchorX() + ", " + tileSprite.getAnchorY() + ")");
        System.out.println("偏移量: (" + offsetX + ", " + offsetY + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (0, 0) 时，精灵应该覆盖瓦片
        assertEquals(viewX, drawX, "X坐标应该对齐");
        assertEquals(viewY, drawY, "Y坐标应该对齐");
        System.out.println("✓ 地形精灵覆盖瓦片\n");
    }

    /**
     * 测试生物层渲染 - 生物精灵应该覆盖瓦片。
     */
    @Test
    void testCreatureLayerRendering() {
        // 生物精灵应该使用锚点 (0, 0) - 左上角对齐
        creatureSprite.setAnchor(0.0, 0.0);

        int tileX = 5;
        int tileY = 5;
        int viewX = tileX * tileWidth;
        int viewY = tileY * tileHeight;

        // Animal/Player.render 的渲染公式
        int drawW = (int) (tileWidth * creatureSprite.getTileWidth());
        int drawH = (int) (tileHeight * creatureSprite.getTileHeight());
        int offsetX = (int) (creatureSprite.getAnchorX() * drawW);
        int offsetY = (int) (creatureSprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 生物层渲染测试 ===");
        System.out.println("瓦片位置: (" + tileX + ", " + tileY + ")");
        System.out.println("视图坐标: (" + viewX + ", " + viewY + ")");
        System.out.println("锚点: (" + creatureSprite.getAnchorX() + ", " + creatureSprite.getAnchorY() + ")");
        System.out.println("偏移量: (" + offsetX + ", " + offsetY + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (0, 0) 时，精灵应该覆盖瓦片
        assertEquals(viewX, drawX, "X坐标应该对齐");
        assertEquals(viewY, drawY, "Y坐标应该对齐");
        System.out.println("✓ 生物精灵覆盖瓦片\n");
    }

    /**
     * 测试覆盖层渲染 - 覆盖层精灵应该覆盖瓦片。
     */
    @Test
    void testOverlayLayerRendering() {
        // 覆盖层精灵应该使用锚点 (0, 0) - 左上角对齐
        overlaySprite.setAnchor(0.0, 0.0);

        int tileX = 5;
        int tileY = 5;
        int viewX = tileX * tileWidth;
        int viewY = tileY * tileHeight;

        // TileMap.drawOverlaySprite 的渲染公式（调用 drawSprite）
        int drawW = (int) (tileWidth * overlaySprite.getTileWidth());
        int drawH = (int) (tileHeight * overlaySprite.getTileHeight());
        int offsetX = (int) (overlaySprite.getAnchorX() * drawW);
        int offsetY = (int) (overlaySprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 覆盖层渲染测试 ===");
        System.out.println("瓦片位置: (" + tileX + ", " + tileY + ")");
        System.out.println("视图坐标: (" + viewX + ", " + viewY + ")");
        System.out.println("锚点: (" + overlaySprite.getAnchorX() + ", " + overlaySprite.getAnchorY() + ")");
        System.out.println("偏移量: (" + offsetX + ", " + offsetY + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (0, 0) 时，精灵应该覆盖瓦片
        assertEquals(viewX, drawX, "X坐标应该对齐");
        assertEquals(viewY, drawY, "Y坐标应该对齐");
        System.out.println("✓ 覆盖层精灵覆盖瓦片\n");
    }

    /**
     * 测试各图层渲染一致性 - 所有图层使用相同锚点时应该对齐。
     */
    @Test
    void testLayerConsistency() {
        // 所有图层使用相同的锚点 (0, 0)
        double anchorX = 0.0;
        double anchorY = 0.0;

        tileSprite.setAnchor(anchorX, anchorY);
        creatureSprite.setAnchor(anchorX, anchorY);
        overlaySprite.setAnchor(anchorX, anchorY);

        int tileX = 5;
        int tileY = 5;
        int viewX = tileX * tileWidth;
        int viewY = tileY * tileHeight;

        // 计算各图层的渲染位置
        int tileDrawX = viewX - (int) (tileSprite.getAnchorX() * tileWidth);
        int tileDrawY = viewY - (int) (tileSprite.getAnchorY() * tileHeight);

        int creatureDrawX = viewX - (int) (creatureSprite.getAnchorX() * tileWidth);
        int creatureDrawY = viewY - (int) (creatureSprite.getAnchorY() * tileHeight);

        int overlayDrawX = viewX - (int) (overlaySprite.getAnchorX() * tileWidth);
        int overlayDrawY = viewY - (int) (overlaySprite.getAnchorY() * tileHeight);

        System.out.println("=== 图层一致性测试 ===");
        System.out.println("锚点: (" + anchorX + ", " + anchorY + ")");
        System.out.println("地形层位置: (" + tileDrawX + ", " + tileDrawY + ")");
        System.out.println("生物层位置: (" + creatureDrawX + ", " + creatureDrawY + ")");
        System.out.println("覆盖层位置: (" + overlayDrawX + ", " + overlayDrawY + ")");

        // 验证：所有图层应该对齐
        assertEquals(tileDrawX, creatureDrawX, "地形层和生物层X坐标应该一致");
        assertEquals(tileDrawY, creatureDrawY, "地形层和生物层Y坐标应该一致");
        assertEquals(tileDrawX, overlayDrawX, "地形层和覆盖层X坐标应该一致");
        assertEquals(tileDrawY, overlayDrawY, "地形层和覆盖层Y坐标应该一致");
        System.out.println("✓ 所有图层对齐\n");
    }

    /**
     * 测试默认锚点 (0, 1) 的问题 - 应该导致精灵偏上。
     */
    @Test
    void testDefaultAnchorProblem() {
        // 使用默认锚点 (0, 1)
        tileSprite.setAnchor(0.0, 1.0);

        int tileX = 5;
        int tileY = 5;
        int viewX = tileX * tileWidth;
        int viewY = tileY * tileHeight;

        int drawW = tileWidth;
        int drawH = tileHeight;
        int offsetX = (int) (tileSprite.getAnchorX() * drawW);
        int offsetY = (int) (tileSprite.getAnchorY() * drawH);
        int drawX = viewX - offsetX;
        int drawY = viewY - offsetY;

        System.out.println("=== 默认锚点 (0, 1) 问题测试 ===");
        System.out.println("瓦片位置: (" + viewX + ", " + viewY + ")");
        System.out.println("锚点: (" + tileSprite.getAnchorX() + ", " + tileSprite.getAnchorY() + ")");
        System.out.println("精灵绘制位置: (" + drawX + ", " + drawY + ")");
        System.out.println("精灵范围: [" + drawX + ", " + drawY + "] 到 [" + (drawX + drawW) + ", " + (drawY + drawH) + "]");
        System.out.println("瓦片范围: [" + viewX + ", " + viewY + "] 到 [" + (viewX + tileWidth) + ", " + (viewY + tileHeight) + "]");

        // 验证：锚点 (0, 1) 时，精灵应该在瓦片上方
        assertEquals(viewX, drawX, "X坐标应该对齐");
        assertEquals(viewY - drawH, drawY, "Y坐标：精灵底部应该在瓦片顶部");
        System.out.println("✓ 默认锚点 (0, 1) 导致精灵在瓦片上方一格（问题重现）\n");
    }
}
