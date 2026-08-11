package com.github.game.engine.core.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * 淡入淡出调度器。
 *
 * <p>提供音量渐变过渡，用于场景切换、音乐交叉淡入淡出等。
 * 每帧由 {@link AudioManager#update(long)} 驱动。
 */
public class FadeManager {

    /** 活跃的淡入淡出操作 */
    private final List<FadeOperation> activeFades = new ArrayList<>();

    /**
     * 对指定音源执行淡入（从 0 到目标音量）。
     * 音源会立即开始播放，音量在 durationMs 内从 0 渐增到 targetVolume。
     *
     * @param source        音源
     * @param targetVolume  目标音量 (0~1)
     * @param durationMs    淡入时长（毫秒）
     */
    public void fadeIn(AudioSource source, float targetVolume, long durationMs) {
        source.setVolume(0f);
        source.play();  // 开始播放（音量 0 启动，由渐变控制音量增长）
        addFade(source, 0f, targetVolume, durationMs, null);
    }

    /**
     * 对指定音源执行淡出（从当前到 0）。
     *
     * @param source     音源
     * @param durationMs 淡出时长（毫秒）
     * @param onComplete 完成后回调
     */
    public void fadeOut(AudioSource source, long durationMs, Runnable onComplete) {
        addFade(source, source.getVolume(), 0f, durationMs, () -> {
            source.stop();
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * BGM 交叉淡入淡出（旧音乐淡出 + 新音乐淡入同时）。
     *
     * @param oldSource    旧音源（淡出并停止）
     * @param newSource    新音源（淡入）
     * @param newVolume    新音源目标音量
     * @param durationMs   过渡时长
     */
    public void crossFade(AudioSource oldSource, AudioSource newSource,
                           float newVolume, long durationMs) {
        fadeOut(oldSource, durationMs, null);
        newSource.setVolume(0f);
        newSource.play();
        addFade(newSource, 0f, newVolume, durationMs, null);
    }

    /**
     * 每帧更新所有活跃的淡入淡出。
     *
     * @param deltaTime 距上一帧的时间（毫秒）
     */
    public void update(long deltaTime) {
        for (int i = activeFades.size() - 1; i >= 0; i--) {
            FadeOperation op = activeFades.get(i);
            if (op.update(deltaTime)) {
                activeFades.remove(i);
            }
        }
    }

    /** 取消指定音源的所有淡入淡出 */
    public void cancel(AudioSource source) {
        activeFades.removeIf(op -> op.source == source);
    }

    /** 活跃淡入淡出数 */
    public int getActiveCount() { return activeFades.size(); }

    private void addFade(AudioSource source, float from, float to,
                          long durationMs, Runnable onComplete) {
        // 取消该音源之前的淡入淡出
        activeFades.removeIf(op -> op.source == source);
        activeFades.add(new FadeOperation(source, from, to, durationMs, onComplete));
    }

    // ── 淡入淡出操作 ──────────────────────────────────

    private static class FadeOperation {
        final AudioSource source;
        final float fromVolume;
        final float toVolume;
        final long durationMs;
        final Runnable onComplete;
        long elapsedMs = 0;

        FadeOperation(AudioSource source, float from, float to,
                       long durationMs, Runnable onComplete) {
            this.source = source;
            this.fromVolume = from;
            this.toVolume = to;
            this.durationMs = durationMs;
            this.onComplete = onComplete;
        }

        /** 更新并返回是否完成 */
        boolean update(long deltaTime) {
            elapsedMs += deltaTime;
            float t = Math.min(1f, (float) elapsedMs / durationMs);

            // 平滑曲线（ease-in-out）
            t = t * t * (3 - 2 * t);

            float currentVolume = fromVolume + (toVolume - fromVolume) * t;
            source.setVolume(currentVolume);

            if (elapsedMs >= durationMs) {
                if (onComplete != null) onComplete.run();
                return true;
            }
            return false;
        }
    }
}
