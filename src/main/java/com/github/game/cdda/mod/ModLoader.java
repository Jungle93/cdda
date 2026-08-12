package com.github.game.cdda.mod;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Mod 加载器。
 * 扫描 mods/ 目录，解析 mod.json，按依赖和 loadOrder 排序。
 */
public class ModLoader {

    private static final Logger logger = LoggerFactory.getLogger(ModLoader.class);

    private static final String MOD_JSON = "mod.json";
    private static final Gson GSON = new Gson();

    private ModLoader() {} // 不可实例化

    /**
     * 发现并加载所有 Mod。
     *
     * @param modsDir mods 根目录
     * @return 排序后的已加载 Mod 列表
     */
    public static List<LoadedMod> discoverAndLoad(Path modsDir) {
        if (!Files.isDirectory(modsDir)) {
            logger.debug("Mods 目录不存在，跳过加载: {}", modsDir);
            return List.of();
        }

        List<LoadedMod> mods = new ArrayList<>();
        try (Stream<Path> entries = Files.list(modsDir)) {
            entries.filter(Files::isDirectory)
                    .forEach(dir -> {
                        LoadedMod mod = loadMod(dir);
                        if (mod != null) {
                            mods.add(mod);
                        }
                    });
        } catch (IOException e) {
            logger.error("扫描 Mods 目录失败: {}", modsDir, e);
            return List.of();
        }

        // 按 loadOrder 排序（数字越大越晚加载）
        mods.sort(Comparator.comparingInt(m -> m.manifest.loadOrder));

        // 检查依赖
        resolveDependencies(mods);

        logger.info("Mod 加载完成，共 {} 个 Mod", mods.size());
        for (LoadedMod mod : mods) {
            logger.info("  - {}", mod.manifest);
        }
        return mods;
    }

    /**
     * 加载单个 Mod。
     */
    private static LoadedMod loadMod(Path dir) {
        Path manifestPath = dir.resolve(MOD_JSON);
        if (!Files.exists(manifestPath)) {
            logger.warn("目录缺少 mod.json，跳过: {}", dir.getFileName());
            return null;
        }

        try (java.io.BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            ModManifest manifest = GSON.fromJson(reader, ModManifest.class);
            if (manifest == null || manifest.id == null || manifest.id.isBlank()) {
                logger.warn("mod.json 格式无效: {}", manifestPath);
                return null;
            }
            if (manifest.name == null) manifest.name = manifest.id;
            if (manifest.version == null) manifest.version = "0.0.0";

            LoadedMod loaded = new LoadedMod();
            loaded.manifest = manifest;
            loaded.modDir = dir;
            logger.info("发现 Mod: {}", manifest);
            return loaded;
        } catch (Exception e) {
            logger.error("加载 Mod 失败: {}", manifestPath, e);
            return null;
        }
    }

    /**
     * 检查依赖是否满足。
     */
    private static void resolveDependencies(List<LoadedMod> mods) {
        Set<String> loadedIds = new HashSet<>();
        for (LoadedMod mod : mods) {
            loadedIds.add(mod.manifest.id);
        }

        for (LoadedMod mod : mods) {
            for (String dep : mod.manifest.dependencies) {
                if (!loadedIds.contains(dep)) {
                    logger.warn("Mod '{}' 依赖 '{}' 未找到，可能无法正常工作",
                            mod.manifest.id, dep);
                }
            }
        }
    }

    /**
     * 已加载的 Mod 信息。
     */
    public static class LoadedMod {
        public ModManifest manifest;
        public Path modDir;
    }
}
