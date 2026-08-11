package com.github.game.engine.core.audio;

/**
 * 播放源抽象基类。
 *
 * <p>每个 AudioSource 代表一个独立的音频播放实例，
 * 支持播放/暂停/停止/恢复、循环、音量、音调、延迟、跳转等控制。
 *
 * <p>子类：
 * <ul>
 *   <li>{@link ClipSource} — 短音效（Clip 驱动，全部预载到内存）</li>
 *   <li>{@link StreamSource} — 流式播放（SourceDataLine 驱动，边读边播）</li>
 * </ul>
 */
public abstract class AudioSource {

    /** 播放状态 */
    public enum State {
        /** 未播放 */
        STOPPED,
        /** 播放中 */
        PLAYING,
        /** 已暂停 */
        PAUSED
    }

    /** 所属通道 */
    private AudioChannel channel;

    /** 音频资源路径 */
    protected String clipId;

    /** 播放状态 */
    protected volatile State state = State.STOPPED;

    /** 是否循环（0=无限循环） */
    protected boolean loop = false;

    /** 循环次数计数 */
    protected int loopCycleCount = 0;

    /** 本音源自身体音量 (0~1) */
    protected float volume = 1.0f;

    /** 有效音量（源音量 × 通道有效音量） */
    protected volatile float effectiveVolume = 1.0f;

    /** 音调/播放速率 (1.0=正常, 0.5=半速, 2.0=双倍速) */
    protected float pitch = 1.0f;

    /** 延迟播放（毫秒），0=立即播放 */
    protected long delayMs = 0;

    /** 延迟倒计时剩余（毫秒） */
    protected long remainingDelayMs = 0;

    /** 当前播放位置（毫秒） */
    protected volatile long currentPositionMs = 0;

    // ── 抽象方法 ──────────────────────────────────

    /** 开始播放 */
    public abstract void play();

    /** 设置音量 */
    public abstract void setVolume(float volume);

    /** 设置音调 */
    public abstract void setPitch(float pitch);

    /** 获取当前播放位置（毫秒） */
    public abstract long getPositionMs();

    /** 跳转到指定位置（毫秒） */
    public abstract void seekMs(long ms);

    // ── 基础实现 ──────────────────────────────────

    /** 暂停 */
    public void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    /** 恢复 */
    public void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    /** 停止 */
    public void stop() {
        state = State.STOPPED;
        currentPositionMs = 0;
        loopCycleCount = 0;
        remainingDelayMs = 0;
    }

    /**
     * 每帧更新（由 AudioManager.update 调用）。
     * 处理延迟倒计时、状态检查等。
     */
    public void update(long deltaTime) {
        if (state == State.PLAYING && delayMs > 0 && remainingDelayMs > 0) {
            remainingDelayMs -= deltaTime;
            if (remainingDelayMs <= 0) {
                remainingDelayMs = 0;
                onDelayComplete();
            }
        }
    }

    /** 延迟完成回调（子类可覆盖） */
    protected void onDelayComplete() {
        // 子类可在此触发实际播放
    }

    /** 更新有效音量（通道音量变化时调用） */
    void updateEffectiveVolume() {
        if (channel != null) {
            this.effectiveVolume = this.volume * channel.getEffectiveVolume();
            applyVolume();
        }
    }

    /** 应用音量到实际音频设备（子类实现） */
    protected abstract void applyVolume();

    /** 播放完成回调 */
    protected void onPlaybackComplete() {
        if (loop) {
            loopCycleCount++;
            seekMs(0);
            onPlaybackLoop();
        } else {
            state = State.STOPPED;
        }
    }

    /** 循环开始回调 */
    protected void onPlaybackLoop() {
        // 子类可覆盖
    }

    // ── 访问器 ──────────────────────────────────

    void setChannel(AudioChannel channel) { this.channel = channel; }
    void setClipId(String clipId) { this.clipId = clipId; }

    public AudioChannel getChannel() { return channel; }
    public String getClipId() { return clipId; }
    public State getState() { return state; }
    public boolean isLoop() { return loop; }
    public int getLoopCycleCount() { return loopCycleCount; }
    public float getVolume() { return volume; }
    public float getEffectiveVolume() { return effectiveVolume; }
    public float getPitch() { return pitch; }
    public long getDelayMs() { return delayMs; }

    /** 设置是否循环 */
    public void setLoop(boolean loop) {
        this.loop = loop;
        this.loopCycleCount = 0;
    }

    /** 设置循环次数（0=无限循环） */
    public void setLoopCount(int count) {
        this.loop = count != 0;
        this.loopCycleCount = 0;
    }

    /** 设置延迟播放 */
    public void setDelay(long delayMs) {
        this.delayMs = delayMs;
        this.remainingDelayMs = delayMs;
    }

    @Override
    public String toString() {
        return "AudioSource[" + clipId + " " + state + " vol="
                + String.format("%.2f", volume) + " loop=" + loop + "]";
    }
}
