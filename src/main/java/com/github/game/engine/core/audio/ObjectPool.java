package com.github.game.engine.core.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 对象池 — AudioSource 组件复用。
 *
 * <p>避免频繁创建/销毁 AudioSource 带来的 GC 压力和性能开销。
 * 池中对象在归还时自动重置状态。
 *
 * @param <T> 池化对象类型（必须是 AudioSource 子类）
 */
public class ObjectPool<T extends AudioSource> {

    private static final Logger logger = LoggerFactory.getLogger(ObjectPool.class);

    private final Supplier<T> factory;
    private final java.util.function.Consumer<T> resetter;
    private final List<T> available;
    private final List<T> inUse;
    private final int maxSize;

    /**
     * 创建对象池。
     *
     * @param factory   对象工厂（创建新实例）
     * @param resetter  重置回调（归还对象时调用，恢复初始状态）
     * @param maxSize   池最大容量
     */
    public ObjectPool(Supplier<T> factory, java.util.function.Consumer<T> resetter, int maxSize) {
        this.factory = factory;
        this.resetter = resetter;
        this.maxSize = maxSize;
        this.available = new ArrayList<>();
        this.inUse = new ArrayList<>();
    }

    /**
     * 从池中获取一个对象。
     * 池空时创建新实例。
     *
     * @return 可用的 AudioSource 实例
     */
    public T acquire() {
        T obj;
        if (available.isEmpty()) {
            obj = factory.get();
            logger.debug("对象池扩容: {} (池大小: {})", obj.getClass().getSimpleName(), inUse.size() + 1);
        } else {
            obj = available.remove(available.size() - 1);
        }
        inUse.add(obj);
        return obj;
    }

    /**
     * 归还对象到池中。
     * 自动调用 resetter 重置状态。
     *
     * @param obj 要归还的对象
     */
    public void release(T obj) {
        if (inUse.remove(obj)) {
            resetter.accept(obj);
            if (available.size() < maxSize) {
                available.add(obj);
            }
        }
    }

    /**
     * 回收所有在用对象（不清空池，只是全部标记为可用）。
     * 用于场景切换时批量回收。
     */
    public void releaseAll() {
        for (T obj : inUse) {
            resetter.accept(obj);
            if (available.size() < maxSize) {
                available.add(obj);
            }
        }
        inUse.clear();
    }

    /** 池中可用对象数 */
    public int availableCount() { return available.size(); }

    /** 当前在用对象数 */
    public int inUseCount() { return inUse.size(); }

    /** 池总容量 */
    public int totalSize() { return available.size() + inUse.size(); }
}
