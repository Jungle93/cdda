package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.item.GroundItem;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.ItemRegistry;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.ItemType;
import com.github.game.cdda.log.GameLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 解剖动作。
 *
 * <p>使用切割类工具（"cutting" 标签）对动物尸体进行解剖，
 * 获取肉块、骨头、皮革等产物。
 *
 * <p>优先级：
 * <ol>
 *   <li>玩家当前瓦片：1 个尸体直接解剖，多个则循环选择</li>
 *   <li>当前瓦片没有尸体时，进入方向选择（检查四邻接瓦片）</li>
 * </ol>
 *
 * <p>绑定标签："cutting"。
 */
public class ButcherAction implements ItemAction {

    private static final Random RANDOM = new Random();

    /**
     * 尸体 → 解剖产物映射表。
     * 每个条目：物品名称 → [概率, 最小数量, 最大数量]
     */
    private static final Map<String, List<ButcherProduct>> BUTCHER_TABLE = new HashMap<>();

    static {
        // 鹿
        addProduct("deer_corpse", "venison_raw", 1.0, 2, 3);
        addProduct("deer_corpse", "deer_hide", 1.0, 1, 1);
        addProduct("deer_corpse", "bone", 0.9, 1, 2);
        addProduct("deer_corpse", "antler", 0.5, 0, 1);

        // 兔子
        addProduct("rabbit_corpse", "rabbit_meat_raw", 1.0, 1, 1);
        addProduct("rabbit_corpse", "rabbit_pelt", 1.0, 1, 1);
        addProduct("rabbit_corpse", "bone", 0.5, 0, 1);

        // 野猪
        addProduct("boar_corpse", "boar_meat_raw", 1.0, 3, 5);
        addProduct("boar_corpse", "boar_hide", 1.0, 1, 1);
        addProduct("boar_corpse", "bone", 0.9, 1, 3);
        addProduct("boar_corpse", "boar_tusk", 0.7, 1, 2);

        // 狼
        addProduct("wolf_corpse", "wolf_meat_raw", 1.0, 2, 3);
        addProduct("wolf_corpse", "wolf_pelt", 1.0, 1, 1);
        addProduct("wolf_corpse", "bone", 0.9, 1, 2);
        addProduct("wolf_corpse", "wolf_fang", 0.6, 1, 2);

        // 狐狸
        addProduct("fox_corpse", "wolf_meat_raw", 1.0, 1, 2); // 狐狸肉用狼肉代替
        addProduct("fox_corpse", "fox_pelt", 1.0, 1, 1);
        addProduct("fox_corpse", "bone", 0.7, 0, 1);

        // 獾
        addProduct("badger_corpse", "badger_meat_raw", 1.0, 1, 2);
        addProduct("badger_corpse", "badger_fur", 1.0, 1, 1);
        addProduct("badger_corpse", "bone", 0.8, 1, 2);

        // 野兔
        addProduct("hare_corpse", "hare_meat_raw", 1.0, 1, 1);
        addProduct("hare_corpse", "hare_pelt", 1.0, 1, 1);
        addProduct("hare_corpse", "bone", 0.5, 0, 1);

        // 狍子
        addProduct("roe_deer_corpse", "roe_venison_raw", 1.0, 2, 3);
        addProduct("roe_deer_corpse", "deer_hide", 1.0, 1, 1); // 用鹿皮代替
        addProduct("roe_deer_corpse", "bone", 0.9, 1, 2);

        // 盘羊
        addProduct("mouflon_corpse", "mouflon_meat_raw", 1.0, 3, 4);
        addProduct("mouflon_corpse", "mouflon_hide", 1.0, 1, 1);
        addProduct("mouflon_corpse", "bone", 0.9, 1, 3);

        // 松鼠
        addProduct("squirrel_corpse", "squirrel_meat_raw", 1.0, 1, 1);
        addProduct("squirrel_corpse", "rabbit_pelt", 0.8, 1, 1); // 用小皮代替
        addProduct("squirrel_corpse", "bone", 0.4, 0, 1);
    }

    /** 当前瓦片有多个尸体时的循环选择索引 */
    private int cycleIndex = 0;

    /** 解剖产物定义 */
    private static class ButcherProduct {
        final String itemId;
        final double chance;
        final int minCount;
        final int maxCount;

        ButcherProduct(String itemId, double chance, int minCount, int maxCount) {
            this.itemId = itemId;
            this.chance = chance;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }
    }

    /** 添加产物到映射表 */
    private static void addProduct(String corpseName, String itemId,
                                   double chance, int minCount, int maxCount) {
        BUTCHER_TABLE.computeIfAbsent(corpseName, k -> new ArrayList<>())
                .add(new ButcherProduct(itemId, chance, minCount, maxCount));
    }

    @Override
    public String getName() {
        return "解剖";
    }

