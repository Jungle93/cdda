package com.github.game.cdda.npc.ai;

import com.github.game.cdda.creature.Player;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.npc.Npc;
import com.github.game.cdda.npc.NpcType;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.util.Random;

/**
 * NPC AI 状态机。
 *
 * <p>状态转换规则：
 * <ul>
 *   <li>玩家进入感知范围 → 根据类型和态度决定：FLEE（友好/中立且态度低）、HUNT_PREY（敌对）</li>
 *   <li>IDLE 持续 3-8 回合 → WALK/SLEEP</li>
 *   <li>WALK 持续 2-5 回合 → IDLE</li>
 *   <li>HUNT_PREY 追上玩家 → ATTACK</li>
 *   <li>HUNT_PREY 玩家跑远 → IDLE</li>
 *   <li>ATTACK 玩家死亡/NPC死亡/跑远 → IDLE</li>
 *   <li>FLEE 远离玩家 10+ 瓦片 → IDLE</li>
 *   <li>SLEEP 持续 5-10 回合 → IDLE</li>
 * </ul>
 *
 * <p>行为执行：
 * <ul>
 *   <li>IDLE: 不动</li>
 *   <li>WALK: 30% 概率随机移动一个瓦片</li>
 *   <li>PATROL: 沿巡逻路径点移动</li>
 *   <li>ATTACK: 近战攻击玩家</li>
 *   <li>HUNT_PREY: 向玩家方向移动（追击）</li>
 *   <li>FLEE: 远离玩家方向移动</li>
 *   <li>SLEEP: 不动（恢复体力/HP）</li>
 * </ul>
 */
public class NpcAI {

    /** 当前状态 */
    private NpcAIState currentState = NpcAIState.IDLE;

    /** 当前状态持续回合数 */
    private int stateTurns = 0;

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 状态最大持续回合（每次进入状态时随机） */
    private int maxStateTurns = 0;

    /**
     * 更新 AI 状态并执行行为。
     *
     * @param npc     NPC 实例
     * @param context 行动上下文
     */
    public void update(Npc npc, CreatureActionContext context) {
        if (!npc.isAlive()) return;

        // 1. 检测玩家并决定是否需要状态转换
        int playerDistance = npc.distanceTo(context.getPlayerTileX(), context.getPlayerTileY());
        int perceptionRange = npc.getVisionRange();
        NpcType npcType = npc.getNpcType();

        // 敌对 NPC：玩家在感知范围内 → 追击
        if (npcType == NpcType.HOSTILE && playerDistance <= perceptionRange) {
            if (currentState != NpcAIState.HUNT_PREY && currentState != NpcAIState.ATTACK) {
                enterState(NpcAIState.HUNT_PREY);
            }
        }

        // 友好/中立 NPC 且态度低：玩家在感知范围内 → 逃跑
        if ((npcType == NpcType.FRIENDLY || npcType == NpcType.NEUTRAL)
                && playerDistance <= perceptionRange
                && npc.getSocial().getAttitudeToPlayer() < 30) {
            if (currentState != NpcAIState.FLEE) {
                enterState(NpcAIState.FLEE);
            }
        }

        // 2. 状态转换逻辑
        stateTurns++;
        switch (currentState) {
            case IDLE:
                if (stateTurns >= maxStateTurns) {
                    enterNextState(npc);
                }
                break;

            case WALK:
                if (stateTurns >= maxStateTurns) {
                    enterState(NpcAIState.IDLE);
                }
                break;

            case PATROL:
                // 沿巡逻路径移动
                if (!npc.hasPatrolRoute() || stateTurns >= maxStateTurns) {
                    enterState(NpcAIState.IDLE);
                }
                break;

            case HUNT_PREY:
                if (playerDistance > perceptionRange * 2) {
                    // 玩家跑太远，放弃追击
                    enterState(NpcAIState.IDLE);
                } else if (playerDistance <= 1) {
                    // 追上玩家，开始攻击
                    enterState(NpcAIState.ATTACK);
                }
                break;

            case ATTACK:
                if (playerDistance > 1) {
                    // 玩家不在攻击范围内，回到追击或空闲
                    if (playerDistance <= perceptionRange) {
                        enterState(NpcAIState.HUNT_PREY);
                    } else {
                        enterState(NpcAIState.IDLE);
                    }
                }
                break;

            case FLEE:
                // 远离玩家 10+ 瓦片 → IDLE
                if (playerDistance >= 10) {
                    enterState(NpcAIState.IDLE);
                }
                break;

            case SLEEP:
                if (stateTurns >= maxStateTurns) {
                    enterState(NpcAIState.IDLE);
                }
                break;

            case TALK:
            case TRADE:
                // 交互状态由玩家输入驱动，不自动退出
                break;
        }

        // 3. 执行当前状态行为
        execute(npc, context);
    }

