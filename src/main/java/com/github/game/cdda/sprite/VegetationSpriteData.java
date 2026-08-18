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
 * 植被精灵数据 —— 从外部 PNG 文件加载植被贴图，支持按物种配置渲染尺寸和偏移。
 * <p>
 * 加载优先级：
 * <ol>
 *   <li>外部 PNG 文件（{@code sprites/vegetation/<speciesId>.png}）</li>
 *   <li>classpath PNG 文件（{@code /gfx/sprites/vegetation/<speciesId>.png}）</li>
 *   <li>程序化生成回退（简单几何形状）</li>
 * </ol>
 * </p>
 * <p>
 * 配置文件（{@code vegetation.json}）格式：
 * <pre>
 * {
 *   "oak": { "width": 1.5, "height": 2.0, "offsetX": 0, "offsetY": -16 },
 *   "birch": { "width": 1.2, "height": 1.8, "offsetX": -4, "offsetY": -12 }
 * }
 * </pre>
 * <ul>
 *   <li>{@code width/height} — 渲染尺寸（瓦片单位，1.0 = 1 个瓦片格）</li>
 *   <li>{@code offsetX/offsetY} — 像素偏移量（正值向右/下，负值向左/上）</li>
 * </ul>
 * </p>
 */
public final class VegetationSpriteData {

    private static final Logger logger = LoggerFactory.getLogger(VegetationSpriteData.class);

    /** 外部植被贴图目录 */
    private static final String VEGETATION_DIR = "sprites/vegetation";

    /** classpath 资源路径 */
    private static final String CLASSPATH_VEGETATION_DIR = "/gfx/sprites/vegetation/";

    /** 配置文件名 */
    private static final String CONFIG_FILE = "vegetation.json";

    /** 基础瓦片尺寸 */
    private static final int TILE_SIZE = 32;

    /**
     * 植被尺寸配置（物种 ID → 配置条目）。
     * 从 {@code vegetation.json} 加载。
     */
    private static final Map<String, VegetationConfig> vegetationConfigs;

    /** 所有已知的植被物种 ID（用于加载贴图） */
    private static final String[] SPECIES_IDS = {
            // 树木
            "oak", "birch", "fir", "pine", "beech", "willow",
            // 灌木
            "hazel", "holly", "gorse", "heather",
            // 草类
            "tall_grass", "meadow_grass",
            // 苔藓
            "green_moss", "sphagnum",
            // 水生植物
            "reed", "cattail"
    };

    static {
        Map<String, VegetationConfig> loaded = loadConfigs();
        vegetationConfigs = loaded != null ? loaded : Collections.emptyMap();
        if (!vegetationConfigs.isEmpty()) {
            logger.info("已加载 {} 个植被尺寸配置", vegetationConfigs.size());
        }
    }

    private VegetationSpriteData() {}

    // ==================== 配置加载 ====================

