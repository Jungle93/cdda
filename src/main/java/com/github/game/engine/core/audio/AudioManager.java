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

    /** 音效节流：clipId → 冷却结束时间戳（ms）。同一音效在冷却期内不重复播放 */
    private final java.util.Map<String, Long> sfxCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    /** 淡入淡出调度器 */
    private final FadeManager fadeManager = new FadeManager();

    /** 延迟播放调度器 */
    private final AudioScheduler scheduler;

    /** 当前 BGM 音源（可能是 ClipSource 或 StreamSource） */
    private AudioSource currentBgm;

    /** 动作音效：动作名 → 音源（循环播放，由 stopActionSound 显式停止） */
    private final Map<String, AudioSource> actionSounds = new java.util.concurrent.ConcurrentHashMap<>();

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
     * 同一音效在上一次播放完成前不会重复触发（节流）。
     */
    private void playSound(String clipId, String channelName, boolean loop, float volume) {
        // 节流：检查是否还在冷却中
        long now = System.currentTimeMillis();
        Long cooldownEnd = sfxCooldowns.get(clipId);
        if (cooldownEnd != null && now < cooldownEnd) {
            return; // 仍在冷却，跳过
        }

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

        // 设置冷却时间 = 当前时间 + 音频时长
        sfxCooldowns.put(clipId, now + cached.getDurationMs());

        // 创建新的 ClipSource（每次播放独立实例，由 update() 在播放完成后释放）
        // 注：sfxPool 的复用逻辑需后续实现 ClipSource.reset(CachedAudio) 后才能启用
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

        // 先加载音频获取格式和 PCM 数据，使用 ClipSource 直接播放（更可靠，支持所有 JavaSound 解码器）
        AudioSource bgm;
        AudioCache.CachedAudio cached = cache.loadSync(clipId);
        if (cached != null) {
            // 缓存命中：直接用缓存的 PCM 数据创建 ClipSource
            bgm = new ClipSource(cached);
        } else {
            // 缓存未命中：解码文件，再创建 ClipSource
            try (InputStream in = resourceLoader.apply(clipId)) {
                if (in == null) {
                    logger.warn("BGM 文件不存在: {}", clipId);
                    return;
                }
                AudioFormatDecoder.DecodeResult result = AudioFormatDecoder.decode(in, clipId);
                bgm = new ClipSource(result.getFormat(), result.getPcmData(), result.getDurationMs());
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

    // ── 动作音效（按动作名绑定生命周期） ──────────────────────────────────

    /**
     * 播放动作音效（循环，绑定到动作名）。
     *
     * <p>动作音效与一次性 SFX 不同：它持续循环播放，直到显式调用 {@link #stopActionSound} 停止。
     * 适合与持续性动作绑定（如行走、砍树、挖掘等）：动作开始时播放，动作结束时停止。
     *
     * <p>特性：
     * <ul>
     *   <li>同一动作名重复调用（同一 clipId）是 no-op，不会重复播放</li>
     *   <li>同一动作名不同 clipId 会先停止旧音效再播放新音效</li>
     *   <li>不同动作名可同时播放（如"行走"+"喘息"）</li>
     * </ul>
     *
     * @param actionName 动作名称（如 "walk"、"chop"、"dig"）
     * @param clipId     音频资源路径（如 "audio/sfx/walk.mp3"）
     * @param volume     音量 (0~1)
     */
    public void playActionSound(String actionName, String clipId, float volume) {
        // 同一动作已在播放相同音效，无需重触发
        AudioSource existing = actionSounds.get(actionName);
        if (existing != null
                && clipId.equals(existing.getClipId())
                && existing.getState() == AudioSource.State.PLAYING) {
            return;
        }

        // 同一动作名正在播放不同音效，或旧音效已意外停止：先停止
        if (existing != null) {
            stopActionSound(actionName);
        }

        // 加载音频并创建循环音源
        AudioCache.CachedAudio cached = cache.loadSync(clipId);
        if (cached == null) {
            logger.warn("动作音效加载失败: {}", clipId);
            return;
        }

        ClipSource source = new ClipSource(cached);
        source.setClipId(clipId);
        source.setLoop(true);
        source.setVolume(volume);

        AudioChannel channel = channels.get(CHANNEL_SFX);
        if (channel == null) return;
        source.setChannel(channel);
        source.updateEffectiveVolume();

        actionSounds.put(actionName, source);
        channel.addSource(source);
        source.play();

        logger.debug("开始动作音效: {} → {}", actionName, clipId);
    }

    /**
     * 停止指定动作的音效。
     * 若该动作没有正在播放的音效，此方法为空操作。
     *
     * @param actionName 动作名称
     */
    public void stopActionSound(String actionName) {
        AudioSource source = actionSounds.remove(actionName);
        if (source != null) {
            source.stop();
            AudioChannel channel = source.getChannel();
            if (channel != null) {
                channel.removeSource(source);
            }
            if (source instanceof ClipSource clip) {
                clip.dispose();
            }
            logger.debug("停止动作音效: {}", actionName);
        }
    }

    /** 停止所有动作音效。 */
    public void stopAllActionSounds() {
        for (String actionName : new java.util.ArrayList<>(actionSounds.keySet())) {
            stopActionSound(actionName);
        }
    }

    /**
     * 查询动作音效是否正在播放。
     *
     * @param actionName 动作名称
     * @return true 如果该动作有正在播放的音效
     */
    public boolean isActionSoundPlaying(String actionName) {
        AudioSource source = actionSounds.get(actionName);
        return source != null && source.getState() == AudioSource.State.PLAYING;
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
     * 同时清理已完成播放的非循环音效，释放原生音频线路。
     *
     * <p>每次 playSFX() 都会创建新的 ClipSource（持有 Java Sound Clip），
     * 播放完毕后状态变为 STOPPED，但 Clip 不主动释放。
     * 此处在每帧扫描 STOPPED 的非循环音源，调用 {@link ClipSource#dispose()}
     * 释放原生音频线路，并将对象从通道中移除，防止线路耗尽。
     *
     * @param deltaTime 距上一帧的时间（毫秒）
     */
    public void update(long deltaTime) {
        fadeManager.update(deltaTime);
        scheduler.update(deltaTime);

        // 更新所有活跃音源，并收集已完成的音效进行清理
        for (AudioChannel ch : channels.values()) {
            java.util.List<AudioSource> completed = null;

            for (AudioSource src : ch.getSources()) {
                if (src.getState() == AudioSource.State.PLAYING) {
                    src.update(deltaTime);
                } else if (src.getState() == AudioSource.State.STOPPED
                        && !src.isLoop()
                        && !actionSounds.containsValue(src)) {
                    // 非循环、非动作音效的音源播放完成，待清理
                    if (completed == null) {
                        completed = new java.util.ArrayList<>();
                    }
                    completed.add(src);
                }
            }

            // 清理：从通道移除并释放原生资源
            if (completed != null) {
                for (AudioSource src : completed) {
                    ch.removeSource(src);
                    if (src instanceof ClipSource clip) {
                        clip.dispose(); // 释放 Java Sound Clip 及其原生音频线路
                    }
                }
            }
        }
    }

    // ── 销毁 ──────────────────────────────────

    /** 停止所有音频，释放资源 */
    public void dispose() {
        stopAllActionSounds();
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
