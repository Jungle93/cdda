package com.github.game.cdda.world.vegetation;

import com.github.game.cdda.game.PlantGrowthConstants;

/**
 * 植物生长阶段。
 *
 * <p>每种植被在不同阶段有不同的需求和行为：
 * <ul>
 *   <li><b>发芽期</b> — 种子刚萌发，需要高肥力，极低消耗</li>
 *   <li><b>幼苗期</b> — 初期生长，需要中等肥力，低消耗</li>
 *   <li><b>生长期</b> — 快速生长，需要较高肥力，中消耗</li>
 *   <li><b>成熟期</b> — 完全长成，需要较低肥力，高消耗</li>
 *   <li><b>枯萎</b> — 肥力不足导致枯萎，不再消耗肥力</li>
 * </ul>
 */
public enum GrowthStage {
    /** 发芽期（种子刚萌发） */
    GERMINATING("发芽期"),
    /** 幼苗期（幼小植株） */
    SEEDLING("幼苗期"),
    /** 生长期（快速生长） */
    GROWING("生长期"),
    /** 成熟期（完全长成，可繁殖） */
    MATURE("成熟期"),
    /** 枯萎（肥力不足或寿命到期） */
    WITHERED("枯萎");

    private final String displayName;

    GrowthStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 是否已死亡/枯萎 */
    public boolean isDead() {
        return this == WITHERED;
    }

    /** 是否还在生长 */
    public boolean isAlive() {
        return this != WITHERED;
    }

    /**
     * 获取当前阶段所需的游戏天数（依植物类型而定）。
     *
     * @param plantType 植被类型字符串（"tree", "shrub", "grass", "flower", "moss"）
     * @return 该阶段持续的游戏天数
     */
    public int getDurationDays(String plantType) {
        return switch (this) {
            case GERMINATING -> 1; // 发芽期统一 1 天
            case SEEDLING -> PlantGrowthConstants.getSeedlingDays(plantType);
            case GROWING -> PlantGrowthConstants.getGrowingDays(plantType);
            case MATURE -> PlantGrowthConstants.getMatureLifespanDays(plantType);
            case WITHERED -> Integer.MAX_VALUE; // 永久
        };
    }
}
