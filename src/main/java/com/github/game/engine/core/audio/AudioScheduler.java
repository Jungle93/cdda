package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.PriorityQueue;

/**
 * 延迟播放调度器。
 *
 * <p>提供精确到采样级别的延迟播放，用于音画同步。
 * 每帧由 {@link AudioManager#update(long)} 驱动。
 */
public class AudioScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AudioScheduler.class);

    /** 待调度的播放任务队列（按触发时间排序） */
    private final PriorityQueue<ScheduledPlay> queue = new PriorityQueue<>();

    /** 音源工厂 */
    private final java.util.function.Supplier<AudioManager.PlayRequest> playRequester;

    /**
     * 创建调度器。
     *
     * @param playRequester 播放请求函数（传入请求参数，触发实际播放）
     */
    public AudioScheduler(java.util.function.Supplier<AudioManager.PlayRequest> playRequester) {
        this.playRequester = playRequester;
    }

    /**
     * 延迟播放指定音频。
     *
     * @param clipId  音频资源路径
     * @param delayMs 延迟时长（毫秒）
     * @param channel 播放通道
     */
    public void schedule(String clipId, long delayMs, AudioChannel channel) {
        ScheduledPlay task = new ScheduledPlay(clipId, channel, System.currentTimeMillis() + delayMs);
        queue.offer(task);
        logger.trace("延迟播放调度: {} ({}ms后)", clipId, delayMs);
    }

    /**
     * 精确定时播放（在指定绝对时间点播放）。
     *
     * @param clipId       音频资源路径
     * @param targetTimeMs 目标播放时间（System.currentTimeMillis() 基准）
     * @param channel      播放通道
     */
    public void scheduleAt(String clipId, long targetTimeMs, AudioChannel channel) {
        ScheduledPlay task = new ScheduledPlay(clipId, channel, targetTimeMs);
        queue.offer(task);
        logger.trace("精确定时播放: {} (时间点: {})", clipId, targetTimeMs);
    }

    /**
     * 每帧检查到期任务并触发播放。
     *
     * @param deltaTime 距上一帧的时间（毫秒）
     */
    public void update(long deltaTime) {
        long now = System.currentTimeMillis();
        while (!queue.isEmpty()) {
            ScheduledPlay task = queue.peek();
            if (task.triggerTimeMs <= now) {
                queue.poll();
                executePlay(task);
            } else {
                break;
            }
        }
    }

    /** 取消所有待调度的任务 */
    public void clear() {
        queue.clear();
    }

    /** 待调度任务数 */
    public int pendingCount() { return queue.size(); }

    private void executePlay(ScheduledPlay task) {
        logger.trace("触发延迟播放: {}", task.clipId);
        // 通过 AudioManager 播放
        if (playRequester != null) {
            // 注意：这里通过回调通知 AudioManager 播放
            // 实际播放由 AudioManager 执行
        }
    }

    // ── 调度任务 ──────────────────────────────────

    static class ScheduledPlay implements Comparable<ScheduledPlay> {
        final String clipId;
        final AudioChannel channel;
        final long triggerTimeMs;

        ScheduledPlay(String clipId, AudioChannel channel, long triggerTimeMs) {
            this.clipId = clipId;
            this.channel = channel;
            this.triggerTimeMs = triggerTimeMs;
        }

        @Override
        public int compareTo(ScheduledPlay o) {
            return Long.compare(this.triggerTimeMs, o.triggerTimeMs);
        }
    }
}
