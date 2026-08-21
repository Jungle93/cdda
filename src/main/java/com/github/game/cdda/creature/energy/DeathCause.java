package com.github.game.cdda.creature.energy;

/**
 * 死亡原因枚举。
 *
 * <p>决定死亡后的处理方式：
 * <ul>
 *   <li>{@link #NATURAL_AGE} / {@link #STARVATION} — 不掉落，尸体分解</li>
 *   <li>{@link #PREDATION} — 被上层捕食者吃掉，能量直接转移</li>
 *   <li>{@link #PLAYER_KILL} — 玩家杀死，掉落物品</li>
 * </ul>
 */
public enum DeathCause {

    /** 自然老死 */
    NATURAL_AGE,

    /** 饿死（长期找不到食物） */
    STARVATION,

    /** 脱水而死（长期缺水） */
    DEHYDRATION,

    /** 体温异常致死（失温/中暑） */
    TEMPERATURE,

    /** 被上层捕食者吃掉 */
    PREDATION,

    /** 被玩家杀死 */
    PLAYER_KILL;

    /** 是否自然死亡（不掉落物品） */
    public boolean isNatural() {
        return this == NATURAL_AGE || this == STARVATION
                || this == DEHYDRATION || this == TEMPERATURE;
    }

    /** 是否被猎杀（有能量转移或掉落） */
    public boolean isKilled() {
        return this == PREDATION || this == PLAYER_KILL;
    }
}
