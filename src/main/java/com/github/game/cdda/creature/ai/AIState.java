package com.github.game.cdda.creature.ai;

/**
 * 动物 AI 状态枚举。
 * 定义动物行为状态机的所有状态。
 */
public enum AIState {

    /** 空闲 — 原地休息 */
    IDLE,

    /** 游荡 — 随机移动 */
    WANDER,

    /** 觅食 — 吃草/寻找食物 */
    GRAZE,

    /** 逃跑 — 远离威胁（玩家） */
    FLEE,

    /** 狩猎 — 主动追击猎物 */
    HUNT,

    /** 食腐 — 寻找尸体 */
    SCAVENGE
}
