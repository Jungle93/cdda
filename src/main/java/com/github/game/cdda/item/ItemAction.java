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
}
