package com.github.game.cdda.creature;

import com.github.game.cdda.creature.config.CreatureDefinition;
import com.github.game.cdda.creature.config.CreatureRegistry;
import com.github.game.cdda.creature.energy.DeathCause;
import com.github.game.cdda.creature.energy.EnergyFlowManager;
import com.github.game.cdda.creature.energy.TrophicLevel;
import com.github.game.cdda.item.world.GroundItemManager;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.registry.LootTable;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 生物管理器。
 * 管理世界中所有生物的生命周期：生成、回合处理、繁殖、渲染。
 *
 * <p>核心机制：
 * <ul>
 *   <li><b>空间索引</b> — CreatureGrid 提供 O(1) 位置查询，替代 O(N) 全列表扫描</li>
 *   <li><b>回合异步化</b> — AI 回合在后台线程计算，EDT 每帧应用变更</li>
 *   <li><b>现实气泡</b> — 气泡内生物活跃，气泡外静止（但保留）</li>
 *   <li><b>繁殖</b> — 成熟生物按概率繁殖后代，受密度限制</li>
 * </ul>
 */
public class CreatureManager {

    private static final Logger logger = LoggerFactory.getLogger(CreatureManager.class);

    /** 所有生物列表（向后兼容遍历） */
    private final List<Creature> creatures = new ArrayList<>();

    /** 空间索引（快速查询） */
    private final CreatureGrid creatureGrid = new CreatureGrid();

    /** 地图管理器 */
    private final ChunkManager chunkManager;

    /** 回合管理器 */
    private final com.github.game.cdda.game.TurnManager turnManager;

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
    private int bubbleCenterY = 0;
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

    // ═══════════════════════════════════════════════
    // 异步回合处理
    // ═══════════════════════════════════════════════

    /** 后台 AI 计算线程池 */
    private final ExecutorService turnExecutor;

    /** 变更队列（后台写入 → EDT 读取） */
    private final ConcurrentLinkedQueue<CreatureMutation> mutationQueue = new ConcurrentLinkedQueue<>();

    /** 是否有后台回合计算正在运行 */
    private final AtomicBoolean computingTurns = new AtomicBoolean(false);

    /** 上次请求回合处理时的回合数（防止重复提交） */
    private volatile int lastRequestedRound = -1;

    /**
     * 创建生物管理器。
     *
     * @param chunkManager 地图管理器
     * @param turnManager  回合管理器
     */
    public CreatureManager(ChunkManager chunkManager, com.github.game.cdda.game.TurnManager turnManager) {
        this.chunkManager = chunkManager;
        this.turnManager = turnManager;
        this.turnExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "creature-ai");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    // ═══════════════════════════════════════════════
    // 依赖注入
    // ═══════════════════════════════════════════════

    public void setGroundItemManager(GroundItemManager groundItemManager) {
        this.groundItemManager = groundItemManager;
    }

    public void setEnergyFlowManager(EnergyFlowManager energyFlowManager) {
        this.energyFlowManager = energyFlowManager;
    }

    public EnergyFlowManager getEnergyFlowManager() {
        return energyFlowManager;
    }

    // ═══════════════════════════════════════════════
    // 现实气泡
    // ═══════════════════════════════════════════════

    public void updateBubble(int centerTileX, int centerTileY) {
        this.bubbleCenterX = centerTileX;
        this.bubbleCenterY = centerTileY;
    }

    private boolean isInBubble(Creature creature) {
        if (bubbleCenterX < 0) return true;
        int dist = Math.abs(creature.getTileX() - bubbleCenterX)
                 + Math.abs(creature.getTileY() - bubbleCenterY);
        return dist <= BUBBLE_RADIUS;
    }

    // ═══════════════════════════════════════════════
    // 区块加载生成
    // ═══════════════════════════════════════════════

