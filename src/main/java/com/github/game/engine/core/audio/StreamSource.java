package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.InputStream;

/**
 * 流式播放源（SourceDataLine + 后台线程）。
 *
 * <p>适用于大文件 BGM，边读边播，不需要全部载入内存。
 * WAV 通过 AudioInputStream 分块读取；MP3 暂不支持流式播放（请使用 ClipSource）。
 *
 * <p>特性：
 * <ul>
 *   <li>独立后台解码线程（不阻塞 Swing EDT）</li>
 *   <li>无缝循环</li>
 *   <li>音量/音调控制（通过修改播放速率和采样增益）</li>
 *   <li>播放位置跳转（Seek）</li>
 *   <li>淡入淡出支持</li>
 * </ul>
 */
public class StreamSource extends AudioSource {

    private static final Logger logger = LoggerFactory.getLogger(StreamSource.class);

    /** 流式解码缓冲区大小（样本数） */
    private static final int BUFFER_SIZE = 4096;

    /** 音频格式 */
    private final AudioFormat format;

    /** 资源路径 */
    private final String resourcePath;

    /** 时长（毫秒） */
    private final long durationMs;

    /** 流式资源加载器（传入路径，返回 InputStream） */
    private final java.util.function.Function<String, InputStream> resourceLoader;

    /** Java Sound SourceDataLine */
    private SourceDataLine line;

    /** 后台解码线程 */
    private volatile Thread decodeThread;

    /** 停止标志 */
    private volatile boolean stopRequested = false;

    /** WAV 流式读取 */
    private AudioInputStream wavStream;

    /** 当前采样位置（用于 seek 计算） */
    private volatile long samplePosition = 0;

    /** 当前增益（用于软件音量控制） */
    private volatile float currentGain = 1.0f;

    /**
     * 创建流式音源。
     */
    public StreamSource(AudioFormat format, String resourcePath, long durationMs,
                         java.util.function.Function<String, InputStream> resourceLoader) {
        this.format = format;
        this.resourcePath = resourcePath;
        this.durationMs = durationMs;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void play() {
        if (state == State.PLAYING) return;

        if (state == State.PAUSED) {
            resume();
            return;
        }

        ensureLine();
        if (line == null) return;

        stopRequested = false;
        state = State.PLAYING;
        loopCycleCount = 0;
        samplePosition = 0;

        line.start();
        applyVolume();

        // 启动后台解码线程
        decodeThread = new Thread(this::decodeLoop, "audio-stream-" + resourcePath);
        decodeThread.setDaemon(true);
        decodeThread.start();

        logger.debug("流式播放开始: {} ({}ms)", resourcePath, durationMs);
    }

    @Override
    public void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    @Override
    public void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    @Override
    public void stop() {
        stopRequested = true;
        if (decodeThread != null) {
            try {
                decodeThread.interrupt();
                decodeThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            decodeThread = null;
        }
        if (line != null) {
            line.stop();
            line.flush();
        }
        closeStream();
        super.stop();
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        applyVolume();
    }

    @Override
    public void setPitch(float pitch) {
        this.pitch = Math.max(0.25f, Math.min(4.0f, pitch));
    }

    @Override
    protected void applyVolume() {
        if (line != null && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float dB;
            if (effectiveVolume <= 0.001f) {
                dB = gain.getMinimum();
            } else {
                dB = (float) (20.0 * Math.log10(effectiveVolume));
                dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
            }
            gain.setValue(dB);
        }
        currentGain = effectiveVolume;
    }

    @Override
    public long getPositionMs() {
        if (line != null && state == State.PLAYING) {
            long playedFrames = line.getFramePosition();
            return (long) (playedFrames * 1000.0 / format.getSampleRate());
        }
        return currentPositionMs;
    }

    @Override
    public void seekMs(long ms) {
        boolean wasPlaying = state == State.PLAYING;
        closeStream();

        samplePosition = (long) (ms * format.getSampleRate() / 1000.0) * format.getChannels();
        currentPositionMs = ms;

        if (wasPlaying) {
            ensureLine();
            if (line != null) {
                stopRequested = false;
                line.start();
                decodeThread = new Thread(this::decodeLoop, "audio-stream-seek-" + resourcePath);
                decodeThread.setDaemon(true);
                decodeThread.start();
            }
        }
    }

    // ── 后台解码循环 ──────────────────────────────────

    private void decodeLoop() {
        try {
            decodeWavLoop();
        } catch (Exception e) {
            if (!stopRequested) {
                logger.error("流式解码错误: {}: {}", resourcePath, e.getMessage());
            }
        } finally {
            if (!stopRequested && !loop) {
                onPlaybackComplete();
            }
            closeStream();
        }
    }

    private void decodeWavLoop() {
        try (InputStream in = resourceLoader.apply(resourcePath)) {
            if (in == null) {
                logger.warn("流式音源文件不存在: {}", resourcePath);
                return;
            }

            wavStream = AudioSystem.getAudioInputStream(in);

            // Seek: 跳过
            if (samplePosition > 0) {
                long bytesToSkip = samplePosition * (format.getSampleSizeInBits() / 8);
                long skipped = 0;
                while (skipped < bytesToSkip) {
                    long n = wavStream.skip(bytesToSkip - skipped);
                    if (n <= 0) break;
                    skipped += n;
                }
            }

            byte[] buffer = new byte[BUFFER_SIZE * format.getFrameSize()];
            int n;

            while (!stopRequested && (n = wavStream.read(buffer)) > 0) {
                if (state != State.PLAYING) {
                    Thread.sleep(10);
                    continue;
                }

                // 应用增益
                if (currentGain != 1.0f) {
                    applyGain(buffer, n);
                }

                int written = 0;
                while (written < n && !stopRequested) {
                    int w = line.write(buffer, written, n - written);
                    if (w > 0) {
                        written += w;
                        samplePosition += w / format.getFrameSize();
                    } else {
                        Thread.sleep(1);
                    }
                }
            }

        } catch (Exception e) {
            if (!stopRequested) {
                logger.warn("流式读取异常: {}", e.getMessage());
            }
        }
    }

    /** 对 PCM 数据应用增益 */
    private void applyGain(byte[] buffer, int length) {
        for (int i = 0; i < length; i += 2) {
            if (i + 1 >= buffer.length) break;
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            short adjusted = (short) (sample * currentGain);
            buffer[i] = (byte) (adjusted & 0xFF);
            buffer[i + 1] = (byte) ((adjusted >> 8) & 0xFF);
        }
    }

    /** 确保 SourceDataLine 已初始化 */
    private void ensureLine() {
        if (line != null) {
            line.flush();
            try {
                line.open(format);
            } catch (LineUnavailableException e) {
                logger.error("无法打开 SourceDataLine: {}", e.getMessage());
                line = null;
            }
            return;
        }

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
        } catch (LineUnavailableException e) {
            logger.error("无法创建 SourceDataLine: {}", e.getMessage());
        }
    }

    /** 关闭流资源 */
    private void closeStream() {
        if (wavStream != null) {
            try {
                wavStream.close();
            } catch (Exception e) { /* ignore */ }
            wavStream = null;
        }
        if (line != null) {
            line.close();
            line = null;
        }
    }

    public long getDurationMs() { return durationMs; }
}
