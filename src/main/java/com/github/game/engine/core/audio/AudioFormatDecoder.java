package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * 音频格式检测与解码器。
 *
 * <p>支持格式：
 * <ul>
 *   <li><b>WAV</b> — javax.sound.sampled 原生</li>
 *   <li><b>MP3</b> — MP3SPI（通过 JavaSound SPI 解码）</li>
 * </ul>
 *
 * <p>解码输出统一为 PCM 格式（{@link AudioFormat} + {@code byte[]}），
 * 供 {@link ClipSource} 使用。
 */
public class AudioFormatDecoder {

    private static final Logger logger = LoggerFactory.getLogger(AudioFormatDecoder.class);

    /**
     * 解码音频文件为 PCM 数据。
     *
     * @param in   输入流
     * @param path 文件路径（用于格式检测）
     * @return 解码结果（AudioFormat + PCM 数据 + 时长）
     * @throws IOException 读取失败
     */
    public static DecodeResult decode(InputStream in, String path) throws IOException {
        String ext = getExtension(path).toLowerCase();
        switch (ext) {
            case "mp3":
                return decodeMp3(in);
            case "wav":
                return decodeWav(in);
            default:
                // 尝试 WAV（AudioSystem 自动检测格式）
                try {
                    return decodeWav(in);
                } catch (Exception e) {
                    // 回退到 MP3
                    return decodeMp3(in);
                }
        }
    }

    /** 计算 PCM 数据的时长（毫秒） */
    public static long computeDurationMs(byte[] pcmData, AudioFormat format) {
        long frameCount = pcmData.length / format.getFrameSize();
        return (long) (frameCount * 1000.0 / format.getSampleRate());
    }

    // ── WAV 解码 ──────────────────────────────────

    private static DecodeResult decodeWav(InputStream in) throws IOException {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(in)) {
            AudioFormat format = ais.getFormat();

            // 确保是 PCM 格式
            if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                    && format.getEncoding() != AudioFormat.Encoding.PCM_UNSIGNED) {
                AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        format.getSampleRate(),
                        16,
                        format.getChannels(),
                        format.getChannels() * 2,
                        format.getSampleRate(),
                        false);
                AudioInputStream converted = AudioSystem.getAudioInputStream(targetFormat, ais);
                return readAllPcm(converted, targetFormat);
            }

            return readAllPcm(ais, format);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("不支持的 WAV 格式: " + e.getMessage(), e);
        }
    }

    private static DecodeResult readAllPcm(AudioInputStream ais, AudioFormat format)
            throws IOException {
        int estimatedSize = (int) ais.getFrameLength() * format.getFrameSize();
        if (estimatedSize <= 0) {
            estimatedSize = 65536;
        }

        byte[] buffer = new byte[estimatedSize];
        int totalRead = 0;
        int n;
        while ((n = ais.read(buffer, totalRead, buffer.length - totalRead)) > 0) {
            totalRead += n;
            if (totalRead == buffer.length) {
                byte[] larger = new byte[buffer.length * 2];
                System.arraycopy(buffer, 0, larger, 0, buffer.length);
                buffer = larger;
            }
        }

        byte[] pcmData = new byte[totalRead];
        System.arraycopy(buffer, 0, pcmData, 0, totalRead);

        long durationMs = computeDurationMs(pcmData, format);
        logger.debug("WAV 解码完成: {} 字节, {}ms, {}Hz, {}ch",
                totalRead, durationMs, (int) format.getSampleRate(), format.getChannels());

        return new DecodeResult(format, pcmData, durationMs);
    }

    // ── MP3 解码 ──────────────────────────────────

    /**
     * MP3 解码（MP3SPI）。
     * MP3SPI 注册为 AudioSystem 的 SPI，可直接用 AudioSystem.getAudioInputStream 解码 MP3。
     */
    private static DecodeResult decodeMp3(InputStream in) throws IOException {
        AudioInputStream rawAis;
        try {
            rawAis = AudioSystem.getAudioInputStream(in);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("不支持的 MP3 格式: " + e.getMessage(), e);
        }
        try {
            AudioFormat srcFormat = rawAis.getFormat();

            // 确保输出是 PCM_SIGNED
            AudioFormat targetFormat;
            AudioInputStream ais;
            if (srcFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        srcFormat.getSampleRate(),
                        16,
                        srcFormat.getChannels(),
                        srcFormat.getChannels() * 2,
                        srcFormat.getSampleRate(),
                        false);
                ais = AudioSystem.getAudioInputStream(targetFormat, rawAis);
            } else {
                targetFormat = srcFormat;
                ais = rawAis;
            }

            try {
                DecodeResult result = readAllPcm(ais, targetFormat);
                logger.debug("MP3 解码完成: {}Hz, {}ch, {}ms",
                        (int) targetFormat.getSampleRate(), targetFormat.getChannels(),
                        result.getDurationMs());
                return result;
            } finally {
                ais.close();
            }
        } finally {
            rawAis.close();
        }
    }

    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : "";
    }

    // ── 结果类 ──────────────────────────────────

    /** 解码结果。 */
    public static class DecodeResult {
        private final AudioFormat format;
        private final byte[] pcmData;
        private final long durationMs;

        DecodeResult(AudioFormat format, byte[] pcmData, long durationMs) {
            this.format = format;
            this.pcmData = pcmData;
            this.durationMs = durationMs;
        }

        public AudioFormat getFormat() { return format; }
        public byte[] getPcmData() { return pcmData; }
        public long getDurationMs() { return durationMs; }
    }
}
