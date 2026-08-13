package com.github.game.engine.core.sprite;

import java.util.Set;

/**
 * 图形包接口 —— 定义一组命名的精灵集合。
 * <p>
 * 图形包提供瓦片化渲染所需的全部精灵图像。每个图形包有固定的瓦片尺寸（tileSize），
 * 所有精灵通常与该尺寸一致。实现类可以是内置程序生成、文件系统加载或 ZIP 归档等。
 * </p>
 * <p>
 * 精灵 ID 命名约定：
 * <ul>
 *   <li>生物：{@code creature.<id>}（如 "creature.wolf"）</li>
 *   <li>玩家：{@code player}</li>
 *   <li>地形：{@code tile.<name>}（如 "tile.grass"）</li>
 * </ul>
 * </p>
 */
public interface SpritePack {

    /**
     * 获取图形包唯一标识。
     *
     * @return 包 ID（如 "builtin"）
     */
    String getId();

    /**
     * 获取图形包显示名称。
     *
     * @return 人类可读的名称
     */
    String getName();

    /**
     * 获取该图形包的瓦片尺寸（像素）。
     * <p>
     * 所有精灵的宽度和高度应与此值一致。渲染管线以此值作为网格大小。
     * </p>
     *
     * @return 瓦片边长（如 16）
     */
    int getTileSize();

    /**
     * 按 ID 获取精灵。
     *
     * @param id 精灵标识
     * @return 对应的精灵，若不存在则返回 null
     */
    Sprite getSprite(String id);

    /**
     * 检查是否包含指定 ID 的精灵。
     *
     * @param id 精灵标识
     * @return 存在返回 true
     */
    boolean hasSprite(String id);

    /**
     * 获取该图形包中所有精灵的 ID 集合。
     *
     * @return 不可变的精灵 ID 集合
     */
    Set<String> getSpriteIds();
}
