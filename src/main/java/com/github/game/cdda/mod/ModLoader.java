package com.github.game.cdda.mod;

import com.github.game.engine.core.data.DataScanner;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
 * 扫描 mods/ 目录，解析 mod.json，按依赖和 loadOrder 排序，
 * 并将 Mod 数据加载到各注册表。
 *
 * <p>Mod 目录结构约定：
 * <pre>
 * mods/
 *   my_mod/
 *     mod.json              ← Mod 清单（必需）
 *     data/
 *       creatures/           ← 生物定义（可选）
 *         animal/
 *           my_creature.json
 *       items/               ← 物品定义（可选）
 *         food/
 *           my_food.json
 *       recipes/             ← 合成配方（可选）
 *         my_recipe.json
 *       vegetation/          ← 植被定义（可选）
 *         my_plant.json
 * </pre>
 *
 * <p>加载流程：
 * <ol>
 *   <li>扫描 mods/ 目录</li>
 *   <li>解析 mod.json → ModManifest</li>
 *   <li>按 loadOrder 排序</li>
 *   <li>检查依赖</li>
 *   <li>加载 Mod 数据（creatures/items/recipes/vegetation）到各 Registry</li>
 * </ol>
 */
public class ModLoader {

    private static final Logger logger = LoggerFactory.getLogger(ModLoader.class);

    private static final String MOD_JSON = "mod.json";
    private static final String DATA_DIR = "data";
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

        // 加载 Mod 数据
        for (LoadedMod mod : mods) {
            loadModData(mod);
        }

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
     * 加载 Mod 的数据文件（creatures/items/recipes/vegetation）。
     */
    private static void loadModData(LoadedMod mod) {
        Path dataDir = mod.modDir.resolve(DATA_DIR);
        if (!Files.isDirectory(dataDir)) {
            logger.debug("Mod '{}' 无 data/ 目录，跳过数据加载", mod.manifest.id);
            return;
        }

        int loadedCount = 0;

        // 加载生物
        Path creaturesDir = dataDir.resolve("creatures");
        if (Files.isDirectory(creaturesDir)) {
            loadedCount += loadCreaturesFromDir(creaturesDir, mod.manifest.id);
        }

        // 加载物品
        Path itemsDir = dataDir.resolve("items");
        if (Files.isDirectory(itemsDir)) {
            loadedCount += loadItemsFromDir(itemsDir, mod.manifest.id);
        }

        // 加载配方
        Path recipesDir = dataDir.resolve("recipes");
        if (Files.isDirectory(recipesDir)) {
            loadedCount += loadRecipesFromDir(recipesDir, mod.manifest.id);
        }

        // 加载植被
        Path vegetationDir = dataDir.resolve("vegetation");
        if (Files.isDirectory(vegetationDir)) {
            loadedCount += loadVegetationFromDir(vegetationDir, mod.manifest.id);
        }

        if (loadedCount > 0) {
            logger.info("Mod '{}' 加载了 {} 个数据项", mod.manifest.id, loadedCount);
        }
    }

    /**
     * 从目录加载生物定义。
     */
    private static int loadCreaturesFromDir(Path dir, String modId) {
        int count = 0;
        try {
            List<String> files = DataScanner.scanFileJson(dir);
            for (String relPath : files) {
                try (InputStream is = DataScanner.openFileStream(dir, relPath)) {
                    if (is == null) continue;
                    com.github.game.cdda.creature.config.CreatureDefinition def = GSON.fromJson(
                            new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
                            com.github.game.cdda.creature.config.CreatureDefinition.class);
                    if (def != null && def.id != null) {
                        // Mod 生物 ID 添加前缀避免冲突
                        if (!def.id.contains(":")) {
                            def.id = modId + ":" + def.id;
                        }
                        com.github.game.cdda.creature.config.CreatureRegistry.register(def);
                        count++;
                    }
                } catch (Exception e) {
                    logger.error("加载 Mod '{}' 生物失败: {}", modId, relPath, e);
                }
            }
        } catch (Exception e) {
            logger.error("扫描 Mod '{}' 生物目录失败", modId, e);
        }
        return count;
    }

    /**
     * 从目录加载物品定义。
     */
    private static int loadItemsFromDir(Path dir, String modId) {
        int count = 0;
        try {
            List<String> files = DataScanner.scanFileJson(dir);
            for (String relPath : files) {
                try (InputStream is = DataScanner.openFileStream(dir, relPath)) {
                    if (is == null) continue;
                    com.github.game.cdda.item.model.ItemDefinition def = GSON.fromJson(
                            new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
                            com.github.game.cdda.item.model.ItemDefinition.class);
                    if (def != null && def.name != null) {
                        // Mod 物品 name 添加前缀避免冲突
                        String name = def.name;
                        if (!name.contains(":")) {
                            name = modId + ":" + name;
                        }
                        com.github.game.cdda.item.registry.ItemRegistry.loadDefinition(def, name);
                        count++;
                    }
                } catch (Exception e) {
                    logger.error("加载 Mod '{}' 物品失败: {}", modId, relPath, e);
                }
            }
        } catch (Exception e) {
            logger.error("扫描 Mod '{}' 物品目录失败", modId, e);
        }
        return count;
    }

    /**
     * 从目录加载配方定义。
     */
    private static int loadRecipesFromDir(Path dir, String modId) {
        int count = 0;
        try {
            List<String> files = DataScanner.scanFileJson(dir);
            for (String relPath : files) {
                try (InputStream is = DataScanner.openFileStream(dir, relPath)) {
                    if (is == null) continue;
                    com.github.game.cdda.crafting.ProcessingRecipe recipe = GSON.fromJson(
                            new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
                            com.github.game.cdda.crafting.ProcessingRecipe.class);
                    if (recipe != null && recipe.id != null) {
                        // Mod 配方 ID 添加前缀避免冲突
                        if (!recipe.id.contains(":")) {
                            recipe.id = modId + ":" + recipe.id;
                        }
                        com.github.game.cdda.crafting.RecipeRegistry.register(recipe);
                        count++;
                    }
                } catch (Exception e) {
                    logger.error("加载 Mod '{}' 配方失败: {}", modId, relPath, e);
                }
            }
        } catch (Exception e) {
            logger.error("扫描 Mod '{}' 配方目录失败", modId, e);
        }
        return count;
    }

    /**
     * 从目录加载植被定义。
     */
    private static int loadVegetationFromDir(Path dir, String modId) {
        int count = 0;
        try {
            List<String> files = DataScanner.scanFileJson(dir);
            for (String relPath : files) {
                try (InputStream is = DataScanner.openFileStream(dir, relPath)) {
                    if (is == null) continue;
                    com.github.game.cdda.world.vegetation.VegetationDefinition def = GSON.fromJson(
                            new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
                            com.github.game.cdda.world.vegetation.VegetationDefinition.class);
                    if (def != null && def.id != null) {
                        // Mod 植被 ID 添加前缀避免冲突
                        if (!def.id.contains(":")) {
                            def.id = modId + ":" + def.id;
                        }
                        com.github.game.cdda.world.vegetation.VegetationRegistry.register(def);
                        count++;
                    }
                } catch (Exception e) {
                    logger.error("加载 Mod '{}' 植被失败: {}", modId, relPath, e);
                }
            }
        } catch (Exception e) {
            logger.error("扫描 Mod '{}' 植被目录失败", modId, e);
        }
        return count;
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
