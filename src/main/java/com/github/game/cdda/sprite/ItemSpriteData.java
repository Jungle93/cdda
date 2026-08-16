package com.github.game.cdda.sprite;

import com.github.game.cdda.item.registry.ItemRegistry;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.engine.core.sprite.Sprite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 物品精灵纹理数据 —— 从外部 PNG 文件加载物品贴图。
 * <p>
 * 优先从 {@code sprites/items/} 目录加载 PNG 贴图；
 * 若文件不存在，不生成程序化回退（由调用方使用字符替代）。
 * </p>
 * <p>
 * 命名约定：
 * <ul>
 *   <li>文件路径：{@code /gfx/sprites/items/item_<name>.png}（classpath）
 *   <li>文件路径：{@code sprites/items/item_<name>.png}（文件系统）
 *   <li>精灵 ID：{@code item.<name>}（如 "item.axe", "item.bread"）
 * </ul>
 * </p>
 */
public final class ItemSpriteData {

    private static final Logger logger = LoggerFactory.getLogger(ItemSpriteData.class);

    /** 外部物品贴图目录（相对于项目根目录，用于开发时） */
    private static final String ITEM_DIR = "sprites/items";

    /** classpath 资源路径（打包后从 JAR 加载） */
    private static final String CLASSPATH_ITEM_DIR = "/gfx/sprites/items/";

    /** 目标精灵尺寸（像素） */
    private static final int SPRITE_SIZE = 32;

    private ItemSpriteData() {}

    /**
     * 创建所有已注册物品的精灵贴图。
     * 遍历 ItemRegistry 中的所有物品，尝试加载对应的 PNG 贴图。
     *
     * @return 物品精灵映射（ID → Sprite），仅包含成功加载的贴图
     */
    public static Map<String, Sprite> createAllItemSprites() {
        Map<String, Sprite> sprites = new HashMap<>();

        // 遍历所有已注册的ItemType
        for (ItemType itemType : ItemRegistry.getAll()) {
            String name = itemType.getName();
            Sprite sprite = loadFromPng(name);
            if (sprite != null) {
                sprites.put(sprite.getId(), sprite);
            }
        }

        return sprites;
    }

    /**
     * 尝试加载指定物品的 PNG 贴图。
     * 优先从 classpath 资源加载（打包后），回退到外部文件（开发时）。
     *
     * @param name 物品名称（如 "axe", "bread"）
     * @return 加载的精灵，文件不存在时返回 null
     */
    private static Sprite loadFromPng(String name) {
        String filename = "item_" + name + ".png";
        BufferedImage image = null;
        String source = null;

        // 1. 尝试 classpath 加载（打包后 /gfx/sprites/items/）
        String classpathPath = CLASSPATH_ITEM_DIR + filename;
        try (InputStream is = ItemSpriteData.class.getResourceAsStream(classpathPath)) {
            if (is != null) {
                image = ImageIO.read(is);
                source = "classpath:" + classpathPath;
            }
        } catch (IOException e) {
            logger.debug("classpath 加载失败: {}", classpathPath);
        }

        // 2. 回退到外部文件（开发时）
        if (image == null) {
            Path path = Paths.get(ITEM_DIR, filename);
            if (Files.exists(path)) {
                try {
                    image = ImageIO.read(path.toFile());
                    source = path.toString();
                } catch (IOException e) {
                    logger.debug("文件加载失败: {}", path);
                }
            }
        }

        if (image == null) {
            return null;
        }

        // 缩放到目标尺寸（nearest-neighbor 保持像素画风格）
        if (image.getWidth() != SPRITE_SIZE || image.getHeight() != SPRITE_SIZE) {
            BufferedImage scaled = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, SPRITE_SIZE, SPRITE_SIZE, null);
            g.dispose();
            image = scaled;
        }

        String spriteId = "item." + name;
        logger.info("从 {} 加载物品贴图: {} ({}x{})", source, spriteId, SPRITE_SIZE, SPRITE_SIZE);
        Sprite sprite = new Sprite(spriteId, image);
        // 设置锚点为左上角 (0, 0)：精灵的左上角对齐瓦片位置，精灵覆盖瓦片
        sprite.setAnchor(0.0, 0.0);
        return sprite;
    }
}
