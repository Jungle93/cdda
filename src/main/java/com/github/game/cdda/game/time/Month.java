package com.github.game.cdda.game.time;

/**
 * 月份枚举。定义12个月份的中文名及所属季节。
 *
 * <p>日历规则：
 * <ul>
 *   <li>每月固定 30 天</li>
 *   <li>每年 12 个月 = 360 天</li>
 *   <li>每季 3 个月</li>
 * </ul>
 *
 * <p>季节映射（北半球/欧洲参考）：
 * <ul>
 *   <li>冬季：十二月、一月、二月</li>
 *   <li>春季：三月、四月、五月</li>
 *   <li>夏季：六月、七月、八月</li>
 *   <li>秋季：九月、十月、十一月</li>
 * </ul>
 */
public enum Month {

    JANUARY(1, "一月", Season.WINTER),
    FEBRUARY(2, "二月", Season.WINTER),
    MARCH(3, "三月", Season.SPRING),
    APRIL(4, "四月", Season.SPRING),
    MAY(5, "五月", Season.SPRING),
    JUNE(6, "六月", Season.SUMMER),
    JULY(7, "七月", Season.SUMMER),
    AUGUST(8, "八月", Season.SUMMER),
    SEPTEMBER(9, "九月", Season.AUTUMN),
    OCTOBER(10, "十月", Season.AUTUMN),
    NOVEMBER(11, "十一月", Season.AUTUMN),
    DECEMBER(12, "十二月", Season.WINTER);

    /** 月份编号（1-12） */
    private final int monthNumber;
    /** 中文名 */
    private final String chineseName;
    /** 所属季节 */
    private final Season season;

    Month(int monthNumber, String chineseName, Season season) {
        this.monthNumber = monthNumber;
        this.chineseName = chineseName;
        this.season = season;
    }

    /**
     * 获取月份编号（1-12）。
     *
     * @return 月份编号
     */
    public int getMonthNumber() { return monthNumber; }

    /**
     * 获取中文名。
     *
     * @return 中文月份名（如"一月"）
     */
    public String getChineseName() { return chineseName; }

    /**
     * 获取所属季节。
     *
     * @return 所属季节
     */
    public Season getSeason() { return season; }

    /**
     * 根据索引获取月份（0=JANUARY, 11=DECEMBER）。
     *
     * @param index 月份索引（0-11）
     * @return 对应的月份
     */
    public static Month fromIndex(int index) {
        return values()[((index % 12) + 12) % 12];
    }

    /**
     * 获取下一个月份。
     *
     * @return 下一个月（十二月→一月）
     */
    public Month next() {
        return values()[(ordinal() + 1) % 12];
    }
}
