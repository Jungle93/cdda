package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.HydrationManager;
import com.github.game.cdda.MetabolismManager;
import com.github.game.cdda.Player;
import com.github.game.cdda.TurnManager;
import com.github.game.cdda.item.ConsumableType;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.PlayerInventory;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 进食/饮水界面。
 * 从背包中选择可消耗物品进行食用或饮用。
 *
 * <p>支持三种消耗类型：
 * <ul>
 *   <li>食物 (FOOD) — 补充热量（卡路里）</li>
 *   <li>水 (WATER) — 补充水分</li>
 *   <li>药品 (MEDICINE) — 回复生命值</li>
 * </ul>
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择物品</li>
 *   <li>←/→ — 调整消耗数量（仅可堆叠物品）</li>
 *   <li>Enter — 确认消耗</li>
 *   <li>Esc — 返回</li>
 * </ul>
 */
public class EatingScreen extends MenuScreen {

    private static final String TITLE = "进食";

    private final GameWorld world;
    private final Player player;
    private final PlayerInventory inventory;
    private final MetabolismManager metabolismManager;
    private final HydrationManager hydrationManager;
    private final TurnManager turnManager;

    /** 背包中可消耗物品的索引映射（列表索引 → 背包索引） */
    private int[] consumableIndices;
    /** 每项当前选定的消耗数量 */
    private int[] consumeCounts;

    public EatingScreen(GameEngine engine, GameWorld world) {
        super(engine);
        this.world = world;
        this.player = world.getPlayer();
        this.inventory = player.getInventory();
        this.metabolismManager = world.getMetabolismManager();
        this.hydrationManager = world.getHydrationManager();
        this.turnManager = world.getTurnManager();
        refreshConsumables();
    }

