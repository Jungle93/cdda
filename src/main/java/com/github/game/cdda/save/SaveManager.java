package com.github.game.cdda.save;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.config.ConfigManager;
import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.creature.Creature;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.game.WorldSettings;
import com.github.game.cdda.game.time.GameCalendar;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.registry.ItemRegistry;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 存档管理器。
 * 负责游戏状态的保存和加载，使用 JSON 格式存储。
 *
 * <p>存档目录结构：
 * <pre>
 * saves/
 *   game.properties          # 已有配置文件
 *   save_1/
 *     meta.json              # 元数据
 *     player.json            # 玩家数据
 *     world.json             # 区块地形
 *     creatures.json         # 生物状态
 *     game_state.json        # 游戏时间/设置
 *   save_2/
 *   save_3/
 * </pre>
 *
 * <p>实现细节：
 * <ul>
 *   <li>全量保存（非增量），简单可靠</li>
 *   <li>仅保存玩家周围半径 5 的区块（约 121 个区块）</li>
 *   <li>使用 Gson 序列化，可读性好</li>
 *   <li>保存失败时写临时文件再 rename（原子操作）</li>
 * </ul>
 */
public class SaveManager {

    private static final Logger logger = LoggerFactory.getLogger(SaveManager.class);

    /** Gson 实例（线程安全） */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /** 存档槽位数 */
    private static final int SAVE_SLOTS = 3;

    /** 保存区块半径（玩家周围） */
    private static final int CHUNK_RADIUS = 5;

