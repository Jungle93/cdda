package com.github.game.cdda.game;

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
 *   <li>执行行动时计算实际消耗回合并推进游戏时钟</li>
 *   <li>每回合开始时为所有实体恢复能量</li>
 *   <li>为未来 NPC/敌人预留调度接口</li>
 * </ul>
 *
 * <h3>回合流程：</h3>
 * <ol>
 *   <li>玩家按键 → 若有足够能量 → 执行动作 → {@link #addAction(Entity, long)}</li>
 *   <li>调用 {@link #processRound()} → 所有实体 energy += speed</li>
 *   <li>未来扩展：NPC/敌人按 energy 顺序行动</li>
 * </ol>
 *
 * <h3>能量系统：</h3>
 * <ul>
 *   <li>每回合所有实体获得 energy += speed</li>
 *   <li>行动消耗 ENERGY_PER_ACTION（默认100）点能量</li>
 *   <li>速度100 → 每回合获得100能量 → 恰好行动1次</li>
 *   <li>速度200 → 每回合获得200能量 → 可行动2次</li>
 *   <li>速度50 → 每回合获得50能量 → 需2回合才能行动1次</li>
 * </ul>
 */
public class TurnManager {

    /** 每次行动消耗的基础能量 */
    public static final long ENERGY_PER_ACTION = 100;

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
     * 执行一个行动：计算消耗回合、推进时钟、消耗能量。
     *
     * @param entity    执行行动的实体
     * @param baseTurns 基础行动回合数
     */
    public void addAction(Entity entity, long baseTurns) {
        // 计算实际消耗回合（受速度影响）
        long actualTurns = entity.getActionTime(baseTurns);

        // 推进游戏时钟
        gameTime.advance(actualTurns);

        // 消耗能量
        entity.spendEnergy(ENERGY_PER_ACTION);
    }

    /**
     * 处理新回合开始：为所有实体恢复能量。
     * 在玩家行动后调用，准备下一轮行动。
     */
    public void processRound() {
        currentRound++;
        for (Entity entity : entities) {
            entity.addEnergy(entity.getSpeed());
        }
    }

    /**
     * 检查实体是否可以行动。
     *
     * @param entity 要检查的实体
     * @return 是否有足够能量（≥ ENERGY_PER_ACTION）
     */
    public boolean canAct(Entity entity) {
        return entity.hasEnergy(ENERGY_PER_ACTION);
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
