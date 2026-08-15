package com.github.game.engine.core.audio;

/**
 * 音频引擎 — 对外门面。
 *
 * <p>游戏层通过 {@code engine.getAudioEngine()} 访问，
 * 提供简洁的音频 API：播放音效、播放 BGM、音量控制、静音等。
 *
 * <p>生命周期：
 * <ol>
 *   <li>GameEngine 构造时创建</li>
 *   <li>每帧 tick() 调用 update(deltaTime)</li>
 *   <li>GameEngine.stop() 时调用 dispose()</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>
 * AudioEngine audio = engine.getAudioEngine();
 * audio.playSFX("audio/click.wav");              // 播放音效
 * audio.playBGM("audio/bgm.mp3");                // 播放循环 BGM
 * audio.setMasterVolume(0.5f);                   // 设置主音量
 * audio.toggleMute();                            // 静音切换
 * audio.stopBGM(1500);                           // BGM 淡出停止
 * </pre>
 */
public class AudioEngine {

    /** 内部管理器 */
    private final AudioManager manager;

    /**
     * 创建音频引擎。
     *
     * @param resourceLoader 资源加载函数（传入路径，返回 InputStream）
     */
    public AudioEngine(java.util.function.Function<String, java.io.InputStream> resourceLoader) {
        this.manager = new AudioManager(resourceLoader);
    }

    // ── 音效 ──────────────────────────────────

    /**
     * 播放音效（SFX 通道，默认音量 1.0，不循环）。
     *
     * @param clipId 音频资源路径（如 "audio/click.wav"）
     */
    public void playSFX(String clipId) {
        manager.playSFX(clipId);
    }

    /**
     * 播放音效（可配置参数）。
     *
     * @param clipId 音频资源路径
     * @param loop   是否循环
     * @param volume 音量 (0~1)
     */
    public void playSFX(String clipId, boolean loop, float volume) {
        manager.playSFX(clipId, loop, volume);
    }

    // ── BGM ──────────────────────────────────

    /**
     * 播放背景音乐（默认：循环，音量 0.7，淡入 2 秒）。
     *
     * @param clipId 音频资源路径（如 "audio/bgm.mp3"）
     */
    public void playBGM(String clipId) {
        manager.playBGM(clipId);
    }

    /**
     * 播放背景音乐（可配置参数）。
     *
     * @param clipId  音频资源路径
     * @param loop    是否循环
     * @param volume  音量 (0~1)
     * @param fadeMs  淡入时长（毫秒），0=不淡入
     */
    public void playBGM(String clipId, boolean loop, float volume, long fadeMs) {
        manager.playBGM(clipId, loop, volume, fadeMs);
    }

    /**
     * 停止当前 BGM（默认淡出 1 秒）。
     */
    public void stopBGM() {
        manager.stopBGM();
    }

    /**
     * 停止当前 BGM。
     *
     * @param fadeMs 淡出时长（毫秒），0=立即停止
     */
    public void stopBGM(long fadeMs) {
        manager.stopBGM(fadeMs);
    }

    // ── 动作音效（按动作名绑定生命周期） ──────────────────────────────────

    /**
     * 播放动作音效（循环，绑定到动作名）。
     *
     * <p>动作音效持续循环播放，直到显式调用 {@link #stopActionSound} 停止。
     * 适合与持续性动作绑定：动作开始时调用一次，动作结束时停止。
     *
     * <p>示例：
     * <pre>
     * // 开始行走时
     * audio.playActionSound("walk", "audio/sfx/walk.mp3", 0.6f);
     * // 停止行走时
     * audio.stopActionSound("walk");
     * </pre>
     *
     * @param actionName 动作名称（如 "walk"、"chop"、"dig"）
     * @param clipId     音频资源路径
     * @param volume     音量 (0~1)
     */
    public void playActionSound(String actionName, String clipId, float volume) {
        manager.playActionSound(actionName, clipId, volume);
    }

    /**
     * 停止指定动作的音效。
     * 若该动作没有正在播放的音效，此方法为空操作。
     */
    public void stopActionSound(String actionName) {
        manager.stopActionSound(actionName);
    }

    /** 停止所有动作音效。 */
    public void stopAllActionSounds() {
        manager.stopAllActionSounds();
    }

    /** 查询动作音效是否正在播放。 */
    public boolean isActionSoundPlaying(String actionName) {
        return manager.isActionSoundPlaying(actionName);
    }

    // ── 音量控制 ──────────────────────────────────

    /**
     * 设置全局主音量。
     *
     * @param volume 音量 (0~1)
     */
    public void setMasterVolume(float volume) {
        manager.setMasterVolume(volume);
    }

    /** 获取全局主音量 */
    public float getMasterVolume() {
        return manager.getMasterVolume();
    }

    /**
     * 设置全局静音。
     */
    public void setMuted(boolean muted) {
        manager.setMuted(muted);
    }

    /** 是否静音 */
    public boolean isMuted() {
        return manager.isMuted();
    }

    /** 切换静音 */
    public void toggleMute() {
        manager.toggleMute();
    }

    // ── 通道控制 ──────────────────────────────────

    /**
     * 获取指定混音通道。
     *
     * @param name 通道名称（{@link AudioManager#CHANNEL_SFX},
     *             {@link AudioManager#CHANNEL_BGM},
     *             {@link AudioManager#CHANNEL_AMBIENT}）
     */
    public AudioChannel getChannel(String name) {
        return manager.getChannel(name);
    }

    /** 设置通道音量 */
    public void setChannelVolume(String channelName, float volume) {
        manager.setChannelVolume(channelName, volume);
    }

    /** 停止指定通道所有音源 */
    public void stopChannel(String channelName) {
        manager.stopChannel(channelName);
    }

    // ── 淡入淡出 & 调度 ──────────────────────────────────

    /** 获取淡入淡出调度器 */
    public FadeManager getFadeManager() {
        return manager.getFadeManager();
    }

    /** 获取延迟播放调度器 */
    public AudioScheduler getScheduler() {
        return manager.getScheduler();
    }

    /**
     * 交叉淡入淡出（旧 BGM 淡出 + 新 BGM 淡入）。
     *
     * @param newClipId 新 BGM 路径
     * @param durationMs 过渡时长
     */
    public void crossFadeBGM(String newClipId, long durationMs) {
        crossFadeBGM(newClipId, 0.7f, durationMs);
    }

    /**
     * 交叉淡入淡出（可配置音量）。
     */
    public void crossFadeBGM(String newClipId, float volume, long durationMs) {
        // 停止当前 BGM（淡出）
        stopBGM(durationMs);
        // 播放新 BGM（淡入）
        playBGM(newClipId, true, volume, durationMs);
    }

    // ── 缓存管理 ──────────────────────────────────

    /** 预加载音频到缓存 */
    public void preload(String... paths) {
        manager.preload(paths);
    }

    /** 卸载未使用的音频缓存 */
    public void unloadUnused() {
        manager.unloadUnused();
    }

    // ── 生命周期 ──────────────────────────────────

    /**
     * 每帧更新。
     * 由 GameEngine.tick() 调用。
     */
    public void update(long deltaTime) {
        manager.update(deltaTime);
    }

    /**
     * 销毁音频引擎，释放所有资源。
     * 由 GameEngine.stop() 调用。
     */
    public void dispose() {
        manager.dispose();
    }

    // ── 访问器 ──────────────────────────────────

    /** 获取内部管理器（高级用法） */
    public AudioManager getManager() {
        return manager;
    }
}
