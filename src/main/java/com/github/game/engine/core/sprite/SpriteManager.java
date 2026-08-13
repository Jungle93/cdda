package com.github.game.engine.core.sprite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 精灵管理器 —— 管理当前激活的图形包。
 * <p>
 * 静态单例，全局访问点。渲染管线通过此类查询当前图形包的精灵。
 * 若未激活任何图形包，所有查询返回 null，渲染管线回退到 ASCII 字符模式。
 * </p>
 */
public final class SpriteManager {

    private static final Logger logger = LoggerFactory.getLogger(SpriteManager.class);

    /** 当前激活的图形包 */
    private static volatile SpritePack activePack;

    private SpriteManager() {}

    /**
     * 设置当前激活的图形包。
     * <p>
     * 传入 null 可停用图形包，回退到 ASCII 渲染模式。
     * </p>
     *
     * @param pack 要激活的图形包，或 null 以停用
     */
    public static void setActivePack(SpritePack pack) {
        SpritePack old = activePack;
        activePack = pack;
        if (pack != null) {
            logger.info("激活图形包: {} ({})，瓦片尺寸: {}px，精灵数量: {}",
                    pack.getName(), pack.getId(), pack.getTileSize(), pack.getSpriteIds().size());
        } else if (old != null) {
            logger.info("停用图形包: {} ({})", old.getName(), old.getId());
        }
    }

    /**
     * 获取当前激活的图形包。
     *
     * @return 当前图形包，未激活时返回 null
     */
    public static SpritePack getActivePack() {
        return activePack;
    }

    /**
     * 检查是否有激活的图形包。
     *
     * @return 有激活包返回 true
     */
    public static boolean hasActivePack() {
        return activePack != null;
    }

    /**
     * 快捷方法：从当前图形包中获取精灵。
     *
     * @param id 精灵标识
     * @return 对应的精灵，无图形包或无此精灵时返回 null
     */
    public static Sprite getSprite(String id) {
        SpritePack pack = activePack;
        return pack != null ? pack.getSprite(id) : null;
    }

    /**
     * 获取当前图形包的瓦片尺寸。
     *
     * @return 瓦片边长（像素），无图形包时返回 0
     */
    public static int getTileSize() {
        SpritePack pack = activePack;
        return pack != null ? pack.getTileSize() : 0;
    }
}
