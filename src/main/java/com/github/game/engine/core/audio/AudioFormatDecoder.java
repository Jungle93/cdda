package com.github.game.engine.core.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
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
 *   <li><b>MP3</b> — JavaLayer 纯 Java 解码</li>
 * </ul>
 *
 * <p>解码输出统一为 PCM 格式（{@link AudioFormat} + {@code byte[]}），
 * 供 {@link ClipSource} 和 {@link StreamSource} 使用。
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

    /**
     * 创建 MP3 流式解码器（用于 {@link StreamSource}）。
     */
    public static Mp3StreamDecoder createMp3Stream(InputStream in) throws IOException {
        return new Mp3StreamDecoder(in);
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

    private static DecodeResult decodeMp3(InputStream in) throws IOException {
        try {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();

            // 读取第一帧获取格式信息
            Header firstHeader = bitstream.readFrame();
            if (firstHeader == null) {
                throw new IOException("无效的 MP3 文件");
            }

            int sampleRate = firstHeader.sample_frequency();
            int channels = firstHeader.mode() == Header.SINGLE_CHANNEL ? 1 : 2;

            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, channels, channels * 2, sampleRate, false);

            // 解码所有帧
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int frameCount = 0;

            // 处理第一帧
            SampleBuffer sampleBuf = (SampleBuffer) decoder.decodeFrame(firstHeader, null);
            writeSampleBuffer(sampleBuf, out);
            bitstream.closeFrame();
            frameCount++;

            // 处理剩余帧
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(header, null);
                writeSampleBuffer(sb, out);
                bitstream.closeFrame();
                frameCount++;
            }

            byte[] pcmData = out.toByteArray();
            long durationMs = computeDurationMs(pcmData, format);

            logger.debug("MP3 解码完成: {} 帧, {} 字节, {}ms, {}Hz, {}ch",
                    frameCount, pcmData.length, durationMs, sampleRate, channels);

            return new DecodeResult(format, pcmData, durationMs);

        } catch (Exception e) {
            throw new IOException("MP3 解码失败: " + e.getMessage(), e);
        }
    }

    private static void writeSampleBuffer(SampleBuffer sb, java.io.ByteArrayOutputStream out) {
        short[] samples = sb.getBuffer();
        int length = sb.getBufferLength();
        for (int i = 0; i < length; i++) {
            short sample = samples[i];
            out.write(sample & 0xFF);
            out.write((sample >> 8) & 0xFF);
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
        private final Mp3StreamDecoder mp3Stream;

        DecodeResult(AudioFormat format, byte[] pcmData, long durationMs) {
            this(format, pcmData, durationMs, null);
        }

        DecodeResult(AudioFormat format, byte[] pcmData, long durationMs, Mp3StreamDecoder mp3Stream) {
            this.format = format;
            this.pcmData = pcmData;
            this.durationMs = durationMs;
            this.mp3Stream = mp3Stream;
        }

        public AudioFormat getFormat() { return format; }
        public byte[] getPcmData() { return pcmData; }
        public long getDurationMs() { return durationMs; }
        public Mp3StreamDecoder getMp3Stream() { return mp3Stream; }
    }

    /**
     * MP3 流式解码器（供 StreamSource 在后台线程中使用）。
     */
    public static class Mp3StreamDecoder {
        private final Bitstream bitstream;
        private final Decoder decoder;
        private final int sampleRate;
        private final int channels;

        public Mp3StreamDecoder(InputStream in) throws IOException {
            this.bitstream = new Bitstream(in);
            this.decoder = new Decoder();

            Header header;
            try {
                header = bitstream.readFrame();
            } catch (BitstreamException e) {
                throw new IOException("读取 MP3 帧失败", e);
            }
            if (header == null) {
                throw new IOException("无效的 MP3 文件");
            }
            this.sampleRate = header.sample_frequency();
            this.channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
            bitstream.closeFrame();
        }

        /** 获取输出音频格式 */
        public AudioFormat getFormat() {
            return new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, channels, channels * 2, sampleRate, false);
        }

        /**
         * 解码一帧 MP3 数据，返回 PCM 样本。
         *
         * @return PCM 样本数组（小端序 16-bit），无更多帧时返回 null
         */
        public short[] decodeFrame() {
            try {
                Header header = bitstream.readFrame();
                if (header == null) return null;

                SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(header, null);
                bitstream.closeFrame();

                if (sb == null) return null;

                short[] samples = sb.getBuffer();
                int length = sb.getBufferLength();

                short[] result = new short[length];
                System.arraycopy(samples, 0, result, 0, length);
                return result;
            } catch (Exception e) {
                logger.warn("MP3 解码帧错误: {}", e.getMessage());
                return null;
            }
        }

        /** 关闭流 */
        public void close() {
            try {
                bitstream.close();
            } catch (javazoom.jl.decoder.BitstreamException e) {
                // ignore
            }
        }
    }
}
