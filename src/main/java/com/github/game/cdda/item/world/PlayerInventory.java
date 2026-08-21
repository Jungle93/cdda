package com.github.game.cdda.item.world;

import com.github.game.cdda.Constants;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.model.ConsumableType;
import com.github.game.cdda.item.model.DisplayUnit;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 玩家背包。
 * 管理玩家携带的物品，基于重量限制容量。
 *
 * <p>携带上限 = 玩家力量 × {@link com.github.game.cdda.Constants#CARRY_PER_STRENGTH}
 *
 * <p>添加物品时优先堆叠到已有同类物品，超出 maxStackSize 则创建新条目。
 */
public class PlayerInventory {

    private static final Logger logger = LoggerFactory.getLogger(PlayerInventory.class);

    /** 物品列表 */
    private final List<ItemStack> items = new ArrayList<>();

    /** 所属玩家（用于计算携带上限） */
    private final Player player;

    /**
     * 创建背包。
     *
     * @param player 所属玩家
     */
    public PlayerInventory(Player player) {
        this.player = player;
    }

    // ── 容量计算 ──────────────────────────────────

    /**
     * 获取携带上限（克）。
     *
     * @return 最大携带重量
     */
    public int getCarryCapacity() {
        return player.getStrength() * com.github.game.cdda.Constants.CARRY_PER_STRENGTH;
    }

    /**
     * 获取当前已携带的总重量（克）。
     *
     * @return 总重量
     */
    public double getTotalWeight() {
        double total = 0;
        for (ItemStack stack : items) {
            total += stack.getTotalWeightGrams();
        }
        return total;
    }

    /**
     * 获取剩余可携带重量（克）。
     *
     * @return 剩余容量
     */
    public double getRemainingCapacity() {
        return getCarryCapacity() - getTotalWeight();
    }

    /**
     * 检查是否能携带指定物品（不超出重量上限）。
     *
     * @param stack 物品堆
     * @return 是否能携带
     */
    public boolean canCarry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        return getTotalWeight() + stack.getTotalWeightGrams() <= getCarryCapacity();
    }

    // ── 物品管理 ──────────────────────────────────

    /**
     * 添加物品到背包。
     * 优先堆叠到已有同类物品，超出限制则创建新条目。
     *
     * @param stack 要添加的物品堆
     * @return 是否成功添加（重量超限时返回 false）
     */
    public boolean addItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!canCarry(stack)) {
            logger.debug("背包超重，无法添加: {} ({}g)", stack.getType().getName(),
                    (int) stack.getTotalWeightGrams());
            return false;
        }

        // 尝试堆叠到已有物品
        for (ItemStack existing : items) {
            if (existing.canMerge(stack)) {
                int remainder = existing.merge(stack);
                if (remainder <= 0) {
                    // 全部堆叠成功
                    return true;
                }
                // 部分堆叠，剩余的继续尝试或新建条目
                stack = new ItemStack(stack.getType(), remainder);
            }
        }

        // 无法堆叠 → 新建条目
        items.add(new ItemStack(stack.getType(), stack.getCount()));
        return true;
    }

    /**
     * 移除指定索引的物品（整组）。
     *
     * @param index 物品索引
     * @return 被移除的物品堆
     */
    public ItemStack removeItem(int index) {
        if (index < 0 || index >= items.size()) return null;
        return items.remove(index);
    }

    /**
     * 从指定索引的物品中移除指定数量。
     * 如果数量减到 0 或以下，整组移除。
     *
     * @param index 物品索引
     * @param count 移除数量
     * @return 实际移除的物品堆
     */
    public ItemStack removeItem(int index, int count) {
        if (index < 0 || index >= items.size()) return null;

        ItemStack stack = items.get(index);
        if (count >= stack.getCount()) {
            return items.remove(index);
        }

        // 部分取出
        ItemStack removed = new ItemStack(stack.getType(), count);
        stack.removeCount(count);
        return removed;
    }

    /**
     * 按物品 ID 移除指定数量的物品。
     * 遍历背包，从匹配 ID 的堆中依次扣减，直到满足数量或耗尽。
     *
     * @param itemId 物品 ID
     * @param count  要移除的总数量
     */
    public void removeItemsById(int itemId, int count) {
        java.util.Iterator<ItemStack> it = items.iterator();
        while (it.hasNext() && count > 0) {
            ItemStack stack = it.next();
            if (stack.getType().getId() == itemId) {
                int take = Math.min(count, stack.getCount());
                stack.setCount(stack.getCount() - take);
                count -= take;
                if (stack.getCount() <= 0) it.remove();
            }
        }
    }

    // ── 查询 ──────────────────────────────────

    /**
     * 获取物品列表（只读）。
     *
     * @return 不可变的物品列表
     */
    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * 获取物品种类数。
     *
     * @return 物品种类数量
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * 背包是否为空。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 获取指定索引的物品。
     *
     * @param index 索引
     * @return 物品堆，越界返回 null
     */
    public ItemStack getItem(int index) {
        if (index < 0 || index >= items.size()) return null;
        return items.get(index);
    }

    /**
     * 清空背包。
     * 用于加载存档时重置背包状态。
     */
    public void clear() {
        items.clear();
    }
}
