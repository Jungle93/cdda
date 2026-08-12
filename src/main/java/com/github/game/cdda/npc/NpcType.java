package com.github.game.cdda.npc;

/**
 * NPC 类型枚举。
 * 影响 NPC 的 AI 行为初始倾向和交互方式。
 */
public enum NpcType {

    /** 友好 — 可对话、交易、提供信息 */
    FRIENDLY,

    /** 中立 — 观察后决定态度 */
    NEUTRAL,

    /** 敌对 — 主动攻击玩家 */
    HOSTILE,

    /** 功能型 — 提供任务、引导等特殊功能 */
    FUNCTIONAL
}
