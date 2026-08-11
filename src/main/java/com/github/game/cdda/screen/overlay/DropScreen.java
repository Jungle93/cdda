package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.Player;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.PlayerInventory;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * 丢弃物品界面。
 * 从背包中选择物品并指定数量丢弃到脚下。
 *
 * <p>操作流程（两步）：
 * <ol>
 *   <li>↑/↓ 选择物品，Enter 进入数量输入</li>
 *   <li>输入数字，Enter 确认丢弃</li>
 * </ol>
 *
 * <p>不可堆叠物品直接整件丢弃（跳过数量输入）。
 */
public class DropScreen extends MenuScreen {

    private static final String TITLE = "丢弃物品";

    private final Player player;
    private final GroundItemManager groundItemManager;
    private final PlayerInventory inventory;

    /** 每项当前选定的丢弃数量 */
    private int[] dropCounts;

    /** 是否处于数量输入模式 */
    private boolean quantityInputMode = false;
    /** 数量输入缓冲 */
    private StringBuilder numberBuffer = new StringBuilder();

    public DropScreen(GameEngine engine, Player player, GroundItemManager groundItemManager) {
        super(engine);
        this.player = player;
        this.groundItemManager = groundItemManager;
        this.inventory = player.getInventory();
        refreshDropCounts();
    }

    private void refreshDropCounts() {
        int count = inventory.getItemCount();
        dropCounts = new int[count];
        for (int i = 0; i < count; i++) {
            dropCounts[i] = 1;
        }
    }

    private boolean isCurrentStackable() {
        if (selectedIndex < 0 || selectedIndex >= inventory.getItemCount()) return false;
        ItemStack stack = inventory.getItem(selectedIndex);
        return stack != null && isStackable(stack.getType());
    }

    private boolean isStackable(com.github.game.cdda.item.ItemType type) {
        return type.getMaxStackSize() > 1 && !type.isUnique();
    }

    @Override
    protected int getItemCount() {
        return inventory.getItemCount();
    }

    @Override
    public void onKeyPressed(int keyCode) {
        if (inventory.isEmpty()) {
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
                    // 不可堆叠物品：直接整件丢弃
                    dropCounts[selectedIndex] = 1;
                    confirmDrop();
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
            int max = inventory.getItem(selectedIndex).getCount();
            int value = parseClamped(max);
            dropCounts[selectedIndex] = Math.max(1, Math.min(value, max));
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE:
                if (numberBuffer.length() > 0) {
                    numberBuffer.deleteCharAt(numberBuffer.length() - 1);
                    int max = inventory.getItem(selectedIndex).getCount();
                    dropCounts[selectedIndex] = numberBuffer.length() == 0
                            ? 1
                            : Math.max(1, Math.min(parseClamped(max), max));
                }
                return;
            case KeyEvent.VK_ENTER:
                quantityInputMode = false;
                numberBuffer.setLength(0);
                confirmDrop();
                return;
            case KeyEvent.VK_ESCAPE:
                quantityInputMode = false;
                numberBuffer.setLength(0);
                dropCounts[selectedIndex] = 1;
                return;
            default:
                break;
        }
    }

    private int toDigit(int keyCode) {
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
            return keyCode - KeyEvent.VK_0;
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9)
            return keyCode - KeyEvent.VK_NUMPAD0;
        return -1;
    }

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

        drawTitle(renderer, TITLE, 24, height / 5);

        if (inventory.isEmpty()) {
            renderer.setColor(Color.GRAY);
            String msg = "背包中没有可丢弃的物品";
            renderer.drawText(msg, (width - renderer.getTextWidth(msg)) / 2, height / 2);
            drawHintBar(renderer, "Esc 返回");
            return;
        }

        // 重量信息
        int totalWeight = (int) inventory.getTotalWeight();
        int capacity = inventory.getCarryCapacity();
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
        renderer.setColor(Color.CYAN);
        String weightStr = String.format("背包: %dg / %dg", totalWeight, capacity);
        renderer.drawText(weightStr, (width - renderer.getTextWidth(weightStr)) / 2, height / 5 + 28);

        // 物品列表
        int listStartY = height / 5 + 55;
        int itemHeight = 24;
        int fontSize = 14;
        int maxVisible = (height * 3 / 5 - 60) / itemHeight;

        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        List<ItemStack> items = inventory.getItems();
        for (int i = 0; i < items.size(); i++) {
            int vi = i - scrollOffset;
            if (vi < 0 || vi >= maxVisible) continue;

            ItemStack stack = items.get(i);
            boolean sel = (i == selectedIndex);
            int dropCount = dropCounts[i];
            int totalCount = stack.getCount();
            int singleWeight = (int) (stack.getTotalWeightGrams() / totalCount);
            int dropWeight = singleWeight * dropCount;
            boolean stackable = isStackable(stack.getType());

            if (sel) {
                renderer.setColor(new Color(60, 60, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 14, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";
            String name = stack.getType().getDisplayName();

            String nameStr = stackable
                    ? String.format("%s%s (共%d)", prefix, name, totalCount)
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, 30, listStartY + vi * itemHeight);

            if (stackable) {
                String countStr = String.format("丢弃: %d", dropCount);
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.LIGHT_GRAY);
                renderer.drawText(countStr, 30 + renderer.getTextWidth(nameStr) + 20,
                        listStartY + vi * itemHeight);
            } else {
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.LIGHT_GRAY);
                renderer.drawText("(整件丢弃)", 30 + renderer.getTextWidth(nameStr) + 20,
                        listStartY + vi * itemHeight);
            }

            String wStr = String.format("(%dg)", dropWeight);
            renderer.setColor(Color.GRAY);
            renderer.drawText(wStr, width - 80, listStartY + vi * itemHeight);
        }

        // ── 数量输入提示框 ──
        if (quantityInputMode) {
            int promptY = height - 55;
            renderer.setColor(new Color(30, 30, 10, 200));
            renderer.fillRect(20, promptY - 18, width - 40, 36);

            renderer.setFont(new Font("Monospaced", Font.BOLD, 14));
            renderer.setColor(Color.YELLOW);
            String prompt = String.format("输入数量 (最多 %d): %s_",
                    inventory.getItem(selectedIndex).getCount(),
                    numberBuffer.length() > 0 ? numberBuffer.toString() : "");
            renderer.drawText(prompt, 30, promptY);
        }

        // 底部提示
        if (quantityInputMode) {
            drawHintBar(renderer, "数字键输入 | Backspace 删除 | Enter 确认 | Esc 取消");
        } else {
            String hint = isCurrentStackable()
                    ? "↑↓ 选择 | Enter 输入数量 | Esc 返回"
                    : "↑↓ 选择 | Enter 整件丢弃 | Esc 返回";
            drawHintBar(renderer, hint);
        }
    }

    private void confirmDrop() {
        if (selectedIndex < 0 || selectedIndex >= inventory.getItemCount()) return;

        int dropCount = dropCounts[selectedIndex];
        if (dropCount <= 0) return;

        ItemStack stack = inventory.getItem(selectedIndex);
        if (stack == null) return;

        ItemStack dropped = inventory.removeItem(selectedIndex, dropCount);
        if (dropped != null) {
            groundItemManager.dropItem(dropped, player.getTileX(), player.getTileY());
            GameLog.getInstance().log(String.format("丢弃了 %s x%d",
                    dropped.getType().getDisplayName(), dropped.getCount()));

            refreshDropCounts();
            if (selectedIndex >= inventory.getItemCount() && selectedIndex > 0) {
                selectedIndex--;
            }
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
