package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.creature.Creature;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.log.GameLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 追踪动作 — 探查周围区域的动物踪迹。
 *
 * <p>使用带有 "tracking" 标签的物品时，扫描 {@link Constants#HUNT_TRACK_RANGE} 范围内的所有动物，
 * 在日志中显示方向和距离信息（足迹提示）。
 *
 * <p>不需要方向选择 — 直接以玩家为中心扫描全方向。
 *
 * <p>绑定标签："tracking"。
 */
public class TrackingAction implements ItemAction {

    @Override
    public String getName() {
        return "追踪";
    }

    @Override
    public String getDescription() {
        return "探查周围 " + Constants.HUNT_TRACK_RANGE + " 格内的动物踪迹";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        // 只要附近有活的动物就可以追踪
        return !findNearbyAnimals(player, world).isEmpty();
    }

    @Override
    public void execute(Player player, GameWorld world, ItemStack tool) {
        List<AnimalInfo> animals = findNearbyAnimals(player, world);

        if (animals.isEmpty()) {
            GameLog.getInstance().log("仔细观察周围...没有发现任何动物的踪迹");
            world.getTurnManager().addAction(player, Constants.MOVE_BASE_TIME);
            return;
        }

        // 按距离排序（近的优先显示）
        animals.sort((a, b) -> a.distance - b.distance);

        // 显示发现结果
        GameLog.getInstance().log(String.format("仔细观察周围...发现了 %d 处动物踪迹：", animals.size()));

        int shown = 0;
        for (AnimalInfo info : animals) {
            if (shown >= 5) {
                GameLog.getInstance().log(String.format("  ...还有其他 %d 处踪迹", animals.size() - shown));
                break;
            }
            String direction = getDirectionName(info.dx, info.dy);
            String trackDesc = getTrackDescription(info.animal);
            GameLog.getInstance().log(String.format("  %s方向 %d 格 — %s",
                    direction, info.distance, trackDesc));
            shown++;
        }

        // 消耗游戏时间（追踪需要专注）
        world.getTurnManager().addAction(player, Constants.ATTACK_BASE_TIME);
        world.getTurnManager().processRound();
    }

    @Override
    public boolean needsDirection() {
        return false; // 全方向扫描
    }

    /** 扫描周围范围内的所有活体动物 */
    private List<AnimalInfo> findNearbyAnimals(Player player, GameWorld world) {
        List<AnimalInfo> result = new ArrayList<>();
        int px = player.getTileX();
        int py = player.getTileY();
        int range = Constants.HUNT_TRACK_RANGE;

        for (Creature creature : world.getCreatureManager().getCreatures()) {
            if (!(creature instanceof Animal animal)) continue;
            if (!animal.isAlive()) continue;

            int dx = animal.getTileX() - px;
            int dy = animal.getTileY() - py;
            int dist = Math.abs(dx) + Math.abs(dy); // 曼哈顿距离

            if (dist <= range && dist > 0) {
                result.add(new AnimalInfo(animal, dx, dy, dist));
            }
        }
        return result;
    }

    /** 获取方向名称（八方位） */
    private String getDirectionName(int dx, int dy) {
        if (dx == 0 && dy < 0) return "北";
        if (dx == 0 && dy > 0) return "南";
        if (dx < 0 && dy == 0) return "西";
        if (dx > 0 && dy == 0) return "东";
        if (dx < 0 && dy < 0) return "西北";
        if (dx > 0 && dy < 0) return "东北";
        if (dx < 0 && dy > 0) return "西南";
        if (dx > 0 && dy > 0) return "东南";
        return "附近";
    }

    /** 获取足迹描述（根据动物类型和状态） */
    private String getTrackDescription(Animal animal) {
        String name = "未知动物";
        if (animal.getDefinition() != null) {
            name = animal.getDefinition().name;
        }

        // 根据 HP 状态给出不同提示
        int hpPercent = animal.getMaxHp() > 0
                ? (animal.getHp() * 100 / animal.getMaxHp())
                : 100;

        if (hpPercent < 30) {
            return name + "（受伤严重，血迹斑斑）";
        } else if (hpPercent < 70) {
            return name + "（留有足迹）";
        } else {
            return name + "（新鲜足迹）";
        }
    }

    /** 动物信息内部记录 */
    private record AnimalInfo(Animal animal, int dx, int dy, int distance) {}
}