    public void onChunksGenerated(int minChunkX, int minChunkY, int maxChunkX, int maxChunkY) {
        int spawned = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                long key = chunkKey(cx, cy);
                if (spawnedChunks.contains(key)) continue;

                spawnedChunks.add(key);

                if (random.nextFloat() >= CHUNK_LOAD_SPAWN_CHANCE) continue;

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

    private boolean spawnCreatureInChunk(int chunkX, int chunkY) {
        int chunkSize = Chunk.SIZE;
        int baseTileX = chunkX * chunkSize;
        int baseTileY = chunkY * chunkSize;

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

    private void spawnCreaturesInChunkInitial(int chunkX, int chunkY) {
        int chunkSize = Chunk.SIZE;
        int baseTileX = chunkX * chunkSize;
        int baseTileY = chunkY * chunkSize;

        int count = 0;
        for (int i = 0; i < MAX_CREATURES_PER_CHUNK; i++) {
            if (random.nextFloat() < INITIAL_SPAWN_CHANCE) {
                count++;
            }
        }

        for (int i = 0; i < count; i++) {
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

    private static long chunkKey(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }

    private void injectEnergyFlowManager(Animal animal) {
        if (energyFlowManager != null) {
            animal.setEnergyFlowManager(energyFlowManager);
        }
    }

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

    // ═══════════════════════════════════════════════
    // 添加/移除生物（同步更新空间索引）
    // ═══════════════════════════════════════════════

    public void addCreature(Creature creature) {
        if (creature == null) return;
        creatures.add(creature);
        creatureGrid.add(creature);
        turnManager.addEntity(creature);
    }

    public void removeCreature(Creature creature) {
        creatures.remove(creature);
        creatureGrid.remove(creature);
        turnManager.removeEntity(creature);
    }

    // ═══════════════════════════════════════════════
    // 异步回合处理
    // ═══════════════════════════════════════════════

    /**
     * 请求处理生物回合（异步，立即返回）。
     * 后台线程执行 AI，变更放入队列。
     * EDT 通过 {@link #applyPendingCreatureMutations()} 应用。
     *
     * @param context 行动上下文（提供玩家位置等信息）
     */
    public void requestTurnProcessing(CreatureActionContext context) {
        int currentRound = turnManager.getCurrentRound();
        if (currentRound <= 0) return;

        // 防止重复提交同一回合
        if (currentRound == lastRequestedRound) return;

        // 如果上次计算还没完成，跳过（避免队列堆积）
        if (!computingTurns.compareAndSet(false, true)) return;

        lastRequestedRound = currentRound;

        final int round = currentRound;
        final int lastRepro = lastReproductionCheckRound;
        final long lastReproInterval = OUT_OF_BUBBLE_CHECK_INTERVAL;
        final int bx = bubbleCenterX;
        final int by = bubbleCenterY;
        final int bubbleR = BUBBLE_RADIUS;

        // 快照当前存活生物列表（后台线程只读这份快照）
        final List<Animal> snapshot = snapshotAliveAnimals();

        turnExecutor.submit(() -> {
            try {
                computeTurns(snapshot, context, round, lastRepro, lastReproInterval, bx, by, bubbleR);
            } finally {
                computingTurns.set(false);
            }
        });
    }

    /**
     * 快照当前存活动物列表（用于后台线程安全计算）。
     */
    private List<Animal> snapshotAliveAnimals() {
        List<Animal> result = new ArrayList<>(creatures.size());
        for (Creature c : creatures) {
            if (c.isAlive() && c instanceof Animal) {
                result.add((Animal) c);
            }
        }
        return result;
    }

    /**
     * 后台计算：执行所有生物的回合。
     */
    private void computeTurns(List<Animal> animals, CreatureActionContext context,
                              int currentRound, int lastReproductionCheckRound,
                              long checkInterval, int bubbleCenterX, int bubbleCenterY,
                              int bubbleRadius) {
        // 注入快照（AI 的 findNearestPrey 使用此快照而非全列表扫描）
        context.setTurnSnapshot(animals);

        List<Animal> newborns = new ArrayList<>();
        boolean doOutOfBubbleReproduction =
                currentRound - lastReproductionCheckRound >= checkInterval;

        for (Animal animal : animals) {
            if (!animal.isAlive()) continue;

            boolean inBubble;
            if (bubbleCenterX < 0) {
                inBubble = true;
            } else {
                int dist = Math.abs(animal.getTileX() - bubbleCenterX)
                         + Math.abs(animal.getTileY() - bubbleCenterY);
                inBubble = dist <= bubbleRadius;
            }

            if (inBubble) {
                // 气泡内：检查能量并执行 AI（while 循环，用完所有积攒的能量）
                while (turnManager.canAct(animal)) {
                    animal.takeTurn(context);
                    animal.spendEnergy(com.github.game.cdda.game.TurnManager.ENERGY_PER_ACTION);
                }
                // 繁殖检查
                Animal baby = tryReproduce(animal, currentRound);
                if (baby != null) newborns.add(baby);
            } else if (doOutOfBubbleReproduction) {
                // 气泡外：仅繁殖检查
                Animal baby = tryReproduce(animal, currentRound);
                if (baby != null) newborns.add(baby);
            }
        }

        // 出生变更
        for (Animal baby : newborns) {
            mutationQueue.add(CreatureMutation.birth(baby));
        }

        // 死亡变更 + 掉落（遍历完整列表，不仅是快照）
        // 快照只包含计算开始时的存活动物，被玩家中途击杀的动物不在快照中，
        // 但它们已经在主线程标记为死亡 → 需要扫描完整列表才能发现并触发掉落。
        // 使用 lootDropped 标记避免重复处理（后台线程可能在 mutationQueue 处理前多次扫到同一尸体）。
        for (Creature c : creatures) {
            if (!c.isAlive() && c instanceof Animal animal && !animal.isLootDropped()) {
                dropCreatureLoot(animal);
                mutationQueue.add(CreatureMutation.death(animal));
            }
        }

        // 能量流动更新
        if (energyFlowManager != null) {
            energyFlowManager.processDecay();
            energyFlowManager.updateVegetationBoosts();
            processMigrationSpawning(context);
        }
    }

    /**
     * 应用所有待处理的生物变更（必须在主线程/EDT 调用）。
     *
     * @return 应用的变更数量
     */
    public int applyPendingCreatureMutations() {
        int count = 0;
        CreatureMutation mutation;
        while ((mutation = mutationQueue.poll()) != null) {
            applyCreatureMutation(mutation);
            count++;
        }
        return count;
    }

    /**
     * 应用单条生物变更。
     */
    private void applyCreatureMutation(CreatureMutation m) {
        switch (m.type) {
            case BIRTH -> {
                Animal baby = (Animal) m.creature;
                injectEnergyFlowManager(baby);
                addCreature(baby);
                GameLog.getInstance().log(String.format("一只%s繁殖了后代！",
                        baby.getLocalizedName()));
            }
            case DEATH -> {
                removeCreature(m.creature);
            }
            case MIGRATION -> {
                Animal migrant = (Animal) m.creature;
                injectEnergyFlowManager(migrant);
                addCreature(migrant);
                GameLog.getInstance().log(String.format("迁徙触发: 一只%s来到了这个区域！",
                        migrant.getLocalizedName()));
                logger.info("迁徙生成: {} at ({},{})", migrant.getDefinition().name,
                        migrant.getTileX(), migrant.getTileY());
            }
        }
    }

    /**
     * 是否有后台计算正在运行。
     */
    public boolean isComputingTurns() {
        return computingTurns.get();
    }

    /**
     * 待应用的变更数量。
     */
    public int getPendingMutationCount() {
        return mutationQueue.size();
    }

    // ═══════════════════════════════════════════════
    // 回合处理（繁殖、迁徙 —— 供后台线程调用）
    // ═══════════════════════════════════════════════

    private Animal tryReproduce(Animal animal, int currentRound) {
        // 密度检查：使用空间索引，O(相邻区块) 而非 O(全列表)
        int sameCount = creatureGrid.countSameSpeciesNearby(animal, 5);
        if (sameCount >= MAX_NEARBY_SAME_SPECIES) return null;

        Animal offspring = animal.tryReproduce(currentRound, random);
        if (offspring == null) return null;

        boolean placed = placeNearby(offspring, animal.getTileX(), animal.getTileY());
        if (!placed) return null;

        return offspring;
    }

    private boolean placeNearby(Animal animal, int centerX, int centerY) {
        TileType centerTile = chunkManager.getTile(centerX, centerY);
        if (centerTile != null && centerTile.isPassable()) {
            return true;
        }

        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};
        for (int i = 0; i < dx.length; i++) {
            int nx = centerX + dx[i];
            int ny = centerY + dy[i];
            TileType tile = chunkManager.getTile(nx, ny);
            if (tile != null && tile.isPassable()) {
                animal.setTileX(nx);
                animal.setTileY(ny);
                return true;
            }
        }
        return false;
    }

    private void processMigrationSpawning(CreatureActionContext context) {
        int currentRound = turnManager.getCurrentRound();
        if (currentRound % 100 != 0) return;

        int playerTileX = context.getPlayerTileX();
        int playerTileY = context.getPlayerTileY();
        int playerChunkX = playerTileX >> 5;
        int playerChunkY = playerTileY >> 5;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int cx = playerChunkX + dx;
                int cy = playerChunkY + dy;
                long chunkKey = chunkKey(cx, cy);
                trySpawnMigratingPredator(cx, cy, chunkKey);
            }
        }
    }

    private void trySpawnMigratingPredator(int chunkX, int chunkY, long chunkKey) {
        if (energyFlowManager == null) return;

        // 使用空间索引 O(1) 查询替代 O(N) 扫描
        int apexCount = countApePredatorsInChunk(chunkX, chunkY);
        if (apexCount >= energyFlowManager.getMaxApexPerChunk()) return;

        if (energyFlowManager.shouldSpawnPredator(chunkKey, TrophicLevel.SECONDARY_CONSUMER)) {
            spawnPredatorInChunk(chunkX, chunkY, TrophicLevel.SECONDARY_CONSUMER);
            return;
        }

        if (energyFlowManager.shouldSpawnPredator(chunkKey, TrophicLevel.APEX_PREDATOR)) {
            spawnPredatorInChunk(chunkX, chunkY, TrophicLevel.APEX_PREDATOR);
        }
    }

    /**
     * 统计区块内顶级捕食者数量（使用空间索引，O(单个区块)）。
     */
    private int countApePredatorsInChunk(int chunkX, int chunkY) {
        List<Creature> chunkCreatures = creatureGrid.getInChunk(chunkX, chunkY);
        int count = 0;
        for (Creature c : chunkCreatures) {
            if (c instanceof Animal a && a.getDefinition().getTrophicLevel() == TrophicLevel.APEX_PREDATOR) {
                count++;
            }
        }
        return count;
    }

    private void spawnPredatorInChunk(int chunkX, int chunkY, TrophicLevel trophicLevel) {
        int chunkSize = Chunk.SIZE;
        int baseTileX = chunkX * chunkSize;
        int baseTileY = chunkY * chunkSize;

        for (int attempt = 0; attempt < 20; attempt++) {
            int tileX = baseTileX + random.nextInt(chunkSize);
            int tileY = baseTileY + random.nextInt(chunkSize);

            TileType tile = chunkManager.getTile(tileX, tileY);
            if (tile == null || !tile.isPassable()) continue;

            CreatureDefinition def = getCreatureByTrophicLevel(trophicLevel);
            if (def == null) continue;

            Animal animal = new Animal(def, tileX, tileY);
            injectEnergyFlowManager(animal);
            mutationQueue.add(CreatureMutation.migration(animal));
            return;
        }
    }

    private CreatureDefinition getCreatureByTrophicLevel(TrophicLevel level) {
        Collection<CreatureDefinition> all = CreatureRegistry.getAll();
        if (all.isEmpty()) return null;

        List<CreatureDefinition> matching = new ArrayList<>();
        for (CreatureDefinition def : all) {
            if (def.getTrophicLevel() == level) {
                matching.add(def);
            }
        }
        if (matching.isEmpty()) return null;
        return matching.get(random.nextInt(matching.size()));
    }

    // ═══════════════════════════════════════════════
    // 死亡掉落
    // ═══════════════════════════════════════════════

    private void dropCreatureLoot(Creature creature) {
        if (groundItemManager == null) return;
        if (!(creature instanceof Animal)) return;

        Animal animal = (Animal) creature;

        // 避免重复掉落（后台线程可能在 mutationQueue 处理前再次扫描到同一尸体）
        if (animal.isLootDropped()) return;

        if (animal.getDeathCause() != DeathCause.PLAYER_KILL) {
            return;
        }

        // 立即标记，防止后续回合重复掉落
        animal.setLootDropped(true);

        CreatureDefinition def = animal.getDefinition();
        LootTable lootTable = def.getKillLootTable();

        if (lootTable == null) return;

        List<ItemStack> drops = lootTable.roll(random);
        for (ItemStack stack : drops) {
            groundItemManager.dropItem(stack, creature.getTileX(), creature.getTileY());
        }
        if (!drops.isEmpty()) {
            GameLog.getInstance().log(String.format("%s 掉落了 %d 件物品",
                    animal.getLocalizedName(), drops.size()));
        }
    }

    // ═══════════════════════════════════════════════
    // 查询（使用空间索引）
    // ═══════════════════════════════════════════════

    /**
     * 获取指定瓦片位置的生物（O(1)）。
     */
    public Creature getCreatureAtTile(int tileX, int tileY) {
        return creatureGrid.getAtTile(tileX, tileY);
    }

    /**
     * 获取指定范围内的所有存活生物，按曼哈顿距离升序排序。
     * 使用空间索引，仅查询相关区块。
     */
    public List<Creature> getVisibleCreatures(int centerTileX, int centerTileY, int maxDistance) {
        List<Creature> result = creatureGrid.getInRadius(centerTileX, centerTileY, maxDistance);
        result.sort((a, b) -> {
            int distA = Math.abs(a.getTileX() - centerTileX) + Math.abs(a.getTileY() - centerTileY);
            int distB = Math.abs(b.getTileX() - centerTileX) + Math.abs(b.getTileY() - centerTileY);
            return Integer.compare(distA, distB);
        });
        return result;
    }

    public List<Creature> getCreatures() {
        return creatures;
    }

    public int getCreatureCount() {
        return creatureGrid.totalCreatureCount();
    }

    /** 获取生物空间索引（供 AI 移动时更新位置） */
    public CreatureGrid getCreatureGrid() {
        return creatureGrid;
    }

    /**
     * 获取所有存活动物（用于 AI 查询，避免迭代死亡个体）。
     */
    List<Animal> getAliveAnimals() {
        List<Animal> result = new ArrayList<>();
        for (Creature c : creatures) {
            if (c.isAlive() && c instanceof Animal) {
                result.add((Animal) c);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════
    // 渲染（使用空间索引，仅渲染可见区域）
    // ═══════════════════════════════════════════════

    /**
     * 渲染可见范围内的生物。
     * 通过摄像机视口计算可见区块，仅遍历这些区块内的生物。
     *
     * @param renderer   渲染器
     * @param camera     摄像机
     * @param tileWidth  瓦片像素宽度
     * @param tileHeight 瓦片像素高度
     */
    public void renderCreatures(Renderer renderer, Camera camera, int tileWidth, int tileHeight) {
        // 计算摄像机视口覆盖的瓦片范围
        int viewStartX = camera.getX();
        int viewStartY = camera.getY();
        int viewWidth = camera.getViewportWidth();
        int viewHeight = camera.getViewportHeight();

        int startTileX = viewStartX / tileWidth;
        int startTileY = viewStartY / tileHeight;
        int endTileX = (viewStartX + viewWidth) / tileWidth;
        int endTileY = (viewStartY + viewHeight) / tileHeight;

        // 向外扩展一个区块，确保边界生物不遗漏
        int startChunkX = Math.floorDiv(startTileX - Chunk.SIZE, Chunk.SIZE);
        int startChunkY = Math.floorDiv(startTileY - Chunk.SIZE, Chunk.SIZE);
        int endChunkX = Math.floorDiv(endTileX + Chunk.SIZE, Chunk.SIZE);
        int endChunkY = Math.floorDiv(endTileY + Chunk.SIZE, Chunk.SIZE);

        // 仅遍历可见区块
        for (int cy = startChunkY; cy <= endChunkY; cy++) {
            for (int cx = startChunkX; cx <= endChunkX; cx++) {
                List<Creature> chunkCreatures = creatureGrid.getInChunk(cx, cy);
                for (Creature creature : chunkCreatures) {
                    if (creature.isAlive()) {
                        creature.render(renderer, camera, tileWidth, tileHeight);
                    }
                }
            }
        }
    }

    /**
     * 关闭后台线程池（游戏退出时调用）。
     */
    public void shutdown() {
        turnExecutor.shutdownNow();
    }
}
