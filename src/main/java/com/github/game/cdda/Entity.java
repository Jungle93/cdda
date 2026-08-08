package com.github.game.cdda;

/**
 * 游戏实体基类。所有参与回合系统的对象（玩家、NPC、生物）的公共父类。
 *
 * <p>管理通用的回合制属性：
 * <ul>
 *   <li><b>speed</b> — 移动速度，影响行动耗时。100 = 正常步行（1.2 m/s）</li>
 *   <li><b>energy</b> — 行动能量，每回合增加 speed 点，行动时消耗</li>
 * </ul>
 *
 * <p>行动时间公式：{@code actionTime = baseTime × 100 / speed}
 * <ul>
 *   <li>speed=100 → 正常速度</li>
 *   <li>speed=200 → 行动耗时减半（同样游戏时间内可行动两次）</li>
 *   <li>speed=50 → 行动耗时翻倍（同样游戏时间内行动减半）</li>
 * </ul>
 */
public abstract class Entity {

    /** 移动速度（默认100 = 正常步行） */
    protected int speed = 100;

    /** 当前行动能量 */
    protected int energy = 0;

    // ── 行动时间计算 ──────────────────────────────────

    /**
     * 根据速度计算行动实际耗时。
     * 速度越快，耗时越短：{@code baseTime × 100 / speed}
     *
     * @param baseTime 基础行动时间（游戏秒）
     * @return 实际消耗的游戏秒数
     */
    public int getActionTime(int baseTime) {
        if (speed <= 0) return baseTime;
        return baseTime * 100 / speed;
    }

    // ── 能量管理 ──────────────────────────────────

    /** 增加能量（每回合调用） */
    public void addEnergy(int amount) {
        energy += amount;
    }

    /** 消耗能量（行动时调用） */
    public void spendEnergy(int amount) {
        energy = Math.max(0, energy - amount);
    }

    /** 是否有足够能量执行行动 */
    public boolean hasEnergy(int amount) {
        return energy >= amount;
    }

    // ── 访问器 ──────────────────────────────────

    public int getSpeed() { return speed; }

    public void setSpeed(int speed) { this.speed = speed; }

    public int getEnergy() { return energy; }
}
