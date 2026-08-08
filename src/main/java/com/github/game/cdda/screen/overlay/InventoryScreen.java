package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.Player;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.PlayerInventory;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.util.List;

/**
 * 背包界面。
 * 显示玩家携带的所有物品及其重量。
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择物品</li>
 *   <li>Esc — 关闭</li>
 * </ul>
 */
public class InventoryScreen extends MenuScreen {

    private static final String TITLE = "背包";

    /** 玩家引用 */
    private final Player player;

    /** 地面物品管理器（丢弃物品用） */
    private final GroundItemManager groundItemManager;

    /** 背包引用 */
    private final PlayerInventory inventory;

    /**
     * 创建背包界面。
     *
     * @param engine            引擎
     * @param player            玩家
     * @param groundItemManager 地面物品管理器（用于丢弃）
     */
    public InventoryScreen(GameEngine engine, Player player, GroundItemManager groundItemManager) {
        super(engine);
        this.player = player;
        this.groundItemManager = groundItemManager;
        this.inventory = player.getInventory();
    }

    @Override
    protected int getItemCount() {
        return inventory.getItemCount();
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int height = getHeight();
        int width = getWidth();

        // 标题
        drawTitle(renderer, TITLE, 28, height / 5);

        // 重量信息
        int totalWeight = (int) inventory.getTotalWeight();
        int capacity = inventory.getCarryCapacity();

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 14));
        boolean overweight = totalWeight > capacity;
        renderer.setColor(overweight ? Color.RED : Color.CYAN);
        String weightStr = String.format("重量: %dg / %dg", totalWeight, capacity);
        int weightX = (width - renderer.getTextWidth(weightStr)) / 2;
        renderer.drawText(weightStr, weightX, height / 5 + 30);

        // 物品列表
        if (inventory.isEmpty()) {
            renderer.setColor(Color.GRAY);
            String emptyMsg = "背包空空如也...";
            int emptyX = (width - renderer.getTextWidth(emptyMsg)) / 2;
            renderer.drawText(emptyMsg, emptyX, height / 2);
        } else {
            int listStartY = height / 5 + 60;
            int itemHeight = 22;
            int fontSize = 14;
            int maxVisible = (height * 3 / 5 - 60) / itemHeight;

            // 滚动偏移
            int scrollOffset = 0;
            if (selectedIndex >= maxVisible) {
                scrollOffset = selectedIndex - maxVisible + 1;
            }

            List<ItemStack> items = inventory.getItems();
            for (int i = 0; i < items.size(); i++) {
                int visibleIndex = i - scrollOffset;
                if (visibleIndex < 0 || visibleIndex >= maxVisible) continue;

                ItemStack stack = items.get(i);
                boolean sel = (i == selectedIndex);

                String name = stack.getType().getName();
                int count = stack.getCount();
                int weight = (int) stack.getTotalWeightGrams();
                String line = String.format("%s%s x%d  (%dg)",
                        sel ? "> " : "  ", name, count, weight);

                renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
                renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
                int lineX = (width - renderer.getTextWidth(line)) / 2;
                int lineY = listStartY + visibleIndex * itemHeight;
                renderer.drawText(line, lineX, lineY);
            }
        }

        // 底部提示
        String hint = "Esc 返回";
        drawHintBar(renderer, hint);
    }

    @Override
    protected void onSelect(int index) {
        // Enter 暂不实现（物品使用功能后续添加）
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }
}
