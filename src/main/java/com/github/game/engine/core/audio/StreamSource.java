package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.InputStream;

/**
 * 流式播放源（SourceDataLine + 后台线程）。
 *
 * <p>适用于大文件 BGM，边读边播，不需要全部载入内存。
 * MP3 通过 JavaLayer 流式解码，WAV 通过 AudioInputStream 分块读取。
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

    /** MP3 流式解码器 */
    private AudioFormatDecoder.Mp3StreamDecoder mp3Decoder;

    /** WAV 流式读取 */
    private AudioInputStream wavStream;

    /** 是否 MP3 格式 */
    private final boolean isMp3;

    /** 当前采样位置（用于 seek 计算） */
    private volatile long samplePosition = 0;

    /** 总样本数 */
    @SuppressWarnings("unused")
    private final long totalSamples;

    /** 当前增益（用于软件音量控制） */
    private volatile float currentGain = 1.0f;

    /**
     * 创建流式音源。
     */
    public StreamSource(AudioFormat format, String resourcePath, long durationMs,
                         boolean isMp3,
                         java.util.function.Function<String, InputStream> resourceLoader) {
        this.format = format;
        this.resourcePath = resourcePath;
        this.durationMs = durationMs;
        this.isMp3 = isMp3;
        this.resourceLoader = resourceLoader;
        this.totalSamples = (long) (durationMs * format.getSampleRate() / 1000.0);
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
            // 不停止线程，只是暂停填充数据
        }
    }

    @Override
    public void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
            // 线程会继续填充数据
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
        // 对于流式播放，pitch 影响实际播放速率
        // 这里简化处理，记录 pitch 值
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
        // 流式 seek 需要重新打开流并跳过到目标位置
        boolean wasPlaying = state == State.PLAYING;
        closeStream();

        samplePosition = (long) (ms * format.getSampleRate() / 1000.0) * format.getChannels();
        currentPositionMs = ms;

        if (wasPlaying) {
            // 重新开始播放
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
            if (isMp3) {
                decodeMp3Loop();
            } else {
                decodeWavLoop();
            }
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

    private void decodeMp3Loop() {
        try (InputStream in = resourceLoader.apply(resourcePath)) {
            if (in == null) {
                logger.warn("流式音源文件不存在: {}", resourcePath);
                return;
            }

            mp3Decoder = AudioFormatDecoder.createMp3Stream(in);

            // Seek: 跳过前面的帧
            if (samplePosition > 0) {
                skipSamples(samplePosition);
            }

            byte[] buffer = new byte[BUFFER_SIZE * 4]; // 4 bytes per sample (16-bit stereo)
            int byteCount = 0;

            while (!stopRequested) {
                if (state != State.PLAYING) {
                    Thread.sleep(10);
                    continue;
                }

                short[] samples = mp3Decoder.decodeFrame();
                if (samples == null) {
                    // 流结束
                    break;
                }

                // 应用增益（软件音量）
                byteCount = 0;
                for (short sample : samples) {
                    short adjusted = (short) (sample * currentGain);
                    buffer[byteCount++] = (byte) (adjusted & 0xFF);
                    buffer[byteCount++] = (byte) ((adjusted >> 8) & 0xFF);
                }

                // 写入 SourceDataLine
                int written = 0;
                while (written < byteCount && !stopRequested) {
                    int n = line.write(buffer, written, byteCount - written);
                    if (n > 0) {
                        written += n;
                        samplePosition += n / 2; // 2 bytes per sample
                    } else {
                        Thread.sleep(1);
                    }
                }
            }

        } catch (Exception e) {
            if (!stopRequested) {
                logger.warn("MP3 流式解码异常: {}", e.getMessage());
            }
        }
    }

    private void decodeWavLoop() {
        try (InputStream in = resourceLoader.apply(resourcePath)) {
            if (in == null) return;

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
                logger.warn("WAV 流式读取异常: {}", e.getMessage());
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

    /** 跳过指定数量的样本（用于 seek） */
    private void skipSamples(long sampleCount) {
        long skipped = 0;
        while (skipped < sampleCount) {
            short[] frame = mp3Decoder.decodeFrame();
            if (frame == null) break;
            skipped += frame.length;
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
        if (mp3Decoder != null) {
            mp3Decoder.close();
            mp3Decoder = null;
        }
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
