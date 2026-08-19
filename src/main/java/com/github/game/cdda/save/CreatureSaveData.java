package com.github.game.cdda.save;

import java.util.ArrayList;
import java.util.List;

/**
 * 生物存档数据。
 * 保存所有生物的信息（不包括玩家，玩家单独保存）。
 */
public class CreatureSaveData {
    /** 生物列表 */
    public List<CreatureData> creatures = new ArrayList<>();

    public CreatureSaveData() {}
}
