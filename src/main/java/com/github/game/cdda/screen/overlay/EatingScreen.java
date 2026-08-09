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
 * <p>操作流程（两步）：
 * <ol>
 *   <li>↑/↓ 选择物品，Enter 进入数量输入</li>
 *   <li>输入数字，Enter 确认消耗</li>
 * </ol>
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

    /** 是否处于数量输入模式 */
    private boolean quantityInputMode = false;
    /** 数量输入缓冲 */
    private StringBuilder numberBuffer = new StringBuilder();

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
            consumeCounts[i] = 1;
        }
        if (selectedIndex >= consumableIndices.length) {
            selectedIndex = Math.max(0, consumableIndices.length - 1);
        }
    }

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

        // ── 数量输入模式 ──
        if (quantityInputMode) {
            handleQuantityInput(keyCode);
            return;
        }

        // ── 选择模式 ──
        switch (keyCode) {
            case KeyEvent.VK_ENTER:
                if (isCurrentStackable()) {
                    // 可堆叠物品：进入数量输入模式
                    quantityInputMode = true;
                    numberBuffer.setLength(0);
                } else {
                    // 不可堆叠物品：直接消耗 1 个
                    consumeCounts[selectedIndex] = 1;
                    confirmConsume();
                }
                return;
            default:
                super.onKeyPressed(keyCode);
        }
    }

    /** 数量输入模式下的按键处理 */
    private void handleQuantityInput(int keyCode) {
        int digit = toDigit(keyCode);
        if (digit >= 0) {
            numberBuffer.append(digit);
            int max = getSelectedStack().getCount();
            int value = parseClamped(max);
            consumeCounts[selectedIndex] = Math.max(1, Math.min(value, max));
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE:
                // 退格：删除最后一位
                if (numberBuffer.length() > 0) {
                    numberBuffer.deleteCharAt(numberBuffer.length() - 1);
                    int max = getSelectedStack().getCount();
                    consumeCounts[selectedIndex] = numberBuffer.length() == 0
                            ? 1
                            : Math.max(1, Math.min(parseClamped(max), max));
                }
                return;
            case KeyEvent.VK_ENTER:
                // 确认消耗
                quantityInputMode = false;
                numberBuffer.setLength(0);
                confirmConsume();
                return;
            case KeyEvent.VK_ESCAPE:
                // 取消输入，回到选择模式（数量重置为 1）
                quantityInputMode = false;
                numberBuffer.setLength(0);
                consumeCounts[selectedIndex] = 1;
                return;
            default:
                break;
        }
    }

    /** 将按键码转换为数字 0-9，非数字键返回 -1 */
    private int toDigit(int keyCode) {
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
            return keyCode - KeyEvent.VK_0;
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9)
            return keyCode - KeyEvent.VK_NUMPAD0;
        return -1;
    }

    /** 解析数字缓冲，限制不超过 max */
    private int parseClamped(int max) {
        if (numberBuffer.length() == 0) return 1;
        try {
            return Integer.parseInt(numberBuffer.toString());
        } catch (NumberFormatException e) {
            return 1;
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
            boolean stackable = isStackable(stack.getType());

            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 16, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";

            String name = stack.getType().getName();
            String nameStr = stackable
                    ? String.format("%s%s (共%d)", prefix, name, stack.getCount())
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, 30, listStartY + vi * itemHeight);

            String effectStr = buildEffectString(stack.getType(), count);
            renderer.setColor(sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
            renderer.drawText(effectStr, 30, listStartY + vi * itemHeight + 14);

            if (stackable && stack.getCount() > 1) {
                String countStr = String.format("×%d", count);
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.GRAY);
                renderer.drawText(countStr, width - 90, listStartY + vi * itemHeight);
            }
        }

        // ── 数量输入提示框 ──
        if (quantityInputMode) {
            int promptY = height - 55;
            renderer.setColor(new Color(30, 30, 10, 200));
            renderer.fillRect(20, promptY - 18, width - 40, 36);

            renderer.setFont(new Font("Monospaced", Font.BOLD, 14));
            renderer.setColor(Color.YELLOW);
            String prompt = String.format("输入数量 (最多 %d): %s_",
                    getSelectedStack().getCount(),
                    numberBuffer.length() > 0 ? numberBuffer.toString() : "");
            renderer.drawText(prompt, 30, promptY);
        }

        // 底部提示
        if (quantityInputMode) {
            drawHintBar(renderer, "数字键输入 | Backspace 删除 | Enter 确认 | Esc 取消");
        } else {
            String hint = isCurrentStackable()
                    ? "↑↓ 选择 | Enter 输入数量 | Esc 返回"
                    : "↑↓ 选择 | Enter 食用 | Esc 返回";
            drawHintBar(renderer, hint);
        }
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

    private void confirmConsume() {
        if (selectedIndex < 0 || selectedIndex >= consumableIndices.length) return;

        int count = consumeCounts[selectedIndex];
        if (count <= 0) return;

        ItemStack stack = getSelectedStack();
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

        ItemStack consumed = inventory.removeItem(consumableIndices[selectedIndex], count);

        if (consumed != null) {
            turnManager.addAction(player, Constants.EAT_BASE_TIME);
            turnManager.processRound();

            GameLog.getInstance().log(String.format("食用了 %s x%d (%s)",
                    type.getName(), count, effectLog.toString().trim()));

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
