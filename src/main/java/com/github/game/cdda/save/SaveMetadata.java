package com.github.game.cdda.save;

/**
 * 存档元数据。
 * 记录存档时间、游戏版本等信息。
 */
public class SaveMetadata {
    /** 存档名称 */
    public String name;
    /** 存档时间（ISO 格式） */
    public String saveTime;
    /** 游戏总秒数 */
    public long totalSeconds;
    /** 存档版本号 */
    public int version = 1;

    public SaveMetadata() {}

    public SaveMetadata(String name, String saveTime, long totalSeconds) {
        this.name = name;
        this.saveTime = saveTime;
        this.totalSeconds = totalSeconds;
    }
}
