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
    PRIMARY_CONSUMER("初级消费者"),

    /**
     * 次级消费者 — 小型捕食者（狐狸、獾、蛇等）。
     * 捕食初级消费者，数量中等，有领地意识。
     */
    SECONDARY_CONSUMER("次级消费者"),

    /**
     * 顶级消费者 — 大型捕食者（狼、熊、豹等）。
     * 捕食次级消费者和大型食草动物，数量稀少，战斗力强。
     */
    APEX_PREDATOR("顶级捕食者"),

    /**
     * 食腐动物 — 以尸体为食（秃鹫等）。
     * 寻找自然死亡的动物尸体。
     */
    SCAVENGER("食腐动物"),

    /**
     * 分解者 — 真菌、昆虫等。
     * 将死亡生物分解为无机物，完成能量循环。
     */
    DECOMPOSER("分解者");

    /** 显示名称 */
    private final String displayName;

    TrophicLevel(String displayName) {
        this.displayName = displayName;
    }

    /** 获取显示名称 */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取可捕食的营养级集合。
     * 使用静态方法避免枚举初始化时的循环依赖。
     */
    public Set<TrophicLevel> getPreyLevels() {
        switch (this) {
            case SECONDARY_CONSUMER:
                return Collections.singleton(PRIMARY_CONSUMER);
            case APEX_PREDATOR:
                return EnumSet.of(PRIMARY_CONSUMER, SECONDARY_CONSUMER);
            default:
                return Collections.emptySet();
        }
    }

    /** 判断是否可以捕食指定营养级 */
    public boolean canPreyOn(TrophicLevel target) {
        return getPreyLevels().contains(target);
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
