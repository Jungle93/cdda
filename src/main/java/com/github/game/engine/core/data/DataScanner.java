package com.github.game.engine.core.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 通用数据扫描器。
 * 扫描 classpath 或文件系统中的 JSON 文件，供各 Registry 统一使用。
 */
public final class DataScanner {

    private static final Logger logger = LoggerFactory.getLogger(DataScanner.class);

    private DataScanner() {} // 不可实例化

    /**
     * 扫描 classpath 目录下所有 JSON 文件（递归）。
     *
     * @param basePath classpath 根路径，如 "data/core/items"
     * @return 相对路径列表（如 "data/core/items/food/bread.json"），有序
     */
    public static List<String> scanClasspathJson(String basePath) {
        List<String> result = new ArrayList<>();
        scanClasspathDir(basePath, result);
        Collections.sort(result);
        logger.debug("扫描 classpath 目录: {} → {} 个 JSON 文件", basePath, result.size());
        return result;
    }

    /**
     * 扫描文件系统目录下所有 JSON 文件（递归）。
     *
     * @param dir 目录路径
     * @return 相对路径列表（相对于 dir），有序
     */
    public static List<String> scanFileJson(Path dir) {
        if (!Files.isDirectory(dir)) {
            logger.debug("目录不存在，跳过: {}", dir);
            return List.of();
        }

        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        String rel = dir.relativize(p).toString().replace('\\', '/');
                        result.add(rel);
                    });
        } catch (IOException e) {
            logger.error("扫描目录失败: {}", dir, e);
        }
        Collections.sort(result);
        logger.debug("扫描文件系统目录: {} → {} 个 JSON 文件", dir, result.size());
        return result;
    }

    /**
     * 从 classpath 打开 JSON 文件的输入流。
     *
     * @param path classpath 相对路径，如 "data/core/items/food/bread.json"
     * @return 输入流，未找到返回 null
     */
    public static InputStream openClasspathStream(String path) {
        String res = path.startsWith("/") ? path : "/" + path;
        InputStream is = DataScanner.class.getResourceAsStream(res);
        if (is == null) {
            logger.warn("classpath 资源未找到: {}", res);
        }
        return is;
    }

    /**
     * 从文件系统打开 JSON 文件的输入流。
     *
     * @param baseDir      基准目录
     * @param relativePath 相对路径
     * @return 输入流，未找到返回 null
     */
    public static InputStream openFileStream(Path baseDir, String relativePath) {
        Path path = baseDir.resolve(relativePath);
        if (!Files.exists(path)) {
            logger.warn("文件不存在: {}", path);
            return null;
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            logger.error("打开文件失败: {}", path, e);
            return null;
        }
    }

    // ── 内部实现 ──────────────────────────────────────

    /**
     * 递归扫描 classpath 目录下的 JSON 文件。
     * 兼容 JAR 和文件系统两种场景。
     */
    private static void scanClasspathDir(String dirPath, List<String> result) {
        String resPath = dirPath.startsWith("/") ? dirPath : "/" + dirPath;
        URL url = DataScanner.class.getResource(resPath);
        if (url == null) {
            logger.debug("classpath 目录未找到: {}", resPath);
            return;
        }

        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                scanJarDirectory(uri, dirPath, result);
            } else {
                scanFsDirectory(Path.of(uri), dirPath, result);
            }
        } catch (URISyntaxException e) {
            logger.error("解析 classpath URI 失败: {}", url, e);
        }
    }

    /**
     * 扫描 JAR 内的目录（递归）。
     */
    private static void scanJarDirectory(URI uri, String dirPath, List<String> result) {
        String[] parts = uri.toString().split("!");
        if (parts.length < 2) return;

        String jarPath = parts[0]; // jar:file:/path/to/app.jar
        try (FileSystem fs = FileSystems.newFileSystem(URI.create(jarPath),
                Collections.<String, Object>emptyMap())) {
            Path root = fs.getPath(dirPath);
            if (!Files.isDirectory(root)) return;

            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".json"))
                        .filter(Files::isRegularFile)
                        .forEach(p -> result.add(p.toString().replace('\\', '/')));
            }
        } catch (IOException e) {
            logger.error("扫描 JAR 目录失败: {}", uri, e);
        }
    }

    /**
     * 扫描文件系统目录（开发模式，classpath 指向 target/classes）。
     */
    private static void scanFsDirectory(Path dir, String dirPath, List<String> result) {
        if (!Files.isDirectory(dir)) {
            logger.debug("目录不存在: {}", dir);
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .forEach(p -> result.add(p.toString().replace('\\', '/')));
        } catch (IOException e) {
            logger.error("扫描目录失败: {}", dir, e);
        }
    }
}