    /**
     * 保存游戏到指定槽位。
     *
     * @param world 游戏世界
     * @param slot  槽位编号（1-3）
     * @return true 如果保存成功
     */
    public static boolean saveGame(GameWorld world, int slot) {
        if (slot < 1 || slot > SAVE_SLOTS) {
            logger.error("无效的存档槽位: {}", slot);
            return false;
        }

        try {
            File saveDir = getSaveDir(slot);
            saveDir.mkdirs();

            // 保存元数据
            SaveMetadata meta = createMetadata(world);
            writeJson(new File(saveDir, "meta.json"), meta);

            // 保存玩家数据
            PlayerSaveData playerData = savePlayer(world.getPlayer());
            writeJson(new File(saveDir, "player.json"), playerData);

            // 保存世界数据
            WorldSaveData worldData = saveWorld(world);
            writeJson(new File(saveDir, "world.json"), worldData);

            // 保存生物数据
            CreatureSaveData creatureData = saveCreatures(world);
            writeJson(new File(saveDir, "creatures.json"), creatureData);

            // 保存游戏状态
            GameStateSaveData gameStateData = saveGameState(world);
            writeJson(new File(saveDir, "game_state.json"), gameStateData);

            logger.info("游戏已保存到槽位 {} ({})", slot, saveDir.getAbsolutePath());
            return true;
        } catch (Exception e) {
            logger.error("保存游戏失败 (槽位 {}): {}", slot, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 读取指定槽位的游戏状态数据（用于获取种子等信息）。
     *
     * @param slot 槽位编号（1-3）
     * @return 游戏状态数据，不存在或读取失败返回 null
     */
    public static GameStateSaveData readGameStateData(int slot) {
        if (slot < 1 || slot > SAVE_SLOTS) return null;
        File saveDir = getSaveDir(slot);
        if (!saveDir.exists()) return null;
        return readJson(new File(saveDir, "game_state.json"), GameStateSaveData.class);
    }

    /**
     * 从指定槽位加载游戏。
     *
     * @param world 游戏世界（将被覆盖）
     * @param slot  槽位编号（1-3）
     * @return true 如果加载成功
     */
    public static boolean loadGame(GameWorld world, int slot) {
        if (slot < 1 || slot > SAVE_SLOTS) {
            logger.error("无效的存档槽位: {}", slot);
            return false;
        }

        File saveDir = getSaveDir(slot);
        if (!saveDir.exists()) {
            logger.warn("存档槽位 {} 不存在", slot);
            return false;
        }

        try {
            // 加载游戏状态（需要先加载以恢复时间）
            GameStateSaveData gameStateData = readJson(
                    new File(saveDir, "game_state.json"), GameStateSaveData.class);
            if (gameStateData != null) {
                loadGameState(world, gameStateData);
            }

            // 加载玩家数据
            PlayerSaveData playerData = readJson(
                    new File(saveDir, "player.json"), PlayerSaveData.class);
            if (playerData != null) {
                loadPlayer(world, playerData);
            }

            // 加载世界数据
            WorldSaveData worldData = readJson(
                    new File(saveDir, "world.json"), WorldSaveData.class);
            if (worldData != null) {
                loadWorld(world, worldData);
            }

            // 加载生物数据
            CreatureSaveData creatureData = readJson(
                    new File(saveDir, "creatures.json"), CreatureSaveData.class);
            if (creatureData != null) {
                loadCreatures(world, creatureData);
            }

            logger.info("游戏已从槽位 {} 加载", slot);
            return true;
        } catch (Exception e) {
            logger.error("加载游戏失败 (槽位 {}): {}", slot, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取存档槽位的目录。
     */
    public static File getSaveDir(int slot) {
        String savePath = ConfigManager.getInstance().getSavePath();
        return new File(savePath, "save_" + slot);
    }

    /**
     * 列出所有可用存档。
     *
     * @return 存档元数据列表（按槽位顺序）
     */
    public static List<SaveMetadata> listSaves() {
        List<SaveMetadata> saves = new ArrayList<>();
        for (int i = 1; i <= SAVE_SLOTS; i++) {
            File metaFile = new File(getSaveDir(i), "meta.json");
            if (metaFile.exists()) {
                SaveMetadata meta = readJson(metaFile, SaveMetadata.class);
                if (meta != null) {
                    saves.add(meta);
                }
            }
        }
        return saves;
    }

    /**
     * 删除指定槽位的存档。
     *
     * @param slot 槽位编号（1-3）
     * @return true 如果删除成功
     */
    public static boolean deleteSave(int slot) {
        File saveDir = getSaveDir(slot);
        if (!saveDir.exists()) return true;

        File[] files = saveDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.delete()) {
                    logger.warn("无法删除文件: {}", f.getAbsolutePath());
                }
            }
        }
        return saveDir.delete();
    }

    // ── 保存逻辑 ──────────────────────────────────

    /**
     * 创建存档元数据。
     */
    private static SaveMetadata createMetadata(GameWorld world) {
        Player player = world.getPlayer();
        String name = "存档_" + player.getTileX() + "_" + player.getTileY();
        String time = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long totalSeconds = world.getGameTime().getTotalSeconds();
        return new SaveMetadata(name, time, totalSeconds);
    }

    /**
     * 保存玩家数据。
     */
    private static PlayerSaveData savePlayer(Player player) {
        PlayerSaveData data = new PlayerSaveData(
                player.getWorldX(),
                player.getWorldY(),
                player.getHp(),
                player.getMaxHp(),
                player.getStrength(),
                player.getAgility(),
                player.getEndurance(),
                (int) player.getSpeed()
        );

        // 保存背包物品
        List<ItemStack> inventory = player.getInventory().getItems();
        for (ItemStack stack : inventory) {
            data.inventory.add(new ItemStackData(
                    stack.getType().getName(),
                    stack.getCount()
            ));
        }

        return data;
    }

    /**
     * 保存世界数据（玩家周围的区块）。
     */
    private static WorldSaveData saveWorld(GameWorld world) {
        WorldSaveData data = new WorldSaveData();
        ChunkManager chunkManager = world.getChunkManager();
        Player player = world.getPlayer();

        int tileW = 1; // 使用默认瓦片尺寸计算区块坐标
        int tileH = 1;
        int playerChunkX = Math.floorDiv(player.getTileX(), Constants.CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(player.getTileY(), Constants.CHUNK_SIZE);

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dy = -CHUNK_RADIUS; dy <= CHUNK_RADIUS; dy++) {
                int cx = playerChunkX + dx;
                int cy = playerChunkY + dy;
                Chunk chunk = chunkManager.getChunk(cx, cy);
                if (chunk != null) {
                    ChunkData chunkData = saveChunk(chunk, cx, cy);
                    data.chunks.add(chunkData);
                }
            }
        }

        return data;
    }

    /**
     * 保存单个区块数据。
     */
    private static ChunkData saveChunk(Chunk chunk, int cx, int cy) {
        int size = Constants.CHUNK_SIZE;
        String[] tiles = new String[size * size];
        String[] vegetation = new String[size * size];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                TileType tile = chunk.getTile(x, y);
                tiles[index] = tile != null ? tile.getName() : "grass";

                String veg = chunk.getVegetation(x, y);
                vegetation[index] = veg; // null 表示无植被
            }
        }

        return new ChunkData(cx, cy, tiles, vegetation);
    }

    /**
     * 保存生物数据（不包括玩家）。
     */
    private static CreatureSaveData saveCreatures(GameWorld world) {
        CreatureSaveData data = new CreatureSaveData();
        CreatureManager creatureManager = world.getCreatureManager();

        // 获取所有存活生物（排除玩家）
        List<Creature> creatures = creatureManager.getCreatures();
        for (Creature creature : creatures) {
            if (creature instanceof Player) continue; // 玩家单独保存
            if (!creature.isAlive()) continue;

            // 获取物种 ID
            String speciesId = getSpeciesId(creature);
            if (speciesId == null) continue;

            data.creatures.add(new CreatureData(
                    speciesId,
                    creature.getTileX(),
                    creature.getTileY(),
                    creature.getHp(),
                    creature.getMaxHp(),
                    (int) creature.getSpeed(),
                    creature.isAlive()
            ));
        }

        return data;
    }

