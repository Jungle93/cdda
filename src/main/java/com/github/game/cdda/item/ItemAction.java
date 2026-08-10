package com.github.game.cdda.item;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;

/**
 * 物品动作接口。
 *
 * <p>每个动作代表物品可以执行的一种功能（如砍树、挖掘、生火等）。
 * 通过 {@link ItemActionRegistry} 将动作注册到特定的功能标签上，
 * 当玩家使用带有对应标签的物品时，可执行该动作。
 *
 * @see ItemActionRegistry
 */
public interface ItemAction {

    /**
     * 动作显示名称（如"砍树"）。
     */
    String getName();

    /**
     * 动作描述（如"砍伐相邻的树木"）。
     */
    String getDescription();

    /**
     * 判断当前条件下是否可以执行此动作。
     * 例如：砍树需要玩家 adjacent 有树木。
     *
     * @param player 执行动作的玩家
     * @param world  游戏世界
     * @return true 如果可以执行
     */
    boolean canExecute(Player player, GameWorld world);

    /**
     * 执行动作。
     *
     * @param player 执行动作的玩家
     * @param world  游戏世界
     * @param tool   执行动作使用的物品
     */
    void execute(Player player, GameWorld world, ItemStack tool);

    /**
     * 是否需要方向选择。
     * 返回 true 时，玩家选择此动作后需按方向键指定目标方向，
     * 然后调用 {@link #executeDirection}。
     *
     * <p>默认返回 false。子类可覆写。
     */
    default boolean needsDirection() { return false; }

    /**
     * 是否需要方向选择（带上下文）。
     * 默认委托给无参版本。需要动态判断的场景（如根据玩家脚下是否有目标）
     * 可覆写此方法。
     *
     * @param player 执行动作的玩家
     * @param world  游戏世界
     * @return true 如果需要选择方向
     */
    default boolean needsDirection(Player player, GameWorld world) {
        return needsDirection();
    }

    /**
     * 带方向执行动作（仅当 {@link #needsDirection()} 返回 true 时调用）。
     *
     * @param player 执行动作的玩家
     * @param world  游戏世界
     * @param tool   执行动作使用的物品
     * @param dx     方向 X（-1/0/1）
     * @param dy     方向 Y（-1/0/1）
     */
    default void executeDirection(Player player, GameWorld world,
                                  ItemStack tool, int dx, int dy) {}
}
