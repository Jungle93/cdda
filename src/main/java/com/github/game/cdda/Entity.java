package com.github.game.cdda;

/**
 * 游戏实体基类。所有参与回合系统的对象（玩家、NPC、生物）的公共父类。
 *
 * <p>管理通用的回合制属性：
 * <ul>
 *   <li><b>speed</b> — 移动速度，影响行动耗时。100 = 正常步行</li>
 *   <li><b>energy</b> — 行动能量，每回合增加 speed 点，行动时消耗</li>
 * </ul>
 *
 * <p>行动回合公式：{@code actionTurns = baseTurns × 100 / speed}
 * <ul>
 *   <li>speed=100 → 正常速度</li>
 *   <li>speed=200 → 行动回合减半（同样回合内可行动两次）</li>
 *   <li>speed=50 → 行动回合翻倍（同样回合内行动减半）</li>
 * </ul>
 */
public abstract class Entity {

    /** 移动速度（默认100 = 正常步行） */
    protected long speed = 100;

    /** 当前行动能量 */
    protected long energy = 0;

    // ── 行动回合计算 ──────────────────────────────────

    /**
     * 根据速度计算行动实际消耗回合数。
     * 速度越快，消耗越少：{@code baseTurns × 100 / speed}
     *
     * @param baseTurns 基础行动回合数
     * @return 实际消耗的回合数
     */
    public long getActionTime(long baseTurns) {
        if (speed <= 0) return baseTurns;
        return baseTurns * 100 / speed;
    }

    // ── 能量管理 ──────────────────────────────────

    /** 增加能量（每回合调用） */
    public void addEnergy(long amount) {
        energy += amount;
    }

    /** 消耗能量（行动时调用） */
    public void spendEnergy(long amount) {
        energy = Math.max(0, energy - amount);
    }

    /** 是否有足够能量执行行动 */
    public boolean hasEnergy(long amount) {
        return energy >= amount;
    }

    // ── 访问器 ──────────────────────────────────

    public long getSpeed() { return speed; }

    public void setSpeed(long speed) { this.speed = speed; }

    public long getEnergy() { return energy; }
}
