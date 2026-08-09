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
 * <p>检查玩家 4 个相邻瓦片，若有树木（{@link TileType#TREE}）则将其变为草地，
 * 消耗 {@link Constants#CHOP_BASE_TIME} 回合时间。
 *
 * <p>绑定标签："chopping"。
 */
public class ChopTreeAction implements ItemAction {

    /** 四方向偏移：上、下、左、右 */
    private static final int[][] DIRECTIONS = {
            {0, -1}, {0, 1}, {-1, 0}, {1, 0}
    };

    @Override
    public String getName() {
        return "砍树";
    }

    @Override
    public String getDescription() {
        return "砍伐相邻的树木";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        return findAdjacentTree(player, world) != null;
    }

    @Override
    public void execute(Player player, GameWorld world, ItemStack tool) {
        ChunkManager chunkManager = world.getChunkManager();
        int[] treePos = findAdjacentTree(player, world);

        if (treePos == null) {
            GameLog.getInstance().log("附近没有可以砍伐的树木");
            return;
        }

        // 将树木瓦片替换为草地
        chunkManager.setTile(treePos[0], treePos[1], TileType.GRASS);

        // 消耗回合时间
        world.getTurnManager().addAction(player, Constants.CHOP_BASE_TIME);
        world.getTurnManager().processRound();

        GameLog.getInstance().log("你砍倒了一棵树");
    }

    /**
     * 查找玩家 adjacent 的树木瓦片。
     *
     * @return 树木瓦片的世界坐标 [x, y]，若无可砍伐树木返回 null
     */
    private int[] findAdjacentTree(Player player, GameWorld world) {
        ChunkManager chunkManager = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();

        for (int[] dir : DIRECTIONS) {
            int tx = px + dir[0];
            int ty = py + dir[1];
            TileType tile = chunkManager.getTile(tx, ty);
            if (tile == TileType.TREE) {
                return new int[]{tx, ty};
            }
        }
        return null;
    }
}
