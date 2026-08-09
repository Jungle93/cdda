package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.input.InputStateMachine;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.ItemActionRegistry;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.PlayerInventory;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 物品使用菜单（第一级）。
 * 显示背包中所有有可用动作的物品，选择后推入 {@link ItemActionMenuScreen} 显示动作菜单。
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择物品</li>
 *   <li>Enter — 查看该物品的可用动作</li>
 *   <li>Esc — 返回游戏</li>
 * </ul>
 */
public class ItemUseScreen extends MenuScreen {

    private static final String TITLE = "使用物品";

    private final Player player;
    private final PlayerInventory inventory;
    private final GameWorld world;
    private final InputStateMachine inputStateMachine;

    /** 背包中有可用动作的物品索引映射（列表索引 → 背包索引） */
    private int[] actionableIndices;

    public ItemUseScreen(GameEngine engine, Player player, GameWorld world,
                         InputStateMachine inputStateMachine) {
        super(engine);
        this.player = player;
        this.world = world;
        this.inventory = player.getInventory();
        this.inputStateMachine = inputStateMachine;
        refreshActionableItems();
    }

    /** 扫描背包，筛选有可用动作的物品 */
    private void refreshActionableItems() {
        List<Integer> indices = new ArrayList<>();
        List<ItemStack> items = inventory.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (ItemActionRegistry.hasAnyAction(items.get(i))) {
                indices.add(i);
            }
        }
        actionableIndices = indices.stream().mapToInt(Integer::intValue).toArray();
        if (selectedIndex >= actionableIndices.length) {
            selectedIndex = Math.max(0, actionableIndices.length - 1);
        }
    }

    private boolean hasActionableItems() {
        return actionableIndices.length > 0;
    }

    /** 获取当前选中的 ItemStack */
    private ItemStack getSelectedStack() {
        if (selectedIndex < 0 || selectedIndex >= actionableIndices.length) return null;
        return inventory.getItem(actionableIndices[selectedIndex]);
    }

    @Override
    protected int getItemCount() {
        return actionableIndices.length;
    }

    @Override
    protected void onSelect(int index) {
        ItemStack stack = getSelectedStack();
        if (stack == null) return;
        // 推入第二级：动作菜单
        engine.getScreenManager().pushScreen(
                new ItemActionMenuScreen(engine, player, world, stack, inputStateMachine));
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        renderer.setColor(new Color(10, 10, 20, 240));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int height = getHeight();

        // 标题
        drawTitle(renderer, TITLE, 24, height / 5);

        if (!hasActionableItems()) {
            renderer.setColor(Color.GRAY);
            String msg = "背包中没有可使用的物品";
            renderer.drawText(msg, (width - renderer.getTextWidth(msg)) / 2, height / 2);
            drawHintBar(renderer, "Esc 返回");
            return;
        }

        // 物品列表
        int listStartY = height / 5 + 36;
        int itemHeight = 24;
        int fontSize = 13;
        int maxVisible = (height - listStartY - 50) / itemHeight;

        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        for (int i = 0; i < actionableIndices.length; i++) {
            int vi = i - scrollOffset;
            if (vi < 0 || vi >= maxVisible) continue;

            ItemStack stack = inventory.getItem(actionableIndices[i]);
            boolean sel = (i == selectedIndex);

            // 高亮选中行背景
            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 16, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";

            // 物品名
            String name = stack.getType().getDescription() != null
                    && !stack.getType().getDescription().isBlank()
                    ? stack.getType().getDescription()
                    : stack.getType().getName();
            String nameStr = stack.getCount() > 1
                    ? String.format("%s%s ×%d", prefix, name, stack.getCount())
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, 30, listStartY + vi * itemHeight);

            // 可用动作提示（右侧）
            List<ItemAction> actions = ItemActionRegistry.getActionsFor(stack);
            if (!actions.isEmpty()) {
                String actionHint = String.format("[%s]", actions.get(0).getName());
                renderer.setColor(sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
                renderer.drawText(actionHint, width - 100, listStartY + vi * itemHeight);
            }
        }

        drawHintBar(renderer, "↑↓ 选择 | Enter 使用 | Esc 返回");
    }
}
