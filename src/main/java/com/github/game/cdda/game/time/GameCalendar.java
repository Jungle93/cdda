package com.github.game.cdda.game.time;

/**
 * CDDA 游戏日历。继承通用 {@link com.github.game.engine.core.time.GameClock}，
 * 添加游戏特定的日历规则。
 *
 * <p>日历规则：
 * <ul>
 *   <li>1 分钟 = 60 秒</li>
 *   <li>1 小时 = 60 分钟 = 3600 秒</li>
 *   <li>1 天 = 24 小时 = 86400 秒</li>
 *   <li>1 月 = 30 天</li>
 *   <li>1 年 = 12 个月 = 360 天</li>
 * </ul>
 *
 * <p>支持自定义起始月份和小时。
 */
public class GameCalendar extends com.github.game.engine.core.time.GameClock {

    /** 每月的天数 */
    public static final int DAYS_PER_MONTH = 30;
    /** 每年的月数 */
    public static final int MONTHS_PER_YEAR = 12;
    /** 每年的天数 */
    public static final int DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR; // 360

    /** 起始月份（游戏开始的月份） */
    private final Month startMonth;

    /** 起始小时（游戏开始的时间，默认8点） */
    private final int startHour;

    /**
     * 使用默认设置创建：一月一日 08:00。
     */
    public GameCalendar() {
        this(Month.JANUARY, 8);
    }

    /**
     * 使用指定起始月份和小时创建。
     *
     * @param startMonth 起始月份
     * @param startHour  起始小时（0-23）
     */
    public GameCalendar(Month startMonth, int startHour) {
        this.startMonth = startMonth;
        this.startHour = Math.max(0, Math.min(23, startHour));
    }

    // ── 日历分量查询 ──────────────────────────────────

    /**
     * 获取当前月份。
     * 从起始月份开始，按月偏移计算。
     *
     * @return 当前月份
     */
    public Month getMonth() {
        return Month.fromIndex(startMonth.ordinal() + (int) (getTotalDays() / DAYS_PER_MONTH));
    }

    /**
     * 获取当前季节（由月份决定）。
     *
     * @return 当前季节
     */
    public Season getSeason() {
        return getMonth().getSeason();
    }

    /**
     * 获取当前年份（从1开始）。
     * 每年360天（12月×30天）。
     *
     * @return 当前年份（≥1）
     */
    public int getYear() {
        long totalMonths = startMonth.ordinal() + getTotalDays() / DAYS_PER_MONTH;
        return 1 + (int) (totalMonths / MONTHS_PER_YEAR);
    }

    /**
     * 获取当前月份中的日期（1-30）。
     *
     * @return 月内日期（1-30）
     */
    public int getDayOfMonth() {
        return (int) (getTotalDays() % DAYS_PER_MONTH) + 1;
    }

    /**
     * 获取当前是第几天（从起始日算起，从1开始）。
     * 兼容旧接口。
     *
     * @return 天数（≥1）
     */
    public int getDay() {
        return (int) getTotalDays() + 1;
    }

    // ── 访问器 ──────────────────────────────────

    /**
     * 获取起始月份。
     *
     * @return 起始月份
     */
    public Month getStartMonth() { return startMonth; }

    /**
     * 获取起始小时。
     *
     * @return 起始小时（0-23）
     */
    public int getStartHour() { return startHour; }

    // ── 格式化 ──────────────────────────────────

    /**
     * 格式化当前日期为 "第N年 季节 月份 第N天" 格式。
     * 示例："第1年 春季 三月 第15天"
     *
     * @return 格式化后的日期字符串
     */
    public String formatDate() {
        return String.format("第%d年 %s %s 第%d天",
                getYear(), getSeason().getFullName(),
                getMonth().getChineseName(), getDayOfMonth());
    }

    /**
     * 格式化完整日期时间。
     * 示例："第1年 春季 三月 第15天 08:30"
     *
     * @return 格式化后的日期时间字符串
     */
    public String formatDateTime() {
        return formatDate() + " " + formatTime();
    }

    @Override
    public String toString() {
        return formatDateTime();
    }
}
