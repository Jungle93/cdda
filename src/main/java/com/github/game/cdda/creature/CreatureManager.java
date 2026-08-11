package com.github.game.cdda.creature;

import com.github.game.cdda.creature.config.CreatureDefinition;
import com.github.game.cdda.creature.config.CreatureRegistry;
import com.github.game.cdda.creature.energy.DeathCause;
import com.github.game.cdda.creature.energy.EnergyFlowManager;
import com.github.game.cdda.creature.energy.TrophicLevel;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.LootTable;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 生物管理器。
 * 管理世界中所有生物的生命周期：生成、回合处理、繁殖、渲染。
 *
 * <p>核心机制：
 * <ul>
 *   <li><b>区块加载生成</b> — 新区块生成时按概率生成新生物</li>
 *   <li><b>现实气泡</b> — 气泡内生物活跃，气泡外静止（但保留）</li>
 *   <li><b>繁殖</b> — 成熟生物按概率繁殖后代，受密度限制</li>
 * </ul>
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

    /** 地面物品管理器（用于生物死亡掉落） */
    private GroundItemManager groundItemManager;

    /** 能量流动管理器 */
    private EnergyFlowManager energyFlowManager;

    /** 已生成过生物的区块集合（避免重复生成） */
    private final Set<Long> spawnedChunks = new HashSet<>();

    /** 现实气泡中心瓦片 X（-1 表示未设置，所有生物活跃） */
    private int bubbleCenterX = -1;
    /** 现实气泡中心瓦片 Y */
    private int bubbleCenterY = -1;
    /** 现实气泡激活半径（瓦片数，曼哈顿距离） */
    private static final int BUBBLE_RADIUS = 40;

    /** 上次全局繁殖检查的回合数 */
    private int lastReproductionCheckRound = 0;
    /** 气泡外繁殖检查间隔（回合数） */
    private static final int OUT_OF_BUBBLE_CHECK_INTERVAL = 500;

    /** 每个区块最大初始生物数 */
    private static final int MAX_CREATURES_PER_CHUNK = 5;

    /** 初始生物生成概率（0-1） */
    private static final float INITIAL_SPAWN_CHANCE = 0.3f;

    /** 区块加载时生成概率（0-1） */
    private static final float CHUNK_LOAD_SPAWN_CHANCE = 0.2f;

    /** 区块加载时最大生成数 */
    private static final int CHUNK_LOAD_MAX_CREATURES = 2;

    /** 同物种密度上限（附近 5 格内） */
    private static final int MAX_NEARBY_SAME_SPECIES = 4;

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
     * 设置地面物品管理器（用于生物死亡掉落）。
     * 由 GameWorld 在构造时调用。
     *
     * @param groundItemManager 地面物品管理器
     */
    public void setGroundItemManager(GroundItemManager groundItemManager) {
        this.groundItemManager = groundItemManager;
    }

    /**
     * 设置能量流动管理器。
     * 由 GameWorld 在构造时调用。
     *
     * @param energyFlowManager 能量流动管理器
     */
    public void setEnergyFlowManager(EnergyFlowManager energyFlowManager) {
        this.energyFlowManager = energyFlowManager;
    }

    /**
     * 获取能量流动管理器。
     */
    public EnergyFlowManager getEnergyFlowManager() {
        return energyFlowManager;
    }

    /**
     * 更新现实气泡中心位置。
     * 气泡内的生物活跃（正常行动），气泡外的生物静止（跳过回合）。
     * 在玩家跨越区块边界时调用。
     *
     * @param centerTileX 气泡中心瓦片 X（玩家位置）
     * @param centerTileY 气泡中心瓦片 Y（玩家位置）
     */
    public void updateBubble(int centerTileX, int centerTileY) {
        this.bubbleCenterX = centerTileX;
        this.bubbleCenterY = centerTileY;
    }

    /**
     * 判断生物是否在现实气泡内（活跃范围内）。
     * 未设置气泡中心时，所有生物视为活跃。
     *
     * @param creature 待检查的生物
     * @return true 如果在气泡内或未设置气泡
     */
    private boolean isInBubble(Creature creature) {
        if (bubbleCenterX < 0) return true; // 未设置气泡，所有活跃
        int dist = Math.abs(creature.getTileX() - bubbleCenterX)
                 + Math.abs(creature.getTileY() - bubbleCenterY);
        return dist <= BUBBLE_RADIUS;
    }

    // ── 区块加载生成 ──────────────────────────────────

    /**
     * 新区块生成后调用，按概率在其中生成生物。
     * 已在 {@link #spawnedChunks} 中记录的区块不会重复生成。
     *
     * @param minChunkX 区域最小区块 X
     * @param minChunkY 区域最小区块 Y
     * @param maxChunkX 区域最大区块 X
     * @param maxChunkY 区域最大区块 Y
     */
    public void onChunksGenerated(int minChunkX, int minChunkY, int maxChunkX, int maxChunkY) {
        int spawned = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                long key = chunkKey(cx, cy);
                if (spawnedChunks.contains(key)) continue;

                // 标记为已处理
                spawnedChunks.add(key);

                // 概率触发
                if (random.nextFloat() >= CHUNK_LOAD_SPAWN_CHANCE) continue;

                // 生成 1 ~ CHUNK_LOAD_MAX_CREATURES 个生物
                int count = 1 + random.nextInt(CHUNK_LOAD_MAX_CREATURES);
                for (int i = 0; i < count; i++) {
                    if (spawnCreatureInChunk(cx, cy)) {
                        spawned++;
                    }
                }
            }
        }
        if (spawned > 0) {
            logger.debug("区块加载生成 {} 个新生物，总生物数: {}", spawned, creatures.size());
        }
    }

    /**
     * 在指定区块生成单个生物。
     *
     * @return true 如果成功生成
     */
    private boolean spawnCreatureInChunk(int chunkX, int chunkY) {
        int chunkSize = Chunk.SIZE;
        int baseTileX = chunkX * chunkSize;
        int baseTileY = chunkY * chunkSize;

        // 尝试多次找到可通行位置
        for (int attempt = 0; attempt < 10; attempt++) {
            int tileX = baseTileX + random.nextInt(chunkSize);
            int tileY = baseTileY + random.nextInt(chunkSize);

            TileType tile = chunkManager.getTile(tileX, tileY);
            if (tile == null || !tile.isPassable()) continue;

            CreatureDefinition def = getRandomCreatureDefinition();
            if (def == null) continue;

            Animal animal = new Animal(def, tileX, tileY);
            injectEnergyFlowManager(animal);
            addCreature(animal);
            return true;
        }
        return false;
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
        int chunkSize = Chunk.SIZE;
        int centerChunkX = Math.floorDiv(centerTileX, chunkSize);
        int centerChunkY = Math.floorDiv(centerTileY, chunkSize);

        for (int cx = centerChunkX - radiusChunks; cx <= centerChunkX + radiusChunks; cx++) {
            for (int cy = centerChunkY - radiusChunks; cy <= centerChunkY + radiusChunks; cy++) {
                long key = chunkKey(cx, cy);
                spawnedChunks.add(key);
                spawnCreaturesInChunkInitial(cx, cy);
            }
        }
        logger.info("初始生物生成完成，共 {} 个生物", creatures.size());
    }

    /**
     * 在指定区块生成初始生物（较高概率）。
     */
    private void spawnCreaturesInChunkInitial(int chunkX, int chunkY) {
        int chunkSize = Chunk.SIZE;
        int baseTileX = chunkX * chunkSize;
        int baseTileY = chunkY * chunkSize;

        // 随机决定生成数量
        int count = 0;
        for (int i = 0; i < MAX_CREATURES_PER_CHUNK; i++) {
            if (random.nextFloat() < INITIAL_SPAWN_CHANCE) {
                count++;
            }
        }

        // 生成指定数量的生物
        for (int i = 0; i < count; i++) {
            // 随机位置（尝试多次）
            for (int attempt = 0; attempt < 10; attempt++) {
                int tileX = baseTileX + random.nextInt(chunkSize);
                int tileY = baseTileY + random.nextInt(chunkSize);

                TileType tile = chunkManager.getTile(tileX, tileY);
                if (tile == null || !tile.isPassable()) continue;

                CreatureDefinition def = getRandomCreatureDefinition();
                if (def == null) continue;

                Animal animal = new Animal(def, tileX, tileY);
                injectEnergyFlowManager(animal);
                addCreature(animal);
                break;
            }
        }
    }

    /**
     * 将区块坐标转为 long key（与 ChunkManager 一致）。
     */
    private static long chunkKey(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }

    /**
     * 注入能量流动管理器到新创建的动物。
     */
    private void injectEnergyFlowManager(Animal animal) {
        if (energyFlowManager != null) {
            animal.setEnergyFlowManager(energyFlowManager);
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

    // ── 回合处理（含繁殖） ──────────────────────────────────

    /**
     * 处理所有生物的回合（玩家行动后调用）。
     * 气泡内生物正常行动 + 繁殖检查；气泡外生物只按间隔做繁殖检查。
     *
     * @param context 行动上下文
     */
    public void processCreatureTurns(CreatureActionContext context) {
        int currentRound = turnManager.getCurrentRound();
        boolean doOutOfBubbleReproduction =
                currentRound - lastReproductionCheckRound >= OUT_OF_BUBBLE_CHECK_INTERVAL;

        // 收集新出生的生物（避免在遍历中修改列表）
        List<Animal> newborns = new ArrayList<>();

        for (Creature creature : creatures) {
            if (!creature.isAlive()) continue;
            if (!(creature instanceof Animal)) continue;
            Animal animal = (Animal) creature;

            if (isInBubble(animal)) {
                // 气泡内：正常 AI + 每回合检查繁殖
                if (turnManager.canAct(animal)) {
                    animal.takeTurn(context);
                    animal.spendEnergy(com.github.game.cdda.TurnManager.ENERGY_PER_ACTION);
                }
                Animal baby = tryReproduce(animal, currentRound);
                if (baby != null) newborns.add(baby);
            } else if (doOutOfBubbleReproduction) {
                // 气泡外：按间隔检查繁殖（模拟时间流逝）
                Animal baby = tryReproduce(animal, currentRound);
                if (baby != null) newborns.add(baby);
            }
        }

        // 添加新出生的生物
        for (Animal baby : newborns) {
            injectEnergyFlowManager(baby);
            addCreature(baby);
        }

        // 更新全局检查标记
        if (doOutOfBubbleReproduction) {
            lastReproductionCheckRound = currentRound;
        }

        // 清理死亡生物，掉落战利品
        creatures.removeIf(c -> {
            if (!c.isAlive()) {
                turnManager.removeEntity(c);
                dropCreatureLoot(c);
                return true;
            }
            return false;
        });

        // 能量流动更新（每回合调用）
        if (energyFlowManager != null) {
            energyFlowManager.processDecay();
            energyFlowManager.updateVegetationBoosts();
        }
    }

    /**
     * 尝试让动物繁殖。
     *
     * @param animal      成年动物
     * @param currentRound 当前回合数
     * @return 后代动物，繁殖失败返回 null
     */
    private Animal tryReproduce(Animal animal, int currentRound) {
        // 密度检查：附近同种生物不能超过上限
        int sameCount = countNearbySameSpecies(animal, 5);
        if (sameCount >= MAX_NEARBY_SAME_SPECIES) return null;

        Animal offspring = animal.tryReproduce(currentRound, random);
        if (offspring == null) return null;

        // 尝试将后代放置在父母附近的可通行位置
        boolean placed = placeNearby(offspring, animal.getTileX(), animal.getTileY());
        if (!placed) return null;

        GameLog.getInstance().log(String.format("一只%s繁殖了后代！",
                animal.getDefinition().name));
        return offspring;
    }

    /**
     * 统计指定动物附近一定范围内的同种生物数量。
     *
     * @param center    中心动物
     * @param maxDist   最大曼哈顿距离
     * @return 同种存活生物数量（不含自身）
     */
    private int countNearbySameSpecies(Animal center, int maxDist) {
        String speciesId = center.getDefinition().id;
        int count = 0;
        for (Creature c : creatures) {
            if (!c.isAlive() || c == center) continue;
            if (!(c instanceof Animal)) continue;
            Animal other = (Animal) c;
            if (!speciesId.equals(other.getDefinition().id)) continue;
            int dist = Math.abs(c.getTileX() - center.getTileX())
                     + Math.abs(c.getTileY() - center.getTileY());
            if (dist <= maxDist) count++;
        }
        return count;
    }

    /**
     * 将动物放置在指定位置附近（3x3 范围内找一个可通行位置）。
     *
     * @return true 如果成功放置
     */
    private boolean placeNearby(Animal animal, int centerX, int centerY) {
        // 先尝试直接放置
        TileType centerTile = chunkManager.getTile(centerX, centerY);
        if (centerTile != null && centerTile.isPassable()) {
            // 使用反射或直接设置（此处通过构造函数已设置，无需额外操作）
            return true;
        }

        // 在 3x3 范围内找可通行位置
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};
        for (int i = 0; i < dx.length; i++) {
            int nx = centerX + dx[i];
            int ny = centerY + dy[i];
            TileType tile = chunkManager.getTile(nx, ny);
            if (tile != null && tile.isPassable()) {
                // 需要更新动物位置——通过 Creature 的 setter
                animal.setTileX(nx);
                animal.setTileY(ny);
                return true;
            }
        }
        return false;
    }

    /**
     * 生物死亡时掉落战利品。
     * 只有玩家杀死的生物才掉落物品，自然死亡不掉落（尸体分解）。
     *
     * @param creature 死亡的生物
     */
    private void dropCreatureLoot(Creature creature) {
        if (groundItemManager == null) return;
        if (!(creature instanceof Animal)) return;

        Animal animal = (Animal) creature;

        // 只有玩家杀死的才掉落
        if (animal.getDeathCause() != DeathCause.PLAYER_KILL) {
            return;
        }

        CreatureDefinition def = animal.getDefinition();
        LootTable lootTable = def.getKillLootTable();

        if (lootTable == null) return;

        List<ItemStack> drops = lootTable.roll(random);
        for (ItemStack stack : drops) {
            groundItemManager.dropItem(stack, creature.getTileX(), creature.getTileY());
        }
        if (!drops.isEmpty()) {
            GameLog.getInstance().log(String.format("%s 掉落了 %d 件物品",
                    def.name, drops.size()));
        }
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

    /**
     * 获取指定瓦片位置的生物。
     * 用于 Look 模式查询。
     *
     * @param tileX 瓦片 X
     * @param tileY 瓦片 Y
     * @return 该位置的生物，无则返回 null
     */
    public Creature getCreatureAtTile(int tileX, int tileY) {
        for (Creature creature : creatures) {
            if (creature.isAlive() && creature.getTileX() == tileX && creature.getTileY() == tileY) {
                return creature;
            }
        }
        return null;
    }

    /**
     * 获取指定范围内的所有存活生物，按曼哈顿距离升序排序。
     *
     * @param centerTileX 中心瓦片 X
     * @param centerTileY 中心瓦片 Y
     * @param maxDistance 最大曼哈顿距离（含）
     * @return 排序后的生物列表（可能为空）
     */
    public List<Creature> getVisibleCreatures(int centerTileX, int centerTileY, int maxDistance) {
        List<Creature> result = new ArrayList<>();
        for (Creature creature : creatures) {
            if (!creature.isAlive()) continue;
            int dist = Math.abs(creature.getTileX() - centerTileX)
                     + Math.abs(creature.getTileY() - centerTileY);
            if (dist <= maxDistance) {
                result.add(creature);
            }
        }
        result.sort((a, b) -> {
            int distA = Math.abs(a.getTileX() - centerTileX) + Math.abs(a.getTileY() - centerTileY);
            int distB = Math.abs(b.getTileX() - centerTileX) + Math.abs(b.getTileY() - centerTileY);
            return Integer.compare(distA, distB);
        });
        return result;
    }
}
