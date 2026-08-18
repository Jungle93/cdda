package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;
import com.github.game.engine.core.sprite.SpritePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 内置图形包 —— 可爱抽象风格的程序化点阵精灵集合。
 * <p>
 * 包含：
 * <ul>
 *   <li>10 种生物精灵（wolf、fox、badger、rabbit、hare、squirrel、deer、roe_deer、boar、mouflon）</li>
 *   <li>玩家精灵</li>
 *   <li>18 种地形精灵</li>
 *   <li>16 种植被精灵（不同树种/灌木/草类/水生植物）</li>
 *   <li>物品精灵（从 PNG 贴图加载，无则跳过）</li>
 * </ul>
 * 所有精灵尺寸为 32×32 像素。
 * </p>
 */
public class BuiltinSpritePack implements SpritePack {

    private static final Logger logger = LoggerFactory.getLogger(BuiltinSpritePack.class);

    /** 图形包 ID */
    public static final String ID = "builtin";

    /** 图形包名称 */
    public static final String NAME = "可爱抽象图形包";

    /** 瓦片尺寸（像素） */
    public static final int TILE_SIZE = 32;

    /** 精灵集合（不可变） */
    private final Map<String, Sprite> sprites;

    /**
     * 构造内置图形包，加载所有精灵数据。
     */
    public BuiltinSpritePack() {
        Map<String, Sprite> all = new HashMap<>();

        // 加载生物精灵（含玩家）
        Map<String, Sprite> creatureSprites = CreatureSpriteData.createAllCreatureSprites();
        all.putAll(creatureSprites);
        logger.debug("加载 {} 个生物/玩家精灵", creatureSprites.size());

        // 加载地形精灵
        Map<String, Sprite> tileSprites = TileSpriteData.createAllTileSprites();
        all.putAll(tileSprites);
        logger.debug("加载 {} 个地形精灵", tileSprites.size());

        // 加载植被精灵（不同树种/灌木）
        Map<String, Sprite> vegetationSprites = VegetationSpriteData.createAllVegetationSprites();
        all.putAll(vegetationSprites);
        logger.debug("加载 {} 个植被精灵", vegetationSprites.size());

        // 加载物品精灵
        Map<String, Sprite> itemSprites = ItemSpriteData.createAllItemSprites();
        all.putAll(itemSprites);
        logger.debug("加载 {} 个物品精灵", itemSprites.size());

        this.sprites = Collections.unmodifiableMap(all);
        logger.info("内置图形包加载完成，共 {} 个精灵", sprites.size());
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getTileSize() {
        return TILE_SIZE;
    }

    @Override
    public Sprite getSprite(String id) {
        return sprites.get(id);
    }

    @Override
    public boolean hasSprite(String id) {
        return sprites.containsKey(id);
    }

    @Override
    public Set<String> getSpriteIds() {
        return sprites.keySet();
    }
}
