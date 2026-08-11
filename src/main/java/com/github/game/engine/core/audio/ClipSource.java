package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;

/**
 * 短音效播放源（Clip 驱动）。
 *
 * <p>适用于短音效（按钮声、爆炸声、拾取音效等），
 * 音频数据全部预载到内存，通过 javax.sound.sampled.Clip 播放。
 *
 * <p>特性：
 * <ul>
 *   <li>无缝循环（Clip.loop()）</li>
 *   <li>MASTER_GAIN 音量控制</li>
 *   <li>支持播放位置跳转</li>
 * </ul>
 *
 * <p>注意：Clip 不支持动态变调（pitch），
 * 需要变调功能时请使用 {@link StreamSource}。
 */
public class ClipSource extends AudioSource {

    private static final Logger logger = LoggerFactory.getLogger(ClipSource.class);

    /** 音频格式 */
    private final AudioFormat format;

    /** PCM 数据 */
    private final byte[] pcmData;

    /** Java Sound Clip 实例 */
    private Clip clip;

    /** 时长（毫秒） */
    private final long durationMs;

    /** 是否已初始化 Clip */
    private boolean initialized = false;

    /** 音量控制 */
    private FloatControl gainControl;

    /**
     * 创建 Clip 音源。
     *
     * @param format   音频格式
     * @param pcmData  PCM 数据
     * @param durationMs 时长（毫秒）
     */
    public ClipSource(AudioFormat format, byte[] pcmData, long durationMs) {
        this.format = format;
        this.pcmData = pcmData;
        this.durationMs = durationMs;
    }

    /**
     * 创建 Clip 音源（从缓存）。
     */
    public ClipSource(AudioCache.CachedAudio cached) {
        this(cached.getFormat(), cached.getPcmData(), cached.getDurationMs());
    }

    /** 懒初始化 Clip */
    private void ensureClip() {
        if (!initialized) {
            try {
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                clip = (Clip) AudioSystem.getLine(info);
                clip.open(format, pcmData, 0, pcmData.length);

                // 尝试获取音量控制
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                }

                // 监听循环完成事件
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        if (loop && clip.getFramePosition() >= clip.getFrameLength()) {
                            // Clip 在循环时不会触发 STOP，只在非循环时处理
                        } else if (!loop) {
                            onPlaybackComplete();
                        }
                    }
                });

                initialized = true;
                logger.debug("Clip 初始化: {} ({}ms)", clipId, durationMs);

            } catch (LineUnavailableException e) {
                logger.error("无法创建 Clip: {}", e.getMessage());
            }
        }
    }

    @Override
    public void play() {
        ensureClip();
        if (clip == null) return;

        // 如果已暂停，恢复
        if (state == State.PAUSED) {
            clip.start();
            state = State.PLAYING;
            return;
        }

        // 如果正在播放，从头重新开始
        if (state == State.PLAYING) {
            clip.setMicrosecondPosition(0);
        }

        // 设置循环
        if (loop) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else {
            clip.loop(0); // 播放一次
        }

        clip.start();
        state = State.PLAYING;
        loopCycleCount = 0;
        applyVolume();
        logger.trace("播放音效: {}", clipId);
    }

    @Override
    public void pause() {
        if (state == State.PLAYING && clip != null) {
            clip.stop();
            state = State.PAUSED;
        }
    }

    @Override
    public void resume() {
        if (state == State.PAUSED && clip != null) {
            clip.start();
            state = State.PLAYING;
        }
    }

    @Override
    public void stop() {
        if (clip != null) {
            clip.stop();
            clip.setMicrosecondPosition(0);
        }
        super.stop();
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        applyVolume();
    }

    @Override
    public void setPitch(float pitch) {
        // Clip 不支持动态变调，记录 pitch 值但不生效
        this.pitch = pitch;
        logger.debug("ClipSource 不支持 pitch 变化: {}", pitch);
    }

    @Override
    protected void applyVolume() {
        if (gainControl != null) {
            // 将线性音量 (0~1) 转换为 dB
            float dB;
            if (effectiveVolume <= 0.001f) {
                dB = gainControl.getMinimum();
            } else {
                dB = (float) (20.0 * Math.log10(effectiveVolume));
                dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            }
            gainControl.setValue(dB);
        }
    }

    @Override
    public long getPositionMs() {
        if (clip != null) {
            return clip.getMicrosecondPosition() / 1000;
        }
        return currentPositionMs;
    }

    @Override
    public void seekMs(long ms) {
        if (clip != null) {
            long microseconds = ms * 1000;
            long maxMicroseconds = clip.getMicrosecondLength();
            if (microseconds < 0) microseconds = 0;
            if (microseconds > maxMicroseconds) microseconds = maxMicroseconds;
            clip.setMicrosecondPosition(microseconds);
            currentPositionMs = ms;
        }
    }

    @Override
    protected void onPlaybackLoop() {
        // Clip 自动循环，不需要手动操作
    }

    /** 释放 Clip 资源 */
    public void dispose() {
        if (clip != null) {
            clip.stop();
            clip.flush();
            clip.close();
            clip = null;
            initialized = false;
        }
    }

    public long getDurationMs() { return durationMs; }
}
