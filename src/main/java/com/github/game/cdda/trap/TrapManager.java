package com.github.game.cdda.trap;

import com.github.game.cdda.Constants;
import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.registry.ItemRegistry;
import com.github.game.cdda.item.world.GroundItemManager;
import com.github.game.cdda.log.GameLog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 陷阱管理器 — 管理世界中所有已放置的陷阱。
 *
 * <p>职责：
 * <ul>
 *   <li>维护所有已放置陷阱的列表</li>
 *   <li>放置新陷阱（{@link #placeTrap}）</li>
 *   <li>检查动物是否踩中陷阱（{@link #checkTrapAt}）</li>
 *   <li>玩家收取陷阱（{@link #collectTrap}）</li>
 *   <li>查询某位置的陷阱（{@link #getTrapAt}）</li>
 * </ul>
 *
 * <p>陷阱以瓦片坐标定位，独立于 GroundItem 系统。
 * 放置陷阱时消耗对应的物品（snare_kit）。
 */
public class TrapManager {

    private static final Random RANDOM = new Random();

    /** 所有已放置的陷阱 */
    private final List<PlacedTrap> traps = new ArrayList<>();

    /** 地面物品管理器（用于生成捕获的尸体） */
    private final GroundItemManager groundItemManager;

    /** 生物管理器（用于移除被捕获的动物） */
    private final CreatureManager creatureManager;

    /**
     * 创建陷阱管理器。
     *
     * @param groundItemManager 地面物品管理器
     * @param creatureManager   生物管理器
     */
    public TrapManager(GroundItemManager groundItemManager, CreatureManager creatureManager) {
        this.groundItemManager = groundItemManager;
        this.creatureManager = creatureManager;
    }

    // ── 放置 ──

    /**
     * 在指定瓦片放置陷阱。
     *
     * @param trapType  陷阱类型 ID（如 "loop_snare"）
     * @param tileX     瓦片 X
     * @param tileY     瓦片 Y
     * @param gameTime  当前游戏时间（秒）
     * @return 放置的陷阱
     */
    public PlacedTrap placeTrap(String trapType, int tileX, int tileY, long gameTime) {
        // 检查该位置是否已有陷阱
        if (getTrapAt(tileX, tileY) != null) {
            GameLog.getInstance().log("这个位置已经有陷阱了");
            return null;
        }

        PlacedTrap trap = new PlacedTrap(trapType, tileX, tileY, gameTime);
        traps.add(trap);

        String trapName = getTrapDisplayName(trapType);
        GameLog.getInstance().log(String.format("在 (%d, %d) 设置了%s", tileX, tileY, trapName));
        return trap;
    }

    // ── 查询 ──

    /**
     * 获取指定瓦片上的陷阱（仅 ARMED 或 TRIGGERED 状态）。
     *
     * @param tileX 瓦片 X
     * @param tileY 瓦片 Y
     * @return 陷阱，或 null 如果没有
     */
    public PlacedTrap getTrapAt(int tileX, int tileY) {
        for (PlacedTrap trap : traps) {
            if (trap.getTileX() == tileX && trap.getTileY() == tileY) {
                return trap;
            }
        }
        return null;
    }

    /**
     * 获取所有已放置的陷阱（不可变视图）。
     */
    public List<PlacedTrap> getAllTraps() {
        return List.copyOf(traps);
    }

    /** 获取陷阱总数 */
    public int getTrapCount() {
        return traps.size();
    }

    // ── 捕获检测 ──

    /**
     * 检查指定瓦片的陷阱是否捕获了动物。
     * 由 AnimalAI 在动物移动后调用。
     *
     * @param tileX  动物所在瓦片 X
     * @param tileY  动物所在瓦片 Y
     * @param animal 踩到陷阱的动物
     */
    public void checkTrapAt(int tileX, int tileY, Animal animal) {
        PlacedTrap trap = getTrapAt(tileX, tileY);
        if (trap == null || trap.getState() != PlacedTrap.State.ARMED) return;

        // 按概率判定捕获
        double catchChance = getCatchChanceForTrap(trap.getTrapType(), animal);
        if (RANDOM.nextDouble() < catchChance) {
            // 捕获成功：动物死亡
            animal.killByPlayer(); // 标记为玩家击杀（陷阱捕获视为玩家猎物）
            trap.capture(animal);
            GameLog.getInstance().log(String.format("陷阱捕获了一只%s！",
                    getAnimalDisplayName(animal)));
        } else {
            // 捕获失败：动物惊走，陷阱可能空触发
            if (RANDOM.nextDouble() < 0.3) {
                trap.emptyTrigger();
                GameLog.getInstance().log("陷阱被触发了，但没有捕获到猎物");
            }
        }
    }

    // ── 收取 ──

    /**
     * 收取指定瓦片的陷阱。
     * 如果有捕获的猎物，生成对应的尸体物品到地面。
     *
     * @param tileX     瓦片 X
     * @param tileY     瓦片 Y
     * @param gameTime  当前游戏时间
     * @return true 如果成功收取
     */
    public boolean collectTrap(int tileX, int tileY, long gameTime) {
        PlacedTrap trap = getTrapAt(tileX, tileY);
        if (trap == null) {
            GameLog.getInstance().log("这里没有陷阱");
            return false;
        }

        // 如果有捕获的猎物，生成尸体物品
        if (trap.hasCapture()) {
            Animal captured = trap.getCapturedAnimal();
            String corpseItemId = getCorpseItemIdForAnimal(captured);
            if (corpseItemId != null) {
                ItemType corpseType = ItemRegistry.getByName(corpseItemId);
                if (corpseType != null) {
                    groundItemManager.dropItem(new ItemStack(corpseType, 1), tileX, tileY);
                }
            }
            // 从生物管理器中移除已捕获的动物
            creatureManager.removeCreature(captured);
        }

        // 移除陷阱
        traps.remove(trap);

        // 返还陷阱组件（有一定概率损坏）
        String returnedItemId = getReturnedItemId(trap.getTrapType());
        if (returnedItemId != null && RANDOM.nextDouble() < 0.8) {
            ItemType returnedType = ItemRegistry.getByName(returnedItemId);
            if (returnedType != null) {
                groundItemManager.dropItem(new ItemStack(returnedType, 1), tileX, tileY);
            }
        }

        String trapName = getTrapDisplayName(trap.getTrapType());
        if (trap.hasCapture()) {
            GameLog.getInstance().log(String.format("收取了%s，获得猎物", trapName));
        } else {
            GameLog.getInstance().log(String.format("收取了%s", trapName));
        }
        return true;
    }

    // ── 定期清理 ──

    /**
     * 清理超时或被破坏的陷阱。
     * 每 N 个游戏回合调用一次。
     *
     * @param gameTime 当前游戏时间
     */
    public void cleanup(long gameTime) {
        Iterator<PlacedTrap> it = traps.iterator();
        while (it.hasNext()) {
            PlacedTrap trap = it.next();
            // 陷阱超过 10000 游戏秒（约 2.8 游戏小时）后自然损坏
            if (gameTime - trap.getPlacedAtGameTime() > 10000) {
                it.remove();
            }
        }
    }

    // ── 辅助方法 ──

    /** 根据陷阱类型获取捕获概率 */
    private double getCatchChanceForTrap(String trapType, Animal animal) {
        double base = Constants.SNARE_CATCH_CHANCE;
        // 小型动物更容易捕获
        int hp = animal.getMaxHp();
        if (hp <= 10) {
            return base * 1.5;  // 小兔子、松鼠等：60%
        } else if (hp <= 30) {
            return base;         // 中型动物（狐狸等）：40%
        } else {
            return base * 0.3;  // 大型动物（鹿等）：12% — 圈套很难捕获大型动物
        }
    }

    /** 获取陷阱显示名称 */
    private String getTrapDisplayName(String trapType) {
        return switch (trapType) {
            case "loop_snare" -> "绳套陷阱";
            default -> "陷阱";
        };
    }

    /** 获取动物显示名称 */
    private String getAnimalDisplayName(Animal animal) {
        return animal.getLocalizedName();
    }

    /** 根据动物获取对应的尸体物品 ID */
    private String getCorpseItemIdForAnimal(Animal animal) {
        if (animal.getDefinition() == null) return null;
        return animal.getDefinition().id + "_corpse";
    }

    /** 收取陷阱时返还的物品 ID */
    private String getReturnedItemId(String trapType) {
        return switch (trapType) {
            case "loop_snare" -> "fiber_cord"; // 返还纤维绳（陷阱主体）
            default -> null;
        };
    }
}
