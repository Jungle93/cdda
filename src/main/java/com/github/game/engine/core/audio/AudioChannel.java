package com.github.game.engine.core.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 混音通道。
 *
 * <p>每个通道管理一组 AudioSource，拥有独立的音量和静音控制。
 * 通道音量与全局主音量相乘得到有效音量。
 *
 * <p>预设通道：
 * <ul>
 *   <li><b>SFX</b> — 音效（爆炸、按钮、拾取），支持并发多实例</li>
 *   <li><b>BGM</b> — 背景音乐，单曲播放 + 无缝循环</li>
 *   <li><b>AMBIENT</b> — 环境音（风声、雨声），长循环</li>
 * </ul>
 */
public class AudioChannel {

    /** 通道名称 */
    private final String name;

    /** 通道音量 (0~1) */
    private float volume = 1.0f;

    /** 是否静音 */
    private boolean muted = false;

    /** 全局主音量（由 AudioManager 设置） */
    private volatile float masterVolume = 1.0f;

    /** 本通道的 AudioSource 列表 */
    private final List<AudioSource> sources = Collections.synchronizedList(new ArrayList<>());

    public AudioChannel(String name) {
        this.name = name;
    }

    /** 添加音源到本通道 */
    void addSource(AudioSource source) {
        if (source != null && !sources.contains(source)) {
            sources.add(source);
        }
    }

    /** 从本通道移除音源 */
    void removeSource(AudioSource source) {
        sources.remove(source);
    }

    /** 设置通道音量 (0~1) */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        // 通知所有音源更新有效音量
        for (AudioSource src : sources) {
            src.updateEffectiveVolume();
        }
    }

    /** 设置静音 */
    public void setMuted(boolean muted) {
        this.muted = muted;
        for (AudioSource src : sources) {
            src.updateEffectiveVolume();
        }
    }

    /** 获取有效音量（通道 × 主音量 × 静音） */
    float getEffectiveVolume() {
        if (muted) return 0f;
        return volume * masterVolume;
    }

    void setMasterVolume(float masterVolume) {
        this.masterVolume = masterVolume;
        for (AudioSource src : sources) {
            src.updateEffectiveVolume();
        }
    }

    public String getName() { return name; }
    public float getVolume() { return volume; }
    public boolean isMuted() { return muted; }

    /** 获取通道中所有音源（只读视图） */
    List<AudioSource> getSources() { return sources; }

    /** 停止通道中所有音源 */
    public void stopAll() {
        for (AudioSource src : new ArrayList<>(sources)) {
            src.stop();
        }
    }

    /** 暂停通道中所有音源 */
    public void pauseAll() {
        for (AudioSource src : new ArrayList<>(sources)) {
            src.pause();
        }
    }

    /** 恢复通道中所有音源 */
    public void resumeAll() {
        for (AudioSource src : new ArrayList<>(sources)) {
            if (src.getState() == AudioSource.State.PAUSED) {
                src.resume();
            }
        }
    }

    /** 活跃播放的音源数 */
    public int getActiveCount() {
        int count = 0;
        for (AudioSource src : sources) {
            if (src.getState() == AudioSource.State.PLAYING) count++;
        }
        return count;
    }

    @Override
    public String toString() {
        return "AudioChannel[" + name + " vol=" + String.format("%.2f", volume)
                + " muted=" + muted + " active=" + getActiveCount() + "]";
    }
}
