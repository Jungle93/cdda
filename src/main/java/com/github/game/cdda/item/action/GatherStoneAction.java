package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.registry.ItemRegistry;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

/**
 * 采集石头动作。
 *
 * <p>从石质地面（{@link TileType#STONE} 或 {@link TileType#ROCK}）上采集石头。
 * 需要选择方向（目标瓦片必须是石质地表）。
 *
 * <p>绑定标签："gathering"。
 */
public class GatherStoneAction implements ItemAction {

    @Override
    public String getName() {
        return "采集石头";
    }

    @Override
    public String getDescription() {
        return "从石质地面上采集石头";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return isStoneTile(cm, px, py - 1)
                || isStoneTile(cm, px, py + 1)
                || isStoneTile(cm, px - 1, py)
                || isStoneTile(cm, px + 1, py)
                || isStoneTile(cm, px, py); // 脚下也可以
    }

    /** 检查瓦片是否为石质地表 */
    private boolean isStoneTile(ChunkManager cm, int x, int y) {
        TileType tile = cm.getTile(x, y);
        return tile == TileType.STONE || tile == TileType.ROCK;
    }

    @Override
    public void execute(Player player, GameWorld world, ItemStack tool) {
        // 需要方向选择
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

        if (tile != TileType.STONE && tile != TileType.ROCK) {
            GameLog.getInstance().log("那个方向没有可以采集的石头");
            return;
        }

        // 采集 1-2 块石头
        ItemType stoneType = ItemRegistry.getByName("stone");
        if (stoneType == null) {
            GameLog.getInstance().log("石头物品未定义");
            return;
        }

        int count = 1 + (int) (Math.random() * 2); // 1-2
        ItemStack stoneStack = new ItemStack(stoneType, count);

        if (player.getInventory().addItem(stoneStack)) {
            GameLog.getInstance().log(String.format("采集了 %d 块石头", count));
        } else {
            GameLog.getInstance().log("背包已满，无法放入石头");
            return;
        }

        // 消耗游戏时间
        world.getTurnManager().addAction(player, Constants.PICKUP_BASE_TIME);
        world.getTurnManager().processRound();
    }
}