    /**
     * 加载 {@code vegetation.json} 配置。
     */
    private static Map<String, VegetationConfig> loadConfigs() {
        // 1. 尝试 classpath
        String classpathPath = CLASSPATH_VEGETATION_DIR + CONFIG_FILE;
        try (InputStream is = VegetationSpriteData.class.getResourceAsStream(classpathPath)) {
            if (is != null) {
                return parseConfig(new InputStreamReader(is), "classpath:" + classpathPath);
            }
        } catch (IOException e) {
            logger.debug("classpath 配置文件读取失败: {}", classpathPath);
        }

        // 2. 回退到外部文件
        Path path = Paths.get(VEGETATION_DIR, CONFIG_FILE);
        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                return parseConfig(new InputStreamReader(is), path.toString());
            } catch (IOException e) {
                logger.debug("外部配置文件读取失败: {}", path);
            }
        }
        return null;
    }

    /**
     * 解析 JSON 配置。
     */
    private static Map<String, VegetationConfig> parseConfig(InputStreamReader reader, String source) {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, VegetationConfig>>() {}.getType();
            Map<String, VegetationConfig> configs = gson.fromJson(reader, type);
            logger.info("从 {} 加载植被尺寸配置", source);
            return configs;
        } catch (Exception e) {
            logger.warn("解析植被配置 JSON 失败: {}", source, e);
            return null;
        }
    }

    /**
     * 植被尺寸配置条目。
     */
    private static class VegetationConfig {
        /** 渲染宽度（瓦片单位，1.0 = 1 个瓦片格） */
        double width = 1.0;
        /** 渲染高度（瓦片单位） */
        double height = 1.0;
        /** 像素偏移 X（正值向右） */
        double offsetX = 0.0;
        /** 像素偏移 Y（正值向下） */
        double offsetY = 0.0;
    }

    // ==================== 公开 API ====================

    /**
     * 生成所有植被精灵。
     * <p>
     * 优先从 PNG 文件加载；文件不存在时回退到程序化生成。
     * </p>
     *
     * @return ID → Sprite 映射（ID 格式：{@code vegetation.<speciesId>}）
     */
    public static Map<String, Sprite> createAllVegetationSprites() {
        Map<String, Sprite> sprites = new HashMap<>();

        for (String speciesId : SPECIES_IDS) {
            String spriteId = "vegetation." + speciesId;

            // 1. 尝试从 PNG 加载
            Sprite fromPng = loadFromPng(speciesId);
            if (fromPng != null) {
                sprites.put(spriteId, fromPng);
                continue;
            }

            // 2. 回退到程序化生成
            logger.debug("PNG 不存在，回退到程序化生成: {}", spriteId);
            Sprite fallback = createFallbackSprite(spriteId, speciesId);
            if (fallback != null) {
                sprites.put(spriteId, fallback);
            }
        }

        logger.info("生成 {} 个植被精灵", sprites.size());
        return sprites;
    }

    // ==================== PNG 加载 ====================

    /**
     * 从 PNG 文件加载植被贴图。
     */
    private static Sprite loadFromPng(String speciesId) {
        String filename = speciesId + ".png";
        BufferedImage image = null;
        String source = null;

        // 1. 尝试 classpath 加载
        String classpathPath = CLASSPATH_VEGETATION_DIR + filename;
        try (InputStream is = VegetationSpriteData.class.getResourceAsStream(classpathPath)) {
            if (is != null) {
                image = ImageIO.read(is);
                source = "classpath:" + classpathPath;
            }
        } catch (IOException e) {
            logger.debug("classpath 加载失败: {}", classpathPath);
        }

        // 2. 回退到外部文件
        if (image == null) {
            Path path = Paths.get(VEGETATION_DIR, filename);
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

        // 从配置读取渲染尺寸
        VegetationConfig config = vegetationConfigs.get(speciesId);
        double tileW = (config != null) ? config.width : 1.0;
        double tileH = (config != null) ? config.height : 1.0;

        // 计算目标像素尺寸
        int targetW = (int) Math.round(TILE_SIZE * tileW);
        int targetH = (int) Math.round(TILE_SIZE * tileH);

        // 缩放到目标尺寸
        if (image.getWidth() != targetW || image.getHeight() != targetH) {
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, targetW, targetH, null);
            g.dispose();
            image = scaled;
        }

        String spriteId = "vegetation." + speciesId;
        logger.info("从 {} 加载植被贴图: {} ({}x{}, {}x{} 瓦片单位)",
                source, spriteId, targetW, targetH,
                String.format("%.1f", tileW), String.format("%.1f", tileH));

        Sprite sprite = new Sprite(spriteId, image);
        sprite.setTileSize(tileW, tileH);

        // 转换像素偏移为锚点
        double offsetX = (config != null) ? config.offsetX : 0.0;
        double offsetY = (config != null) ? config.offsetY : 0.0;
        double anchorX = (targetW > 0) ? -offsetX / targetW : 0.0;
        double anchorY = (targetH > 0) ? -offsetY / targetH : 0.0;
        sprite.setAnchor(anchorX, anchorY);

        return sprite;
    }

    // ==================== 程序化回退 ====================

    /**
     * 创建回退精灵（简单几何形状，用颜色区分物种）。
     */
    private static Sprite createFallbackSprite(String spriteId, String speciesId) {
        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color primaryColor;
        Color secondaryColor;
        boolean isTree = false;
        boolean isTall = false;

        // 根据物种设置颜色
        switch (speciesId) {
            case "oak":
                primaryColor = new Color(40, 100, 30);
                secondaryColor = new Color(60, 130, 50);
                isTree = true;
                break;
            case "birch":
                primaryColor = new Color(120, 180, 80);
                secondaryColor = new Color(150, 210, 100);
                isTree = true;
                break;
            case "fir":
                primaryColor = new Color(30, 80, 60);
                secondaryColor = new Color(50, 110, 80);
                isTree = true;
                isTall = true;
                break;
            case "pine":
                primaryColor = new Color(35, 90, 40);
                secondaryColor = new Color(55, 120, 55);
                isTree = true;
                isTall = true;
                break;
            case "beech":
                primaryColor = new Color(100, 140, 50);
                secondaryColor = new Color(130, 170, 70);
                isTree = true;
                break;
            case "willow":
                primaryColor = new Color(90, 150, 70);
                secondaryColor = new Color(110, 170, 90);
                isTree = true;
                break;
            case "hazel":
                primaryColor = new Color(70, 130, 50);
                secondaryColor = new Color(90, 160, 70);
                break;
            case "holly":
                primaryColor = new Color(30, 90, 40);
                secondaryColor = new Color(200, 40, 40); // 红果
                break;
            case "gorse":
                primaryColor = new Color(100, 140, 40);
                secondaryColor = new Color(240, 220, 50); // 黄花
                break;
            case "heather":
                primaryColor = new Color(50, 100, 45);
                secondaryColor = new Color(180, 80, 160); // 紫花
                break;
            case "tall_grass":
            case "meadow_grass":
                primaryColor = new Color(90, 180, 70);
                secondaryColor = new Color(120, 210, 90);
                break;
            case "green_moss":
            case "sphagnum":
                primaryColor = new Color(80, 140, 60);
                secondaryColor = new Color(100, 160, 80);
                break;
            case "reed":
            case "cattail":
                primaryColor = new Color(70, 140, 50);
                secondaryColor = new Color(100, 60, 30);
                break;
            default:
                primaryColor = new Color(60, 120, 50);
                secondaryColor = new Color(80, 150, 70);
        }

        if (isTree) {
            // 树干
            g.setColor(new Color(90, 60, 30));
            g.fillRect(13, 22, 6, 10);

            if (isTall) {
                // 锥形树冠（冷杉/松树）
                g.setColor(primaryColor);
                int[] xPoints = {16, 4, 28};
                int[] yPoints = {2, 28, 28};
                g.fillPolygon(xPoints, yPoints, 3);
                g.setColor(secondaryColor);
                int[] xHigh = {16, 10, 22};
                int[] yHigh = {8, 20, 20};
                g.fillPolygon(xHigh, yHigh, 3);
            } else {
                // 圆形树冠
                g.setColor(primaryColor);
                g.fillOval(4, 4, 24, 20);
                g.setColor(secondaryColor);
                g.fillOval(8, 6, 12, 10);
            }
        } else if (speciesId.equals("holly") || speciesId.equals("gorse") || speciesId.equals("heather")) {
            // 灌木 + 装饰
            g.setColor(primaryColor);
            g.fillOval(4, 10, 24, 18);
            g.setColor(secondaryColor);
            g.fillOval(8, 12, 4, 4);
            g.fillOval(18, 14, 4, 4);
            g.fillOval(12, 20, 4, 4);
        } else {
            // 简单圆形/草丛
            g.setColor(primaryColor);
            g.fillOval(6, 12, 20, 16);
        }

        g.dispose();

        Sprite sprite = new Sprite(spriteId, image);

        // 设置瓦片占用（树木较高）
        VegetationConfig config = vegetationConfigs.get(speciesId);
        if (config != null) {
            sprite.setTileSize(config.width, config.height);
        } else if (isTree) {
            sprite.setTileSize(1.0, isTall ? 1.3 : 1.2);
        }

        return sprite;
    }
}
