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
import java.util.ArrayList;
import java.util.List;

/**
 * 进食/饮水界面。
 * 从背包中选择可消耗物品进行食用或饮用。
 *
 * <p>操作流程（一步）：
 * <ol>
 *   <li>↑/↓ 选择物品，←/→ 增减数量（默认 1）</li>
 *   <li>Enter 确认消耗，Esc 关闭</li>
 * </ol>
 */
public class EatingScreen extends MenuScreen {

    private static final String TITLE = "进食";

    private final Player player;
    private final PlayerInventory inventory;
    private final MetabolismManager metabolismManager;
    private final HydrationManager hydrationManager;
    private final TurnManager turnManager;

    /** 背包中可消耗物品的索引映射（列表索引 → 背包索引） */
    private int[] consumableIndices;

    public EatingScreen(GameEngine engine, GameWorld world) {
        super(engine);
        this.player = world.getPlayer();
        this.inventory = player.getInventory();
        this.metabolismManager = world.getMetabolismManager();
        this.hydrationManager = world.getHydrationManager();
        this.turnManager = world.getTurnManager();
        refreshConsumables();
    }

    /** 扫描背包，筛选可消耗物品并初始化 */
    private void refreshConsumables() {
        List<Integer> indices = new ArrayList<>();
        List<ItemStack> items = inventory.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != null && items.get(i).getType().isConsumable()) {
                indices.add(i);
            }
        }
        consumableIndices = indices.stream().mapToInt(Integer::intValue).toArray();
        // 同步 consumeCounts 数组大小，保留已有的数量选择
        int[] newCounts = new int[consumableIndices.length];
        int copyLen = Math.min(newCounts.length, consumeCounts.length);
        System.arraycopy(consumeCounts, 0, newCounts, 0, copyLen);
        // 新增项默认为 1
        for (int i = copyLen; i < newCounts.length; i++) {
            newCounts[i] = 1;
        }
        consumeCounts = newCounts;
        if (selectedIndex >= consumableIndices.length) {
            selectedIndex = Math.max(0, consumableIndices.length - 1);
        }
    }

    private boolean hasConsumables() {
        return consumableIndices.length > 0;
    }

    private ItemStack getSelectedStack() {
        if (selectedIndex < 0 || selectedIndex >= consumableIndices.length) return null;
        return inventory.getItem(consumableIndices[selectedIndex]);
    }

    @Override
    protected int getItemCount() {
        return consumableIndices.length;
    }

    @Override
    protected void onAdjust(int index, int direction) {
        if (!hasConsumables()) return;
        ItemStack stack = getSelectedStack();
        if (stack == null) return;

        int max = stack.getCount();
        int current = consumeCounts[index];
        int next = current + direction;
        consumeCounts[index] = Math.max(1, Math.min(next, max));
    }

    /** 每项当前选定的消耗数量 */
    private int[] consumeCounts = new int[0];

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

        int energyPct = metabolismManager.getHungerPercent();
        renderer.setColor(energyPct > 30 ? Color.ORANGE : Color.RED);
        String energyStr = String.format("能量: %d%%", energyPct);
        renderer.drawText(energyStr, 30, statusY);

        int waterPct = hydrationManager.getWaterPercent();
        renderer.setColor(waterPct > 30 ? Color.CYAN : Color.RED);
        String waterStr = String.format("水分: %d%%", waterPct);
        renderer.drawText(waterStr, 30 + renderer.getTextWidth(energyStr) + 30, statusY);

        renderer.setColor(player.getHp() > player.getMaxHp() / 2 ? Color.GREEN : Color.RED);
        String hpStr = String.format("HP: %d/%d", player.getHp(), player.getMaxHp());
        renderer.drawText(hpStr, width - 120, statusY);

        // 物品列表
        int listStartY = statusY + 28;
        int itemHeight = 28;
        int fontSize = 13;
        int maxVisible = (height - listStartY - 70) / itemHeight;

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
            boolean stackable = stack.getCount() > 1;

            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 16, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";

            String name = stack.getType().getDisplayName();
            String nameStr = stackable
                    ? String.format("%s%s (共%d)", prefix, name, stack.getCount())
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, 30, listStartY + vi * itemHeight);

            String effectStr = buildEffectString(stack.getType(), count);
            renderer.setColor(sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
            renderer.drawText(effectStr, 30, listStartY + vi * itemHeight + 14);

            // 数量显示（选中的高亮）
            if (stackable) {
                String countStr = String.format("×%d", count);
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.GRAY);
                renderer.drawText(countStr, width - 90, listStartY + vi * itemHeight);
            }
        }

        // 底部提示
        drawHintBar(renderer, "↑↓ 选择 | ←→ 增减数量 | Enter 食用 | Esc 返回");
    }

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

    private ItemStack getStackAtIndex(int listIndex) {
        return inventory.getItem(consumableIndices[listIndex]);
    }

    @Override
    protected void onSelect(int index) {
        confirmConsume(index);
    }

    private void confirmConsume(int index) {
        if (!hasConsumables() || index < 0 || index >= consumableIndices.length) return;

        int count = consumeCounts[index];
        if (count <= 0) return;

        ItemStack stack = inventory.getItem(consumableIndices[index]);
        if (stack == null) return;

        com.github.game.cdda.item.ItemType type = stack.getType();

        StringBuilder effectLog = new StringBuilder();

        if (type.hasConsumableType(ConsumableType.FOOD)) {
            double calories = type.getCalories() * count;
            metabolismManager.addCalories(calories);
            double satietyBoost = type.getSatiety() * count / 100.0 * metabolismManager.getMaxEnergy();
            metabolismManager.addCalories(satietyBoost);
            effectLog.append(String.format("热量+%dkcal 饱腹+%d ",
                    (int) calories, (int) (type.getSatiety() * count)));
        }

        if (type.hasConsumableType(ConsumableType.WATER)) {
            double water = type.getWaterContent() * count;
            hydrationManager.addWater(water);
            effectLog.append(String.format("水分+%dml ", (int) water));
        }

        if (type.hasConsumableType(ConsumableType.MEDICINE)) {
            int heal = Constants.MEDICINE_HEAL_AMOUNT * count;
            player.heal(heal);
            effectLog.append(String.format("HP+%d ", heal));
        }

        ItemStack consumed = inventory.removeItem(consumableIndices[index], count);

        if (consumed != null) {
            turnManager.addAction(player, Constants.EAT_BASE_TIME);
            turnManager.processRound();

            GameLog.getInstance().log(String.format("食用了 %s x%d (%s)",
                    type.getName(), count, effectLog.toString().trim()));

            refreshConsumables();
        }
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }
}