    /** 扫描背包，筛选可消耗物品并初始化数量数组 */
    private void refreshConsumables() {
        List<Integer> indices = new ArrayList<>();
        List<ItemStack> items = inventory.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != null && items.get(i).getType().isConsumable()) {
                indices.add(i);
            }
        }
        consumableIndices = indices.stream().mapToInt(Integer::intValue).toArray();
        consumeCounts = new int[consumableIndices.length];
        for (int i = 0; i < consumableIndices.length; i++) {
            ItemStack stack = inventory.getItem(consumableIndices[i]);
            // 不可堆叠物品只能消耗 1 个
            consumeCounts[i] = isStackable(stack.getType()) ? stack.getCount() : 1;
        }
        // 确保选中索引有效
        if (selectedIndex >= consumableIndices.length) {
            selectedIndex = Math.max(0, consumableIndices.length - 1);
        }
    }

    /** 当前选中物品是否可堆叠 */
    private boolean isCurrentStackable() {
        if (!hasConsumables()) return false;
        ItemStack stack = getSelectedStack();
        return stack != null && isStackable(stack.getType());
    }

    private boolean isStackable(com.github.game.cdda.item.ItemType type) {
        return type.getMaxStackSize() > 1 && !type.isUnique();
    }

    private boolean hasConsumables() {
        return consumableIndices.length > 0;
    }

    /** 获取当前选中的 ItemStack */
    private ItemStack getSelectedStack() {
        if (selectedIndex < 0 || selectedIndex >= consumableIndices.length) return null;
        return inventory.getItem(consumableIndices[selectedIndex]);
    }

    @Override
    protected int getItemCount() {
        return consumableIndices.length;
    }

    @Override
    public void onKeyPressed(int keyCode) {
        if (!hasConsumables()) {
            super.onKeyPressed(keyCode);
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_LEFT:
                if (isCurrentStackable() && consumeCounts[selectedIndex] > 1) {
                    consumeCounts[selectedIndex]--;
                }
                return;
            case KeyEvent.VK_RIGHT:
                if (isCurrentStackable()) {
                    ItemStack curStack = getSelectedStack();
                    if (curStack != null && consumeCounts[selectedIndex] < curStack.getCount()) {
                        consumeCounts[selectedIndex]++;
                    }
                }
                return;
            case KeyEvent.VK_ENTER:
                confirmConsume();
                return;
            default:
                super.onKeyPressed(keyCode);
        }
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        renderer.setColor(new Color(10, 10, 20, 240));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int height = getHeight();

        // 标题
        drawTitle(renderer, TITLE, 24, height / 5);

        if (!hasConsumables()) {
            renderer.setColor(Color.GRAY);
            String msg = "背包中没有可食用的物品";
            renderer.drawText(msg, (width - renderer.getTextWidth(msg)) / 2, height / 2);
            drawHintBar(renderer, "Esc 返回");
            return;
        }

        // 当前状态概览
        int statusY = height / 5 + 28;
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // 能量条
        int energyPct = metabolismManager.getHungerPercent();
        renderer.setColor(energyPct > 30 ? Color.ORANGE : Color.RED);
        String energyStr = String.format("能量: %d%%", energyPct);
        renderer.drawText(energyStr, 30, statusY);

        // 水分条
        int waterPct = hydrationManager.getWaterPercent();
        renderer.setColor(waterPct > 30 ? Color.CYAN : Color.RED);
        String waterStr = String.format("水分: %d%%", waterPct);
        renderer.drawText(waterStr, 30 + renderer.getTextWidth(energyStr) + 30, statusY);

        // HP
        renderer.setColor(player.getHp() > player.getMaxHp() / 2 ? Color.GREEN : Color.RED);
        String hpStr = String.format("HP: %d/%d", player.getHp(), player.getMaxHp());
        renderer.drawText(hpStr, width - 120, statusY);

        // 物品列表
        int listStartY = statusY + 28;
        int itemHeight = 28;
        int fontSize = 13;
        int maxVisible = (height - listStartY - 50) / itemHeight;

        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        for (int i = 0; i < consumableIndices.length; i++) {
            int vi = i - scrollOffset;
            if (vi < 0 || vi >= maxVisible) continue;

            ItemStack stack = getStackAtIndex(i);
            boolean sel = (i == selectedIndex);
            int count = consumeCounts[i];
            boolean stackable = isStackable(stack.getType());

            // 高亮选中行背景
            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 16, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";

            // 物品名 + 数量
            String name = stack.getType().getName();
            String nameStr = stackable
                    ? String.format("%s%s (共%d)", prefix, name, stack.getCount())
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, 30, listStartY + vi * itemHeight);

            // 效果描述
            String effectStr = buildEffectString(stack.getType(), count);
            renderer.setColor(sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
            renderer.drawText(effectStr, 30, listStartY + vi * itemHeight + 14);

            // 消耗数量提示（右侧）
            if (stackable && stack.getCount() > 1) {
                String countStr = String.format("×%d ←→", count);
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.GRAY);
                renderer.drawText(countStr, width - 90, listStartY + vi * itemHeight);
            }
        }

        // 底部提示
        String hint = isCurrentStackable()
                ? "↑↓ 选择 | ←→ 数量 | Enter 食用 | Esc 返回"
                : "↑↓ 选择 | Enter 食用 | Esc 返回";
        drawHintBar(renderer, hint);
    }

    /** 构建物品效果描述字符串（根据消耗类型） */
    private String buildEffectString(com.github.game.cdda.item.ItemType type, int count) {
        StringBuilder sb = new StringBuilder();
        var types = type.getConsumableTypes();

        if (types.contains(ConsumableType.FOOD)) {
            int cal = (int) (type.getCalories() * count);
            int sat = (int) (type.getSatiety() * count);
            sb.append(String.format("热量+%dkcal 饱腹+%d ", cal, sat));
        }
        if (types.contains(ConsumableType.WATER)) {
            int water = (int) (type.getWaterContent() * count);
            sb.append(String.format("水分+%dml ", water));
        }
        if (types.contains(ConsumableType.MEDICINE)) {
            int heal = Constants.MEDICINE_HEAL_AMOUNT * count;
            sb.append(String.format("HP+%d ", heal));
        }
        return sb.toString().trim();
    }

    /** 获取指定列表索引处的 ItemStack */
    private ItemStack getStackAtIndex(int listIndex) {
        return inventory.getItem(consumableIndices[listIndex]);
    }

    /** 确认消耗当前选中物品 */
    private void confirmConsume() {
        if (selectedIndex < 0 || selectedIndex >= consumableIndices.length) return;

        int count = consumeCounts[selectedIndex];
        if (count <= 0) return;

        ItemStack stack = getSelectedStack();
        if (stack == null) return;

        com.github.game.cdda.item.ItemType type = stack.getType();

        // ── 应用效果 ──
        StringBuilder effectLog = new StringBuilder();

        // 食物 → 热量 + 饱腹度
        if (type.hasConsumableType(ConsumableType.FOOD)) {
            double calories = type.getCalories() * count;
            metabolismManager.addCalories(calories);
            // 饱腹度直接提升能量储备（satiety 0~100 映射为 maxEnergy 的百分比）
            double satietyBoost = type.getSatiety() * count / 100.0 * metabolismManager.getMaxEnergy();
            metabolismManager.addCalories(satietyBoost);
            effectLog.append(String.format("热量+%dkcal 饱腹+%d ",
                    (int) calories, (int) (type.getSatiety() * count)));
        }

        // 水 → 水分
        if (type.hasConsumableType(ConsumableType.WATER)) {
            double water = type.getWaterContent() * count;
            hydrationManager.addWater(water);
            effectLog.append(String.format("水分+%dml ", (int) water));
        }

        // 药品 → 治疗
        if (type.hasConsumableType(ConsumableType.MEDICINE)) {
            int heal = Constants.MEDICINE_HEAL_AMOUNT * count;
            player.heal(heal);
            effectLog.append(String.format("HP+%d ", heal));
        }

        // ── 从背包移除 ──
        ItemStack consumed = inventory.removeItem(consumableIndices[selectedIndex], count);

        if (consumed != null) {
            // 消耗游戏时间
            turnManager.addAction(player, Constants.EAT_BASE_TIME);
            turnManager.processRound();

            GameLog.getInstance().log(String.format("食用了 %s x%d (%s)",
                    type.getName(), count, effectLog.toString().trim()));

            // 刷新列表（物品可能已完全移除）
            refreshConsumables();
        }
    }

    @Override
    protected void onSelect(int index) {
        // Enter 由 onKeyPressed 处理
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }
}
