package com.github.game.cdda.npc.ai;

/**
 * NPC AI 状态枚举。
 * 定义 NPC 行为状态机的所有状态。
 */
public enum NpcAIState {

    /** 空闲 — 原地休息 */
    IDLE,

    /** 游荡 — 随机移动 */
    WALK,

    /** 巡逻 — 沿固定路线移动（预留） */
    PATROL,

    /** 对话 — 与玩家交互 */
    TALK,

    /** 交易 — 与玩家交易 */
    TRADE,

    /** 攻击 — 近战攻击玩家 */
    ATTACK,

    /** 逃跑 — 远离威胁 */
    FLEE,

    /** 追击 — 追击玩家 */
    HUNT_PREY,

    /** 睡眠 — 休息恢复 */
    SLEEP
}
