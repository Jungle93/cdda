package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 地形精灵纹理数据 —— 从外部 PNG 文件加载地形贴图。
 * <p>
 * 优先从 {@code sprites/tile/} 目录加载 PNG 贴图（由 SD API 批量生成）；
 * 若文件不存在，回退到程序化点阵生成（向后兼容）。
 * </p>
 * <p>
 * 支持通过 JSON 配置文件（{@code tiles.json}）为特定地形指定自定义渲染尺寸，
 * 使精灵可占据多个瓦片格（如松树 48×64 = 1.5×2.0 瓦片）。
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

    /** 瓦片尺寸配置 JSON 文件路径 */
    private static final String CONFIG_FILE = "tiles.json";

    /**
     * 瓦片尺寸配置（地形名称 → 配置条目）。
     * 从 {@code gfx/sprites/tile/tiles.json} 加载，指定精灵渲染尺寸和像素偏移量。
     * <p>
     * JSON 格式示例：
     * <pre>
     * {
     *   "tree": { "width": 1.5, "height": 2.0, "offsetX": 0, "offsetY": -16 }
     * }
     * </pre>
     * <ul>
     *   <li>{@code width/height} — 渲染尺寸（瓦片单位，1.0 = 1 个瓦片格）</li>
     *   <li>{@code offsetX/offsetY} — 像素偏移量（正值向右/下，负值向左/上）</li>
     * </ul>
     */
    private static final Map<String, TileConfig> tileConfigs;

    static {
        Map<String, TileConfig> loaded = loadTileConfigs();
        tileConfigs = loaded != null ? loaded : Collections.emptyMap();
        if (!tileConfigs.isEmpty()) {
            logger.info("已加载 {} 个地形尺寸配置", tileConfigs.size());
        }
    }

    private TileSpriteData() {}

    // ==================== 配置加载 ====================

    /**
     * 加载 {@code tiles.json} 配置。
     * 优先 classpath，回退到外部文件。配置加载失败返回 null。
     */
    private static Map<String, TileConfig> loadTileConfigs() {
        // 1. 尝试 classpath
        String classpathPath = CLASSPATH_TILE_DIR + CONFIG_FILE;
        try (InputStream is = TileSpriteData.class.getResourceAsStream(classpathPath)) {
            if (is != null) {
                return parseConfig(new InputStreamReader(is), "classpath:" + classpathPath);
            }
        } catch (IOException e) {
            logger.debug("classpath 配置文件读取失败: {}", classpathPath, e);
        }

        // 2. 回退到外部文件
        Path path = Paths.get(TILE_DIR, CONFIG_FILE);
        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                return parseConfig(new InputStreamReader(is), path.toString());
            } catch (IOException e) {
                logger.debug("外部配置文件读取失败: {}", path, e);
            }
        }
        return null;
    }

    /**
     * 解析 JSON 配置为 TileConfig 映射。
     */
    private static Map<String, TileConfig> parseConfig(InputStreamReader reader, String source) {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, TileConfig>>() {}.getType();
            Map<String, TileConfig> configs = gson.fromJson(reader, type);
            logger.info("从 {} 加载地形尺寸配置", source);
            return configs;
        } catch (Exception e) {
            logger.warn("解析地形配置 JSON 失败: {}", source, e);
            return null;
        }
    }

    /**
     * 地形尺寸配置条目（JSON 反序列化目标）。
     * <p>
     * 使用像素偏移量（借鉴 Cataclysm-DDA 的 {@code sprite_offset_x/y} 设计），
     * 比锚点比例更直观：直接指定精灵相对默认位置的像素位移。
     */
    private static class TileConfig {
        /** 渲染宽度（瓦片单位，1.0 = 1 个瓦片格） */
        double width = 1.0;
        /** 渲染高度（瓦片单位，1.0 = 1 个瓦片格） */
        double height = 1.0;
        /** 像素偏移 X（正值向右，负值向左，默认 0） */
        double offsetX = 0.0;
        /** 像素偏移 Y（正值向下，负值向上，默认 0） */
        double offsetY = 0.0;
    }

    // ==================== 从 PNG 文件加载 ====================

    /**
     * 尝试加载指定地形的 PNG 贴图。
     * 优先从 classpath 资源加载（打包后），回退到外部文件（开发时 SD 生成）。
     * 若该地形在 {@code tiles.json} 中有配置，按配置的尺寸和锚点渲染；
     * 否则按默认的单瓦片（size × size）渲染。
     *
     * @param id       地形名称（如 "grass"、"tree"）
     * @param tileSize 基础瓦片尺寸（像素）
     * @return 加载的精灵，文件不存在时返回 null
     */
    private static Sprite loadFromPng(String id, int tileSize) {
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

        // 从配置读取渲染尺寸（瓦片单位），默认 1.0×1.0
        TileConfig config = tileConfigs.get(id);
        double tileW = (config != null) ? config.width : 1.0;
        double tileH = (config != null) ? config.height : 1.0;

        // 计算目标像素尺寸（基于瓦片单位 × 基础瓦片大小）
        int targetW = (int) Math.round(tileSize * tileW);
        int targetH = (int) Math.round(tileSize * tileH);

        // 缩放到目标尺寸（nearest-neighbor 保持像素画风格）
        if (image.getWidth() != targetW || image.getHeight() != targetH) {
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, targetW, targetH, null);
            g.dispose();
            image = scaled;
        }

        String spriteId = "tile." + id;
        logger.info("从 {} 加载地形贴图: {} ({}x{}，{}x{} 瓦片单位)", source, spriteId,
                targetW, targetH,
                String.format("%.1f", tileW), String.format("%.1f", tileH));

        Sprite sprite = new Sprite(spriteId, image);
        // 设置精灵的瓦片占用尺寸（用于多瓦片渲染和去重）
        sprite.setTileSize(tileW, tileH);
        // 将像素偏移量转换为锚点比例（内部渲染使用锚点）
        // 公式：anchor = -offset / spritePixelSize
        //   offsetX=+8（右移 8px）→ anchorX = -8/48 = -0.167
        //   offsetY=-16（上移 16px）→ anchorY = 16/64 = 0.25
        double offsetX = (config != null) ? config.offsetX : 0.0;
        double offsetY = (config != null) ? config.offsetY : 0.0;
        double anchorX = (targetW > 0) ? -offsetX / targetW : 0.0;
        double anchorY = (targetH > 0) ? -offsetY / targetH : 0.0;
        sprite.setAnchor(anchorX, anchorY);
        return sprite;
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
