package com.github.game.cdda.npc;

import com.github.game.cdda.Constants;
import com.github.game.cdda.item.model.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NPC 背包。
 * 管理 NPC 携带的物品，基于重量限制容量。
 *
 * <p>携带上限 = NPC 力量 × {@link Constants#CARRY_PER_STRENGTH}
 *
 * <p>与 {@link com.github.game.cdda.item.world.PlayerInventory} 类似，
 * 但简化了部分功能（不支持玩家那样的手动管理）。
 * NPC 死亡时，所有物品通过此背包掉落。
 */
public class NpcInventory {

    private static final Logger logger = LoggerFactory.getLogger(NpcInventory.class);

    /** 物品列表 */
    private final List<ItemStack> items = new ArrayList<>();

    /** 所属 NPC */
    private final Npc npc;

    /**
     * 创建背包。
     *
     * @param npc 所属 NPC
     */
    public NpcInventory(Npc npc) {
        this.npc = npc;
    }

    // ── 容量计算 ──────────────────────────────────

    /**
     * 获取携带上限（克）。
     *
     * @return 最大携带重量
     */
    public int getCarryCapacity() {
        return npc.getStrength() * Constants.CARRY_PER_STRENGTH;
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
            logger.debug("NPC {} 背包超重，无法添加: {}",
                    npc.getName(), stack.getType().getName());
            return false;
        }

        // 尝试堆叠到已有物品
        for (ItemStack existing : items) {
            if (existing.canMerge(stack)) {
                int remainder = existing.merge(stack);
                if (remainder <= 0) {
                    return true;
                }
                stack = new ItemStack(stack.getType(), remainder);
            }
        }

        // 无法堆叠 → 新建条目
        items.add(new ItemStack(stack.getType(), stack.getCount()));
        return true;
    }

    /**
     * 添加物品（忽略重量限制，用于 NPC 初始装备加载）。
     *
     * @param stack 要添加的物品堆
     */
    public void addItemUnchecked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        for (ItemStack existing : items) {
            if (existing.canMerge(stack)) {
                int remainder = existing.merge(stack);
                if (remainder <= 0) return;
                stack = new ItemStack(stack.getType(), remainder);
            }
        }

        items.add(new ItemStack(stack.getType(), stack.getCount()));
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

    // ── 交易 ──────────────────────────────────

    /**
     * 尝试从玩家处购买物品（玩家卖给 NPC）。
     *
     * @param stack 要购买的物品
     * @return 是否成功
     */
    public boolean buyFromPlayer(ItemStack stack) {
        if (!canCarry(stack)) return false;
        return addItem(stack);
    }

    /**
     * NPC 将物品卖给玩家（从 NPC 背包转移到玩家背包）。
     *
     * @param index         NPC 背包中的物品索引
     * @param playerInv     玩家背包
     * @param count         购买数量（1 = 整组）
     * @return 是否成功
     */
    public boolean sellToPlayer(int index, com.github.game.cdda.item.world.PlayerInventory playerInv,
                                int count) {
        if (index < 0 || index >= items.size()) return false;
        ItemStack stack = items.get(index);
        if (stack == null || stack.isEmpty()) return false;

        // 确定实际购买数量
        int buyCount = Math.min(count, stack.getCount());
        ItemStack toSell = new ItemStack(stack.getType(), buyCount);

        // 检查玩家背包重量限制
        if (playerInv != null && !playerInv.canCarry(toSell)) {
            logger.debug("玩家背包超重/容量不足，无法购买 {}",
                    stack.getType().getDisplayName());
            return false;
        }

        // 转移物品
        if (playerInv != null) {
            playerInv.addItem(toSell);
        }

        // 从 NPC 背包移除
        stack.setCount(stack.getCount() - buyCount);
        if (stack.getCount() <= 0) {
            items.remove(index);
        }

        return true;
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
     *
     * @return 被清空的所有物品
     */
    public List<ItemStack> clearAll() {
        List<ItemStack> all = new ArrayList<>(items);
        items.clear();
        return all;
    }
}
