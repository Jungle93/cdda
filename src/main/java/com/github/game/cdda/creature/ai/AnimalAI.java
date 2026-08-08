package com.github.game.cdda.creature.ai;

import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.util.Random;

/**
 * 动物 AI 状态机。
 *
 * <p>状态转换规则：
 * <ul>
 *   <li>玩家进入感知范围 → FLEE</li>
 *   <li>IDLE 持续 3-8 回合 → WANDER 或 GRAZE（随机）</li>
 *   <li>WANDER 持续 2-5 回合 → IDLE</li>
 *   <li>GRAZE 持续 3-6 回合 → IDLE</li>
 *   <li>FLEE 远离玩家 10+ 瓦片 → IDLE</li>
 * </ul>
 *
 * <p>行为执行：
 * <ul>
 *   <li>IDLE: 不动</li>
 *   <li>WANDER: 30% 概率随机移动一个瓦片</li>
 *   <li>GRAZE: 不动（未来扩展进食逻辑）</li>
 *   <li>FLEE: 远离玩家方向移动</li>
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

    /**
     * 更新 AI 状态并执行行为。
     *
     * @param animal  动物实例
     * @param context 行动上下文
     */
    public void update(Animal animal, CreatureActionContext context) {
        // 1. 检测威胁 → FLEE
        int playerDistance = animal.distanceTo(context.getPlayerTileX(), context.getPlayerTileY());
        if (playerDistance <= animal.getVisionRange()) {
            if (currentState != AIState.FLEE) {
                enterState(AIState.FLEE);
            }
        }

        // 2. 状态转换逻辑
        stateTurns++;
        switch (currentState) {
            case IDLE:
                if (stateTurns >= maxStateTurns) {
                    // IDLE 结束 → 随机选择 WANDER 或 GRAZE
                    AIState next = random.nextBoolean() ? AIState.WANDER : AIState.GRAZE;
                    enterState(next);
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

            case FLEE:
                // 远离玩家 10+ 瓦片 → IDLE
                if (playerDistance >= 10) {
                    enterState(AIState.IDLE);
                }
                break;
        }

        // 3. 执行当前状态行为
        execute(animal, context);
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
            case FLEE:
                maxStateTurns = Integer.MAX_VALUE;  // 持续到远离威胁
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
                // 不动（未来扩展进食逻辑）
                break;

            case FLEE:
                // 远离玩家方向移动
                fleeFromPlayer(animal, context);
                break;
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
