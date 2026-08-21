package com.github.game.cdda.creature.ai;

import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.creature.energy.TrophicLevel;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkCoords;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.util.List;
import java.util.Random;

/**
 * 动物 AI 状态机。
 *
 * <p>状态转换规则：
 * <ul>
 *   <li>玩家进入感知范围 → FLEE（初级消费者）</li>
 *   <li>IDLE 持续 3-8 回合 → WANDER/GRAZE/HUNT（根据饥饿度和营养级）</li>
 *   <li>WANDER 持续 2-5 回合 → IDLE</li>
 *   <li>GRAZE 持续 3-6 回合 → IDLE</li>
 *   <li>HUNT 持续直到捕食成功/猎物逃跑/饥饿缓解</li>
 *   <li>FLEE 远离玩家 10+ 瓦片 → IDLE</li>
 * </ul>
 *
 * <p>行为执行：
 * <ul>
 *   <li>IDLE: 不动</li>
 *   <li>WANDER: 30% 概率随机移动一个瓦片</li>
 *   <li>GRAZE: 吃草 → 增加 bodyEnergy</li>
 *   <li>FLEE: 远离玩家方向移动</li>
 *   <li>HUNT: 寻找猎物 → 追击 → 捕食</li>
 *   <li>SCAVENGE: 寻找尸体（暂未实现）</li>
 * </ul>
 */
public class AnimalAI {

    // ── 状态转换概率 ────────────────────────────────

    /** 饥饿的次级消费者进入 SCAVENGE 的概率 */
    private static final float STARVING_SCAVENGER_CHANCE = 0.4f;

    /** 饥饿的捕食者连续失败后回退 WANDER 的阈值 */
    private static final int HUNT_FAIL_BACKOFF_THRESHOLD = 15;

    /** 饥饿的捕食者进入 HUNT 的概率（非饥饿时同样使用） */
    private static final float PREDATOR_HUNT_CHANCE = 0.4f;

    /** 食草动物进入 GRAZE 的概率（非饥饿时） */
    private static final float HERBIVORE_GRAZE_CHANCE = 0.5f;

    /** 次级消费者进入 SCAVENGE 的概率（非饥饿） */
    private static final float SECONDARY_SCAVENGE_CHANCE = 0.3f;

    /** 食腐动物进入 SCAVENGE 的概率 */
    private static final float SCAVENGER_STATE_CHANCE = 0.5f;

    // ── 食腐行为 ────────────────────────────────

    /** 食腐基础成功概率（%） */
    private static final int SCAVENGE_SUCCESS_PERCENT = 40;

    /** 状态行为中随机移动的概率（%） */
    private static final int STATE_ACTION_MOVE_CHANCE = 40;

    // ── 狩猎行为 ────────────────────────────────

    /** 找不到猎物时随机移动的概率（%） */
    private static final int HUNT_NO_PREY_MOVE_CHANCE = 70;

    /** 优先选择的猎物最低能量阈值 */
    private static final int PREY_MIN_ENERGY = 10;

    /** 捕食基础成功率 */
    private static final double PREDATION_BASE_CHANCE = 0.5;

    /** 捕食敏捷差异系数（每点敏捷差影响 2%） */
    private static final double PREDATION_AGI_COEFFICIENT = 0.02;

    /** 捕食最低成功率 */
    private static final double PREDATION_MIN_CHANCE = 0.2;

    /** 捕食最高成功率 */
    private static final double PREDATION_MAX_CHANCE = 0.9;

    /** 捕食失败额外能量损耗 */
    private static final int PREDATION_FAIL_ENERGY_PENALTY = 3;

    // ── 逃跑行为 ────────────────────────────────

    /** 逃跑安全距离（气泡边界、FLEE 退出统一使用） */
    public static final int SAFE_FLEE_DISTANCE = 90;

    /** 耐力极低阈值（低于此几乎无法移动） */
    private static final double STAMINA_CRITICAL = 0.1;

    /** 耐力极低时无法移动的概率 */
    private static final double STAMINA_CRITICAL_FAIL_CHANCE = 0.8;

    /** 耐力较低阈值 */
    private static final double STAMINA_LOW = 0.3;

    /** 耐力较低时无法移动的概率 */
    private static final double STAMINA_LOW_FAIL_CHANCE = 0.5;

    /** 逃跑评分中"远离玩家"方向权重 */
    private static final int FLEE_AWAY_WEIGHT = 8;

    /** 水边绝境：水邻居超过此数视为危险 */
    private static final int FLEE_WATER_NEIGHBOR_THRESHOLD = 3;