    /**
     * 根据 NPC 类型和状态选择下一个状态。
     *
     * @param npc NPC 实例
     */
    private void enterNextState(Npc npc) {
        // 低血量时可能进入睡眠
        if (npc.getHp() < npc.getMaxHp() * 0.3f) {
            enterState(NpcAIState.SLEEP);
            return;
        }

        // 有巡逻路线且未进入战斗状态时，优先巡逻
        if (npc.hasPatrolRoute() && npc.getNpcType() != NpcType.HOSTILE) {
            float r = random.nextFloat();
            if (r < 0.4f) {
                enterState(NpcAIState.PATROL);
                return;
            } else if (r < 0.7f) {
                enterState(NpcAIState.WALK);
                return;
            }
        }

        // 敌对 NPC 有概率进入游荡
        if (npc.getNpcType() == NpcType.HOSTILE && random.nextFloat() < 0.5f) {
            enterState(NpcAIState.WALK);
            return;
        }

        // 默认随机
        enterState(random.nextBoolean() ? NpcAIState.WALK : NpcAIState.IDLE);
    }

    /**
     * 进入新状态，重置计数。
     *
     * @param newState 新状态
     */
    private void enterState(NpcAIState newState) {
        currentState = newState;
        stateTurns = 0;

        // 根据状态设置最大持续回合
        switch (newState) {
            case IDLE:
                maxStateTurns = 3 + random.nextInt(6);  // 3-8
                break;
            case WALK:
                maxStateTurns = 2 + random.nextInt(4);  // 2-5
                break;
            case PATROL:
                maxStateTurns = 10 + random.nextInt(10);  // 10-20
                break;
            case HUNT_PREY:
                maxStateTurns = Integer.MAX_VALUE;  // 持续到追上或丢失
                break;
            case ATTACK:
                maxStateTurns = Integer.MAX_VALUE;  // 持续到脱离战斗
                break;
            case FLEE:
                maxStateTurns = Integer.MAX_VALUE;  // 持续到安全
                break;
            case SLEEP:
                maxStateTurns = 5 + random.nextInt(6);  // 5-10
                break;
            default:
                maxStateTurns = 3;
                break;
        }
    }

    /**
     * 执行当前状态的行为。
     *
     * @param npc     NPC 实例
     * @param context 行动上下文
     */
    private void execute(Npc npc, CreatureActionContext context) {
        switch (currentState) {
            case IDLE:
                // 不动
                break;

            case WALK:
                // 30% 概率随机移动
                if (random.nextInt(100) < 30) {
                    int dx = random.nextInt(3) - 1;
                    int dy = random.nextInt(3) - 1;
                    if (dx != 0 || dy != 0) {
                        tryMove(npc, dx, dy, context);
                    }
                }
                break;

            case PATROL:
                patrol(npc, context);
                break;

            case HUNT_PREY:
                huntPlayer(npc, context);
                break;

            case ATTACK:
                attackPlayer(npc, context);
                break;

            case FLEE:
                fleeFromPlayer(npc, context);
                break;

            case SLEEP:
                // 睡眠时恢复少量 HP
                if (npc.getHp() < npc.getMaxHp()) {
                    npc.heal(1);
                }
                break;

            case TALK:
            case TRADE:
                // 交互状态，不需要自动执行行为
                break;
        }
    }

    /**
     * 追击玩家 — 向玩家方向移动。
     *
     * @param npc     NPC 实例
     * @param context 行动上下文
     */
    private void huntPlayer(Npc npc, CreatureActionContext context) {
        int targetX = context.getPlayerTileX();
        int targetY = context.getPlayerTileY();
        int dx = Integer.compare(targetX, npc.getTileX());
        int dy = Integer.compare(targetY, npc.getTileY());

        // 尝试直接向玩家移动
        if (!tryMove(npc, dx, dy, context)) {
            // 对角线尝试
            if (dx != 0 && dy != 0) {
                if (!tryMove(npc, dx, 0, context)) {
                    tryMove(npc, 0, dy, context);
                }
            } else if (dx != 0) {
                tryMove(npc, dx, random.nextBoolean() ? 1 : -1, context);
            } else {
                tryMove(npc, random.nextBoolean() ? 1 : -1, dy, context);
            }
        }
    }

    /**
     * 攻击玩家 — 近战攻击。
     *
     * @param npc     NPC 实例
     * @param context 行动上下文
     */
    private void attackPlayer(Npc npc, CreatureActionContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isAlive()) {
            enterState(NpcAIState.IDLE);
            return;
        }

