package com.github.game.cdda.sprite;

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
 * 地形精灵纹理数据 —— 从外部 PNG 文件加载 32×32 地形贴图。
 * <p>
 * 优先从 {@code sprites/tile/} 目录加载 PNG 贴图（由 SD API 批量生成）；
 * 若文件不存在，回退到程序化点阵生成（向后兼容）。
 * </p>
 * <p>
 * 风格：可爱抽象像素画，俯视视角。
 * </p>
 */
public final class TileSpriteData {

    private static final Logger logger = LoggerFactory.getLogger(TileSpriteData.class);

    /** 外部地形贴图目录（相对于项目根目录，用于开发时 SD 生成） */
    private static final String TILE_DIR = "sprites/tile";

    /** classpath 资源路径（打包后从 JAR 加载） */
    private static final String CLASSPATH_TILE_DIR = "/gfx/sprites/tile/";

    private TileSpriteData() {}

    // ==================== 从 PNG 文件加载 ====================

    /**
     * 尝试加载指定地形的 PNG 贴图。
     * 优先从 classpath 资源加载（打包后），回退到外部文件（开发时 SD 生成）。
     *
     * @param id   地形名称（如 "grass"、"tree"）
     * @param size 目标尺寸
     * @return 加载的精灵，文件不存在时返回 null
     */
    private static Sprite loadFromPng(String id, int size) {
        String filename = "tile_" + id + ".png";
        BufferedImage image = null;
        String source = null;

        // 1. 尝试 classpath 加载（打包后 /gfx/sprites/tile/）
        String classpathPath = CLASSPATH_TILE_DIR + filename;
        try (InputStream is = TileSpriteData.class.getResourceAsStream(classpathPath)) {
            if (is != null) {
                image = ImageIO.read(is);
                source = "classpath:" + classpathPath;
            }
        } catch (IOException e) {
            logger.debug("classpath 加载失败: {}", classpathPath);
        }

        // 2. 回退到外部文件（开发时 SD 生成到 sprites/tile/）
        if (image == null) {
            Path path = Paths.get(TILE_DIR, filename);
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
        if (image.getWidth() != size || image.getHeight() != size) {
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, size, size, null);
            g.dispose();
            image = scaled;
        }

        String spriteId = "tile." + id;
        logger.info("从 {} 加载地形贴图: {} ({}x{})", source, spriteId, size, size);
        return new Sprite(spriteId, image);
    }

    // ==================== 程序化回退（向后兼容）====================

