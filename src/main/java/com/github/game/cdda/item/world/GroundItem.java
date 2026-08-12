package com.github.game.cdda.item.world;

import com.github.game.cdda.item.model.ItemStack;

/**
 * 地面物品。
 * 代表掉落在世界地图某个瓦片上的物品堆。
 *
 * <p>地面物品由 {@link GroundItemManager} 统一管理，
 * 可被玩家拾取或由生物掉落产生。
 */
public class GroundItem {

    /** 物品堆 */
    private ItemStack itemStack;

    /** 瓦片 X 坐标 */
    private int tileX;

    /** 瓦片 Y 坐标 */
    private int tileY;

    /**
     * 创建地面物品。
     *
     * @param itemStack 物品堆（不为 null 且非空）
     * @param tileX     瓦片 X
     * @param tileY     瓦片 Y
     */
    public GroundItem(ItemStack itemStack, int tileX, int tileY) {
        this.itemStack = itemStack;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    // ── 访问器 ──────────────────────────────────

    public ItemStack getItemStack() { return itemStack; }
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }

    public void setTileX(int tileX) { this.tileX = tileX; }
    public void setTileY(int tileY) { this.tileY = tileY; }
}
