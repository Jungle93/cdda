package com.github.game.cdda.world.vegetation;

import com.github.game.cdda.game.PlantGrowthConstants;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.cdda.world.chunk.SoilFertility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 植物生长系统（后台异步版）。
 *
 * <p>管理世界中所有植物的生长、枯萎、传播和定殖。
 * 与地块肥力系统联动，实现生态系统循环。
 *
 * <h3>核心机制：</h3>
 * <ol>
 *   <li><b>生长推进</b> — 每 2 游戏小时，所有植物推进生长阶段</li>
 *   <li><b>肥力消耗</b> — 每游戏日，植物根据类型和阶段消耗肥力</li>
 *   <li><b>肥力恢复</b> — 每 4 游戏小时，空闲地块恢复肥力</li>
 *   <li><b>植物枯萎</b> — 肥力低于需求时，健康度下降 → 枯萎</li>
 *   <li><b>植物传播</b> — 成熟植物向相邻空地扩散种子</li>
 *   <li><b>野生定殖</b> — 肥力足够且无植物的空地可能长出新植物</li>
 * </ol>
 *
 * <h3>线程模型：</h3>
 * <p>采用"后台计算 + 前台应用"模式：
 * <ul>
 *   <li><b>后台线程</b> — 遍历区块、计算生长、收集变更</li>
 *   <li><b>主线程（EDT）</b> — 调用 {@link #applyPendingMutations()} 批量应用变更</li>
 * </ul>
 * <p>变更通过 {@link Mutation} 对象传递，线程安全的 {@link ConcurrentLinkedQueue} 存储。
 * 后台线程只读区块数据，主线程负责写入，避免竞态。
 *
 * <h3>时间系统：</h3>
 * <p>使用游戏总秒数（GameClock.totalSeconds）作为时间基准。
 * 系统记录上次更新时的总秒数，计算时间差来推进生长。
 */
public class PlantGrowthSystem {

    private static final Logger logger = LoggerFactory.getLogger(PlantGrowthSystem.class);

    /** 游戏时间常量：1 游戏日 = 86400 游戏秒 */
    private static final long SECONDS_PER_DAY = 86400L;
    /** 1 游戏小时 = 3600 游戏秒 */
    private static final long SECONDS_PER_HOUR = 3600L;

    /** 区块管理器（用于遍历已加载区块） */
    private final ChunkManager chunkManager;

    /** 上次生长更新时的总游戏秒数 */
    private volatile long lastGrowthUpdateTotalSeconds = 0;
    /** 上次肥力恢复更新时的总游戏秒数 */
    private volatile long lastFertilityUpdateTotalSeconds = 0;
    /** 上次野生定殖检查时的总游戏秒数 */
    private volatile long lastColonizeCheckTotalSeconds = 0;

    /** 随机数生成器（使用种子偏移确保可复现） */
    private final Random random = new Random(42);

    /** 后台计算线程池（单线程，顺序执行避免竞态） */
    private final ExecutorService growthExecutor;

    /** 变更队列（后台写入 → 主线程读取） */
    private final ConcurrentLinkedQueue<Mutation> mutationQueue = new ConcurrentLinkedQueue<>();

    /** 是否有后台任务正在运行 */
    private final AtomicBoolean computing = new AtomicBoolean(false);

    /** 玩家区块坐标（供后台线程读取） */
    private volatile int playerChunkX = 0;
    private volatile int playerChunkY = 0;

    /** 统计：上次更新的植物总数 */
    private volatile int lastProcessedPlantCount = 0;
    /** 统计：上次更新枯萎的植物数 */
    private volatile int lastWitheredCount = 0;
    /** 统计：上次更新新传播的植物数 */
    private volatile int lastSpreadCount = 0;

    /**
     * 变更类型。
     * 后台线程产生变更，主线程应用变更。
     */
    public enum MutationType {
        /** 瓦片类型改变 */
        TILE_CHANGE,
        /** 生长状态更新（健康度、阶段等） */
        GROWTH_STATE_UPDATE,
        /** 肥力消耗 */
        FERTILITY_CONSUME,
        /** 肥力恢复 */
        FERTILITY_RECOVER,
        /** 新植物放置 */
        NEW_PLANT
    }

    /**
     * 单条变更。
     */
    public static class Mutation {
        public final MutationType type;
        public final int chunkX, chunkY;
        public final int col, row;
        public final double value;      // 肥力变化量 / 健康度
        public final String speciesId;  // 新植物物种 ID
        public final TileType tileType; // 新瓦片类型

        private Mutation(MutationType type, int chunkX, int chunkY, int col, int row,
                         double value, String speciesId, TileType tileType) {
            this.type = type;
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.col = col;
            this.row = row;
            this.value = value;
            this.speciesId = speciesId;
            this.tileType = tileType;
        }

        public static Mutation tileChange(int chunkX, int chunkY, int col, int row, TileType tileType) {
            return new Mutation(MutationType.TILE_CHANGE, chunkX, chunkY, col, row, 0, null, tileType);
        }

        public static Mutation growthStateUpdate(int chunkX, int chunkY, int col, int row,
                                                  double health, GrowthStage stage, int days) {
            Mutation m = new Mutation(MutationType.GROWTH_STATE_UPDATE, chunkX, chunkY, col, row, health, null, null);
            m._stage = stage;
            m._days = days;
            return m;
        }

        public static Mutation fertilityConsume(int chunkX, int chunkY, int col, int row, double amount) {
            return new Mutation(MutationType.FERTILITY_CONSUME, chunkX, chunkY, col, row, -amount, null, null);
        }

        public static Mutation fertilityRecover(int chunkX, int chunkY, int col, int row, double amount) {
            return new Mutation(MutationType.FERTILITY_RECOVER, chunkX, chunkY, col, row, amount, null, null);
        }

        public static Mutation newPlant(int chunkX, int chunkY, int col, int row,
                                         String speciesId, TileType tileType) {
            return new Mutation(MutationType.NEW_PLANT, chunkX, chunkY, col, row, 0, speciesId, tileType);
        }

        // 扩展字段（仅 GROWTH_STATE_UPDATE 使用）
        private GrowthStage _stage;
        private int _days;

        public GrowthStage getStage() { return _stage; }
        public int getDays() { return _days; }
    }

    /**
     * 创建植物生长系统。
     *
     * @param chunkManager 区块管理器
     */
    public PlantGrowthSystem(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
        this.growthExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "plant-growth");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // 略低于主线程
            return t;
        });
    }

    // ═══════════════════════════════════════════════
    // 外部接口
    // ═══════════════════════════════════════════════

    /**
     * 请求生长更新。
     * 该方法立即返回，实际计算在后台线程执行。
     * 主线程通过调用 {@link #applyPendingMutations()} 应用变更。
     *
     * @param currentTotalSeconds 当前游戏总秒数
     */
    public void requestUpdate(long currentTotalSeconds) {
        if (currentTotalSeconds <= 0) {
            lastGrowthUpdateTotalSeconds = currentTotalSeconds;
            lastFertilityUpdateTotalSeconds = currentTotalSeconds;
            lastColonizeCheckTotalSeconds = currentTotalSeconds;
            return;
        }

        // 首次运行时，初始化已有植物的生长状态（同步）
        if (lastGrowthUpdateTotalSeconds == 0) {
            initializeExistingVegetation(currentTotalSeconds);
            lastGrowthUpdateTotalSeconds = currentTotalSeconds;
            lastFertilityUpdateTotalSeconds = currentTotalSeconds;
            lastColonizeCheckTotalSeconds = currentTotalSeconds;
            return;
        }

        long elapsedSeconds = currentTotalSeconds - lastGrowthUpdateTotalSeconds;
        if (elapsedSeconds < SECONDS_PER_HOUR) return; // 至少 1 小时才更新

        // 如果上次计算还没完成，跳过本次（避免队列堆积）
        if (!computing.compareAndSet(false, true)) return;

        // 提交到后台线程
        long elapsedDays = elapsedSeconds / SECONDS_PER_DAY;
        long elapsedHours = elapsedSeconds / SECONDS_PER_HOUR;
        final long currentTs = currentTotalSeconds;
        final long eDays = elapsedDays;
        final long eHours = elapsedHours;

        growthExecutor.submit(() -> {
            try {
                computeGrowth(currentTs, eDays, eHours);
            } finally {
                computing.set(false);
            }
        });
    }

    /**
     * 应用所有待处理的变更（必须在主线程/EDT 调用）。
     *
     * @return 应用的变更数量
     */
    public int applyPendingMutations() {
        int count = 0;
        Mutation mutation;
        while ((mutation = mutationQueue.poll()) != null) {
            applyMutation(mutation);
            count++;
        }
        return count;
    }

    /**
     * 应用单条变更。
     */
    private void applyMutation(Mutation m) {
        Chunk chunk = chunkManager.getChunk(m.chunkX, m.chunkY);
        if (chunk == null || !chunk.isGenerated()) return;

        VegetationMap vegMap = chunk.getVegetationMap();
        SoilFertility fertility = chunk.getSoilFertility();

        switch (m.type) {
            case TILE_CHANGE -> chunk.setTile(m.col, m.row, m.tileType);

            case GROWTH_STATE_UPDATE -> {
                VegetationState state = vegMap.getGrowthState(m.col, m.row);
                if (state != null) {
                    state.health = m.value;
                    if (m.getStage() != null) {
                        state.stage = m.getStage();
                    }
                    if (m.getDays() >= 0) {
                        state.totalGrowthDays = m.getDays();
                    }
                }
            }

            case FERTILITY_CONSUME -> {
                if (fertility != null) {
                    fertility.consumeFertility(m.col, m.row, Math.abs(m.value));
                }
            }

            case FERTILITY_RECOVER -> {
                if (fertility != null) {
                    fertility.recoverFertility(m.col, m.row, m.value,
                            PlantGrowthConstants.FERTILITY_RECOVERY_CAP);
                }
            }

            case NEW_PLANT -> {
                if (vegMap != null && !vegMap.hasVegetation(m.col, m.row)) {
                    VegetationState state = new VegetationState(m.speciesId, lastGrowthUpdateTotalSeconds);
                    vegMap.setVegetation(m.col, m.row, m.speciesId);
                    vegMap.setGrowthState(m.col, m.row, state);
                }
                if (m.tileType != null) {
                    chunk.setTile(m.col, m.row, m.tileType);
                }
            }
        }
    }

    /**
     * 后台计算核心。
     * 读取区块数据，计算所有变更，写入变更队列。
     */
    private void computeGrowth(long currentTotalSeconds, long elapsedDays, long elapsedHours) {
        int totalWithered = 0;
        int totalSpread = 0;
        int totalProcessed = 0;

        List<Chunk> chunks = getLoadedChunks();

        // ── 1. 植物生长推进 + 肥力消耗 ──
        if (shouldUpdateGrowth(elapsedHours)) {
            for (Chunk chunk : chunks) {
                int[] result = computeChunkGrowth(chunk, (int) elapsedDays);
                totalProcessed += result[0];
                totalWithered += result[1];
            }
        }

        // ── 2. 肥力恢复 ──
        if (shouldUpdateFertility(elapsedHours)) {
            for (Chunk chunk : chunks) {
                computeFertilityRecovery(chunk);
            }
        }

        // ── 3. 植物传播 ──
        if (elapsedDays > 0) {
            List<SpreadCandidate> spreadCandidates = new ArrayList<>();
            for (Chunk chunk : chunks) {
                spreadCandidates.addAll(findSpreadCandidates(chunk));
            }
            totalSpread = executeSpread(spreadCandidates);
        }

        // ── 4. 野生定殖 ──
        if (shouldCheckColonize(elapsedHours)) {
            for (Chunk chunk : chunks) {
                computeWildColonization(chunk);
            }
        }

        // 更新统计
        lastProcessedPlantCount = totalProcessed;
        lastWitheredCount = totalWithered;
        lastSpreadCount = totalSpread;

        if (totalWithered > 0 || totalSpread > 0) {
            logger.debug("植物生长更新：处理={} 棵，枯萎={} 棵，传播={} 棵",
                    totalProcessed, totalWithered, totalSpread);
        }

        // 更新时间戳
        lastGrowthUpdateTotalSeconds = currentTotalSeconds;
        lastFertilityUpdateTotalSeconds = currentTotalSeconds;
        lastColonizeCheckTotalSeconds = currentTotalSeconds;
    }

    // ═══════════════════════════════════════════════
    // 初始化
    // ═══════════════════════════════════════════════

    /**
     * 为已有植被初始化生长状态（首次运行时调用，同步执行）。
     * 世界生成时只放置了瓦片类型和物种 ID，没有生长状态。
     */
    private void initializeExistingVegetation(long currentTotalSeconds) {
        int initialized = 0;
        for (Chunk chunk : getLoadedChunks()) {
            VegetationMap vegMap = chunk.getVegetationMap();
            SoilFertility fertility = chunk.getSoilFertility();
            if (vegMap == null || fertility == null) continue;

            for (int row = 0; row < VegetationMap.SIZE; row++) {
                for (int col = 0; col < VegetationMap.SIZE; col++) {
                    if (vegMap.getGrowthState(col, row) != null) continue;

                    String speciesId = vegMap.getSpeciesId(col, row);
                    if (speciesId == null) continue;

                    VegetationState state = new VegetationState(speciesId, currentTotalSeconds);
                    state.totalGrowthDays = 999;
                    state.stage = GrowthStage.MATURE;
                    state.health = 1.0;
                    state.lastFertilityConsumeDay = 0;

                    vegMap.setGrowthState(col, row, state);
                    initialized++;
                }
            }
        }
        if (initialized > 0) {
            logger.info("植物生长系统初始化：为 {} 棵已有植物创建生长状态", initialized);
        }
    }

    // ═══════════════════════════════════════════════
    // 生长推进（后台计算 → 队列写入）
    // ═══════════════════════════════════════════════

    /**
     * 计算单个区块内所有植物的生长变更。
     *
     * @return [处理植物数, 枯萎植物数]
     */
    private int[] computeChunkGrowth(Chunk chunk, int elapsedDays) {
        VegetationMap vegMap = chunk.getVegetationMap();
        SoilFertility fertility = chunk.getSoilFertility();
        if (vegMap == null || fertility == null) return new int[]{0, 0};

        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();
        int processed = 0;
        int withered = 0;

        for (int row = 0; row < VegetationMap.SIZE; row++) {
            for (int col = 0; col < VegetationMap.SIZE; col++) {
                VegetationState state = vegMap.getGrowthState(col, row);
                if (state == null || state.stage.isDead()) continue;

                processed++;

                // 增加生长天数
                state.totalGrowthDays += elapsedDays;

                // 检查肥力是否足够
                double currentFertility = fertility.getFertility(col, row);
                if (!state.isFertilitySufficient(currentFertility)) {
                    // 肥力不足 → 健康度下降
                    double deficitRate = 0.1 * elapsedDays;
                    double newHealth = Math.max(0.0, state.health - deficitRate);

                    if (newHealth <= 0) {
                        // 枯萎
                        witherPlant(chunkX, chunkY, col, row, state, vegMap);
                        withered++;
                        continue;
                    }

                    mutationQueue.add(Mutation.growthStateUpdate(
                            chunkX, chunkY, col, row, newHealth, null, state.totalGrowthDays));
                } else {
                    // 肥力充足 → 健康度恢复
                    double newHealth = Math.min(1.0, state.health + 0.02 * elapsedDays);
                    mutationQueue.add(Mutation.growthStateUpdate(
                            chunkX, chunkY, col, row, newHealth, null, state.totalGrowthDays));
                }

                // 消耗肥力
                int startDay = state.lastFertilityConsumeDay;
                int endDay = state.totalGrowthDays;
                if (endDay > startDay) {
                    double totalCost = state.getDailyFertilityCost() * (endDay - startDay);
                    if (totalCost > 0) {
                        mutationQueue.add(Mutation.fertilityConsume(chunkX, chunkY, col, row, totalCost));
                    }
                }
                state.lastFertilityConsumeDay = endDay;

                // 检查阶段推进
                while (state.shouldAdvanceStage()) {
                    state.advanceStage();
                    if (state.stage.isDead()) {
                        witherPlant(chunkX, chunkY, col, row, state, vegMap);
                        withered++;
                        break;
                    } else {
                        mutationQueue.add(Mutation.growthStateUpdate(
                                chunkX, chunkY, col, row, state.health, state.stage, state.totalGrowthDays));
                    }
                }
            }
        }

        return new int[]{processed, withered};
    }

    /**
     * 将植物标记为枯萎，产生变更。
     */
    private void witherPlant(int chunkX, int chunkY, int col, int row,
                              VegetationState state, VegetationMap vegMap) {
        state.wither();

        // 更新生长状态
        mutationQueue.add(Mutation.growthStateUpdate(
                chunkX, chunkY, col, row, 0.0, GrowthStage.WITHERED, state.totalGrowthDays));

        // 获取当前瓦片类型 → 决定枯萎瓦片类型
        Chunk chunk = chunkManager.getChunk(chunkX, chunkY);
        if (chunk == null) return;

        TileType currentTile = chunk.getTile(col, row);
        TileType witheredTile = getWitheredTileType(currentTile);

        if (witheredTile != null) {
            mutationQueue.add(Mutation.tileChange(chunkX, chunkY, col, row, witheredTile));
        }
    }

    /**
     * 根据当前瓦片类型获取对应的枯萎瓦片类型。
     */
    private TileType getWitheredTileType(TileType current) {
        if (current == TileType.TREE) return TileType.WITHERED_TREE;
        if (current == TileType.BUSH) return TileType.WITHERED_BUSH;
        if (current == TileType.FLOWER) return TileType.DEAD_GRASS;
        if (current == TileType.TALL_GRASS) return TileType.DEAD_GRASS;
        if (current == TileType.REEDS) return TileType.DEAD_GRASS;
        if (current == TileType.WITHERED_TREE
                || current == TileType.WITHERED_BUSH
                || current == TileType.DEAD_GRASS) {
            return current;
        }
        return null;
    }

    // ═══════════════════════════════════════════════
    // 肥力恢复（后台计算 → 队列写入）
    // ═══════════════════════════════════════════════

    /**
     * 计算区块内空闲地块的肥力恢复。
     */
    private void computeFertilityRecovery(Chunk chunk) {
        SoilFertility fertility = chunk.getSoilFertility();
        VegetationMap vegMap = chunk.getVegetationMap();
        if (fertility == null || vegMap == null) return;

        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();

        for (int row = 0; row < VegetationMap.SIZE; row++) {
            for (int col = 0; col < VegetationMap.SIZE; col++) {
                VegetationState state = vegMap.getGrowthState(col, row);
                double recoveryRate;

                if (state == null) {
                    recoveryRate = PlantGrowthConstants.DAILY_FERTILITY_RECOVERY;
                } else if (state.stage.isDead()) {
                    recoveryRate = PlantGrowthConstants.WITHERED_DAILY_FERTILITY_RECOVERY;
                } else {
                    recoveryRate = PlantGrowthConstants.VEGETATED_DAILY_FERTILITY_RECOVERY;
                }

                double current = fertility.getFertility(col, row);
                if (current < PlantGrowthConstants.FERTILITY_RECOVERY_CAP) {
                    mutationQueue.add(Mutation.fertilityRecover(chunkX, chunkY, col, row, recoveryRate));
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 植物传播（后台计算 → 队列写入）
    // ═══════════════════════════════════════════════

    /** 传播候选：源瓦片 → 目标瓦片 */
    private static class SpreadCandidate {
        final int chunkX, chunkY;
        final int sourceCol, sourceRow;
        final int targetCol, targetRow;
        final String speciesId;
        final double probability;
        final TileType targetTileType;

        SpreadCandidate(int chunkX, int chunkY, int sourceCol, int sourceRow,
                        int targetCol, int targetRow, String speciesId,
                        double probability, TileType targetTileType) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.sourceCol = sourceCol;
            this.sourceRow = sourceRow;
            this.targetCol = targetCol;
            this.targetRow = targetRow;
            this.speciesId = speciesId;
            this.probability = probability;
            this.targetTileType = targetTileType;
        }
    }

    /**
     * 查找区块内可以传播的植物。
     */
    private List<SpreadCandidate> findSpreadCandidates(Chunk chunk) {
        List<SpreadCandidate> candidates = new ArrayList<>();
        VegetationMap vegMap = chunk.getVegetationMap();
        SoilFertility fertility = chunk.getSoilFertility();
        if (vegMap == null || fertility == null) return candidates;

        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();

        int[] dx = {0, 0, -1, 1, -1, -1, 1, 1};
        int[] dy = {-1, 1, 0, 0, -1, 1, -1, 1};

        for (int row = 0; row < VegetationMap.SIZE; row++) {
            for (int col = 0; col < VegetationMap.SIZE; col++) {
                VegetationState state = vegMap.getGrowthState(col, row);
                if (state == null || state.stage != GrowthStage.MATURE) continue;

                double spreadProb = PlantGrowthConstants.getSpreadProbability(state.getPlantType());
                if (spreadProb <= 0) continue;

                String speciesId = vegMap.getSpeciesId(col, row);
                TileType targetTileType = getTileTypeForSpecies(speciesId);

                for (int d = 0; d < 8; d++) {
                    int nc = col + dx[d];
                    int nr = row + dy[d];
                    if (nc < 0 || nc >= VegetationMap.SIZE || nr < 0 || nr >= VegetationMap.SIZE) {
                        continue;
                    }

                    if (vegMap.hasVegetation(nc, nr)) continue;

                    if (fertility.getFertility(nc, nr) < PlantGrowthConstants.SPREAD_FERTILITY_MIN) {
                        continue;
                    }

                    TileType targetTile = chunk.getTile(nc, nr);
                    if (!canGrowOn(targetTile)) continue;

                    candidates.add(new SpreadCandidate(
                            chunkX, chunkY, col, row, nc, nr, speciesId, spreadProb, targetTileType));
                }
            }
        }

        return candidates;
    }

    /**
     * 执行传播（后台线程内直接产生变更）。
     */
    private int executeSpread(List<SpreadCandidate> candidates) {
        int spread = 0;
        for (SpreadCandidate c : candidates) {
            if (random.nextDouble() < c.probability) {
                mutationQueue.add(Mutation.newPlant(
                        c.chunkX, c.chunkY, c.targetCol, c.targetRow, c.speciesId, c.targetTileType));
                spread++;
            }
        }
        return spread;
    }

    // ═══════════════════════════════════════════════
    // 野生定殖（后台计算 → 队列写入）
    // ═══════════════════════════════════════════════

    /**
     * 计算区块内的野生植物定殖。
     */
    private void computeWildColonization(Chunk chunk) {
        VegetationMap vegMap = chunk.getVegetationMap();
        SoilFertility fertility = chunk.getSoilFertility();
        if (vegMap == null || fertility == null) return;

        int chunkX = chunk.getChunkX();
        int chunkY = chunk.getChunkY();

        for (int row = 0; row < VegetationMap.SIZE; row++) {
            for (int col = 0; col < VegetationMap.SIZE; col++) {
                if (vegMap.hasVegetation(col, row)) continue;

                TileType tile = chunk.getTile(col, row);
                if (!canGrowOn(tile)) continue;

                if (!fertility.canColonize(col, row,
                        PlantGrowthConstants.WILD_COLONIZE_FERTILITY_MIN)) {
                    continue;
                }

                double hash = tileHash(chunkX * VegetationMap.SIZE + col,
                        chunkY * VegetationMap.SIZE + row);
                double prob = PlantGrowthConstants.WILD_COLONIZE_BASE_PROBABILITY;

                if (hash < prob) {
                    int globalX = chunkX * VegetationMap.SIZE + col;
                    int globalY = chunkY * VegetationMap.SIZE + row;
                    String speciesId = selectWildSpecies(globalX, globalY);
                    if (speciesId != null) {
                        TileType tileType = getTileTypeForSpecies(speciesId);
                        mutationQueue.add(Mutation.newPlant(
                                chunkX, chunkY, col, row, speciesId, tileType));
                    }
                }
            }
        }
    }

    /**
     * 为野生定殖选择适生物种。
     */
    private String selectWildSpecies(int globalX, int globalY) {
        var worldMap = chunkManager.getWorldMap();
        if (worldMap == null) return null;

        double temperature = worldMap.getTemperatureAt(globalX, globalY);
        double humidity = worldMap.getHumidityAt(globalX, globalY);
        double soilDepth = worldMap.getSoilDepthAt(globalX, globalY);

        VegetationType[] priority = {VegetationType.GRASS, VegetationType.MOSS,
                VegetationType.SHRUB, VegetationType.TREE};

        for (VegetationType type : priority) {
            List<VegetationDefinition> candidates = VegetationRegistry.getForEnvironment(
                    temperature, humidity, soilDepth, type);
            if (!candidates.isEmpty()) {
                return candidates.get(0).id;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════

    /**
     * 根据物种 ID 获取对应的瓦片类型。
     */
    private TileType getTileTypeForSpecies(String speciesId) {
        VegetationDefinition def = VegetationRegistry.getById(speciesId);
        if (def == null || def.type == null) return TileType.TALL_GRASS;

        return switch (def.type) {
            case TREE -> TileType.TREE;
            case SHRUB -> TileType.BUSH;
            case GRASS, MOSS -> TileType.TALL_GRASS;
            case AQUATIC -> TileType.REEDS;
        };
    }

    /**
     * 检查某类地形是否可以生长植物。
     */
    private boolean canGrowOn(TileType tile) {
        return tile == TileType.GRASS
                || tile == TileType.DIRT
                || tile == TileType.MUD;
    }

    /**
     * 确定性瓦片哈希函数（0~1）。
     */
    private static double tileHash(int x, int y) {
        long h = (long) x * 374761393L + (long) y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL;
    }

    /**
     * 获取所有已加载的区块。
     */
    private List<Chunk> getLoadedChunks() {
        List<Chunk> result = new ArrayList<>();
        int radius = chunkManager.getPreloadRadius();
        for (int cy = -radius; cy <= radius; cy++) {
            for (int cx = -radius; cx <= radius; cx++) {
                Chunk chunk = chunkManager.getChunk(playerChunkX + cx, playerChunkY + cy);
                if (chunk != null && chunk.isGenerated()) {
                    result.add(chunk);
                }
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════
    // 时间判定
    // ═══════════════════════════════════════════════

    private boolean shouldUpdateGrowth(long elapsedHours) {
        return elapsedHours >= PlantGrowthConstants.GROWTH_UPDATE_INTERVAL_HOURS;
    }

    private boolean shouldUpdateFertility(long elapsedHours) {
        return elapsedHours >= PlantGrowthConstants.FERTILITY_UPDATE_INTERVAL_HOURS;
    }

    private boolean shouldCheckColonize(long elapsedHours) {
        return elapsedHours >= PlantGrowthConstants.COLONIZE_CHECK_INTERVAL_HOURS;
    }

    // ═══════════════════════════════════════════════
    // 外部接口
    // ═══════════════════════════════════════════════

    /**
     * 设置玩家位置（用于确定区块遍历中心）。
     */
    public void setPlayerPosition(int playerTileX, int playerTileY) {
        this.playerChunkX = Math.floorDiv(playerTileX, VegetationMap.SIZE);
        this.playerChunkY = Math.floorDiv(playerTileY, VegetationMap.SIZE);
    }

    // ── 统计访问器 ──

    public int getLastProcessedPlantCount() { return lastProcessedPlantCount; }
    public int getLastWitheredCount() { return lastWitheredCount; }
    public int getLastSpreadCount() { return lastSpreadCount; }

    /** 是否有后台计算正在运行 */
    public boolean isComputing() { return computing.get(); }

    /** 待应用的变更数量 */
    public int getPendingMutationCount() { return mutationQueue.size(); }

    /**
     * 获取指定世界坐标处的植物生长状态。
     */
    public VegetationState getVegetationStateAt(int worldTileX, int worldTileY) {
        int cx = Math.floorDiv(worldTileX, VegetationMap.SIZE);
        int cy = Math.floorDiv(worldTileY, VegetationMap.SIZE);
        Chunk chunk = chunkManager.getChunk(cx, cy);
        if (chunk == null || !chunk.isGenerated()) return null;

        int localCol = Math.floorMod(worldTileX, VegetationMap.SIZE);
        int localRow = Math.floorMod(worldTileY, VegetationMap.SIZE);
        return chunk.getVegetationMap().getGrowthState(localCol, localRow);
    }

    /**
     * 获取指定世界坐标处的土壤肥力。
     */
    public double getSoilFertilityAt(int worldTileX, int worldTileY) {
        int cx = Math.floorDiv(worldTileX, VegetationMap.SIZE);
        int cy = Math.floorDiv(worldTileY, VegetationMap.SIZE);
        Chunk chunk = chunkManager.getChunk(cx, cy);
        if (chunk == null || !chunk.isGenerated()) return 0.0;

        int localCol = Math.floorMod(worldTileX, VegetationMap.SIZE);
        int localRow = Math.floorMod(worldTileY, VegetationMap.SIZE);
        return chunk.getSoilFertility().getFertility(localCol, localRow);
    }

    /**
     * 关闭后台线程池（游戏退出时调用）。
     */
    public void shutdown() {
        growthExecutor.shutdownNow();
    }
}
