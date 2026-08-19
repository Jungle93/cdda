package com.github.game.cdda.save;

/**
 * 游戏状态存档数据。
 * 保存世界设置、游戏时间等信息。
 */
public class GameStateSaveData {
    /** 世界种子 */
    public long seed;
    /** 起始月份 */
    public int startMonth;
    /** 起始小时 */
    public int startHour;
    /** 游戏总秒数 */
    public long totalSeconds;

    public GameStateSaveData() {}

    public GameStateSaveData(long seed, int startMonth, int startHour, long totalSeconds) {
        this.seed = seed;
        this.startMonth = startMonth;
        this.startHour = startHour;
        this.totalSeconds = totalSeconds;
    }
}
