package com.github.game.cdda.creature;

/**
 * 生物回合变更（后台计算 → EDT 应用）。
 *
 * <p>后台线程执行 AI 回合后，将结构性变更放入队列，
 * EDT 在每帧的 {@code update()} 中批量应用。
 */
public class CreatureMutation {

    public enum Type {
        /** 新生物出生 */
        BIRTH,
        /** 生物死亡 */
        DEATH,
        /** 迁徙生成（新区块出现捕食者） */
        MIGRATION
    }

    public final Type type;
    public final Creature creature;

    private CreatureMutation(Type type, Creature creature) {
        this.type = type;
        this.creature = creature;
    }

    public static CreatureMutation birth(Animal baby) {
        return new CreatureMutation(Type.BIRTH, baby);
    }

    public static CreatureMutation death(Creature creature) {
        return new CreatureMutation(Type.DEATH, creature);
    }

    public static CreatureMutation migration(Animal migrant) {
        return new CreatureMutation(Type.MIGRATION, migrant);
    }
}