    /** 水边绝境：开阔度折半除数 */
    private static final int FLEE_WATER_PENALTY_DIVISOR = 2;

    // ── 猎物觉察 ────────────────────────────────

    /** 猎物觉察距离阈值 */
    private static final int PREY_AWARENESS_DIST = 3;

    /** 猎物觉察：听觉权重 */
    private static final double PREY_AWARENESS_HEARING_WEIGHT = 0.05;

    /** 猎物觉察：视觉权重 */
    private static final double PREY_AWARENESS_VISION_WEIGHT = 0.03;

    /** 猎物觉察最高概率 */
    private static final double PREY_AWARENESS_CAP = 0.8;

    // ── 群体惊扰 ────────────────────────────────

    /** 群体惊扰传播距离 */
    private static final int PANIC_SPREAD_DIST = 5;

    /** 群体惊扰传播概率 */
    private static final double PANIC_SPREAD_CHANCE = 0.7;

    // ── 陷阱感知 ────────────────────────────────

    /** 陷阱感知扩展半径（3×3 相邻格检测） */
    private static final int TRAP_DETECT_RADIUS = 1;

    /** 陷阱记忆持续时间（回合） */
    private static final long TRAP_MEMORY_DURATION = 200;

    /** 修剪间隔（回合） */
    private static final long TRAP_PRUNE_INTERVAL = 50;

    /** 陷阱感知上限 */
    private static final double TRAP_AWARENESS_CAP = 0.20;

    /** 陷阱感知：每点视觉范围贡献 */
    private static final double TRAP_AWARENESS_PER_VISION = 0.02;

    /** 小型动物 HP 阈值 */
    private static final int TRAP_SMALL_HP_THRESHOLD = 10;

    /** 中型动物 HP 阈值 */
    private static final int TRAP_MEDIUM_HP_THRESHOLD = 30;

    /** 小型动物警觉倍率 */
    private static final double TRAP_SMALL_MULTIPLIER = 0.5;

    /** 中型动物警觉倍率 */
    private static final double TRAP_MEDIUM_MULTIPLIER = 1.0;

    /** 大型动物警觉倍率 */
    private static final double TRAP_LARGE_MULTIPLIER = 1.5;

    /** 大型动物警觉基础加成 */
    private static final double TRAP_LARGE_BASE_BONUS = 0.15;

    // ── 听觉惊扰 ────────────────────────────────

    /** 听觉惊扰基础概率上限 */
    private static final double HEARING_FLEE_BASE_CAP = 0.5;

    /** 猎物丢失距离倍数（超过视觉×2 放弃追击） */
    private static final int HUNT_GIVE_UP_VISION_MULTIPLIER = 2;

    // ── 实例状态字段 ────────────────────────────────

    /** 当前状态 */
    private AIState currentState = AIState.IDLE;

    /** 当前状态持续回合数 */
    private int stateTurns = 0;

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 状态最大持续回合（每次进入状态时随机） */
    private int maxStateTurns = 0;

    /** 当前追击的目标 */
    private Animal huntTarget;

    /** 狩猎失败连续回合数（用于饥饿回退） */
    private int huntFailTurns = 0;

    /** 已知的陷阱位置 + 最近发现回合（用于老化） */
    private java.util.Map<Long, Long> knownTrapPositions = new java.util.HashMap<>();

    /** 上次修剪陷阱记忆的回合 */
    private long lastTrapPruneRound = 0;

    /** 上次更新已知陷阱位置的游戏回合 */
    private long lastTrapUpdateRound = 0;

