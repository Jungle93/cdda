package com.github.game.cdda.game;

import com.github.game.cdda.Constants;
import com.github.game.cdda.game.time.GameCalendar;
import com.github.game.cdda.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 回合管理器。管理回合制游戏中的行动调度和时钟推进。
 *
 * <p>核心职责：
 * <ul>
 *   <li>维护参与回合系统的所有实体列表</li>
 *   <li>执行行动时扣减移动点、推进游戏时钟</li>
 *   <li>当实体移动点耗尽时补充移动点（= speed）</li>
 *   <li>为 NPC/敌人预留调度接口</li>
 * </ul>
 *
 * <h3>回合流程（CDDA 模式）：</h3>
 * <ol>
 *   <li>玩家按键 → 检查 {@code moves ≥ MOVE_COST} → 执行动作 → {@link #addAction(Entity, long)}</li>
 *   <li>若 {@code moves > 0} → 继续等待输入（不触发生物回合）</li>
 *   <li>若 {@code moves ≤ 0} → 生物回合 → {@link #processRound()} → 补满移动点</li>
 * </ol>
 *
 * <h3>移动点系统：</h3>
 * <ul>
 *   <li>每轮所有实体获得 moves += speed</li>
 *   <li>行动消耗固定 {@link Constants#MOVE_COST}（默认100）点移动点</li>
 *   <li>速度100 → 每轮获得100点 → 恰好行动1次</li>
 *   <li>速度200 → 每轮获得200点 → 可行动2次</li>
 *   <li>速度50 → 每轮获得50点 → 需2回合才能行动1次</li>
 * </ul>
 */
public class TurnManager {

    /** 游戏时钟 */
    private final GameCalendar gameTime;

    /** 所有参与回合的实体 */
    private final List<Entity> entities = new ArrayList<>();

    /** 当前回合数 */
    private long currentRound = 0;

    /**
     * 创建回合管理器。
     *
     * @param gameTime 游戏时钟实例
     */
    public TurnManager(GameCalendar gameTime) {
        this.gameTime = gameTime;
    }

    // ── 实体管理 ──────────────────────────────────

    /**
     * 添加实体到回合系统。
     * 重复添加同一实体将被忽略。
     *
     * @param entity 要添加的实体（null 将被忽略）
     */
    public void addEntity(Entity entity) {
        if (entity != null && !entities.contains(entity)) {
            entities.add(entity);
        }
    }

    /**
     * 移除实体。
     *
     * @param entity 要移除的实体
     */
    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    /**
     * 获取实体列表（只读）。
     *
     * @return 不可修改的实体列表
     */
    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    // ── 行动处理 ──────────────────────────────────

    /**
     * 执行一个行动：推进游戏时钟 + 扣减移动点。
     *
     * <p>移动点扣减使用固定成本 {@link Constants#MOVE_COST}（不随速度变化）。
     * 游戏时钟推进使用速度调整后的时间（快速角色实际用时更少）。
     *
     * @param entity    执行行动的实体
     * @param baseTurns 基础行动回合数（时钟推进用）
     */
    public void addAction(Entity entity, long baseTurns) {
        // 游戏时钟按速度调整后的时间推进（快速角色实际用时更少）
        long actualTurns = entity.getActionTime(baseTurns);
        gameTime.advance(actualTurns);

        // 扣减移动点（固定成本，不随速度变化）
        entity.spendMoves(Constants.MOVE_COST);
    }

    /**
     * 处理新回合开始：当实体的移动点耗尽时调用。
     * 为所有实体恢复移动点（= speed），然后递增回合计数。
     *
     * <p>典型调用时机：玩家移动点耗尽后，触发生物回合，再调用此方法
     * 补满所有实体的移动点，开始下一轮。
     */
    public void processRound() {
        currentRound++;
        for (Entity entity : entities) {
            entity.addMoves(entity.getSpeed());
        }
    }

    /**
     * 检查实体是否可以行动（移动点 ≥ MOVE_COST）。
     *
     * @param entity 要检查的实体
     * @return 是否有足够移动点
     */
    public boolean canAct(Entity entity) {
        return entity.hasMoves(Constants.MOVE_COST);
    }

    /**
     * 检查实体是否有任意移动点（> 0）。
     * 用于判断是否需要触发生物回合 + processRound。
     *
     * @param entity 要检查的实体
     * @return 移动点是否 > 0
     */
    public boolean hasMoves(Entity entity) {
        return entity.getMoves() > 0;
    }

    // ── 访问器 ──────────────────────────────────

    /**
     * 获取游戏时钟。
     *
     * @return 游戏日历实例
     */
    public GameCalendar getGameTime() {
        return gameTime;
    }

    /**
     * 获取当前回合数。
     *
     * @return 回合数（从0开始）
     */
    public long getCurrentRound() {
        return currentRound;
    }
}
