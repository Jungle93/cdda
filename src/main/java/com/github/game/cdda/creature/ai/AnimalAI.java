package com.github.game.cdda.creature.ai;

import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.creature.energy.TrophicLevel;
import com.github.game.cdda.world.TileType;
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

    /**
     * 更新 AI 状态并执行行为。
     *
     * @param animal  动物实例
     * @param context 行动上下文
     */
    public void update(Animal animal, CreatureActionContext context) {
        // 1. 检测威胁 → FLEE（仅初级消费者）
        TrophicLevel trophicLevel = animal.getDefinition().getTrophicLevel();
        int playerDistance = animal.distanceTo(context.getPlayerTileX(), context.getPlayerTileY());

        if (trophicLevel.isHerbivore() && playerDistance <= animal.getVisionRange()) {
            if (currentState != AIState.FLEE) {
                animal.startFleeing();
                enterState(AIState.FLEE);
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
                    enterState(AIState.IDLE);
                } else {
                    int targetDist = animal.distanceTo(huntTarget.getTileX(), huntTarget.getTileY());
                    if (targetDist > animal.getVisionRange() * 2) {
                        // 目标跑太远，放弃
                        huntTarget = null;
                        enterState(AIState.IDLE);
                    } else if (targetDist <= 1) {
                        // 追上猎物，捕食
                        attemptPredation(animal, huntTarget);
                        huntTarget = null;
                        enterState(AIState.IDLE);
                    }
                }
                break;

            case FLEE:
                // 远离玩家 90+ 瓦片或耐力耗尽 → IDLE
                if (playerDistance >= 90) {
                    animal.endFleeing();
                    enterState(AIState.IDLE);
                } else if (animal.shouldStopFleeing(
                        context.getPlayerTileX(), context.getPlayerTileY(), 90)) {
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
                enterState(AIState.HUNT);
            } else if (trophicLevel.isHerbivore()) {
                enterState(AIState.GRAZE);
            } else {
                enterState(AIState.WANDER);
            }
            return;
        }

        // 捕食者有概率进入狩猎状态
        if (trophicLevel.isPredator() && random.nextFloat() < 0.4f) {
            enterState(AIState.HUNT);
            return;
        }

        // 食草动物有概率进入觅食状态
        if (trophicLevel.isHerbivore() && random.nextFloat() < 0.5f) {
            enterState(AIState.GRAZE);
            return;
        }

        // 默认随机
        AIState next = random.nextBoolean() ? AIState.WANDER : AIState.GRAZE;
        enterState(next);
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
                break;

            case GRAZE:
                // 吃草 → 增加 bodyEnergy
                graze(animal);
                // 30% 概率随机移动一步
                if (random.nextInt(100) < 30) {
                    int dx = random.nextInt(3) - 1;
                    int dy = random.nextInt(3) - 1;
                    if (dx != 0 || dy != 0) {
                        tryMove(animal, dx, dy, context);
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
                if (random.nextInt(100) < 40) {
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
     */
    private void scavenge(Animal animal) {
        int gain = animal.getDefinition().getEnergyConfig().getScavengeGain();
        if (gain > 0) {
            // 食腐有概率成功（40% 基础概率）
            if (random.nextInt(100) < 40) {
                animal.addBodyEnergy(gain);
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
                // 找不到猎物，随机移动
                if (random.nextInt(100) < 40) {
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
                if (other.getBodyEnergy() > 10) {
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
        double successChance = 0.5 + (predatorAgi - preyAgi) * 0.02;
        successChance = Math.max(0.2, Math.min(0.9, successChance));

        if (random.nextDouble() < successChance) {
            // 捕食成功
            prey.eatenBy(predator);
        } else {
            // 猎物逃脱
            // 捕食者消耗额外能量
            predator.addBodyEnergy(-3);
        }
    }

    /**
     * 远离玩家方向移动，主动避障。
     * 评估所有 8 个方向，综合"远离玩家距离"和"前方通畅度"打分选最优方向。
     * 前方通畅度 = 目标格 8 邻居中可通行的数量（越多越不容易死胡同）。
     * 耐力越低，移动成功率越低（太累了跑不动）。
     *
     * @param animal  动物实例
     * @param context 行动上下文
     */
    private void fleeFromPlayer(Animal animal, CreatureActionContext context) {
        // 消耗逃跑耐力
        double staminaRatio = animal.consumeFleeStamina();

        // 耐力过低时，有概率无法移动（太累了）
        if (staminaRatio < 0.1 && random.nextDouble() < 0.8) return;
        if (staminaRatio < 0.3 && random.nextDouble() < 0.5) return;

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
            for (int[] ld : dirs) {
                TileType t2 = chunkManager.getTile(nx + ld[0], ny + ld[1]);
                if (t2 != null && t2.isPassable()) openness++;
            }

            // 综合评分：远离玩家的优先级 × 8 + 前方通畅度
            int awayScore = -(Math.abs(dx - awayDx) + Math.abs(dy - awayDy));
            int score = awayScore * 8 + openness;

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

        return true;
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