    /**
     * 更新 AI 状态并执行行为。
     *
     * @param animal  动物实例
     * @param context 行动上下文
     */
    public void update(Animal animal, CreatureActionContext context) {
        // 0. 检查外部触发的逃跑状态（群体惊扰）
        com.github.game.cdda.creature.ai.AIState override = animal.consumeAiOverrideState();
        if (override == AIState.FLEE && currentState != AIState.FLEE) {
            animal.startFleeing();
            enterState(AIState.FLEE);
            spreadPanic(animal, context);
        }

        // 1. 检测威胁 → FLEE（食草动物 或 畏惧玩家的动物）
        TrophicLevel trophicLevel = animal.getDefinition().getTrophicLevel();
        boolean fearsPlayer = trophicLevel.isHerbivore() || animal.getDefinition().fleeFromPlayer;
        int playerDistance = animal.distanceTo(context.getPlayerTileX(), context.getPlayerTileY());

        // 视觉检测：玩家在视觉范围内 → FLEE
        if (fearsPlayer && playerDistance <= animal.getVisionRange()) {
            if (currentState != AIState.FLEE) {
                animal.startFleeing();
                enterState(AIState.FLEE);
                // 群体惊扰：触发附近同物种跟随逃跑
                spreadPanic(animal, context);
            }
        }
        // 听觉检测：玩家超出视觉但在听觉范围内发出噪音 → 有概率 FLEE
        else if (fearsPlayer && currentState != AIState.FLEE
                && playerDistance <= animal.getHearingRange() && playerDistance > animal.getVisionRange()) {
            // 听觉惊扰概率：距离越近概率越高
            double hearingChance = 1.0 - (playerDistance - animal.getVisionRange())
                    / (double) (animal.getHearingRange() - animal.getVisionRange() + 1);
            if (random.nextDouble() < hearingChance * HEARING_FLEE_BASE_CAP) {
                animal.startFleeing();
                enterState(AIState.FLEE);
                spreadPanic(animal, context);
            }
        }

        // 2. 状态转换逻辑
        stateTurns++;
        switch (currentState) {
            case IDLE:
                if (stateTurns >= maxStateTurns) {
                    enterNextState(animal);
                }
                break;

            case WANDER:
                if (stateTurns >= maxStateTurns) {
                    enterState(AIState.IDLE);
                }
                break;

            case GRAZE:
                if (stateTurns >= maxStateTurns) {
                    enterState(AIState.IDLE);
                }
                break;

            case HUNT:
                // 捕食成功、目标死亡或跑远 → 结束狩猎
                if (huntTarget == null || !huntTarget.isAlive()) {
                    huntTarget = null;
                    huntFailTurns++;
                    enterState(AIState.IDLE);
                } else {
                    int targetDist = animal.distanceTo(huntTarget.getTileX(), huntTarget.getTileY());
                    if (targetDist > animal.getVisionRange() * HUNT_GIVE_UP_VISION_MULTIPLIER) {
                        // 目标跑太远，放弃
                        huntTarget = null;
                        huntFailTurns++;
                        enterState(AIState.IDLE);
                    } else if (targetDist <= 1) {
                        // 追上猎物，捕食（猎物有概率觉察并逃跑）
                        if (preyAwarenessCheck(animal, huntTarget)) {
                            // 猎物觉察到危险，触发逃跑
                            huntTarget.startFleeing();
                            // 捕食者仍然尝试追击
                        }
                        attemptPredation(animal, huntTarget);
                        huntTarget = null;
                        enterState(AIState.IDLE);
                    } else {
                        // 追击中，重置失败计数
                        huntFailTurns = 0;
                    }
                }
                break;

            case FLEE:
                // 远离玩家 SAFE_FLEE_DISTANCE+ 瓦片或耐力耗尽 → IDLE
                if (playerDistance >= SAFE_FLEE_DISTANCE) {
                    animal.endFleeing();
                    enterState(AIState.IDLE);
                } else if (animal.shouldStopFleeing(
                        context.getPlayerTileX(), context.getPlayerTileY(), SAFE_FLEE_DISTANCE)) {
                    animal.endFleeing();
                    enterState(AIState.IDLE);
                }
                break;

            case SCAVENGE:
                if (stateTurns >= maxStateTurns) {
                    enterState(AIState.IDLE);
                }
                break;
        }

        // 3. 执行当前状态行为
        execute(animal, context);
    }

    /**
     * 根据饥饿度和营养级选择下一个状态。
     */
    private void enterNextState(Animal animal) {
        TrophicLevel trophicLevel = animal.getDefinition().getTrophicLevel();

        // 饥饿时优先觅食/狩猎
        if (animal.isStarving()) {
            if (trophicLevel.isPredator()) {
                // 肉食动物饥饿时：如果连续狩猎失败超过阈值，回退到 WANDER 保存体力
                if (huntFailTurns >= HUNT_FAIL_BACKOFF_THRESHOLD) {
                    huntFailTurns = 0;
                    enterState(AIState.WANDER);
                } else {
                    enterState(AIState.HUNT);
                }
            } else if (trophicLevel.isHerbivore()) {
                enterState(AIState.GRAZE);
            } else {
                // 次级消费者/清道夫：有尸体时进入 SCAVENGE
                if (random.nextFloat() < STARVING_SCAVENGER_CHANCE && hasNearbyDeathRecords(animal)) {
                    enterState(AIState.SCAVENGE);
                } else {
                    enterState(AIState.WANDER);
                }
            }
            return;
        }

        // 捕食者有概率进入狩猎状态
        if (trophicLevel.isPredator() && random.nextFloat() < PREDATOR_HUNT_CHANCE) {
            enterState(AIState.HUNT);
            return;
        }

        // 食草动物有概率进入觅食状态
        if (trophicLevel.isHerbivore() && random.nextFloat() < HERBIVORE_GRAZE_CHANCE) {
            enterState(AIState.GRAZE);
            return;
        }

        // 次级消费者有概率进入拾荒状态（当附近有死亡记录时）
        if (trophicLevel.isPredator() && trophicLevel != TrophicLevel.APEX_PREDATOR
                && random.nextFloat() < SECONDARY_SCAVENGE_CHANCE && hasNearbyDeathRecords(animal)) {
            enterState(AIState.SCAVENGE);
            return;
        }

        // 食腐动物有概率进入拾荒状态
        if (trophicLevel.isScavenger() && random.nextFloat() < SCAVENGER_STATE_CHANCE && hasNearbyDeathRecords(animal)) {
            enterState(AIState.SCAVENGE);
            return;
        }

        // 默认随机
        AIState next = random.nextBoolean() ? AIState.WANDER : AIState.GRAZE;
        enterState(next);
    }

