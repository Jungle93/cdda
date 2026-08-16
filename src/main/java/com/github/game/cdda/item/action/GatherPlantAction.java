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
import com.github.game.cdda.world.vegetation.VegetationDefinition;
import com.github.game.cdda.world.vegetation.VegetationRegistry;

import java.util.Random;

/**
 * 采集植物动作 — 采集草丛、芦苇等低矮植被。
 *
 * <p>可采集的瓦片类型：
 * <ul>
 *   <li>{@link TileType#TALL_GRASS} — 高草（掉落草捆等）</li>
 *   <li>{@link TileType#REEDS} — 芦苇（掉落芦苇捆等）</li>
 *   <li>{@link TileType#FLOWER} — 花（可能掉落花或种子）</li>
 *   <li>{@link TileType#DEAD_GRASS} — 枯草（掉落少量干草）</li>
 * </ul>
 *
 * <p>采集后恢复地面层瓦片。与砍树不同，这是即时动作（不需要多回合）。
 *
 * <p>绑定标签："foraging"。
 */
public class GatherPlantAction implements ItemAction {

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "采集";
    }

    @Override
    public String getDescription() {
        return "采集草丛、芦苇等低矮植被";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return isGatherable(cm, px, py - 1)
                || isGatherable(cm, px, py + 1)
                || isGatherable(cm, px - 1, py)
                || isGatherable(cm, px + 1, py)
                || isGatherable(cm, px, py);
    }

    /** 检查瓦片是否为可采集的低矮植被 */
    private boolean isGatherable(ChunkManager cm, int x, int y) {
        TileType tile = cm.getTile(x, y);
        return tile == TileType.TALL_GRASS
                || tile == TileType.REEDS
                || tile == TileType.FLOWER
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

        if (!isGatherable(cm, tx, ty)) {
            GameLog.getInstance().log("那个方向没有可采集的植被");
            return;
        }

        // 查询植被物种并生成掉落物
        String speciesId = cm.getVegetation(tx, ty);
        int dropCount = generateDrops(speciesId, world, tx, ty);

        // 恢复地面层瓦片
        TileType groundTile = cm.getGroundTile(tx, ty);
        cm.setTile(tx, ty, groundTile != null ? groundTile : TileType.GRASS);
        cm.clearVegetation(tx, ty);

        // 消耗游戏时间
        world.getTurnManager().addAction(player, Constants.PICKUP_BASE_TIME);
        world.getTurnManager().processRound();

        // 日志
        String plantName = getPlantName(tile);
        if (dropCount > 0) {
            GameLog.getInstance().log(String.format("采集了%s，获得 %d 件物品", plantName, dropCount));
        } else {
            GameLog.getInstance().log(String.format("采集了%s，但没有找到有用的材料", plantName));
        }
    }

    /** 根据植被物种生成掉落物 */
    private int generateDrops(String speciesId, GameWorld world, int tileX, int tileY) {
        VegetationDefinition def = (speciesId != null)
                ? VegetationRegistry.getById(speciesId)
                : null;

        if (def == null || def.drops == null || def.drops.isEmpty()) {
            return 0;
        }

        var gim = world.getGroundItemManager();
        int totalDrops = 0;

        for (VegetationDefinition.Drop drop : def.drops) {
            if (RANDOM.nextDouble() > drop.chance) continue;

            int count = drop.minCount + RANDOM.nextInt(
                    Math.max(1, drop.maxCount - drop.minCount + 1));
            if (count <= 0) continue;

            ItemType itemType = ItemRegistry.getByName(drop.itemId);
            if (itemType == null) continue;

            gim.dropItem(new ItemStack(itemType, count), tileX, tileY);
            totalDrops++;
        }
        return totalDrops;
    }

    /** 获取植被显示名称 */
    private String getPlantName(TileType tile) {
        if (tile == TileType.TALL_GRASS) return "高草";
        if (tile == TileType.REEDS) return "芦苇";
        if (tile == TileType.FLOWER) return "花";
        if (tile == TileType.DEAD_GRASS) return "枯草";
        return "植物";
    }
}