    @Override
    public String getDescription() {
        return "解剖动物尸体";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        GroundItemManager gim = world.getGroundItemManager();
        int px = player.getTileX();
        int py = player.getTileY();
        // 当前瓦片有尸体优先
        if (hasCorpse(gim, px, py)) return true;
        // 四邻接瓦片也有尸体
        return hasCorpse(gim, px, py - 1)
                || hasCorpse(gim, px, py + 1)
                || hasCorpse(gim, px - 1, py)
                || hasCorpse(gim, px + 1, py);
    }

    /** 检查指定瓦片是否有带 "corpse" 标签的地面物品 */
    private boolean hasCorpse(GroundItemManager gim, int x, int y) {
        for (GroundItem gi : gim.getItemsAt(x, y)) {
            if (gi.getItemStack().getType().getTags().contains("corpse")) {
                return true;
            }
        }
        return false;
    }

    /** 收集指定瓦片上的所有尸体 */
    private List<GroundItem> getCorpses(GroundItemManager gim, int x, int y) {
        List<GroundItem> result = new ArrayList<>();
        for (GroundItem gi : gim.getItemsAt(x, y)) {
            if (gi.getItemStack().getType().getTags().contains("corpse")) {
                result.add(gi);
            }
        }
        return result;
    }

    @Override
    public void execute(Player player, GameWorld world, ItemStack tool) {
        // 当 needsDirection(player, world) == false 时调用，
        // 即当前瓦片有尸体，直接解剖，无需方向选择。
        GroundItemManager gim = world.getGroundItemManager();
        int px = player.getTileX();
        int py = player.getTileY();

        List<GroundItem> corpses = getCorpses(gim, px, py);
        if (corpses.isEmpty()) {
            GameLog.getInstance().log("这里没有可解剖的尸体");
            return;
        }

        if (corpses.size() == 1) {
            // 只有一具尸体，直接解剖
            doButcher(corpses.get(0), px, py, gim);
            cycleIndex = 0;
        } else {
            // 多具尸体：按循环索引选择，每次使用后重置
            GroundItem target = corpses.get(cycleIndex % corpses.size());
            doButcher(target, px, py, gim);
            cycleIndex = 0;
        }
        consumeTurn(world);
    }

    @Override
    public boolean needsDirection(Player player, GameWorld world) {
        // 当前瓦片有尸体时，直接解剖，无需选择方向
        GroundItemManager gim = world.getGroundItemManager();
        return !hasCorpse(gim, player.getTileX(), player.getTileY());
    }

    @Override
    public void executeDirection(Player player, GameWorld world,
                                 ItemStack tool, int dx, int dy) {
        // 当 needsDirection(player, world) == true 时调用，
        // 即当前瓦片没有尸体，按方向查找邻接瓦片。
        GroundItemManager gim = world.getGroundItemManager();
        int tx = player.getTileX() + dx;
        int ty = player.getTileY() + dy;

        GroundItem corpseItem = null;
        for (GroundItem gi : gim.getItemsAt(tx, ty)) {
            if (gi.getItemStack().getType().getTags().contains("corpse")) {
                corpseItem = gi;
                break;
            }
        }

        if (corpseItem == null) {
            GameLog.getInstance().log("那个方向没有可解剖的尸体");
            return;
        }

        doButcher(corpseItem, tx, ty, gim);
        consumeTurn(world);
    }

    /**
     * 执行解剖：消耗尸体，按产物表生成地面物品。
     *
     * @param corpseItem 目标尸体
     * @param tileX      尸体所在瓦片 X（产物掉落于此）
     * @param tileY      尸体所在瓦片 Y
     * @param gim        地面物品管理器
     */
    private void doButcher(GroundItem corpseItem, int tileX, int tileY,
                           GroundItemManager gim) {
        String corpseName = corpseItem.getItemStack().getType().getName();
        List<ButcherProduct> products = BUTCHER_TABLE.get(corpseName);

        if (products == null || products.isEmpty()) {
            GameLog.getInstance().log("不知道如何解剖这种动物");
            return;
        }

        // 消耗尸体
        gim.removeGroundItem(corpseItem);

        // 生成产物
        int totalDrops = 0;
        for (ButcherProduct product : products) {
            if (RANDOM.nextDouble() > product.chance) continue;

            int count = product.minCount;
            if (product.maxCount > product.minCount) {
                count += RANDOM.nextInt(product.maxCount - product.minCount + 1);
            }
            if (count <= 0) continue;

            ItemType itemType = ItemRegistry.getByName(product.itemId);
            if (itemType == null) continue;

            ItemStack stack = new ItemStack(itemType, count);
            gim.dropItem(stack, tileX, tileY);
            totalDrops++;
        }

        if (totalDrops > 0) {
            GameLog.getInstance().log(String.format("你解剖了尸体，获得了 %d 种产物", totalDrops));
        } else {
            GameLog.getInstance().log("解剖失败，没有获得任何有用的东西");
        }
    }

    /** 消耗回合时间并处理回合 */
    private void consumeTurn(GameWorld world) {
        world.getTurnManager().addAction(world.getPlayer(), Constants.CHOP_BASE_TIME);
        world.getTurnManager().processRound();
    }
}