    /**
     * 检查附近区块是否有死亡记录（用于 SCAVENGE 状态触发）。
     */
    private boolean hasNearbyDeathRecords(Animal animal) {
        com.github.game.cdda.GameWorld world = com.github.game.cdda.GameWorld.getInstance();
        if (world == null || world.getEnergyFlowManager() == null) return false;
        int cx = ChunkCoords.toChunkX(animal.getTileX());
        int cy = ChunkCoords.toChunkY(animal.getTileY());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                long key = ChunkCoords.key(cx + dx, cy + dy);
                var record = world.getEnergyFlowManager().getDeathRecord(key);
                if (record != null && record.getTotalEnergyReturned() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 消耗附近区块的一个尸体记录（食腐成功后调用）。
     */
    private void consumeNearbyCorpse(Animal animal) {
        com.github.game.cdda.GameWorld world = com.github.game.cdda.GameWorld.getInstance();
        if (world == null || world.getEnergyFlowManager() == null) return;
        int cx = ChunkCoords.toChunkX(animal.getTileX());
        int cy = ChunkCoords.toChunkY(animal.getTileY());
        // 优先消耗最近区块的尸体
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                long key = ChunkCoords.key(cx + dx, cy + dy);
                var record = world.getEnergyFlowManager().getDeathRecord(key);
                if (record != null && record.getCorpseCount() > 0) {
                    record.removeCorpse();
                    return;
                }
            }
        }
    }

    /**
     * 进入新状态，重置计数。
     *
     * @param newState 新状态
     */
    private void enterState(AIState newState) {
        currentState = newState;
        stateTurns = 0;

        // 根据状态设置最大持续回合
        switch (newState) {
            case IDLE:
                maxStateTurns = 3 + random.nextInt(6);  // 3-8
                break;
            case WANDER:
                maxStateTurns = 2 + random.nextInt(4);  // 2-5
                break;
            case GRAZE:
                maxStateTurns = 3 + random.nextInt(4);  // 3-6
                break;
            case HUNT:
                maxStateTurns = Integer.MAX_VALUE;  // 持续到捕食成功或目标丢失
                break;
            case FLEE:
                maxStateTurns = Integer.MAX_VALUE;  // 持续到耐力耗尽或安全
                break;
            case SCAVENGE:
                maxStateTurns = 5 + random.nextInt(6);  // 5-10
                break;
        }
    }

    /**
     * 执行当前状态的行为。
     *
     * @param animal  动物实例
     * @param context 行动上下文
     */
    private void execute(Animal animal, CreatureActionContext context) {
        switch (currentState) {
            case IDLE:
                // 不动
                break;

            case WANDER:
                // 30% 概率随机移动
                if (random.nextInt(100) < 30) {
                    int dx = random.nextInt(3) - 1;  // -1, 0, 1
                    int dy = random.nextInt(3) - 1;
                    if (dx != 0 || dy != 0) {
                        tryMove(animal, dx, dy, context);
                    }
                }
                // 定期扫描周围陷阱
                updateKnownTraps(animal, context);
                break;

            case GRAZE:
                // 吃草 → 增加 bodyEnergy
                graze(animal);
                // 觅食移动：优先走向有食物的瓦片（草丛/花/灌木），而非完全随机
                if (random.nextInt(100) < STATE_ACTION_MOVE_CHANCE) {
                    int[] foodDir = findFoodDirection(animal, context);
                    if (foodDir != null) {
                        tryMove(animal, foodDir[0], foodDir[1], context);
                    } else {
                        // 附近没有食物，随机漫步
                        int dx = random.nextInt(3) - 1;
                        int dy = random.nextInt(3) - 1;
                        if (dx != 0 || dy != 0) {
                            tryMove(animal, dx, dy, context);
                        }
                    }
                }
                break;

            case HUNT:
                // 寻找并追击猎物
                hunt(animal, context);
                break;

            case FLEE:
                // 远离玩家方向移动（消耗耐力）
                fleeFromPlayer(animal, context);
                break;

            case SCAVENGE:
                // 食腐行为 — 在死亡区域附近寻找食物
                scavenge(animal);
                // 随机移动寻找尸体
                if (random.nextInt(100) < STATE_ACTION_MOVE_CHANCE) {
                    int dx = random.nextInt(3) - 1;
                    int dy = random.nextInt(3) - 1;
                    if (dx != 0 || dy != 0) {
                        tryMove(animal, dx, dy, context);
                    }
                }
                break;
        }
    }

