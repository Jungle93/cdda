package com.github.game.cdda.trap;

import com.github.game.cdda.creature.Animal;

/**
 * 已放置的陷阱。
 *
 * <p>陷阱放置在地图瓦片上，持续存在直到被收取或破坏。
 * 状态流转：
 * <pre>
 * ARMED（已布设） → TRIGGERED（已触发） → 被玩家收取
 *                 ↘ 未触发，继续等待
 * </pre>
 *
 * <p>当动物移动到此陷阱所在瓦片时，按捕获概率判定是否捕获。
 * 捕获成功：动物死亡，陷阱状态变为 TRIGGERED，生成尸体物品。
 * 捕获失败：陷阱可能保持 ARMED 或变为 TRIGGERED（空触发）。
 */
public class PlacedTrap {

    /** 陷阱状态 */
    public enum State {
        /** 已布设，等待猎物 */
        ARMED,
        /** 已触发（含捕获或空触发） */
        TRIGGERED
    }

    /** 陷阱类型 ID（对应物品名，如 "loop_snare"） */
    private final String trapType;

    /** 陷阱所在瓦片 X */
    private int tileX;

    /** 陷阱所在瓦片 Y */
    private int tileY;

    /** 当前状态 */
    private State state;

    /** 捕获到的动物（null 表示未捕获或空触发） */
    private Animal capturedAnimal;

    /** 放置时的游戏时间（游戏秒） */
    private final long placedAtGameTime;

    /** 上次检查陷阱的游戏时间 */
    private long lastCheckTime;

    /**
     * 创建已放置的陷阱。
     *
     * @param trapType        陷阱类型 ID
     * @param tileX           瓦片 X
     * @param tileY           瓦片 Y
     * @param placedAtGameTime 放置时的游戏时间
     */
    public PlacedTrap(String trapType, int tileX, int tileY, long placedAtGameTime) {
        this.trapType = trapType;
        this.tileX = tileX;
        this.tileY = tileY;
        this.state = State.ARMED;
        this.placedAtGameTime = placedAtGameTime;
        this.lastCheckTime = placedAtGameTime;
    }

    // ── 访问器 ──

    /** 获取陷阱类型 ID */
    public String getTrapType() { return trapType; }
    /** 获取陷阱所在瓦片 X 坐标 */
    public int getTileX() { return tileX; }
    /** 获取陷阱所在瓦片 Y 坐标 */
    public int getTileY() { return tileY; }
    /** 获取陷阱当前状态 */
    public State getState() { return state; }
    /** 获取被捕获的动物（未捕获时返回 null） */
    public Animal getCapturedAnimal() { return capturedAnimal; }
    /** 获取陷阱放置时的游戏时间（秒） */
    public long getPlacedAtGameTime() { return placedAtGameTime; }
    /** 获取上次检查陷阱的游戏时间（秒） */
    public long getLastCheckTime() { return lastCheckTime; }

    // ── 状态变更 ──

    /**
     * 尝试捕获动物。
     * 仅在 ARMED 状态下有效。
     *
     * @param animal 踩中陷阱的动物
     */
    public void capture(Animal animal) {
        if (state != State.ARMED) return;
        this.capturedAnimal = animal;
        this.state = State.TRIGGERED;
    }

    /**
     * 空触发（动物踩过但未捕获）。
     * 陷阱变为 TRIGGERED 状态。
     */
    public void emptyTrigger() {
        if (state != State.ARMED) return;
        this.state = State.TRIGGERED;
    }

    /**
     * 重置陷阱（重新布设）。
     * 清除捕获的动物，恢复 ARMED 状态。
     */
    public void reset(long currentGameTime) {
        this.capturedAnimal = null;
        this.state = State.ARMED;
        this.lastCheckTime = currentGameTime;
    }

    /** 更新上次检查时间 */
    public void setLastCheckTime(long time) {
        this.lastCheckTime = time;
    }

    /** 是否已捕获动物 */
    public boolean hasCapture() {
        return state == State.TRIGGERED && capturedAnimal != null;
    }
}
