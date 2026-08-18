package com.github.game.cdda;

/**
 * 游戏实体基类。所有参与回合系统的对象（玩家、NPC、生物）的公共父类。
 *
 * <p>管理通用的回合制属性：
 * <ul>
 *   <li><b>speed</b> — 移动速度，决定每轮获得的移动点数。100 = 正常步行</li>
 *   <li><b>moves</b> — 移动点数，每轮增加 speed 点，行动时消耗固定成本</li>
 * </ul>
 *
 * <p>行动回合公式：{@code actionTurns = baseTurns × 100 / speed}
 * <ul>
 *   <li>speed=100 → 正常速度（每轮 100 移动点，移动成本 100 → 1 次行动/轮）</li>
 *   <li>speed=200 → 快速（每轮 200 移动点 → 2 次行动/轮）</li>
 *   <li>speed=50 → 缓慢（每轮 50 移动点 → 每 2 轮 1 次行动）</li>
 * </ul>
 */
public abstract class Entity {

    /** 移动速度（默认100 = 正常步行），决定每轮获得的移动点数 */
    protected long speed = 100;

    /** 当前移动点数（每轮补充 speed，行动消耗固定成本） */
    protected long moves = 0;

    // ── 行动回合计算 ──────────────────────────────────

    /**
     * 根据速度计算行动实际消耗的游戏时钟回合数。
     * 速度越快，时钟推进越少（快速角色实际用时更短）：{@code baseTurns × 100 / speed}
     *
     * @param baseTurns 基础行动回合数
     * @return 实际消耗的回合数（用于推进游戏时钟）
     */
    public long getActionTime(long baseTurns) {
        if (speed <= 0) return baseTurns;
        return baseTurns * 100 / speed;
    }

    // ── 移动点管理 ──────────────────────────────────

    /** 增加移动点（每轮 processRound 调用） */
    public void addMoves(long amount) {
        moves += amount;
    }

    /** 消耗移动点（行动时调用） */
    public void spendMoves(long amount) {
        moves = Math.max(0, moves - amount);
    }

    /** 是否有足够移动点执行行动 */
    public boolean hasMoves(long amount) {
        return moves >= amount;
    }

    // ── 访问器 ──────────────────────────────────

    public long getSpeed() { return speed; }

    public void setSpeed(long speed) { this.speed = speed; }

    public long getMoves() { return moves; }
}
