package com.github.game.cdda.save;

/**
 * 物品堆栈数据。
 * 用于存档中保存物品信息。
 */
public class ItemStackData {
    /** 物品类型名称 */
    public String itemName;
    /** 数量 */
    public int count;

    public ItemStackData() {}

    public ItemStackData(String itemName, int count) {
        this.itemName = itemName;
        this.count = count;
    }
}
