package com.github.game.cdda.creature.ai;

import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.creature.Creature;
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
                // 远离玩家 10+ 瓦片 → IDLE
                if (playerDistance >= 10) {
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
                maxStateTurns = Integer.MAX_VALUE;  // 持续到远离威胁
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
                        tryMove(animal, dx, dy, context.getChunkManager());
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
                        tryMove(animal, dx, dy, context.getChunkManager());
                    }
                }
                break;

            case HUNT:
                // 寻找并追击猎物
                hunt(animal, context);
                break;

            case FLEE:
                // 远离玩家方向移动
                fleeFromPlayer(animal, context);
                break;

            case SCAVENGE:
                // 食腐行为（暂未实现尸体系统）
                // 随机移动寻找尸体
                if (random.nextInt(100) < 40) {
                    int dx = random.nextInt(3) - 1;
                    int dy = random.nextInt(3) - 1;
                    if (dx != 0 || dy != 0) {
                        tryMove(animal, dx, dy, context.getChunkManager());
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
                        tryMove(animal, dx, dy, context.getChunkManager());
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
        if (!tryMove(animal, dx, dy, context.getChunkManager())) {
            // 对角线尝试
            if (dx != 0 && dy != 0) {
                if (!tryMove(animal, dx, 0, context.getChunkManager())) {
                    tryMove(animal, 0, dy, context.getChunkManager());
                }
            } else if (dx != 0) {
                // 尝试侧向移动绕过障碍
                tryMove(animal, dx, random.nextBoolean() ? 1 : -1, context.getChunkManager());
            } else {
                tryMove(animal, random.nextBoolean() ? 1 : -1, dy, context.getChunkManager());
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

        List<Creature> creatures = context.getCreatureManager().getCreatures();
        for (Creature c : creatures) {
            if (!c.isAlive() || c == predator) continue;
            if (!(c instanceof Animal)) continue;
            Animal other = (Animal) c;

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
     * 远离玩家方向移动。
     *
     * @param animal  动物实例
     * @param context 行动上下文
     */
    private void fleeFromPlayer(Animal animal, CreatureActionContext context) {
        int dx = Integer.compare(animal.getTileX(), context.getPlayerTileX());
        int dy = Integer.compare(animal.getTileY(), context.getPlayerTileY());

        // 优先移动距离较大的方向
        if (Math.abs(dx) >= Math.abs(dy)) {
            if (tryMove(animal, dx, 0, context.getChunkManager())) {
                return;
            }
            if (tryMove(animal, 0, dy, context.getChunkManager())) {
                return;
            }
        } else {
            if (tryMove(animal, 0, dy, context.getChunkManager())) {
                return;
            }
            if (tryMove(animal, dx, 0, context.getChunkManager())) {
                return;
            }
        }

        // 对角线逃跑
        if (dx != 0 && dy != 0) {
            tryMove(animal, dx, dy, context.getChunkManager());
        }
    }

    /**
     * 尝试移动动物。
     *
     * @param animal       动物实例
     * @param dx           水平方向（-1, 0, 1）
     * @param dy           垂直方向（-1, 0, 1）
     * @param chunkManager 地图管理器
     * @return 是否成功移动
     */
    private boolean tryMove(Animal animal, int dx, int dy, ChunkManager chunkManager) {
        int newX = animal.getTileX() + dx;
        int newY = animal.getTileY() + dy;

        // 检查目标瓦片是否可通行
        TileType tile = chunkManager.getTile(newX, newY);
        if (tile == null || !tile.isPassable()) {
            return false;
        }

        // 移动
        animal.setTileX(newX);
        animal.setTileY(newY);
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
