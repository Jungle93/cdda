package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

/**
 * 整地动作 — 将普通地面翻耕为农田。
 *
 * <p>可翻耕的地面类型：{@link TileType#DIRT}、{@link TileType#GRASS}、{@link TileType#DEAD_GRASS}。
 * 石地和水面无法翻耕。翻耕后变为 {@link TileType#FARMLAND}。
 *
 * <p>绑定标签："tilling"。
 */
public class PrepareSoilAction implements ItemAction {

    @Override
    public String getName() {
        return "翻耕土地";
    }

    @Override
    public String getDescription() {
        return "将泥地/草地翻耕为农田，用于种植作物";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return isTillable(cm, px, py - 1)
                || isTillable(cm, px, py + 1)
                || isTillable(cm, px - 1, py)
                || isTillable(cm, px + 1, py)
                || isTillable(cm, px, py);
    }

    /** 检查瓦片是否可以翻耕 */
    private boolean isTillable(ChunkManager cm, int x, int y) {
        TileType tile = cm.getTile(x, y);
        return tile == TileType.DIRT
                || tile == TileType.GRASS
                || tile == TileType.DEAD_GRASS;
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

        if (!isTillable(cm, tx, ty)) {
            GameLog.getInstance().log("那个方向的土地无法翻耕");
            return;
        }

        // 检查是否有植被覆盖（如树/灌木等）
        if (cm.getVegetation(tx, ty) != null) {
            GameLog.getInstance().log("先清除地面的植被再翻耕");
            return;
        }

        // 翻耕：将瓦片改为 FARMLAND
        cm.setTile(tx, ty, TileType.FARMLAND);
        cm.clearVegetation(tx, ty);

        // 消耗游戏时间
        world.getTurnManager().addAction(player, Constants.CHOP_BASE_TIME);
        world.getTurnManager().processRound();

        GameLog.getInstance().log("翻耕了一小块土地");
    }
}
