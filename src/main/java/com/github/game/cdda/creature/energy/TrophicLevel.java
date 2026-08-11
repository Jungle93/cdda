package com.github.game.cdda.creature.energy;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 营养级枚举。
 *
 * <p>标注每个物种在能量链中的位置，决定其食物来源和行为模式。
 */
public enum TrophicLevel {

    /**
     * 初级消费者 — 食草动物（兔子、鹿、野猪等）。
     * 直接啃食植物，数量多，繁殖快，警惕性高。
     */
    PRIMARY_CONSUMER("初级消费者", EnumSet.noneOf(TrophicLevel.class)),

    /**
     * 次级消费者 — 小型捕食者（狐狸、獾、蛇等）。
     * 捕食初级消费者，数量中等，有领地意识。
     */
    SECONDARY_CONSUMER("次级消费者", EnumSet.of(PRIMARY_CONSUMER)),

    /**
     * 顶级消费者 — 大型捕食者（狼、熊、豹等）。
     * 捕食次级消费者和大型食草动物，数量稀少，战斗力强。
     */
    APEX_PREDATOR("顶级捕食者", EnumSet.of(PRIMARY_CONSUMER, SECONDARY_CONSUMER)),

    /**
     * 食腐动物 — 以尸体为食（秃鹫等）。
     * 寻找自然死亡的动物尸体。
     */
    SCAVENGER("食腐动物", EnumSet.noneOf(TrophicLevel.class)),

    /**
     * 分解者 — 真菌、昆虫等。
     * 将死亡生物分解为无机物，完成能量循环。
     */
    DECOMPOSER("分解者", EnumSet.noneOf(TrophicLevel.class));

    /** 显示名称 */
    private final String displayName;

    /** 可捕食的营养级（本营养级的猎物范围） */
    private final Set<TrophicLevel> preyLevels;

    TrophicLevel(String displayName, Set<TrophicLevel> preyLevels) {
        this.displayName = displayName;
        this.preyLevels = Collections.unmodifiableSet(preyLevels);
    }

    /** 获取显示名称 */
    public String getDisplayName() {
        return displayName;
    }

    /** 获取可捕食的营养级集合 */
    public Set<TrophicLevel> getPreyLevels() {
        return preyLevels;
    }

    /** 判断是否可以捕食指定营养级 */
    public boolean canPreyOn(TrophicLevel target) {
        return preyLevels.contains(target);
    }

    /** 是否为食草动物 */
    public boolean isHerbivore() {
        return this == PRIMARY_CONSUMER;
    }

    /** 是否为捕食者（次级 + 顶级） */
    public boolean isPredator() {
        return this == SECONDARY_CONSUMER || this == APEX_PREDATOR;
    }

    /** 是否为食腐动物 */
    public boolean isScavenger() {
        return this == SCAVENGER;
    }
}
