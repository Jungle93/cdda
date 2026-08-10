package com.github.game.cdda.creature.config;

import java.util.List;

/**
 * 生物定义（JSON 数据结构）。
 * 通过 Gson 从 JSON 文件加载，描述一种生物的模板数据。
 */
public class CreatureDefinition {

    /** 唯一标识符 */
    public String id;

    /** 显示名称 */
    public String name;

    /** 生命值 */
    public int hp;

    /** 速度（影响行动频率） */
    public int speed;

    /** 基础属性 */
    public Stats stats;

    /** 感知范围 */
    public Perception perception;

    /** 渲染字符 */
    public String displayChar;

    /** 渲染颜色 [r, g, b] */
    public int[] displayColor;

    /** 生命阶段列表 */
    public List<LifeStage> lifeStages;

    /** 战利品表（死亡掉落物品，可为 null） */
    public com.github.game.cdda.item.LootTable lootTable;

    /** 繁殖参数（可为 null 表示不繁殖） */
    public Reproduction reproduction;

    /**
     * 基础属性。
     */
    public static class Stats {
        /** 力量 */
        public int strength;
        /** 敏捷 */
        public int agility;
        /** 耐力 */
        public int endurance;
    }

    /**
     * 感知范围。
     */
    public static class Perception {
        /** 视觉范围（瓦片） */
        public int vision;
        /** 听觉范围（瓦片） */
        public int hearing;
    }

    /**
     * 生命阶段。
     */
    public static class LifeStage {
        /** 阶段标识（如 "juvenile", "adult"） */
        public String stage;
        /** 阶段显示名称 */
        public String name;
        /** 该阶段生命值 */
        public int hp;
        /** 成长到此阶段所需回合数（null 表示最终阶段） */
        public Integer growTurns;
    }

    /**
     * 繁殖参数。
     * 控制生物的繁殖行为：成熟条件、冷却、概率、后代数量。
     */
    public static class Reproduction {
        /** 成熟所需回合数（达到后才能繁殖） */
        public int matureTurns = 1000;
        /** 繁殖冷却（两次繁殖间隔的最小回合数） */
        public int cooldownTurns = 2000;
        /** 每次繁殖最大后代数 */
        public int maxOffspring = 2;
        /** 每次繁殖概率 (0-1) */
        public double chance = 0.05;
    }
}
