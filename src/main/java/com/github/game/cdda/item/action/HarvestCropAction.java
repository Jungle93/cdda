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
import com.github.game.cdda.world.vegetation.GrowthStage;
import com.github.game.cdda.world.vegetation.VegetationDefinition;
import com.github.game.cdda.world.vegetation.VegetationRegistry;
import com.github.game.cdda.world.vegetation.VegetationState;

import java.util.Random;

/**
 * 收获动作 — 收割成熟的农作物。
 *
 * <p>目标瓦片必须有已成熟（{@link GrowthStage#MATURE}）的作物。
 * 收获后根据 {@link VegetationDefinition} 的掉落表生成物品，
 * 并清除植被。
 *
 * <p>使用收割工具（带有"harvesting"或"cutting"标签）可获得种子；
 * 徒手收获则不掉落种子。
 *
 * <p>绑定标签："harvesting"。
 */
public class HarvestCropAction implements ItemAction {

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "收获";
    }

    @Override
    public String getDescription() {
        return "收割成熟的农作物";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return hasMatureCrop(cm, px, py - 1)
                || hasMatureCrop(cm, px, py + 1)
                || hasMatureCrop(cm, px - 1, py)
                || hasMatureCrop(cm, px + 1, py)
                || hasMatureCrop(cm, px, py);
    }

    /** 检查瓦片是否有成熟的作物 */
    private boolean hasMatureCrop(ChunkManager cm, int x, int y) {
        String vegId = cm.getVegetation(x, y);
        if (vegId == null) return false;

        VegetationDefinition def = VegetationRegistry.getById(vegId);
        if (def == null || def.type != com.github.game.cdda.world.vegetation.VegetationType.CROP) {
            return false;
        }

        VegetationState state = cm.getGrowthState(x, y);
        return state != null && state.stage == GrowthStage.MATURE;
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

        String vegId = cm.getVegetation(tx, ty);
        if (vegId == null) {
            GameLog.getInstance().log("这里没有作物");
            return;
        }

        VegetationDefinition def = VegetationRegistry.getById(vegId);
        if (def == null || def.type != com.github.game.cdda.world.vegetation.VegetationType.CROP) {
            GameLog.getInstance().log("这里没有可收获的作物");
            return;
        }

        VegetationState state = cm.getGrowthState(tx, ty);
        if (state == null || state.stage != GrowthStage.MATURE) {
            GameLog.getInstance().log("作物还没有成熟");
            return;
        }

        // 检查是否有收割工具（影响种子掉落）
        boolean hasHarvestingTool = hasHarvestingTool(player);

        // 生成掉落物
        int dropCount = generateHarvestDrops(def, world, tx, ty, hasHarvestingTool);

        // 清除植被，恢复耕地
        cm.setTile(tx, ty, TileType.FARMLAND);
        cm.clearVegetation(tx, ty);

        // 消耗游戏时间
        world.getTurnManager().addAction(player, Constants.PICKUP_BASE_TIME);
        world.getTurnManager().processRound();

        if (dropCount > 0) {
            GameLog.getInstance().log(String.format("收获了%s，获得 %d 件物品%s",
                    def.name, dropCount, hasHarvestingTool ? "" : "（无收割工具，未获得种子）"));
        } else {
            GameLog.getInstance().log(String.format("收获了%s", def.name));
        }
    }

    /** 检查玩家是否有收割工具（"harvesting"或"cutting"标签） */
    private boolean hasHarvestingTool(Player player) {
        for (ItemStack stack : player.getInventory().getItems()) {
            if (stack != null && (stack.getType().hasTag("cutting")
                    || stack.getType().hasTag("harvesting"))) {
                return true;
            }
        }
        return false;
    }

    /** 生成收获掉落物 */
    private int generateHarvestDrops(VegetationDefinition def, GameWorld world,
                                     int tileX, int tileY, boolean hasHarvestingTool) {
        if (def.drops == null || def.drops.isEmpty()) return 0;

        var gim = world.getGroundItemManager();
        int totalDrops = 0;

        for (VegetationDefinition.Drop drop : def.drops) {
            // 无收割工具时不掉落种子（物品名包含 "seed" 的跳过）
            if (!hasHarvestingTool && drop.itemId.contains("seed")) {
                continue;
            }

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
}