    /**
     * 吃草行为 — 增加 bodyEnergy。
     */
    private void graze(Animal animal) {
        int gain = animal.getDefinition().getEnergyConfig().getGrazeGain();
        if (gain > 0) {
            animal.addBodyEnergy(gain);
        }
    }

    /**
     * 食腐行为 — 增加 bodyEnergy。
     * 在死亡记录密集区域，食腐成功率更高。
     * 食腐成功后会消耗一个尸体记录。
     */
    private void scavenge(Animal animal) {
        int gain = animal.getDefinition().getEnergyConfig().getScavengeGain();
        if (gain > 0) {
            // 食腐有概率成功（40% 基础概率）
            if (random.nextInt(100) < SCAVENGE_SUCCESS_PERCENT) {
                animal.addBodyEnergy(gain);
                // 消耗一个尸体记录
                consumeNearbyCorpse(animal);
            }
        }
    }

    /**
     * 狩猎行为 — 寻找猎物并追击。
     */
    private void hunt(Animal animal, CreatureActionContext context) {
        // 如果没有目标，寻找最近的猎物
        if (huntTarget == null || !huntTarget.isAlive()) {
            huntTarget = findNearestPrey(animal, context);
            if (huntTarget == null) {
                // 找不到猎物，提高移动概率（70%）避免看起来在"发呆"
                if (random.nextInt(100) < HUNT_NO_PREY_MOVE_CHANCE) {
                    int dx = random.nextInt(3) - 1;
                    int dy = random.nextInt(3) - 1;
                    if (dx != 0 || dy != 0) {
                        tryMove(animal, dx, dy, context);
                    }
                }
                return;
            }
        }

        // 追击目标
        int targetX = huntTarget.getTileX();
        int targetY = huntTarget.getTileY();
        int dx = Integer.compare(targetX, animal.getTileX());
        int dy = Integer.compare(targetY, animal.getTileY());

        // 尝试直接向目标移动
        if (!tryMove(animal, dx, dy, context)) {
            // 对角线尝试
            if (dx != 0 && dy != 0) {
                if (!tryMove(animal, dx, 0, context)) {
                    tryMove(animal, 0, dy, context);
                }
            } else if (dx != 0) {
                // 尝试侧向移动绕过障碍
                tryMove(animal, dx, random.nextBoolean() ? 1 : -1, context);
            } else {
                tryMove(animal, random.nextBoolean() ? 1 : -1, dy, context);
            }
        }
    }

    /**
     * 寻找最近的可捕食猎物。
     */
    private Animal findNearestPrey(Animal predator, CreatureActionContext context) {
        TrophicLevel predatorLevel = predator.getDefinition().getTrophicLevel();
        int visionRange = predator.getVisionRange();

        Animal nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        // 使用快照列表（后台线程注入），仅遍历存活动物
        List<Animal> animals = context.getTurnSnapshot();
        for (Animal other : animals) {
            if (!other.isAlive() || other == predator) continue;

            // 检查是否是可捕食的营养级
            if (!predatorLevel.canPreyOn(other.getDefinition().getTrophicLevel())) {
                continue;
            }

            int dist = predator.distanceTo(other.getTileX(), other.getTileY());
            if (dist <= visionRange && dist < nearestDist) {
                // 优先选择能量高的猎物（更划算）
                if (other.getBodyEnergy() > PREY_MIN_ENERGY) {
                    nearest = other;
                    nearestDist = dist;
                }
            }
        }

        return nearest;
    }

