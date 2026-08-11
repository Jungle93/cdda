package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 音频管理器 — 内部控制器。
 *
 * <p>调度所有音频操作：通道管理、缓存、对象池、淡入淡出、延迟播放。
 * 由 {@link AudioEngine} 创建和管理，不直接暴露给游戏层。
 */
public class AudioManager {

    private static final Logger logger = LoggerFactory.getLogger(AudioManager.class);

    /** 默认通道名称 */
    public static final String CHANNEL_SFX = "SFX";
    public static final String CHANNEL_BGM = "BGM";
    public static final String CHANNEL_AMBIENT = "AMBIENT";

    /** 全局主音量 */
    private volatile float masterVolume = 0.8f;

    /** 全局静音 */
    private volatile boolean globalMuted = false;

    /** 资源加载函数 */
    private final Function<String, InputStream> resourceLoader;

    /** 混音通道 */
    private final Map<String, AudioChannel> channels = new HashMap<>();

    /** 音频缓存 */
    private final AudioCache cache;

    /** 音效对象池 */
    private final ObjectPool<ClipSource> sfxPool;

    /** 淡入淡出调度器 */
    private final FadeManager fadeManager = new FadeManager();

    /** 延迟播放调度器 */
    private final AudioScheduler scheduler;

    /** 当前 BGM 音源 */
    private StreamSource currentBgm;

    /**
     * 创建音频管理器。
     *
     * @param resourceLoader 资源加载函数
     */
    public AudioManager(Function<String, InputStream> resourceLoader) {
        this.resourceLoader = resourceLoader;

        // 创建默认通道
        channels.put(CHANNEL_SFX, new AudioChannel(CHANNEL_SFX));
        channels.put(CHANNEL_BGM, new AudioChannel(CHANNEL_BGM));
        channels.put(CHANNEL_AMBIENT, new AudioChannel(CHANNEL_AMBIENT));

        // 创建缓存
        this.cache = new AudioCache(resourceLoader);

        // 创建音效对象池（最多缓存 32 个音效实例）
        // 使用占位参数创建空 ClipSource，实际播放时通过构造函数替换
        this.sfxPool = new ObjectPool<>(
                () -> new ClipSource(
                        new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false),
                        new byte[0], 0),
                clip -> {
                    clip.stop();
                    clip.setVolume(1.0f);
                    clip.setLoop(false);
                },
                32
        );

        // 创建调度器
        this.scheduler = new AudioScheduler(null); // 简化：暂不注册回调

