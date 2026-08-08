package com.github.game.cdda.creature;

import com.github.game.cdda.Constants;
import com.github.game.cdda.creature.config.CreatureDefinition;
import com.github.game.cdda.creature.config.CreatureRegistry;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * 生物管理器。
 * 管理世界中所有生物的生命周期：生成、回合处理、渲染。
 */
public class CreatureManager {

    private static final Logger logger = LoggerFactory.getLogger(CreatureManager.class);

    /** 所有生物列表 */
    private final List<Creature> creatures = new ArrayList<>();

    /** 地图管理器 */
    private final ChunkManager chunkManager;

    /** 回合管理器 */
    private final com.github.game.cdda.TurnManager turnManager;

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 每个区块最大生物数 */
    private static final int MAX_CREATURES_PER_CHUNK = 5;

    /** 生物生成概率（0-1） */
    private static final float SPAWN_CHANCE = 0.3f;

    /**
     * 创建生物管理器。
     *
     * @param chunkManager 地图管理器
     * @param turnManager  回合管理器
     */
    public CreatureManager(ChunkManager chunkManager, com.github.game.cdda.TurnManager turnManager) {
        this.chunkManager = chunkManager;
        this.turnManager = turnManager;
    }

    /**
     * 生成初始生物（世界创建时调用）。
     * 在玩家周围一定范围内生成生物。
     *
     * @param centerTileX 中心瓦片 X（玩家位置）
     * @param centerTileY 中心瓦片 Y（玩家位置）
     * @param radiusChunks 半径（区块数）
     */
    public void spawnInitialCreatures(int centerTileX, int centerTileY, int radiusChunks) {
        int chunkSize = 64;  // 与 Chunk.CHUNK_SIZE 一致
        int centerChunkX = Math.floorDiv(centerTileX, chunkSize);
        int centerChunkY = Math.floorDiv(centerTileY, chunkSize);

        for (int cx = centerChunkX - radiusChunks; cx <= centerChunkX + radiusChunks; cx++) {
            for (int cy = centerChunkY - radiusChunks; cy <= centerChunkY + radiusChunks; cy++) {
                spawnCreaturesInChunk(cx, cy);
            }
        }
        logger.info("初始生物生成完成，共 {} 个生物", creatures.size());
    }

    /**
     * 在指定区块生成生物。
     *
     * @param chunkX 区块 X
     * @param chunkY 区块 Y
     */
    private void spawnCreaturesInChunk(int chunkX, int chunkY) {
        int chunkSize = 64;
        int baseTileX = chunkX * chunkSize;
        int baseTileY = chunkY * chunkSize;

        // 随机决定生成数量
        int count = 0;
        for (int i = 0; i < MAX_CREATURES_PER_CHUNK; i++) {
            if (random.nextFloat() < SPAWN_CHANCE) {
                count++;
            }
        }

        // 生成指定数量的生物
        for (int i = 0; i < count; i++) {
            // 随机位置
            int tileX = baseTileX + random.nextInt(chunkSize);
            int tileY = baseTileY + random.nextInt(chunkSize);

            // 检查是否可通行
            TileType tile = chunkManager.getTile(tileX, tileY);
            if (tile == null || !tile.isPassable()) {
                continue;
            }

            // 随机选择生物类型
            CreatureDefinition def = getRandomCreatureDefinition();
            if (def == null) continue;

            // 创建动物并添加
            Animal animal = new Animal(def, tileX, tileY);
            addCreature(animal);
        }
    }

    /**
     * 随机获取一个生物定义。
     *
     * @return 生物定义，或 null
     */
    private CreatureDefinition getRandomCreatureDefinition() {
        Collection<CreatureDefinition> all = CreatureRegistry.getAll();
        if (all.isEmpty()) return null;

        int index = random.nextInt(all.size());
        int i = 0;
        for (CreatureDefinition def : all) {
            if (i == index) return def;
            i++;
        }
        return null;
    }

    /**
     * 添加生物。
     *
     * @param creature 生物实例
     */
    public void addCreature(Creature creature) {
        if (creature == null) return;
        creatures.add(creature);
        // 注册到回合系统
        turnManager.addEntity(creature);
    }

    /**
     * 移除生物。
     *
     * @param creature 生物实例
     */
    public void removeCreature(Creature creature) {
        creatures.remove(creature);
        turnManager.removeEntity(creature);
    }

    /**
     * 处理所有生物的回合（玩家行动后调用）。
     *
     * @param context 行动上下文
     */
    public void processCreatureTurns(CreatureActionContext context) {
        for (Creature creature : creatures) {
            if (!creature.isAlive()) continue;

            // 检查是否有足够能量行动
            if (turnManager.canAct(creature)) {
                // 执行回合行动
                creature.takeTurn(context);
                // 消耗能量并推进时间
                turnManager.addAction(creature, Constants.MOVE_BASE_TIME);
            }
        }

        // 清理死亡生物
        creatures.removeIf(c -> {
            if (!c.isAlive()) {
                turnManager.removeEntity(c);
                return true;
            }
            return false;
        });
    }

    /**
     * 渲染所有生物。
     *
     * @param renderer   渲染器
     * @param camera     摄像机
     * @param tileWidth  瓦片像素宽度
     * @param tileHeight 瓦片像素高度
     */
    public void renderCreatures(Renderer renderer, Camera camera, int tileWidth, int tileHeight) {
        for (Creature creature : creatures) {
            if (creature.isAlive()) {
                creature.render(renderer, camera, tileWidth, tileHeight);
            }
        }
    }

    /**
     * 获取所有生物（只读）。
     *
     * @return 生物列表
     */
    public List<Creature> getCreatures() {
        return creatures;
    }

    /**
     * 获取生物数量。
     *
     * @return 数量
     */
    public int getCreatureCount() {
        return creatures.size();
    }
}
