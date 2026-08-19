package com.github.game.cdda.save;

/**
 * 单生物存档数据。
 * 保存生物的位置、状态等信息。
 */
public class CreatureData {
    /** 物种 ID */
    public String speciesId;
    /** 瓦片 X */
    public int tileX;
    /** 瓦片 Y */
    public int tileY;
    /** 当前 HP */
    public int hp;
    /** 最大 HP */
    public int maxHp;
    /** 速度 */
    public int speed;
    /** 是否存活 */
    public boolean alive;

    public CreatureData() {}

    public CreatureData(String speciesId, int tileX, int tileY,
                        int hp, int maxHp, int speed, boolean alive) {
        this.speciesId = speciesId;
        this.tileX = tileX;
        this.tileY = tileY;
        this.hp = hp;
        this.maxHp = maxHp;
        this.speed = speed;
        this.alive = alive;
    }
}