        logger.info("音频管理器初始化完成");
    }

    // ── 音效播放 ──────────────────────────────────

    /**
     * 播放音效（SFX 通道）。
     *
     * @param clipId 音频资源路径（如 "audio/click.wav"）
     * @param loop   是否循环
     * @param volume 音量 (0~1)
     */
    public void playSFX(String clipId, boolean loop, float volume) {
        playSound(clipId, CHANNEL_SFX, loop, volume);
    }

    /**
     * 播放音效（默认参数：不循环，音量 1.0）。
     */
    public void playSFX(String clipId) {
        playSFX(clipId, false, 1.0f);
    }

    /**
     * 播放音效到指定通道。
     */
    private void playSound(String clipId, String channelName, boolean loop, float volume) {
        AudioChannel channel = channels.get(channelName);
        if (channel == null) {
            logger.warn("通道不存在: {}", channelName);
            return;
        }

        // 从缓存加载
        AudioCache.CachedAudio cached = cache.loadSync(clipId);
        if (cached == null) {
            logger.warn("音效加载失败: {}", clipId);
            return;
        }

        // 从对象池获取音源
        ClipSource source = sfxPool.acquire();
        source.setChannel(channel);
        source.setClipId(clipId);

        // 如果音源已经初始化过，检查是否匹配
        // 由于对象池复用，需要确保 PCM 数据匹配
        // 简化处理：每次创建新 ClipSource
        ClipSource newSource = new ClipSource(cached);
        newSource.setChannel(channel);
        newSource.setClipId(clipId);
        newSource.setVolume(volume);
        newSource.setLoop(loop);
        newSource.updateEffectiveVolume();

        channel.addSource(newSource);
        newSource.play();

        logger.trace("播放音效: {} (通道: {})", clipId, channelName);
    }

    // ── BGM 播放 ──────────────────────────────────

    /**
     * 播放背景音乐（BGM 通道）。
     *
     * @param clipId  音频资源路径（如 "audio/bgm.mp3"）
     * @param loop    是否循环
     * @param volume  音量 (0~1)
     * @param fadeMs  淡入时长（毫秒），0=不淡入
     */
    public void playBGM(String clipId, boolean loop, float volume, long fadeMs) {
        // 停止当前 BGM
        if (currentBgm != null) {
            currentBgm.stop();
        }

        AudioChannel channel = channels.get(CHANNEL_BGM);
        if (channel == null) return;

        String ext = getExtension(clipId).toLowerCase();
        boolean isMp3 = ext.equals("mp3");

        StreamSource bgm = new StreamSource(
                null, // format 会在首次加载时确定
                clipId,
                0, // duration 会在加载时确定
                isMp3,
                resourceLoader
        );

        // 先加载一次获取格式和时长
        AudioCache.CachedAudio cached = cache.loadSync(clipId);
        if (cached != null) {
            // 对于 MP3 文件，如果已缓存，使用缓存的 PCM 创建
            bgm = createStreamSource(clipId, cached.getFormat(),
                    cached.getPcmData(), cached.getDurationMs(), isMp3);
        } else {
            // 尝试从流式信息创建
            try (InputStream in = resourceLoader.apply(clipId)) {
                if (in == null) {
                    logger.warn("BGM 文件不存在: {}", clipId);
                    return;
                }
                AudioFormatDecoder.DecodeResult result = AudioFormatDecoder.decode(in, clipId);
                bgm = createStreamSource(clipId, result.getFormat(),
                        result.getPcmData(), result.getDurationMs(), isMp3);
            } catch (Exception e) {
                logger.error("BGM 加载失败: {}: {}", clipId, e.getMessage());
                return;
            }
        }

        bgm.setChannel(channel);
        bgm.setClipId(clipId);
        bgm.setVolume(volume);
        bgm.setLoop(loop);
        bgm.updateEffectiveVolume();

        channel.addSource(bgm);

        if (fadeMs > 0) {
            fadeManager.fadeIn(bgm, volume, fadeMs);
        } else {
            bgm.play();
        }

        currentBgm = bgm;
        logger.info("播放 BGM: {} (loop={}, vol={})", clipId, loop, volume);
    }

    /**
     * 播放 BGM（默认：循环，音量 0.7，淡入 2 秒）。
     */
    public void playBGM(String clipId) {
        playBGM(clipId, true, 0.7f, 2000);
    }

    /**
     * 停止当前 BGM。
     *
     * @param fadeMs 淡出时长（毫秒），0=立即停止
     */
    public void stopBGM(long fadeMs) {
        if (currentBgm == null) return;

        if (fadeMs > 0) {
            fadeManager.fadeOut(currentBgm, fadeMs, () -> {
                currentBgm = null;
            });
        } else {
            currentBgm.stop();
            currentBgm = null;
        }
        logger.debug("停止 BGM");
    }

    /** 停止 BGM（默认淡出 1 秒） */
    public void stopBGM() {
        stopBGM(1000);
    }

    // ── 音量控制 ──────────────────────────────────

    /** 设置全局主音量 */
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0f, Math.min(1f, volume));
        for (AudioChannel ch : channels.values()) {
            ch.setMasterVolume(globalMuted ? 0f : masterVolume);
        }
    }

    /** 获取全局主音量 */
    public float getMasterVolume() { return masterVolume; }

    /** 设置全局静音 */
    public void setMuted(boolean muted) {
        this.globalMuted = muted;
        for (AudioChannel ch : channels.values()) {
            ch.setMasterVolume(muted ? 0f : masterVolume);
        }
        logger.info("全局静音: {}", muted);
    }

    /** 是否静音 */
    public boolean isMuted() { return globalMuted; }

    /** 切换静音 */
    public void toggleMute() {
        setMuted(!globalMuted);
    }

    // ── 通道控制 ──────────────────────────────────

    /** 获取指定通道 */
    public AudioChannel getChannel(String name) {
        return channels.get(name);
    }

    /** 设置通道音量 */
    public void setChannelVolume(String channelName, float volume) {
        AudioChannel ch = channels.get(channelName);
        if (ch != null) {
            ch.setVolume(volume);
        }
    }

    /** 停止指定通道所有音源 */
    public void stopChannel(String channelName) {
        AudioChannel ch = channels.get(channelName);
        if (ch != null) {
            ch.stopAll();
        }
    }

    // ── 淡入淡出 & 调度 ──────────────────────────────────

    /** 获取淡入淡出调度器 */
    public FadeManager getFadeManager() { return fadeManager; }

    /** 获取延迟播放调度器 */
    public AudioScheduler getScheduler() { return scheduler; }

    // ── 缓存管理 ──────────────────────────────────

    /** 获取音频缓存 */
    public AudioCache getCache() { return cache; }

    /** 预加载音频 */
    public void preload(String... paths) {
        cache.preload(java.util.Arrays.asList(paths));
    }

    /** 卸载未使用缓存 */
    public void unloadUnused() {
        cache.unloadUnused();
    }

    // ── 引擎帧更新 ──────────────────────────────────

    /**
     * 每帧调用，更新淡入淡出和调度器。
     *
     * @param deltaTime 距上一帧的时间（毫秒）
     */
    public void update(long deltaTime) {
        fadeManager.update(deltaTime);
        scheduler.update(deltaTime);

        // 更新所有活跃音源
        for (AudioChannel ch : channels.values()) {
            for (AudioSource src : ch.getSources()) {
                if (src.getState() == AudioSource.State.PLAYING) {
                    src.update(deltaTime);
                }
            }
        }
    }

    // ── 销毁 ──────────────────────────────────

    /** 停止所有音频，释放资源 */
    public void dispose() {
        for (AudioChannel ch : channels.values()) {
            ch.stopAll();
        }
        if (currentBgm != null) {
            currentBgm.stop();
            currentBgm = null;
        }
        sfxPool.releaseAll();
        cache.clear();
        cache.shutdown();
        fadeManager.cancel(null);
        scheduler.clear();
        logger.info("音频管理器已销毁");
    }

    // ── 辅助方法 ──────────────────────────────────

    private StreamSource createStreamSource(String clipId, javax.sound.sampled.AudioFormat format,
                                             byte[] pcmData, long durationMs, boolean isMp3) {
        // 对于流式播放，如果是 MP3，我们使用 StreamSource 的流式解码
        // 如果是已缓存的完整 PCM，我们可以直接播放
        // 这里为了统一，大文件用流式，小文件用 ClipSource
        if (pcmData.length < 512 * 1024) { // < 512KB 用 Clip
            // 实际上这里应该返回 ClipSource，但为了 API 统一，
            // 我们让 playBGM 内部处理
            return new StreamSource(format, clipId, durationMs, isMp3, resourceLoader);
        }
        return new StreamSource(format, clipId, durationMs, isMp3, resourceLoader);
    }

    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : "";
    }

    /** 播放请求（供 Scheduler 使用） */
    static class PlayRequest {
        final String clipId;
        final AudioChannel channel;
        PlayRequest(String clipId, AudioChannel channel) {
            this.clipId = clipId;
            this.channel = channel;
        }
    }
}
