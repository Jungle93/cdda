package com.github.game.engine.core.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏资源管理器。
 * 统一管理图片资源的加载和缓存，支持两种来源：
 * <ul>
 *   <li>程序内（classpath）：打包在 JAR 中的资源，使用 "classpath:" 前缀</li>
 *   <li>程序外（文件系统）：外部目录中的图片，使用 "file:" 前缀</li>
 * </ul>
 * 无前缀时自动检测：优先查 classpath，未找到则查外部文件。
 * 外部文件路径可以是绝对路径，或相对于 baseDir 的相对路径。
 */
public class ResourceManager {

    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";

    /** 图片缓存：规范化路径 → BufferedImage */
    private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();

    /** 外部资源基准目录 */
    private Path baseDir;

    public ResourceManager() {
        this("");
    }

    /**
     * 创建资源管理器。
     *
     * @param basePath 外部资源基准目录，空字符串表示当前工作目录
     */
    public ResourceManager(String basePath) {
        setBasePath(basePath);
        logger.info("资源管理器初始化，基准目录: {}", baseDir.toAbsolutePath());
    }

    // ── 基准路径 ──────────────────────────────────────

    /** 设置外部资源基准目录 */
    public void setBasePath(String basePath) {
        this.baseDir = (basePath == null || basePath.isEmpty())
                ? Paths.get("")
                : Paths.get(basePath);
    }

    /** 获取外部资源基准目录 */
    public String getBasePath() {
        return baseDir.toString();
    }

    // ── 图片加载 ──────────────────────────────────────

    /**
     * 加载图片（自动检测来源）。
     * 带 "classpath:" 前缀从程序内加载，带 "file:" 前缀从外部加载，
     * 无前缀时优先查 classpath，未找到则查外部文件。
     * 已缓存的图片直接返回。
     *
     * @param path 资源路径
     * @return 加载的图片，失败返回 null
     */
    public BufferedImage getImage(String path) {
        String key = normalizePath(path);
        BufferedImage cached = imageCache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage image = null;

        if (path.startsWith(CLASSPATH_PREFIX)) {
            image = loadFromClasspath(path.substring(CLASSPATH_PREFIX.length()));
        } else if (path.startsWith(FILE_PREFIX)) {
            image = loadFromFile(path.substring(FILE_PREFIX.length()));
        } else {
            // 无前缀：先 classpath，后外部文件
            image = loadFromClasspath(path);
            if (image == null) {
                image = loadFromFile(path);
            }
        }

        if (image != null) {
            imageCache.put(key, image);
            logger.debug("图片已加载: {} ({}x{})", path, image.getWidth(), image.getHeight());
        } else {
            logger.warn("图片加载失败: {}", path);
        }

        return image;
    }

    /**
     * 显式从 classpath 加载图片。
     *
     * @param resourcePath classpath 中的路径，如 "/images/player.png"
     * @return 加载的图片，失败返回 null
     */
    public BufferedImage getImageFromClasspath(String resourcePath) {
        String key = "cp:" + normalizePath(resourcePath);
        BufferedImage cached = imageCache.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = loadFromClasspath(resourcePath);
        if (image != null) {
            imageCache.put(key, image);
            logger.debug("图片已加载(classpath): {} ({}x{})", resourcePath, image.getWidth(), image.getHeight());
        } else {
            logger.warn("图片加载失败(classpath): {}", resourcePath);
        }
        return image;
    }

    /**
     * 显式从外部文件加载图片。
     *
     * @param filePath 文件路径（绝对或相对于基准目录）
     * @return 加载的图片，失败返回 null
     */
    public BufferedImage getImageFromFile(String filePath) {
        String key = "file:" + normalizePath(filePath);
        BufferedImage cached = imageCache.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = loadFromFile(filePath);
        if (image != null) {
            imageCache.put(key, image);
            logger.debug("图片已加载(file): {} ({}x{})", filePath, image.getWidth(), image.getHeight());
        } else {
            logger.warn("图片加载失败(file): {}", filePath);
        }
        return image;
    }

    // ── 缓存管理 ──────────────────────────────────────

    /** 检查图片是否已缓存 */
    public boolean isCached(String path) {
        return imageCache.containsKey(normalizePath(path));
    }

    /** 清除所有已缓存的图片 */
    public void clearCache() {
        int count = imageCache.size();
        imageCache.clear();
        logger.info("资源缓存已清除，释放 {} 张图片", count);
    }

    /** 获取当前缓存的图片数量 */
    public int getCachedCount() {
        return imageCache.size();
    }

    // ── 内部实现 ──────────────────────────────────────

    /** 从 classpath 加载图片 */
    private BufferedImage loadFromClasspath(String resourcePath) {
        // 确保以 / 开头（Class.getResource 要求）
        String res = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        try (InputStream is = getClass().getResourceAsStream(res)) {
            if (is == null) {
                logger.debug("classpath 资源未找到: {}", res);
                return null;
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            logger.error("读取 classpath 图片失败: {}", res, e);
            return null;
        }
    }

    /** 从外部文件加载图片 */
    private BufferedImage loadFromFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = baseDir.resolve(path);
        }

        if (!Files.exists(path) || !Files.isReadable(path)) {
            logger.debug("外部文件不存在或不可读: {}", path);
            return null;
        }

        try (InputStream is = Files.newInputStream(path)) {
            return ImageIO.read(is);
        } catch (IOException e) {
            logger.error("读取外部图片失败: {}", path, e);
            return null;
        }
    }

    /** 规范化路径（统一分隔符，去除尾部斜杠） */
    private String normalizePath(String path) {
        if (path == null) return "";
        return path.replace('\\', '/').replaceAll("/+$", "");
    }
}
