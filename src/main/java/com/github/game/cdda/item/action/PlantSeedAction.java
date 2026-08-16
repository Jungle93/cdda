package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.cdda.world.vegetation.VegetationDefinition;
import com.github.game.cdda.world.vegetation.VegetationRegistry;
import com.github.game.cdda.world.vegetation.VegetationState;

/**
 * 播种动作 — 在农田上种植作物种子。
 *
 * <p>需要目标瓦片为 {@link TileType#FARMLAND}。消耗背包中的种子物品，
 * 在目标瓦片创建作物植被并初始化生长状态。
 *
 * <p>种子到作物的映射通过 {@link com.github.game.cdda.item.model.ItemType#getProducesCrop()}
 * 数据驱动：种子 JSON 中的 {@code producesCrop} 字段指定对应的植被物种 ID。
 *
 * <p>绑定标签："sowing"。
 */
public class PlantSeedAction implements ItemAction {

    @Override
    public String getName() {
        return "播种";
    }

    @Override
    public String getDescription() {
        return "在耕地上播种作物种子";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        // 检查是否有种子，且附近有农田
        if (!hasSeeds(player)) return false;

        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return isFarmland(cm, px, py - 1)
                || isFarmland(cm, px, py + 1)
                || isFarmland(cm, px - 1, py)
                || isFarmland(cm, px + 1, py)
                || isFarmland(cm, px, py);
    }

    /** 检查背包中是否有可播种的种子 */
    private boolean hasSeeds(Player player) {
        for (ItemStack stack : player.getInventory().getItems()) {
            if (stack != null && stack.getType().getProducesCrop() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isFarmland(ChunkManager cm, int x, int y) {
        return cm.getTile(x, y) == TileType.FARMLAND;
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

        if (cm.getTile(tx, ty) != TileType.FARMLAND) {
            GameLog.getInstance().log("只能在耕地上播种");
            return;
        }

        // 检查是否已有作物
        if (cm.getVegetation(tx, ty) != null) {
            GameLog.getInstance().log("这块地已经种了作物");
            return;
        }

        // 找到背包中的种子（通过 producesCrop 字段数据驱动）
        String cropSpecies = null;
        ItemStack seedStack = null;
        for (ItemStack stack : player.getInventory().getItems()) {
            if (stack != null && stack.getType().getProducesCrop() != null) {
                cropSpecies = stack.getType().getProducesCrop();
                seedStack = stack;
                break;
            }
        }

        if (cropSpecies == null) {
            GameLog.getInstance().log("没有可播种的种子");
            return;
        }

        // 从植被定义获取作物显示名
        VegetationDefinition vegDef = VegetationRegistry.getById(cropSpecies);
        String cropName = (vegDef != null) ? vegDef.name : cropSpecies;

        // 消耗种子
        if (seedStack.getCount() > 1) {
            seedStack.setCount(seedStack.getCount() - 1);
        } else {
            var items = player.getInventory().getItems();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == seedStack) {
                    player.getInventory().removeItem(i);
                    break;
                }
            }
        }

        // 创建植被（覆盖层 CROP_PLANT + 物种数据）
        cm.setTile(tx, ty, TileType.CROP_PLANT);
        long gameTime = world.getGameTime().getTotalSeconds();
        VegetationState state = new VegetationState(cropSpecies, gameTime);
        cm.setVegetation(tx, ty, cropSpecies, state);

        // 消耗游戏时间
        world.getTurnManager().addAction(player, Constants.PICKUP_BASE_TIME);
        world.getTurnManager().processRound();

        GameLog.getInstance().log(String.format("播种了%s", cropName));
    }
}
