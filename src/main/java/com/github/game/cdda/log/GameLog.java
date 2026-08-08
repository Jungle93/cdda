package com.github.game.cdda.log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 游戏日志管理器（单例）。
 * 记录游戏内交互信息：检查结果、拾取/丢弃物品、事件通知等。
 * <p>
 * 固定容量缓冲（{@link #MAX_ENTRIES}），超出时自动移除最旧条目。
 * 通过 {@link #getInstance()} 获取全局唯一实例。
 */
public class GameLog {

    /** 最大日志条目数 */
    private static final int MAX_ENTRIES = 100;

    /** 单例实例 */
    private static final GameLog INSTANCE = new GameLog();

    /** 日志条目列表（按时间顺序，最新在末尾） */
    private final LinkedList<String> entries = new LinkedList<>();

    private GameLog() {}

    /**
     * 获取全局唯一实例。
     */
    public static GameLog getInstance() {
        return INSTANCE;
    }

    /**
     * 添加一条日志。
     * 超过 {@link #MAX_ENTRIES} 条时自动移除最旧的条目。
     *
     * @param message 日志消息
     */
    public void log(String message) {
        entries.addLast(message);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    /**
     * 获取最近 N 条日志条目（按时间顺序，最旧在前）。
     * 如果不足 N 条，返回全部。
     *
     * @param count 需要的条目数
     * @return 日志条目列表（只读副本）
     */
    public List<String> getRecentEntries(int count) {
        int size = entries.size();
        int start = Math.max(0, size - count);
        return new ArrayList<>(entries.subList(start, size));
    }

    /**
     * 获取全部日志条目。
     *
     * @return 日志条目列表（只读副本）
     */
    public List<String> getAllEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * 获取当前日志总数。
     */
    public int size() {
        return entries.size();
    }
}
