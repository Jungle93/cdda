package com.github.game.cdda.item.registry;

import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;

import com.github.game.cdda.item.action.ButcherAction;
import com.github.game.cdda.item.action.ChopTreeAction;
import com.github.game.cdda.item.action.ProcessItemAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品动作注册表。
 *
 * <p>将功能标签（如 "chopping"）映射到对应的 {@link ItemAction} 实现。
 * 当玩家使用物品时，根据物品的标签查找所有已注册的动作。
 *
 * <p>用法：
 * <pre>
 * // 注册动作（游戏启动时）
 * ItemActionRegistry.register("chopping", new ChopTreeAction());
 *
 * // 查询物品的可用动作
 * List&lt;ItemAction&gt; actions = ItemActionRegistry.getActionsFor(itemStack);
 * </pre>
 *
 * @see ItemAction
 */
public final class ItemActionRegistry {

    /** 标签 → 动作映射 */
    private static final Map<String, ItemAction> TAG_ACTIONS = new HashMap<>();

    // 类加载时注册内置动作
    static {
        registerBuiltins();
    }

    private ItemActionRegistry() {} // 不可实例化

    /**
     * 注册一个动作到指定的功能标签。
     * 同一标签只能注册一个动作（后注册覆盖先注册）。
     *
     * @param tag    功能标签（如 "chopping"）
     * @param action 动作实现
     */
    public static void register(String tag, ItemAction action) {
        TAG_ACTIONS.put(tag, action);
    }

    /**
     * 获取指定物品的所有可用动作。
     * 遍历物品的功能标签，收集所有已注册的动作。
     *
     * @param stack 物品堆
     * @return 可用动作列表（不可变，可能为空）
     */
    public static List<ItemAction> getActionsFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<ItemAction> result = new ArrayList<>();
        for (String tag : stack.getType().getTags()) {
            ItemAction action = TAG_ACTIONS.get(tag);
            if (action != null) {
                result.add(action);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 检查物品是否有任何已注册的动作。
     *
     * @param stack 物品堆
     * @return true 如果至少有一个可用动作
     */
    public static boolean hasAnyAction(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (String tag : stack.getType().getTags()) {
            if (TAG_ACTIONS.containsKey(tag)) return true;
        }
        return false;
    }

    // ── 内置动作注册 ──

    /**
     * 注册所有内置动作。在游戏初始化时调用。
     */
    public static void registerBuiltins() {
        register("chopping", new ChopTreeAction());
        register("processing", new ProcessItemAction());
        register("cutting", new ButcherAction());
    }
}
