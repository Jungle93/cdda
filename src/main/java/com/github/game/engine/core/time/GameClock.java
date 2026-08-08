package com.github.game.engine.core.time;

/**
 * 通用游戏时钟。跟踪累计游戏秒数，提供时分秒查询。
 *
 * <p>这是引擎层的通用时钟，不包含任何特定游戏的日历规则（月份、季节等）。
 * 具体游戏的日历规则由子类实现（如 GameCalendar）。
 *
 * <p>时钟本身不自动推进 — 由外部调用 {@link #advance(long)} 推进时间。
 * 在回合制游戏中，时钟仅在角色执行行动时推进。
 */
public class GameClock {

    /** 每分钟的秒数 */
    protected static final long SECONDS_PER_MINUTE = 60;
    /** 每小时的秒数 */
    protected static final long SECONDS_PER_HOUR = 3600;
    /** 每天的秒数 */
    protected static final long SECONDS_PER_DAY = 86400;

    /** 累计游戏秒数 */
    private long totalSeconds = 0;

    /**
     * 推进时钟。
     *
     * @param seconds 增加的游戏秒数（必须 ≥ 0）
     */
    public void advance(long seconds) {
        if (seconds > 0) {
            totalSeconds += seconds;
        }
    }

    // ── 时间查询 ──────────────────────────────────

    /** 获取累计总秒数 */
    public long getTotalSeconds() {
        return totalSeconds;
    }

    /** 获取从起始时间以来的总天数 */
    public long getTotalDays() {
        return totalSeconds / SECONDS_PER_DAY;
    }

    /** 当前小时（0-23） */
    public int getHour() {
        return (int) ((totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR);
    }

    /** 当前分钟（0-59） */
    public int getMinute() {
        return (int) ((totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
    }

    /** 当前秒（0-59） */
    public int getSecond() {
        return (int) (totalSeconds % SECONDS_PER_MINUTE);
    }

    // ── 格式化 ──────────────────────────────────

    /**
     * 格式化当前时间为 "HH:MM" 格式。
     */
    public String formatTime() {
        return String.format("%02d:%02d", getHour(), getMinute());
    }

    @Override
    public String toString() {
        return formatTime();
    }
}
