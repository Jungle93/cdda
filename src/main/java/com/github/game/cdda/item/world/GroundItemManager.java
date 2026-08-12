package com.github.game.cdda.item.world;

import com.github.game.cdda.item.model.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 地面物品管理器。
 * 管理世界中所有掉落在地面上的物品。
 *
 * <p>职责：
 * <ul>
 *   <li>添加/移除地面物品</li>
 *   <li>按瓦片坐标查询物品</li>
 *   <li>提供所有地面物品列表（用于渲染）</li>
 * </ul>
 */
public class GroundItemManager {

    private static final Logger logger = LoggerFactory.getLogger(GroundItemManager.class);

    /** 所有地面物品 */
    private final List<GroundItem> groundItems = new ArrayList<>();

    /**
     * 添加地面物品。
     *
     * @param groundItem 地面物品
     */
    public void addGroundItem(GroundItem groundItem) {
        if (groundItem == null || groundItem.getItemStack().isEmpty()) return;
        groundItems.add(groundItem);
        logger.debug("添加地面物品: {} at [{},{}]",
                groundItem.getItemStack().getType().getName(),
                groundItem.getTileX(), groundItem.getTileY());
    }

    /**
     * 在指定瓦片放置物品。
     * 如果该位置已有同类物品则尝试堆叠，否则新建地面物品。
     *
     * @param itemStack 物品堆
     * @param tileX     瓦片 X
     * @param tileY     瓦片 Y
     */
    public void dropItem(ItemStack itemStack, int tileX, int tileY) {
        if (itemStack == null || itemStack.isEmpty()) return;

        // 尝试堆叠到已有物品
        for (GroundItem existing : groundItems) {
            if (existing.getTileX() == tileX && existing.getTileY() == tileY
                    && existing.getItemStack().canMerge(itemStack)) {
                existing.getItemStack().merge(itemStack);
                logger.debug("堆叠物品: {} at [{},{}]",
                        itemStack.getType().getName(), tileX, tileY);
                return;
            }
        }

        // 无法堆叠 → 新建地面物品
        addGroundItem(new GroundItem(new ItemStack(itemStack.getType(), itemStack.getCount()), tileX, tileY));
    }

    /**
     * 移除地面物品。
     *
     * @param groundItem 要移除的地面物品
     */
    public void removeGroundItem(GroundItem groundItem) {
        groundItems.remove(groundItem);
    }

    /**
     * 获取指定瓦片的所有地面物品。
     *
     * @param tileX 瓦片 X
     * @param tileY 瓦片 Y
     * @return 该位置的地面物品列表（不可变，可能为空）
     */
    public List<GroundItem> getItemsAt(int tileX, int tileY) {
        List<GroundItem> result = new ArrayList<>();
        for (GroundItem item : groundItems) {
            if (item.getTileX() == tileX && item.getTileY() == tileY
                    && !item.getItemStack().isEmpty()) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 检查指定瓦片是否有地面物品。
     *
     * @param tileX 瓦片 X
     * @param tileY 瓦片 Y
     * @return 是否有物品
     */
    public boolean hasItemAt(int tileX, int tileY) {
        for (GroundItem item : groundItems) {
            if (item.getTileX() == tileX && item.getTileY() == tileY
                    && !item.getItemStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有地面物品（只读）。
     *
     * @return 地面物品列表
     */
    public List<GroundItem> getAllGroundItems() {
        return Collections.unmodifiableList(groundItems);
    }

    /**
     * 获取地面物品总数。
     *
     * @return 数量
     */
    public int getCount() {
        return groundItems.size();
    }
}