        // 检查是否在攻击范围内（相邻瓦片）
        int dist = npc.distanceTo(player.getTileX(), player.getTileY());
        if (dist > 1) {
            // 不在范围内，回到追击
            enterState(NpcAIState.HUNT_PREY);
            return;
        }

        // 近战攻击
        int damage = npc.meleeAttack(player);
        if (damage > 0) {
            com.github.game.cdda.log.GameLog.getInstance().log(
                    String.format("%s 攻击了你，造成 %d 点伤害！",
                            npc.getName(), damage));
        }
    }

    /**
     * 远离玩家方向移动。
     *
     * @param npc     NPC 实例
     * @param context 行动上下文
     */
    private void fleeFromPlayer(Npc npc, CreatureActionContext context) {
        int dx = Integer.compare(npc.getTileX(), context.getPlayerTileX());
        int dy = Integer.compare(npc.getTileY(), context.getPlayerTileY());

        // 优先移动距离较大的方向
        if (Math.abs(dx) >= Math.abs(dy)) {
            if (tryMove(npc, dx, 0, context)) return;
            if (tryMove(npc, 0, dy, context)) return;
        } else {
            if (tryMove(npc, 0, dy, context)) return;
            if (tryMove(npc, dx, 0, context)) return;
        }

        // 对角线逃跑
        if (dx != 0 && dy != 0) {
            tryMove(npc, dx, dy, context);
        }
    }

    /**
     * 沿巡逻路径移动。
     * 向当前目标路径点方向移动，到达后自动推进到下一个。
     *
     * @param npc     NPC 实例
     * @param context 行动上下文
     */
    private void patrol(Npc npc, CreatureActionContext context) {
        java.awt.Point target = npc.getPatrolTarget();
        if (target == null) return;

        int dx = Integer.compare(target.x, npc.getTileX());
        int dy = Integer.compare(target.y, npc.getTileY());

        // 到达当前路径点
        if (dx == 0 && dy == 0) {
            npc.advancePatrolWaypoint();
            // 尝试移动到下一个点
            target = npc.getPatrolTarget();
            if (target == null) return;
            dx = Integer.compare(target.x, npc.getTileX());
            dy = Integer.compare(target.y, npc.getTileY());
        }

        // 尝试向目标移动（优先主轴，备选副轴）
        if (dx != 0 && dy != 0) {
            if (!tryMove(npc, dx, 0, context)) {
                tryMove(npc, 0, dy, context);
            }
        } else if (dx != 0) {
            tryMove(npc, dx, 0, context);
        } else {
            tryMove(npc, 0, dy, context);
        }
    }

    /**
     * 尝试移动 NPC。
     * 移动成功后更新空间索引。
     *
     * @param npc     NPC 实例
     * @param dx      X 方向移动量（-1/0/1）
     * @param dy      Y 方向移动量（-1/0/1）
     * @param context 行动上下文
     * @return 移动成功返回 {@code true}，目标不可通行或被占用返回 {@code false}
     */
    private boolean tryMove(Npc npc, int dx, int dy, CreatureActionContext context) {
        int newX = npc.getTileX() + dx;
        int newY = npc.getTileY() + dy;

        // 检查目标瓦片是否可通行
        ChunkManager chunkManager = context.getChunkManager();
        TileType tile = chunkManager.getTile(newX, newY);
        if (tile == null || !tile.isPassable()) {
            return false;
        }

        // 检查目标位置是否有其他生物
        CreatureManager creatureManager = context.getCreatureManager();
        if (creatureManager != null) {
            com.github.game.cdda.creature.Creature existing = creatureManager.getCreatureAtTile(newX, newY);
            if (existing != null && existing.isAlive()) {
                return false;
            }
        }

        // 记录旧位置
        int oldX = npc.getTileX();
        int oldY = npc.getTileY();

        // 移动
        npc.setTileX(newX);
        npc.setTileY(newY);

        // 更新空间索引
        if (creatureManager != null) {
            creatureManager.getCreatureGrid().move(npc, oldX, oldY, newX, newY);
        }

        return true;
    }

    /**
     * 进入交互状态（由玩家输入触发）。
     *
     * @param state 交互状态（如 {@link NpcAIState#TALK} 或 {@link NpcAIState#TRADE}）
     */
    public void enterStateForInteraction(NpcAIState state) {
        currentState = state;
        stateTurns = 0;
        maxStateTurns = Integer.MAX_VALUE;
    }

    /**
     * 结束交互，回到空闲状态。
     */
    public void endInteraction() {
        enterState(NpcAIState.IDLE);
    }

    /**
     * 获取当前 AI 状态（调试用）。
     *
     * @return 当前 AI 状态枚举值
     */
    public NpcAIState getCurrentState() {
        return currentState;
    }
}
