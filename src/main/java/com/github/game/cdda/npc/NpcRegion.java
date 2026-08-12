package com.github.game.cdda.npc;

import java.awt.Color;

/**
 * NPC 地域背景。
 * 复用 human_kind.md 的地域定义，决定 NPC 的基础属性、显示字符和颜色。
 */
public enum NpcRegion {

    /** 普通人 — 均衡无修正 */
    COMMON("普通人", 'n', new Color(180, 180, 180),
            10, 10, 10, 10, 10, 10),

    /** 北方高地人 — 强壮耐寒 */
    NORTHERN_HIGHLAND("北方高地人", 'H', new Color(100, 120, 180),
            12, 8, 14, 10, 8, 12),

    /** 南方河谷人 — 敏捷聪慧 */
    SOUTHERN_RIVER("南方河谷人", 'S', new Color(120, 180, 120),
            10, 12, 8, 12, 10, 8),

    /** 东部林居人 — 敏捷敏锐 */
    EASTERN_FOREST("东部林居人", 'E', new Color(80, 160, 80),
            8, 14, 8, 8, 14, 10),

    /** 西部草原人 — 强壮坚韧 */
    WESTERN_PRAIRIE("西部草原人", 'W', new Color(180, 160, 100),
            14, 10, 12, 8, 12, 10),

    /** 山地矿工 — 强壮耐力的 */
    MOUNTAIN_MINER("山地矿工", 'M', new Color(120, 120, 120),
            14, 6, 16, 10, 8, 12);

    /** 显示名称 */
    public final String name;
    /** 显示字符 */
    public final char displayChar;
    /** 基础显示颜色 */
    public final Color baseColor;
    /** 力量基线 */
    public final int baseStr;
    /** 敏捷基线 */
    public final int baseAgi;
    /** 体质基线 */
    public final int baseCon;
    /** 智力基线 */
    public final int baseInt;
    /** 感知基线 */
    public final int basePer;
    /** 意志基线 */
    public final int baseWil;

    NpcRegion(String name, char displayChar, Color baseColor,
              int str, int agi, int con, int int_, int per, int wil) {
        this.name = name;
        this.displayChar = displayChar;
        this.baseColor = baseColor;
        this.baseStr = str;
        this.baseAgi = agi;
        this.baseCon = con;
        this.baseInt = int_;
        this.basePer = per;
        this.baseWil = wil;
    }

    /**
     * 根据 NPC 类型计算最终显示颜色。
     * 在基础颜色上叠加类型修正。
     */
    public Color getColorForType(NpcType type) {
        return switch (type) {
            case FRIENDLY -> baseColor;
            case NEUTRAL -> baseColor;
            case HOSTILE -> new Color(
                    Math.min(255, baseColor.getRed() + 40),
                    baseColor.getGreen(),
                    baseColor.getBlue()
            );
            case FUNCTIONAL -> new Color(
                    Math.min(255, baseColor.getRed() + 30),
                    Math.min(255, baseColor.getGreen() + 30),
                    Math.max(0, baseColor.getBlue() - 20)
            );
        };
    }
}
