package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

/**
 * 砍树动作。
 *
 * <p>需要玩家按方向键选择砍伐方向。目标方向必须有树木（{@link TileType#TREE}），
 * 否则无法执行。成功砍伐后将树木变为草地，
 * 消耗 {@link Constants#CHOP_BASE_TIME} 回合时间。
 *
 * <p>绑定标签："chopping"。
 */
public class ChopTreeAction implements ItemAction {

    @Override
    public String getName() {
        return "砍树";
    }

    @Override
    public String getDescription() {
        return "选择方向砍伐树木";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        // 至少有一个相邻方向有树木即可（进入方向选择的前提）
        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return cm.getTile(px, py - 1) == TileType.TREE
                || cm.getTile(px, py + 1) == TileType.TREE
                || cm.getTile(px - 1, py) == TileType.TREE
                || cm.getTile(px + 1, py) == TileType.TREE;
    }

    @Override
    public void execute(Player player, GameWorld world, ItemStack tool) {
        // 不应直接调用——此动作需要方向选择
    }

    @Override
    public boolean needsDirection() {
        return true;
    }

    @Override
    public void executeDirection(Player player, GameWorld world,
                                 ItemStack tool, int dx, int dy) {
        ChunkManager cm = world.getChunkManager();
        int tx = player.getTileX() + dx;
        int ty = player.getTileY() + dy;
        TileType tile = cm.getTile(tx, ty);

        if (tile != TileType.TREE) {
            GameLog.getInstance().log("那个方向没有树木");
            return;
        }

        // 将树木瓦片替换为草地
        cm.setTile(tx, ty, TileType.GRASS);

        // 消耗回合时间
        world.getTurnManager().addAction(player, Constants.CHOP_BASE_TIME);
        world.getTurnManager().processRound();

        GameLog.getInstance().log("你砍倒了一棵树");
    }
}
