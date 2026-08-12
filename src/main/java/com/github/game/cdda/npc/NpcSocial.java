package com.github.game.cdda.npc;

/**
 * NPC 社交/态度数据。
 * 记录 NPC 对玩家的态度及其他 NPC 的关系（预留）。
 */
public class NpcSocial {

    /** 对玩家的态度值（0~100，50 为中立） */
    private int attitudeToPlayer = 50;

    /** 是否已被玩家攻击过 */
    private boolean attackedByPlayer;

    /** 上次交互的游戏时间（秒，-1 表示从未交互） */
    private long lastInteractionTime = -1;

    // ── 预留：NPC 之间的关系 ──────────────────────

    /** 阵营标识（预留，null 表示无阵营） */
    private String faction;

    /**
     * 调整对玩家的态度。
     *
     * @param delta 变化量（正=更友好，负=更敌对）
     */
    public void adjustAttitude(int delta) {
        attitudeToPlayer = Math.max(0, Math.min(100, attitudeToPlayer + delta));
    }

    /**
     * 根据 NPC 类型设置初始态度。
     */
    public void initializeForType(NpcType type) {
        attitudeToPlayer = switch (type) {
            case FRIENDLY -> 60;
            case NEUTRAL -> 50;
            case HOSTILE -> 20;
            case FUNCTIONAL -> 70;
        };
    }

    /**
     * 记录玩家攻击行为。
     */
    public void recordPlayerAttack() {
        attackedByPlayer = true;
        adjustAttitude(-30);
    }

    /**
     * 记录交互时间。
     */
    public void recordInteraction(long gameSeconds) {
        lastInteractionTime = gameSeconds;
    }

    // ── 访问器 ──────────────────────────────────

    public int getAttitudeToPlayer() { return attitudeToPlayer; }
    public boolean isAttackedByPlayer() { return attackedByPlayer; }
    public long getLastInteractionTime() { return lastInteractionTime; }
    public String getFaction() { return faction; }
    public void setFaction(String faction) { this.faction = faction; }
}