    private static Map<Character, Color> grassPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(100, 200, 40));
        return p;
    }

    private static Map<Character, Color> dirtPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(180, 125, 75));
        return p;
    }

    private static Map<Character, Color> sandPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(225, 200, 155));
        return p;
    }

    private static Map<Character, Color> waterPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(90, 160, 240));
        return p;
    }

    private static Map<Character, Color> stonePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(130, 130, 130));
        return p;
    }

    private static Map<Character, Color> tallGrassPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(130, 220, 120));
        return p;
    }

    private static Map<Character, Color> flowerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('p', new Color(220, 50, 180));
        p.put('y', new Color(240, 220, 50));
        p.put('r', new Color(220, 50, 50));
        return p;
    }

    private static Map<Character, Color> bushPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(0, 90, 0));
        p.put('l', new Color(50, 170, 50));
        return p;
    }

    private static Map<Character, Color> treePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(0, 80, 0));
        p.put('l', new Color(30, 150, 30));
        p.put('d', new Color(110, 70, 35));
        return p;
    }

    private static Map<Character, Color> witheredTreePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 65, 30));
        p.put('l', new Color(155, 110, 55));
        p.put('d', new Color(100, 65, 30));
        return p;
    }

    private static Map<Character, Color> reedsPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(80, 175, 70));
        return p;
    }

    private static Map<Character, Color> wallPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(120, 120, 120));
        p.put('l', new Color(195, 195, 195));
        return p;
    }

    private static Map<Character, Color> fencePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 55, 20));
        p.put('d', new Color(139, 69, 19));
        p.put('m', new Color(170, 100, 45));
        return p;
    }

    private static Map<Character, Color> doorPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(110, 60, 25));
        p.put('l', new Color(180, 110, 55));
        return p;
    }

    private static Map<Character, Color> floorPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(110, 110, 110));
        p.put('l', new Color(165, 165, 165));
        return p;
    }

    private static Map<Character, Color> witheredBushPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(95, 70, 40));
        p.put('l', new Color(155, 120, 70));
        return p;
    }

    private static Map<Character, Color> deadGrassPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(200, 178, 118));
        return p;
    }

    private static Map<Character, Color> mudPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(145, 120, 85));
        return p;
    }

    /** 纹理叠加图案（省略，仅用于回退） */
    private static final String[] EMPTY_OVERLAY = new String[32];
    static {
        for (int i = 0; i < 32; i++) EMPTY_OVERLAY[i] = "................................";
    }

    // ==================== 公开 API ====================

    /**
     * 生成所有地形精灵。
     * <p>
     * 优先从 {@code sprites/tile/tile_<name>.png} 加载；
     * 文件不存在时回退到程序化点阵生成。
     * </p>
     *
     * @return ID → Sprite 映射（ID 格式：{@code tile.<name>}）
     */
    public static Map<String, Sprite> createAllTileSprites() {
        Map<String, Sprite> sprites = new HashMap<>();
        int size = 32;

        // 所有地形类型
        String[] tileTypes = {
                "grass", "dirt", "sand", "water", "stone",
                "tree", "bush", "flower", "tall_grass",
                "wall", "fence", "door", "floor",
                "reeds", "mud",
                "withered_tree", "withered_bush", "dead_grass",
                "rock"
        };

        for (String type : tileTypes) {
            String spriteId = "tile." + type;

            // 1. 尝试从 PNG 加载
            Sprite fromPng = loadFromPng(type, size);
            if (fromPng != null) {
                sprites.put(spriteId, fromPng);
                continue;
            }

            // 2. 回退到程序化生成
            logger.debug("PNG 不存在，回退到程序化生成: {}", spriteId);
            switch (type) {
                case "grass":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(76, 180, 0), EMPTY_OVERLAY, grassPalette(), size));
                    break;
                case "dirt":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(160, 110, 60), EMPTY_OVERLAY, dirtPalette(), size));
                    break;
                case "sand":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(210, 185, 140), EMPTY_OVERLAY, sandPalette(), size));
                    break;
                case "water":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(60, 130, 220), EMPTY_OVERLAY, waterPalette(), size));
                    break;
                case "stone":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(160, 160, 160), EMPTY_OVERLAY, stonePalette(), size));
                    break;
                case "tree":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(0, 120, 0), EMPTY_OVERLAY, treePalette(), size));
                    break;
                case "bush":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(0, 128, 0), EMPTY_OVERLAY, bushPalette(), size));
                    break;
                case "flower":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(76, 180, 0), EMPTY_OVERLAY, flowerPalette(), size));
                    break;
                case "tall_grass":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(100, 200, 100), EMPTY_OVERLAY, tallGrassPalette(), size));
                    break;
                case "wall":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(160, 160, 160), EMPTY_OVERLAY, wallPalette(), size));
                    break;
                case "fence":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(76, 180, 0), EMPTY_OVERLAY, fencePalette(), size));
                    break;
                case "door":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(160, 82, 45), EMPTY_OVERLAY, doorPalette(), size));
                    break;
                case "floor":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(128, 128, 128), EMPTY_OVERLAY, floorPalette(), size));
                    break;
                case "reeds":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(60, 130, 220), EMPTY_OVERLAY, reedsPalette(), size));
                    break;
                case "mud":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(120, 100, 70), EMPTY_OVERLAY, mudPalette(), size));
                    break;
                case "withered_tree":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(139, 90, 43), EMPTY_OVERLAY, witheredTreePalette(), size));
                    break;
                case "withered_bush":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(128, 100, 60), EMPTY_OVERLAY, witheredBushPalette(), size));
                    break;
                case "dead_grass":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(180, 160, 100), EMPTY_OVERLAY, deadGrassPalette(), size));
                    break;
                case "rock":
                    sprites.put(spriteId, PixelArt.createTextured(spriteId,
                            new Color(140, 140, 140), EMPTY_OVERLAY, stonePalette(), size));
                    break;
            }
        }

        int pngCount = 0;
        int fallbackCount = 0;
        for (Sprite s : sprites.values()) {
            // 简单判断：如果来自文件，日志已记录
        }

        return sprites;
    }
}
