package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.ItemActionRegistry;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.util.List;

/**
 * 物品动作菜单（第二级）。
 * 显示指定物品的所有可用动作，选择后执行对应动作。
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择动作</li>
 *   <li>Enter — 执行动作</li>
 *   <li>Esc — 返回物品列表</li>
 * </ul>
 */
public class ItemActionMenuScreen extends MenuScreen {

    private final Player player;
    private final GameWorld world;
    private final ItemStack tool;
    private final List<ItemAction> actions;

    public ItemActionMenuScreen(GameEngine engine, Player player,
                                GameWorld world, ItemStack tool) {
        super(engine);
        this.player = player;
        this.world = world;
        this.tool = tool;
        this.actions = ItemActionRegistry.getActionsFor(tool);
    }

    @Override
    protected int getItemCount() {
        return actions.size();
    }

    @Override
    protected void onSelect(int index) {
        if (index < 0 || index >= actions.size()) return;
        ItemAction action = actions.get(index);

        if (!action.canExecute(player, world)) {
            // canExecute 内部或此处可输出提示
            return;
        }

        // 执行动作
        action.execute(player, world, tool);

        // 执行后关闭动作菜单和物品使用菜单（返回游戏）
        engine.getScreenManager().popScreen(); // 弹出 ItemActionMenuScreen
        engine.getScreenManager().popScreen(); // 弹出 ItemUseScreen
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen(); // 返回 ItemUseScreen
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        renderer.setColor(new Color(10, 10, 20, 240));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int height = getHeight();

        // 标题：物品名
        String itemName = tool.getType().getDescription() != null
                && !tool.getType().getDescription().isBlank()
                ? tool.getType().getDescription()
                : tool.getType().getName();
        drawTitle(renderer, "使用: " + itemName, 22, height / 5);

        if (actions.isEmpty()) {
            renderer.setColor(Color.GRAY);
            String msg = "该物品没有可用动作";
            renderer.drawText(msg, (width - renderer.getTextWidth(msg)) / 2, height / 2);
            drawHintBar(renderer, "Esc 返回");
            return;
        }

        // 动作列表
        int listStartY = height / 5 + 36;
        int itemHeight = 32;
        int fontSize = 14;
        int maxVisible = (height - listStartY - 50) / itemHeight;

        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        for (int i = 0; i < actions.size(); i++) {
            int vi = i - scrollOffset;
            if (vi < 0 || vi >= maxVisible) continue;

            ItemAction action = actions.get(i);
            boolean sel = (i == selectedIndex);
            boolean canExec = action.canExecute(player, world);

            // 高亮选中行背景
            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 18, width - 40, itemHeight);
            }

            // 动作名称
            renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
            String prefix = sel ? "▶ " : "  ";
            renderer.setColor(!canExec ? Color.DARK_GRAY
                    : sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(prefix + action.getName(), 30, listStartY + vi * itemHeight);

            // 动作描述
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
            renderer.setColor(!canExec ? new Color(100, 100, 100)
                    : sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
            String desc = canExec ? action.getDescription() : action.getDescription() + "（无法执行）";
            renderer.drawText(desc, 50, listStartY + vi * itemHeight + 16);
        }

        drawHintBar(renderer, "↑↓ 选择 | Enter 执行 | Esc 返回");
    }
}
