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
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择物品</li>
 *   <li>←/→ — 调整丢弃数量（仅可堆叠物品）</li>
 *   <li>Enter — 确认丢弃</li>
 *   <li>Esc — 返回</li>
 * </ul>
 *
 * <p>可堆叠物品（maxStackSize > 1 且非 unique）可按数量丢弃；
 * 不可堆叠物品只能整件丢弃。
 */
public class DropScreen extends MenuScreen {

    private static final String TITLE = "丢弃物品";

    private final Player player;
    private final GroundItemManager groundItemManager;
    private final PlayerInventory inventory;

    /** 每项当前选定的丢弃数量 */
    private int[] dropCounts;

    public DropScreen(GameEngine engine, Player player, GroundItemManager groundItemManager) {
        super(engine);
        this.player = player;
        this.groundItemManager = groundItemManager;
        this.inventory = player.getInventory();
        refreshDropCounts();
    }

    /** 重新初始化丢弃数量数组（物品变动后调用） */
    private void refreshDropCounts() {
        int count = inventory.getItemCount();
        dropCounts = new int[count];
        for (int i = 0; i < count; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null) {
                // 不可堆叠物品只能整体丢弃（数量固定为 1）
                dropCounts[i] = isStackable(stack.getType()) ? stack.getCount() : 1;
            }
        }
    }

    /** 当前选中物品是否可堆叠 */
    private boolean isCurrentStackable() {
        if (selectedIndex < 0 || selectedIndex >= inventory.getItemCount()) return false;
        ItemStack stack = inventory.getItem(selectedIndex);
        return stack != null && isStackable(stack.getType());
    }

    /**
     * 判断物品是否可堆叠（支持按数量丢弃）。
     * maxStackSize > 1 且非 unique 的物品可堆叠。
     */
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

        switch (keyCode) {
            case KeyEvent.VK_LEFT:
                // 仅可堆叠物品可调整丢弃数量
                if (isCurrentStackable()) {
                    if (dropCounts[selectedIndex] > 1) {
                        dropCounts[selectedIndex]--;
                    }
                }
                return;
            case KeyEvent.VK_RIGHT:
                // 仅可堆叠物品可调整丢弃数量
                if (isCurrentStackable()) {
                    ItemStack curStack = inventory.getItem(selectedIndex);
                    if (curStack != null && dropCounts[selectedIndex] < curStack.getCount()) {
                        dropCounts[selectedIndex]++;
                    }
                }
                return;
            case KeyEvent.VK_ENTER:
                confirmDrop();
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
        int maxVisible = (height * 3 / 5 - 40) / itemHeight;

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

            // 高亮选中行背景
            if (sel) {
                renderer.setColor(new Color(60, 60, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 14, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";
            String name = stack.getType().getName();

            // 物品名 + 总数（可堆叠）或仅名称（不可堆叠）
            String nameStr = stackable
                    ? String.format("%s%s (共%d)", prefix, name, totalCount)
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, 30, listStartY + vi * itemHeight);

            // 丢弃数量/整件丢弃
            if (stackable) {
                String countStr = String.format("丢弃: %d  ←→调整", dropCount);
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.LIGHT_GRAY);
                renderer.drawText(countStr, 30 + renderer.getTextWidth(nameStr) + 20,
                        listStartY + vi * itemHeight);
            } else {
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.LIGHT_GRAY);
                renderer.drawText("(整件丢弃)", 30 + renderer.getTextWidth(nameStr) + 20,
                        listStartY + vi * itemHeight);
            }

            // 丢弃重量
            String wStr = String.format("(%dg)", dropWeight);
            renderer.setColor(Color.GRAY);
            renderer.drawText(wStr, width - 80, listStartY + vi * itemHeight);
        }

        // 底部提示（根据选中物品是否可堆叠动态显示）
        String hint = isCurrentStackable()
                ? "↑↓ 选择 | ←→ 数量 | Enter 确认丢弃 | Esc 返回"
                : "↑↓ 选择 | Enter 整件丢弃 | Esc 返回";
        drawHintBar(renderer, hint);
    }

    /** 确认丢弃当前选中物品 */
    private void confirmDrop() {
        if (selectedIndex < 0 || selectedIndex >= inventory.getItemCount()) return;

        int dropCount = dropCounts[selectedIndex];
        if (dropCount <= 0) return;

        ItemStack stack = inventory.getItem(selectedIndex);
        if (stack == null) return;

        // 从背包移除指定数量
        ItemStack dropped = inventory.removeItem(selectedIndex, dropCount);
        if (dropped != null) {
            groundItemManager.dropItem(dropped, player.getTileX(), player.getTileY());
            GameLog.getInstance().log(String.format("丢弃了 %s x%d",
                    dropped.getType().getName(), dropped.getCount()));

            // 刷新数量数组（物品可能已完全移除）
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
        // 返回背包（pop 自己后，如果背包还在栈上就显示背包）
        engine.getScreenManager().popScreen();
    }
}
