package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 音频资源缓存 + 异步加载 + 按需卸载。
 *
 * <p>功能：
 * <ul>
 *   <li>PCM 数据缓存（音效预载到内存）</li>
 *   <li>引用计数（跟踪缓存使用，支持按需卸载）</li>
 *   <li>异步加载（不阻塞主线程）</li>
 *   <li>预加载（批量加载到缓存）</li>
 * </ul>
 */
public class AudioCache {

    private static final Logger logger = LoggerFactory.getLogger(AudioCache.class);

    /** PCM 数据缓存，key = 资源路径 */
    private final Map<String, CachedAudio> clipCache = new ConcurrentHashMap<>();

    /** 引用计数，跟踪缓存使用情况 */
    private final Map<String, Integer> refCount = new ConcurrentHashMap<>();

    /** 异步加载线程 */
    private final ExecutorService loader;

    /** 资源加载器（由 ResourceManager 提供） */
    private final java.util.function.Function<String, InputStream> resourceLoader;

    /**
     * 创建音频缓存。
     *
     * @param resourceLoader 资源加载函数（传入路径，返回 InputStream）
     */
    public AudioCache(java.util.function.Function<String, InputStream> resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.loader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audio-loader");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 同步加载音频到缓存。
     *
     * @param path 资源路径
     * @return 缓存的音频数据
     */
    public synchronized CachedAudio loadSync(String path) {
        CachedAudio cached = clipCache.get(path);
        if (cached != null) {
            refCount.merge(path, 1, Integer::sum);
            return cached;
        }

        try (InputStream in = resourceLoader.apply(path)) {
            if (in == null) {
                logger.warn("音频文件不存在: {}", path);
                return null;
            }

            AudioFormatDecoder.DecodeResult result = AudioFormatDecoder.decode(in, path);
            CachedAudio audio = new CachedAudio(path, result.getFormat(),
                    result.getPcmData(), result.getDurationMs());
            clipCache.put(path, audio);
            refCount.put(path, 1);

            logger.debug("音频加载完成: {} ({} 字节, {}ms)", path,
                    result.getPcmData().length, result.getDurationMs());
            return audio;

        } catch (Exception e) {
            logger.error("音频加载失败: {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 异步加载音频（不阻塞调用线程）。
     *
     * @param path     资源路径
     * @param callback 加载完成回调（失败时传 null）
     */
    public void loadAsync(String path, Consumer<CachedAudio> callback) {
        loader.submit(() -> {
            try {
                CachedAudio audio = loadSync(path);
                if (callback != null) {
                    callback.accept(audio);
                }
            } catch (Exception e) {
                logger.error("异步加载失败: {}: {}", path, e.getMessage());
                if (callback != null) {
                    callback.accept(null);
                }
            }
        });
    }

    /**
     * 预加载多个音频文件。
     *
     * @param paths 资源路径列表
     */
    public void preload(Collection<String> paths) {
        for (String path : paths) {
            loadAsync(path, null);
        }
    }

    /**
     * 增加引用计数（重复使用时调用）。
     *
     * @param path 资源路径
     */
    public void retain(String path) {
        refCount.merge(path, 1, Integer::sum);
    }

    /**
     * 减少引用计数。
     *
     * @param path 资源路径
     */
    public void release(String path) {
        refCount.merge(path, -1, (a, b) -> Math.max(0, a + b));
    }

    /**
     * 按需卸载指定音频。
     *
     * @param path 资源路径
     */
    public void unload(String path) {
        clipCache.remove(path);
        refCount.remove(path);
        logger.debug("音频已卸载: {}", path);
    }

    /**
     * 卸载所有未使用的缓存（refCount == 0）。
     */
    public void unloadUnused() {
        int unloaded = 0;
        for (Map.Entry<String, Integer> entry : refCount.entrySet()) {
            if (entry.getValue() <= 0) {
                clipCache.remove(entry.getKey());
                refCount.remove(entry.getKey());
                unloaded++;
            }
        }
        if (unloaded > 0) {
            logger.info("卸载 {} 个未使用音频，剩余缓存: {}", unloaded, clipCache.size());
        }
    }

    /** 清空所有缓存 */
    public void clear() {
        clipCache.clear();
        refCount.clear();
    }

    /** 获取指定缓存音频 */
    public CachedAudio get(String path) { return clipCache.get(path); }

    /** 已缓存数量 */
    public int size() { return clipCache.size(); }

    /** 是否已缓存 */
    public boolean isCached(String path) { return clipCache.containsKey(path); }

    /** 关闭加载线程 */
    public void shutdown() {
        loader.shutdown();
    }

    // ── 缓存数据类 ──────────────────────────────────

    /**
     * 缓存的音频数据。
     */
    public static class CachedAudio {
        private final String path;
        private final javax.sound.sampled.AudioFormat format;
        private final byte[] pcmData;
        private final long durationMs;

        CachedAudio(String path, javax.sound.sampled.AudioFormat format,
                     byte[] pcmData, long durationMs) {
            this.path = path;
            this.format = format;
            this.pcmData = pcmData;
            this.durationMs = durationMs;
        }

        public String getPath() { return path; }
        public javax.sound.sampled.AudioFormat getFormat() { return format; }
        public byte[] getPcmData() { return pcmData; }
        public long getDurationMs() { return durationMs; }
    }
}