    /**
     * 尝试捕食猎物。
     *
     * @param predator 捕食者
     * @param prey     猎物
     */
    private void attemptPredation(Animal predator, Animal prey) {
        // 捕食成功率基于敏捷差异
        int predatorAgi = predator.getAgility();
        int preyAgi = prey.getAgility();
        double successChance = PREDATION_BASE_CHANCE + (predatorAgi - preyAgi) * PREDATION_AGI_COEFFICIENT;
        successChance = Math.max(PREDATION_MIN_CHANCE, Math.min(PREDATION_MAX_CHANCE, successChance));

        if (random.nextDouble() < successChance) {
            // 捕食成功
            prey.eatenBy(predator);
            huntFailTurns = 0; // 捕食成功，重置失败计数
        } else {
            // 猎物逃脱
            // 捕食者消耗额外能量
            predator.addBodyEnergy(-PREDATION_FAIL_ENERGY_PENALTY);
        }
    }

    /**
     * 远离玩家方向移动，主动避障。
     * 评估所有 8 个方向，综合"远离玩家距离"和"前方通畅度"打分选最优方向。
     * 前方通畅度 = 目标格 8 邻居中可通行的数量（越多越不容易死胡同）。
     * 耐力越低，移动成功率越低（太累了跑不动）。
     * 水边绝境：如果目标格周围水邻居多，降低开阔度评分。
     *
     * @param animal  动物实例
     * @param context 行动上下文
     */
    private void fleeFromPlayer(Animal animal, CreatureActionContext context) {
        // 消耗逃跑耐力
        double staminaRatio = animal.consumeFleeStamina();

        // 耐力过低时，有概率无法移动（太累了）
        if (staminaRatio < STAMINA_CRITICAL && random.nextDouble() < STAMINA_CRITICAL_FAIL_CHANCE) return;
        if (staminaRatio < STAMINA_LOW && random.nextDouble() < STAMINA_LOW_FAIL_CHANCE) return;

        int ax = animal.getTileX();
        int ay = animal.getTileY();
        int px = context.getPlayerTileX();
        int py = context.getPlayerTileY();
        int awayDx = Integer.compare(ax, px);
        int awayDy = Integer.compare(ay, py);

        // 8 个方向（含对角线）
        int[][] dirs = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1,  0},          {1,  0},
            {-1,  1}, {0,  1}, {1,  1}
        };

        // 评估所有可通行方向：综合考虑"远离玩家"和"前方通畅度"
        ChunkManager chunkManager = context.getChunkManager();
        int bestDx = 0, bestDy = 0;
        int bestScore = Integer.MIN_VALUE;
        boolean found = false;

        for (int[] d : dirs) {
            int dx = d[0], dy = d[1];
            int nx = ax + dx;
            int ny = ay + dy;

            // 1 格必须可通行
            TileType t1 = chunkManager.getTile(nx, ny);
            if (t1 == null || !t1.isPassable()) continue;

            // 前瞻：2 格内的通畅邻居数（越多 = 越不容易死胡同）
            int openness = 0;
            int waterNeighbors = 0;
            for (int[] ld : dirs) {
                TileType t2 = chunkManager.getTile(nx + ld[0], ny + ld[1]);
                if (t2 != null && t2.isPassable()) openness++;
                if (t2 == TileType.WATER) waterNeighbors++;
            }

            // 水边绝境：如果目标格周围水邻居多（>3），降低开阔度评分
            int effectiveOpenness = waterNeighbors > FLEE_WATER_NEIGHBOR_THRESHOLD ? openness / FLEE_WATER_PENALTY_DIVISOR : openness;

            // 综合评分：远离玩家的优先级 × 8 + 前方通畅度
            int awayScore = -(Math.abs(dx - awayDx) + Math.abs(dy - awayDy));
            int score = awayScore * FLEE_AWAY_WEIGHT + effectiveOpenness;

            if (score > bestScore) {
                bestScore = score;
                bestDx = dx;
                bestDy = dy;
                found = true;
            }
        }

        if (found) {
            tryMove(animal, bestDx, bestDy, context);
        }
    }


    /**
     * 尝试移动动物。
     * 移动成功后更新空间索引（跨区块时切换索引桶）。
     *
     * @param animal       动物实例
     * @param dx           水平方向（-1, 0, 1）
     * @param dy           垂直方向（-1, 0, 1）
     * @param chunkManager 地图管理器
     * @return 是否成功移动
     */
    private boolean tryMove(Animal animal, int dx, int dy, CreatureActionContext context) {
        int newX = animal.getTileX() + dx;
        int newY = animal.getTileY() + dy;

        // 检查目标瓦片是否可通行
        ChunkManager chunkManager = context.getChunkManager();
        TileType tile = chunkManager.getTile(newX, newY);
        if (tile == null || !tile.isPassable()) {
            return false;
        }

        // 检查是否是已知的陷阱位置（动物记住并避开）
        if (isTrapKnown(newX, newY)) {
            return false;
        }

        // 陷阱感知：动物在移动前有机会发现并避开陷阱
        com.github.game.cdda.GameWorld world = com.github.game.cdda.GameWorld.getInstance();
        if (world != null && world.getTrapManager() != null) {
            var trap = world.getTrapManager().getTrapAt(newX, newY);
            if (trap != null && trap.getState() == com.github.game.cdda.trap.PlacedTrap.State.ARMED) {
                // 陷阱感知概率：基于动物的感知力和体型
                double awarenessChance = getTrapAwarenessChance(animal);
                if (random.nextDouble() < awarenessChance) {
                    // 动物察觉到陷阱，记住位置并拒绝移动
                    long key = ChunkCoords.keyFromTile(newX, newY);
                    knownTrapPositions.put(key, getCurrentRound());
                    return false;
                }
                // 未察觉 → 继续移动，踩中陷阱
            }
        }

        // 记录旧位置，用于更新空间索引
        int oldX = animal.getTileX();
        int oldY = animal.getTileY();

        // 移动
        animal.setTileX(newX);
        animal.setTileY(newY);

        // 更新空间索引（跨区块时移动索引桶，防止动物跨区块后"消失"）
        if (context.getCreatureManager() != null) {
            context.getCreatureManager().getCreatureGrid()
                    .move(animal, oldX, oldY, newX, newY);
        }

        // 移动后再次检查陷阱（对已感知但被迫移动的情况也生效）
        if (world != null && world.getTrapManager() != null) {
            world.getTrapManager().checkTrapAt(newX, newY, animal);
            if (!animal.isAlive()) return true;
        }

        return true;
    }

    /**
     * 在周围 3×3 范围内寻找食物瓦片（草丛/花），返回朝向食物的方向。
     * 优先选择有 TALL_GRASS、FLOWER、DEAD_GRASS 的瓦片。
     * 找不到时返回 null。
     *
     * @return int[]{dx, dy} 或 null
     */
    private int[] findFoodDirection(com.github.game.cdda.creature.Animal animal, CreatureActionContext context) {
        ChunkManager cm = context.getChunkManager();
        int ax = animal.getTileX();
        int ay = animal.getTileY();

        // 收集所有有食物的方向
        java.util.List<int[]> foodDirs = new java.util.ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                TileType t = cm.getTile(ax + dx, ay + dy);
                if (t == TileType.TALL_GRASS || t == TileType.FLOWER
                        || t == TileType.DEAD_GRASS || t == TileType.BUSH) {
                    foodDirs.add(new int[]{dx, dy});
                }
            }
        }

        if (foodDirs.isEmpty()) return null;
        return foodDirs.get(random.nextInt(foodDirs.size()));
    }

    /**
     * 计算动物对陷阱的感知概率。
     * 基于感知力和体型：感知力越高、体型越大，越容易察觉陷阱。
     * <ul>
     *   <li>小型动物（兔/松鼠）：5-15%（容易上钩）</li>
     *   <li>中型动物（狐狸/獾）：20-35%</li>
     *   <li>大型动物（鹿/野猪）：35-50%（难以用陷阱捕获）</li>
     * </ul>
     */
    private double getTrapAwarenessChance(com.github.game.cdda.creature.Animal animal) {
        int vision = animal.getVisionRange();
        int hp = animal.getMaxHp();

        // 基础感知：感知范围贡献 2% 每点（最多 ~20%）
        double base = Math.min(TRAP_AWARENESS_CAP, vision * TRAP_AWARENESS_PER_VISION);

        // 体型加成：HP 越高越警觉
        if (hp <= TRAP_SMALL_HP_THRESHOLD) {
            // 小型：兔子、松鼠 — 警觉性低
            return base * TRAP_SMALL_MULTIPLIER;
        } else if (hp <= TRAP_MEDIUM_HP_THRESHOLD) {
            // 中型：狐狸、獾
            return base * TRAP_MEDIUM_MULTIPLIER;
        } else {
            // 大型：鹿、野猪
            return base * TRAP_LARGE_MULTIPLIER + TRAP_LARGE_BASE_BONUS;
        }
    }

    /**
     * 猎物觉察检查：当捕食者接近猎物（≤3 瓦片）时，猎物有概率发现危险并触发逃跑。
     * 检测概率 = hearingRange × 0.05 + visionRange × 0.03（上限 80%）
     *
     * @param predator 捕食者
     * @param prey     猎物
     * @return true 如果猎物觉察到危险
     */
    private boolean preyAwarenessCheck(Animal predator, Animal prey) {
        int dist = predator.distanceTo(prey.getTileX(), prey.getTileY());
        if (dist > PREY_AWARENESS_DIST) return false;
        double awarenessChance = prey.getHearingRange() * PREY_AWARENESS_HEARING_WEIGHT
                               + prey.getVisionRange() * PREY_AWARENESS_VISION_WEIGHT;
        awarenessChance = Math.min(PREY_AWARENESS_CAP, awarenessChance);
        return random.nextDouble() < awarenessChance;
    }

    /**
     * 群体惊扰：当一只草食动物进入 FLEE 状态时，
     * 触发 5 瓦片内同物种 70% 概率跟随 FLEE。
     */
    private void spreadPanic(Animal animal, CreatureActionContext context) {
        if (animal.getDefinition().getTrophicLevel().isPredator()) return;

        List<Animal> snapshot = context.getTurnSnapshot();
        if (snapshot == null) return;

        int ax = animal.getTileX();
        int ay = animal.getTileY();

        for (Animal other : snapshot) {
            if (other == animal || !other.isAlive()) continue;
            if (!other.getDefinition().getTrophicLevel().isHerbivore()) continue;
            // 只惊扰同物种
            if (!other.getDefinition().id.equals(animal.getDefinition().id)) continue;

            int dist = Math.abs(other.getTileX() - ax) + Math.abs(other.getTileY() - ay);
            if (dist <= PANIC_SPREAD_DIST && random.nextDouble() < PANIC_SPREAD_CHANCE) {
                // 同物种在范围内，概率跟随逃跑
                other.startFleeing();
                other.setAiOverrideState(AIState.FLEE);
            }
        }
    }

    /**
     * 检查指定位置是否是已知的陷阱（未过期）。
     */
    private boolean isTrapKnown(int x, int y) {
        long key = ChunkCoords.keyFromTile(x, y);
        Long lastSeen = knownTrapPositions.get(key);
        if (lastSeen == null) return false;
        long currentRound = getCurrentRound();
        return currentRound - lastSeen < TRAP_MEMORY_DURATION;
    }

    /**
     * 更新已知陷阱位置（老化机制：超过 TRAP_MEMORY_DURATION 回合后遗忘）。
     */
    private void updateKnownTraps(Animal animal, CreatureActionContext context) {
        com.github.game.cdda.GameWorld world = com.github.game.cdda.GameWorld.getInstance();
        if (world == null || world.getTrapManager() == null) return;
        var trapMgr = world.getTrapManager();

        int ax = animal.getTileX();
        int ay = animal.getTileY();

        // 扫描 3×3 范围内的陷阱
        for (int dx = -TRAP_DETECT_RADIUS; dx <= TRAP_DETECT_RADIUS; dx++) {
            for (int dy = -TRAP_DETECT_RADIUS; dy <= TRAP_DETECT_RADIUS; dy++) {
                if (dx == 0 && dy == 0) continue;
                var trap = trapMgr.getTrapAt(ax + dx, ay + dy);
                if (trap != null && trap.getState() == com.github.game.cdda.trap.PlacedTrap.State.ARMED) {
                    double awarenessChance = getTrapAwarenessChance(animal);
                    if (random.nextDouble() < awarenessChance) {
                        long key = ChunkCoords.keyFromTile(ax + dx, ay + dy);
                        knownTrapPositions.put(key, getCurrentRound());
                    }
                }
            }
        }

        // 定期修剪过期记忆
        long currentRound = getCurrentRound();
        if (currentRound - lastTrapPruneRound >= TRAP_PRUNE_INTERVAL) {
            pruneKnownTraps(currentRound);
            lastTrapPruneRound = currentRound;
        }
    }

    /**
     * 修剪过期的陷阱记忆。
     */
    private void pruneKnownTraps(long currentRound) {
        knownTrapPositions.entrySet().removeIf(entry ->
                currentRound - entry.getValue() >= TRAP_MEMORY_DURATION);
    }

    /**
     * 修剪过期的陷阱记忆（测试用，暴露给同包）。
     * @param currentRound 当前回合数
     */
    void pruneKnownTrapsForTest(long currentRound) {
        pruneKnownTraps(currentRound);
    }

    /**
     * 获取当前游戏回合数（用于陷阱记忆老化）。
     */
    private long getCurrentRound() {
        com.github.game.cdda.GameWorld world = com.github.game.cdda.GameWorld.getInstance();
        if (world != null && world.getEnergyFlowManager() != null) {
            return world.getEnergyFlowManager().getCurrentRound();
        }
        return 0;
    }

    /**
     * 获取当前 AI 状态（调试用）。
     *
     * @return 当前状态
     */
    public AIState getCurrentState() {
        return currentState;
    }
}
