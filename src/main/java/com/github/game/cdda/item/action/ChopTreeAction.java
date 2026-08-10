package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.ItemRegistry;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.ItemType;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.cdda.world.vegetation.VegetationDefinition;
import com.github.game.cdda.world.vegetation.VegetationRegistry;

import java.util.Random;

/**
 * 砍树/砍灌木动作。
 *
 * <p>需要玩家按方向键选择砍伐方向。目标方向必须有树木（{@link TileType#TREE}）
 * 或灌木（{@link TileType#BUSH}），否则无法执行。
 *
 * <p>成功砍伐后：
 * <ul>
 *   <li>将植被瓦片替换为草地</li>
 *   <li>根据 {@link VegetationDefinition} 的掉落表生成物品</li>
 *   <li>消耗 {@link Constants#CHOP_BASE_TIME} 回合时间</li>
 * </ul>
 *
 * <p>绑定标签："chopping"。
 */
public class ChopTreeAction implements ItemAction {

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "砍伐";
    }

    @Override
    public String getDescription() {
        return "选择方向砍伐树木或灌木";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        // 至少有一个相邻方向有树木或灌木即可
        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return isVegetation(cm, px, py - 1)
                || isVegetation(cm, px, py + 1)
                || isVegetation(cm, px - 1, py)
                || isVegetation(cm, px + 1, py);
    }

    /** 检查瓦片是否为可砍伐的植被（树木或灌木） */
    private boolean isVegetation(ChunkManager cm, int x, int y) {
        TileType tile = cm.getTile(x, y);
        return tile == TileType.TREE || tile == TileType.BUSH;
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

        if (tile != TileType.TREE && tile != TileType.BUSH) {
            GameLog.getInstance().log("那个方向没有可砍伐的植被");
            return;
        }

        // 查询植被物种
        String speciesId = cm.getVegetation(tx, ty);
        VegetationDefinition vegDef = (speciesId != null)
                ? VegetationRegistry.getById(speciesId)
                : null;

        // 生成掉落物
        int dropCount = generateDrops(vegDef, world, tx, ty);

        // 将植被瓦片替换为草地
        cm.setTile(tx, ty, TileType.GRASS);
        cm.clearVegetation(tx, ty);

        // 消耗回合时间
        world.getTurnManager().addAction(player, Constants.CHOP_BASE_TIME);
        world.getTurnManager().processRound();

        // 日志
        String vegName = (vegDef != null) ? vegDef.name : (tile == TileType.TREE ? "树" : "灌木");
        if (dropCount > 0) {
            GameLog.getInstance().log(String.format("你砍倒了一棵%s，获得了 %d 件物品", vegName, dropCount));
        } else {
            GameLog.getInstance().log(String.format("你砍倒了一棵%s", vegName));
        }
    }

    /**
     * 根据植被定义生成掉落物。
     *
     * @param def    植被定义（可为 null）
     * @param world  游戏世界
     * @param tileX  掉落位置的瓦片 X
     * @param tileY  掉落位置的瓦片 Y
     * @return 实际掉落的物品堆数
     */
    private int generateDrops(VegetationDefinition def, GameWorld world, int tileX, int tileY) {
        if (def == null || def.drops == null || def.drops.isEmpty()) {
            // 无定义时使用默认掉落（少量树枝）
            return dropDefaultLoot(world, tileX, tileY);
        }

        GroundItemManager gim = world.getGroundItemManager();
        int totalDrops = 0;

        for (VegetationDefinition.Drop drop : def.drops) {
            // 按概率判定是否掉落
            if (RANDOM.nextDouble() > drop.chance) {
                continue;
            }

            // 随机数量
            int count = drop.minCount + RANDOM.nextInt(drop.maxCount - drop.minCount + 1);
            if (count <= 0) continue;

            // 查找物品类型
            ItemType itemType = ItemRegistry.getByName(drop.itemId);
            if (itemType == null) {
                continue;
            }

            // 创建物品堆并放置到地面
            ItemStack stack = new ItemStack(itemType, count);
            gim.dropItem(stack, tileX, tileY);
            totalDrops++;
        }

        return totalDrops;
    }

    /**
     * 默认掉落物（无植被定义时）。
     */
    private int dropDefaultLoot(GameWorld world, int tileX, int tileY) {
        GroundItemManager gim = world.getGroundItemManager();

        // 默认掉落 1-2 个树枝
        ItemType branchType = ItemRegistry.getByName("small_branch");
        if (branchType != null) {
            int count = 1 + RANDOM.nextInt(2);
            gim.dropItem(new ItemStack(branchType, count), tileX, tileY);
            return 1;
        }
        return 0;
    }
}