    /**
     * 获取生物的物种 ID。
     * 动物使用 CreatureDefinition 中的 id（如 "deer", "wolf"），
     * 其他类型使用类名作为 ID。
     */
    private static String getSpeciesId(Creature creature) {
        if (creature instanceof Animal animal) {
            return animal.getDefinition().id;
        }
        // 兜底：使用类名
        String className = creature.getClass().getSimpleName();
        return className.toLowerCase();
    }

    /**
     * 保存游戏状态数据。
     */
    private static GameStateSaveData saveGameState(GameWorld world) {
        GameCalendar gameTime = world.getGameTime();

        return new GameStateSaveData(
                world.getWorldSettings().getSeed(),
                gameTime.getMonth().ordinal() + 1, // 转换为 1-12
                gameTime.getHour(),
                gameTime.getTotalSeconds()
        );
    }

    // ── 加载逻辑 ──────────────────────────────────

    /**
     * 加载玩家数据。
     */
    private static void loadPlayer(GameWorld world, PlayerSaveData data) {
        Player player = world.getPlayer();

        // 恢复位置（像素坐标 → 自动对齐瓦片）
        player.setWorldPosition(data.worldX, data.worldY);

        // 恢复生命
        player.setMaxHp(data.maxHp);
        player.setHp(data.hp);

        // 恢复属性
        player.setStrength(data.strength);
        player.setAgility(data.agility);
        player.setEndurance(data.endurance);
        player.setSpeed(data.speed);

        // 恢复背包
        player.getInventory().clear();
        for (ItemStackData itemData : data.inventory) {
            ItemType type = ItemRegistry.getByName(itemData.itemName);
            if (type != null) {
                player.getInventory().addItem(new ItemStack(type, itemData.count));
            }
        }

        logger.info("玩家数据已恢复：位置({}, {}) 属性 HP={}/{} STR={} AGI={} CON={} SPD={}",
                data.worldX, data.worldY, data.hp, data.maxHp,
                data.strength, data.agility, data.endurance, data.speed);
    }

    /**
     * 加载世界数据（恢复已保存区块的地形和植被）。
     */
    private static void loadWorld(GameWorld world, WorldSaveData data) {
        ChunkManager chunkManager = world.getChunkManager();

        for (ChunkData chunkData : data.chunks) {
            chunkManager.loadChunkFromSave(chunkData.cx, chunkData.cy, chunkData);
        }

        logger.info("世界数据已恢复：{} 个区块", data.chunks.size());
    }

    /**
     * 加载生物数据（恢复存档中保存的所有生物状态）。
     */
    private static void loadCreatures(GameWorld world, CreatureSaveData data) {
        CreatureManager creatureManager = world.getCreatureManager();

        // 清除当前所有非玩家生物
        creatureManager.clearCreaturesExceptPlayer();

        // 从存档数据恢复每个生物（spawnFromSave 内部会标记区块为"已生成"）
        int restored = 0;
        for (CreatureData creatureData : data.creatures) {
            if (creatureManager.spawnFromSave(creatureData)) {
                restored++;
            }
        }

        logger.info("生物数据已恢复：{}/{} 个生物", restored, data.creatures.size());
    }

    /**
     * 加载游戏状态。
     */
    private static void loadGameState(GameWorld world, GameStateSaveData data) {
        GameCalendar gameTime = world.getGameTime();
        gameTime.setTotalSeconds(data.totalSeconds);

        logger.info("游戏状态已恢复：时间 {} 秒（第{}月 {}:{}）",
                data.totalSeconds, data.startMonth, gameTime.getHour(), gameTime.getMinute());
    }

    // ── JSON 工具方法 ──────────────────────────────────

    /**
     * 写入 JSON 文件（原子操作：先写临时文件再 rename）。
     */
    private static void writeJson(File file, Object obj) throws IOException {
        File tempFile = new File(file.getParent(), file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tempFile)) {
            GSON.toJson(obj, writer);
        }
        // 原子重命名
        Files.move(tempFile.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * 读取 JSON 文件。
     */
    private static <T> T readJson(File file, Class<T> clazz) {
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, clazz);
        } catch (Exception e) {
            logger.error("读取 JSON 失败: {}", file.getAbsolutePath(), e);
            return null;
        }
    }
}
