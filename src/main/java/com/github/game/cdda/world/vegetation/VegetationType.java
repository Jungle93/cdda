package com.github.game.cdda.world.vegetation;

/**
 * 植被类型枚举。
 * 定义植被的基本分类，决定其渲染方式和交互行为。
 */
public enum VegetationType {
    /** 树木 — 不可通过，可砍伐掉落原木 */
    TREE("树木", '&'),

    /** 灌木 — 不可通过，可采集获取树枝 */
    SHRUB("灌木", '%'),

    /** 草 — 可通过，可采集 */
    GRASS("草", ';'),

    /** 苔藓 — 可通过，可采集 */
    MOSS("苔藓", ','),

    /** 水生植物 — 不可通过，生长于水边 */
    AQUATIC("水生", '‖');

    private final String displayName;
    private final char defaultChar;

    VegetationType(String displayName, char defaultChar) {
        this.displayName = displayName;
        this.defaultChar = defaultChar;
    }

    /** 获取显示名称 */
    public String getDisplayName() {
        return displayName;
    }

    /** 获取默认显示字符 */
    public char getDefaultChar() {
        return defaultChar;
    }
}
