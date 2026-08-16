package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.world.GroundItem;
import com.github.game.cdda.item.world.GroundItemManager;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.trap.PlacedTrap;
import com.github.game.cdda.trap.TrapManager;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.util.List;

/**
 * 放置陷阱动作。
 *
 * <p>使用带有 "snaring" 标签的物品（如 snare_kit）在目标瓦片设置陷阱。
 * 目标瓦片必须是可通行的地面，且不能已有陷阱。
 *
 * <p>交互流程：
 * <ol>
 *   <li>如果脚下有已放置的陷阱 → 直接收取（无需方向）</li>
 *   <li>否则 → 选择方向放置新陷阱</li>
 * </ol>
 *
 * <p>绑定标签："snaring"。
 */
public class SetSnareAction implements ItemAction {

    @Override
    public String getName() {
        return "设置陷阱";
    }

    @Override
    public String getDescription() {
        return "在地面放置陷阱或收取已放置的陷阱";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        TrapManager trapManager = world.getTrapManager();
        // 脚下有陷阱可收取，或者有空地可放置
        return trapManager.getTrapAt(player.getTileX(), player.getTileY()) != null
                || hasAdjacentPassable(player, world);
    }

    /** 检查是否有相邻可通行瓦片 */
    private boolean hasAdjacentPassable(Player player, GameWorld world) {
        ChunkManager cm = world.getChunkManager();
        int px = player.getTileX();
        int py = player.getTileY();
        return isPlaceable(cm, px, py - 1)
                || isPlaceable(cm, px, py + 1)
                || isPlaceable(cm, px - 1, py)
                || isPlaceable(cm, px + 1, py);
    }

    /** 检查瓦片是否可放置陷阱（可通行且无陷阱） */
    private boolean isPlaceable(ChunkManager cm, int x, int y) {
        TileType tile = cm.getTile(x, y);
        return tile != null && tile.isPassable();
    }

    @Override
    public void execute(Player player, GameWorld world, ItemStack tool) {
        // 检查脚下是否有陷阱 → 直接收取
        TrapManager trapManager = world.getTrapManager();
        PlacedTrap trap = trapManager.getTrapAt(player.getTileX(), player.getTileY());
        if (trap != null) {
            collectTrap(player, world, player.getTileX(), player.getTileY());
            return;
        }
        // 脚下无陷阱，需要选择方向
    }

    @Override
    public boolean needsDirection() {
        // 如果脚下有陷阱，不需要方向（直接收取）
        // 否则需要方向来放置
        return true;
    }

    @Override
    public void executeDirection(Player player, GameWorld world,
                                 ItemStack tool, int dx, int dy) {
        ChunkManager cm = world.getChunkManager();
        int tx = player.getTileX() + dx;
        int ty = player.getTileY() + dy;
        TrapManager trapManager = world.getTrapManager();

        // 检查目标瓦片
        TileType tile = cm.getTile(tx, ty);
        if (tile == null || !tile.isPassable()) {
            GameLog.getInstance().log("那个位置不能放置陷阱");
            return;
        }

        // 检查是否已有陷阱
        PlacedTrap existingTrap = trapManager.getTrapAt(tx, ty);
        if (existingTrap != null) {
            // 收取已有陷阱
            collectTrap(player, world, tx, ty);
            return;
        }

        // 消耗陷阱物品
        String trapType = getTrapTypeFromItem(tool);
        if (trapType == null) {
            GameLog.getInstance().log("需要陷阱组件才能设置");
            return;
        }

        // 从背包中消耗一个陷阱物品
        if (!consumeTrapItem(player, tool)) {
            GameLog.getInstance().log("没有足够的陷阱组件");
            return;
        }

        // 放置陷阱
        long gameTime = world.getGameTime().getTotalSeconds();
        trapManager.placeTrap(trapType, tx, ty, gameTime);

        // 消耗游戏时间
        world.getTurnManager().addAction(player, Constants.SNARE_PLACE_TIME);
        world.getTurnManager().processRound();
    }

    /** 收取陷阱 */
    private void collectTrap(Player player, GameWorld world, int tx, int ty) {
        long gameTime = world.getGameTime().getTotalSeconds();
        world.getTrapManager().collectTrap(tx, ty, gameTime);
        world.getTurnManager().addAction(player, Constants.PICKUP_BASE_TIME);
    }

    /** 根据物品确定陷阱类型 */
    private String getTrapTypeFromItem(ItemStack tool) {
        if (tool == null || tool.isEmpty()) return null;
        String name = tool.getType().getName();
        return switch (name) {
            case "snare_kit" -> "loop_snare";
            default -> null;
        };
    }

    /** 从背包中消耗一个陷阱物品 */
    private boolean consumeTrapItem(Player player, ItemStack tool) {
        if (tool == null || tool.isEmpty()) return false;
        // 减少堆叠数量
        if (tool.getCount() > 1) {
            tool.setCount(tool.getCount() - 1);
        } else {
            // 唯一物品或数量为1 → 从背包按索引移除
            var items = player.getInventory().getItems();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == tool) {
                    player.getInventory().removeItem(i);
                    break;
                }
            }
        }
        return true;
    }
}
