package com.github.game.cdda.save;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家存档数据。
 * 保存玩家位置、属性、背包等信息。
 */
public class PlayerSaveData {
    /** 世界坐标 X */
    public int worldX;
    /** 世界坐标 Y */
    public int worldY;
    /** 当前 HP */
    public int hp;
    /** 最大 HP */
    public int maxHp;
    /** 力量 */
    public int strength;
    /** 敏捷 */
    public int agility;
    /** 耐力 */
    public int endurance;
    /** 速度 */
    public int speed;
    /** 背包物品 */
    public List<ItemStackData> inventory = new ArrayList<>();

    public PlayerSaveData() {}

    public PlayerSaveData(int worldX, int worldY, int hp, int maxHp,
                          int strength, int agility, int endurance, int speed) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.hp = hp;
        this.maxHp = maxHp;
        this.strength = strength;
        this.agility = agility;
        this.endurance = endurance;
        this.speed = speed;
    }
}
