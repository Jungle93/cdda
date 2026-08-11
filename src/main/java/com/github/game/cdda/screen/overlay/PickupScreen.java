package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.Player;
import com.github.game.cdda.item.GroundItem;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.PlayerInventory;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.util.List;

/**
 * 拾取界面。
 * 显示玩家脚下的所有地面物品，选择后拾取到背包。
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择物品</li>
 *   <li>Enter — 拾取选中物品</li>
 *   <li>Esc — 关闭</li>
 * </ul>
 *
 * <p>超重时拒绝拾取并给出提示。
 */
public class PickupScreen extends MenuScreen {

    private static final String TITLE = "拾取";

    /** 地面物品管理器（用于移除已拾取物品） */
    private final GroundItemManager groundItemManager;

    /** 玩家引用 */
    private final Player player;

    /** 脚下地面物品列表（快照） */
    private final List<GroundItem> groundItems;

    /**
     * 创建拾取界面。
     *
     * @param engine            引擎
     * @param player            玩家
     * @param groundItemManager 地面物品管理器
     * @param groundItems       脚下地面物品列表
     */
    public PickupScreen(GameEngine engine, Player player,
                         GroundItemManager groundItemManager, List<GroundItem> groundItems) {
        super(engine);
        this.player = player;
        this.groundItemManager = groundItemManager;
        this.groundItems = groundItems;
    }

    @Override
    protected int getItemCount() {
        return groundItems.size();
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int height = getHeight();
        int width = getWidth();

        // 标题
        drawTitle(renderer, TITLE, 28, height / 4);

        // 背包容量信息
        PlayerInventory inventory = player.getInventory();
        int totalWeight = (int) inventory.getTotalWeight();
        int capacity = inventory.getCarryCapacity();

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 14));
        renderer.setColor(Color.CYAN);
        String capacityStr = String.format("背包: %dg / %dg", totalWeight, capacity);
        int capX = (width - renderer.getTextWidth(capacityStr)) / 2;
        renderer.drawText(capacityStr, capX, height / 4 + 30);

        // 物品列表
        int listStartY = height / 4 + 60;
        int itemHeight = 22;
        int fontSize = 14;
        int maxVisible = (height / 2 - 60) / itemHeight;

        // 计算滚动偏移
        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        for (int i = 0; i < groundItems.size(); i++) {
            int visibleIndex = i - scrollOffset;
            if (visibleIndex < 0 || visibleIndex >= maxVisible) continue;

            GroundItem gi = groundItems.get(i);
            ItemStack stack = gi.getItemStack();
            boolean sel = (i == selectedIndex);

            String name = stack.getType().getDisplayName();
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

        // 底部提示
        drawHintBar(renderer, "Enter 拾取 | Esc 取消");
    }

    @Override
    protected void onSelect(int index) {
        if (index < 0 || index >= groundItems.size()) return;

        GroundItem gi = groundItems.get(index);
        ItemStack stack = gi.getItemStack();
        PlayerInventory inventory = player.getInventory();

        if (!inventory.canCarry(stack)) {
            GameLog.getInstance().log(String.format("%s 太重了，无法携带（需 %dg，剩余 %dg）",
                    stack.getType().getDisplayName(),
                    (int) stack.getTotalWeightGrams(),
                    (int) inventory.getRemainingCapacity()));
            return;
        }

        // 拾取到背包
        if (inventory.addItem(stack)) {
            // 从地面管理器移除
            groundItemManager.removeGroundItem(gi);

            GameLog.getInstance().log(String.format("拾取了 %s x%d",
                    stack.getType().getDisplayName(), stack.getCount()));

            // 重新获取地面物品列表
            List<GroundItem> remaining = groundItemManager.getItemsAt(
                    player.getTileX(), player.getTileY());

            if (remaining.isEmpty()) {
                // 没有物品了，关闭界面
                engine.getScreenManager().popScreen();
            } else {
                // 更新列表并调整索引
                // 注意：groundItems 是 final 的，不能重新赋值
                // 所以通过 pop + re-push 或者让 GameScene 处理
                // 简化：直接 pop，让下次按 G 重新打开
                engine.getScreenManager().popScreen();
            }
        }
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }
}
