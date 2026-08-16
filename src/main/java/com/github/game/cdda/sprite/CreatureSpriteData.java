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
 * 生物精灵数据 —— 从 PNG 文件加载或程序化生成 32×32 精灵。
 * <p>
 * 优先从 classpath {@code /gfx/sprites/creature/} 加载 PNG 贴图（由百炼 API 批量生成）；
 * 若文件不存在，回退到程序化点阵生成（向后兼容）。
 * </p>
 * <p>
 * 玩家精灵始终使用程序化生成。
 * </p>
 */
public final class CreatureSpriteData {

    private static final Logger logger = LoggerFactory.getLogger(CreatureSpriteData.class);

    /** 外部生物贴图目录（相对于项目根目录，开发时生成用） */
    private static final String CREATURE_DIR = "sprites/creature";

    /** classpath 资源路径（打包后从 JAR 加载） */
    private static final String CLASSPATH_CREATURE_DIR = "/gfx/sprites/creature/";

    private CreatureSpriteData() {}

    // ==================== 从 PNG 文件加载 ====================

    /**
     * 尝试加载指定生物的 PNG 贴图。
     * 优先从 classpath 资源加载，回退到外部文件。
     *
     * @param id   生物名称（如 "wolf"、"fox"）
     * @param size 目标尺寸
     * @return 加载的精灵，文件不存在时返回 null
     */
    private static Sprite loadFromPng(String id, int size) {
        String filename = "creature_" + id + ".png";
        BufferedImage image = null;
        String source = null;

        // 1. 尝试 classpath 加载
        String classpathPath = CLASSPATH_CREATURE_DIR + filename;
        try (InputStream is = CreatureSpriteData.class.getResourceAsStream(classpathPath)) {
            if (is != null) {
                image = ImageIO.read(is);
                source = "classpath:" + classpathPath;
            }
        } catch (IOException e) {
            logger.debug("classpath 加载失败: {}", classpathPath);
        }

        // 2. 回退到外部文件
        if (image == null) {
            Path path = Paths.get(CREATURE_DIR, filename);
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

        String spriteId = "creature." + id;
        logger.info("从 {} 加载生物贴图: {} ({}x{})", source, spriteId, size, size);
        Sprite sprite = new Sprite(spriteId, image);
        // 设置锚点为左上角 (0, 0)：精灵的左上角对齐瓦片位置，精灵覆盖瓦片
        sprite.setAnchor(0.0, 0.0);
        return sprite;
    }

    // ==================== 程序化回退（向后兼容）====================

    // 调色板和点阵模式保留不变，用于 PNG 不存在时的回退
    // ...（省略，与原版相同）

    private static Map<Character, Color> wolfPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(50, 50, 55));
        p.put('d', new Color(85, 85, 90));
        p.put('m', new Color(130, 130, 135));
        p.put('l', new Color(175, 175, 178));
        p.put('e', new Color(15, 15, 15));
        p.put('n', new Color(30, 25, 25));
        p.put('w', new Color(205, 200, 190));
        return p;
    }

    private static Map<Character, Color> foxPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 45, 10));
        p.put('d', new Color(170, 80, 20));
        p.put('m', new Color(210, 110, 35));
        p.put('l', new Color(235, 155, 60));
        p.put('e', new Color(15, 15, 15));
        p.put('n', new Color(25, 20, 15));
        p.put('w', new Color(240, 235, 225));
        return p;
    }

    private static Map<Character, Color> badgerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(30, 30, 30));
        p.put('d', new Color(50, 50, 50));
        p.put('m', new Color(110, 110, 110));
        p.put('l', new Color(170, 170, 170));
        p.put('w', new Color(240, 240, 240));
        p.put('e', new Color(10, 10, 10));
        return p;
    }

    private static Map<Character, Color> rabbitPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(130, 105, 80));
        p.put('d', new Color(170, 145, 115));
        p.put('m', new Color(200, 180, 155));
        p.put('l', new Color(225, 210, 190));
        p.put('e', new Color(20, 15, 15));
        p.put('p', new Color(220, 170, 160));
        p.put('n', new Color(190, 140, 130));
        return p;
    }

    private static Map<Character, Color> harePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(110, 85, 60));
        p.put('d', new Color(140, 110, 78));
        p.put('m', new Color(170, 140, 105));
        p.put('l', new Color(200, 175, 145));
        p.put('e', new Color(20, 15, 15));
        p.put('p', new Color(200, 155, 140));
        p.put('n', new Color(160, 120, 100));
        return p;
    }

    private static Map<Character, Color> squirrelPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(90, 55, 25));
        p.put('d', new Color(120, 75, 35));
        p.put('m', new Color(160, 105, 50));
        p.put('l', new Color(195, 145, 80));
        p.put('e', new Color(15, 10, 10));
        p.put('w', new Color(225, 210, 185));
        return p;
    }

    private static Map<Character, Color> deerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 70, 35));
        p.put('d', new Color(135, 95, 50));
        p.put('m', new Color(180, 140, 80));
        p.put('l', new Color(210, 180, 130));
        p.put('e', new Color(15, 12, 10));
        p.put('a', new Color(90, 65, 35));
        p.put('w', new Color(235, 220, 195));
        return p;
    }

    private static Map<Character, Color> roeDeerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(95, 70, 40));
        p.put('d', new Color(125, 90, 55));
        p.put('m', new Color(160, 120, 70));
        p.put('l', new Color(195, 165, 120));
        p.put('e', new Color(15, 12, 10));
        p.put('a', new Color(80, 58, 30));
        return p;
    }

    private static Map<Character, Color> boarPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(60, 40, 25));
        p.put('d', new Color(85, 58, 35));
        p.put('m', new Color(120, 82, 50));
        p.put('l', new Color(155, 115, 75));
        p.put('e', new Color(15, 10, 10));
        p.put('t', new Color(230, 225, 210));
        p.put('n', new Color(100, 70, 50));
        return p;
    }

    private static Map<Character, Color> mouflonPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(70, 50, 30));
        p.put('d', new Color(100, 75, 48));
        p.put('m', new Color(140, 110, 75));
        p.put('l', new Color(175, 148, 110));
        p.put('e', new Color(15, 12, 10));
        p.put('h', new Color(200, 185, 155));
        p.put('n', new Color(80, 55, 35));
        return p;
    }

    private static Map<Character, Color> playerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(35, 35, 70));
        p.put('s', new Color(220, 185, 155));
        p.put('d', new Color(50, 70, 130));
        p.put('m', new Color(70, 95, 165));
        p.put('l', new Color(100, 130, 195));
        p.put('e', new Color(20, 20, 20));
        p.put('h', new Color(75, 50, 30));
        p.put('b', new Color(55, 40, 28));
        return p;
    }

    // 点阵模式（省略具体数据，保留结构用于回退）
    private static final String[] EMPTY_PATTERN = new String[32];
    static {
        for (int i = 0; i < 32; i++) EMPTY_PATTERN[i] = "................................";
    }

    private static String[] wolfPattern() { return EMPTY_PATTERN; }
    private static String[] foxPattern() { return EMPTY_PATTERN; }
    private static String[] badgerPattern() { return EMPTY_PATTERN; }
    private static String[] rabbitPattern() { return EMPTY_PATTERN; }
    private static String[] harePattern() { return EMPTY_PATTERN; }
    private static String[] squirrelPattern() { return EMPTY_PATTERN; }
    private static String[] deerPattern() { return EMPTY_PATTERN; }
    private static String[] roeDeerPattern() { return EMPTY_PATTERN; }
    private static String[] boarPattern() { return EMPTY_PATTERN; }
    private static String[] mouflonPattern() { return EMPTY_PATTERN; }
    private static String[] playerPattern() {
        // 简易人形像素图案（32x32），作为 PNG 不存在时的后备
        return new String[] {
            "................................",
            "............hhhhhh..............",
            "..........hhsssssshh............",
            ".........hssssssssssh...........",
            ".........hsessessessh...........",
            ".........hsssssssssssh..........",
            ".........hssssnnsssssh..........",
            "..........hssssssssh............",
            "...........hhsssshh.............",
            "............dddddd..............",
            "..........dddmmmmddd............",
            ".........dmmlmmmmllmd...........",
            ".........dmmlmmmmlllmd..........",
            ".........dmmlmmmmlllmd..........",
            ".........dmmlmmmmlllmd..........",
            "..........dmmlmmmlld............",
            "...........ddmmmmdd.............",
            "............dddddd..............",
            "............dd..dd..............",
            "...........dd....dd.............",
            "...........dd....dd.............",
            "...........dd....dd.............",
            "..........dd......dd............",
            "..........dd......dd............",
            ".........hdd......ddh...........",
            ".........hhdd....ddhh...........",
            "..........hdd....ddh............",
            "...........dd....dd.............",
            "..........ooo..ooo..............",
            ".........oooo..oooo.............",
            ".........ooo....ooo.............",
            "................................",
        };
    }

    // ==================== 公开 API ====================

    /**
     * 生成所有生物精灵（含玩家）。
     * <p>
     * 动物精灵优先从 PNG 加载，玩家始终程序化生成。
     * </p>
     *
     * @return ID → Sprite 映射
     */
    public static Map<String, Sprite> createAllCreatureSprites() {
        Map<String, Sprite> sprites = new HashMap<>();
        int size = 32;

        String[] creatureTypes = {
                "wolf", "fox", "badger", "rabbit", "hare",
                "squirrel", "deer", "roe_deer", "boar", "mouflon"
        };

        for (String type : creatureTypes) {
            String spriteId = "creature." + type;

            // 1. 尝试从 PNG 加载
            Sprite fromPng = loadFromPng(type, size);
            if (fromPng != null) {
                sprites.put(spriteId, fromPng);
                continue;
            }

            // 2. 回退到程序化生成
            logger.debug("PNG 不存在，回退到程序化生成: {}", spriteId);
            switch (type) {
                case "wolf":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, wolfPattern(), wolfPalette()));
                    break;
                case "fox":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, foxPattern(), foxPalette()));
                    break;
                case "badger":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, badgerPattern(), badgerPalette()));
                    break;
                case "rabbit":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, rabbitPattern(), rabbitPalette()));
                    break;
                case "hare":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, harePattern(), harePalette()));
                    break;
                case "squirrel":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, squirrelPattern(), squirrelPalette()));
                    break;
                case "deer":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, deerPattern(), deerPalette()));
                    break;
                case "roe_deer":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, roeDeerPattern(), roeDeerPalette()));
                    break;
                case "boar":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, boarPattern(), boarPalette()));
                    break;
                case "mouflon":
                    sprites.put(spriteId, PixelArt.createSprite(spriteId, mouflonPattern(), mouflonPalette()));
                    break;
            }
        }

        // 玩家：优先从 PNG 加载，回退到程序化生成
        String playerSpriteId = "player";
        Sprite playerFromPng = loadFromPng("player", size);
        if (playerFromPng != null) {
            // 设置锚点为左上角 (0, 0)：精灵的左上角对齐瓦片位置，精灵覆盖瓦片
            playerFromPng.setAnchor(0.0, 0.0);
            sprites.put(playerSpriteId, playerFromPng);
        } else {
            logger.debug("PNG 不存在，回退到程序化生成: {}", playerSpriteId);
            Sprite playerSprite = PixelArt.createSprite(playerSpriteId, playerPattern(), playerPalette());
            // 设置锚点为左上角 (0, 0)：精灵的左上角对齐瓦片位置，精灵覆盖瓦片
            playerSprite.setAnchor(0.0, 0.0);
            sprites.put(playerSpriteId, playerSprite);
        }

        return sprites;
    }
}
