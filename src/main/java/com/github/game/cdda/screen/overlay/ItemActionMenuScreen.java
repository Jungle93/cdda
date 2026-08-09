package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.input.InputStateMachine;
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
 * <p>对于需要方向选择的动作（{@link ItemAction#needsDirection()}），
 * Enter 后关闭菜单回到主游戏界面，由输入状态机在主界面处理方向选择。
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择动作</li>
 *   <li>Enter — 执行动作 / 回到主界面选择方向</li>
 *   <li>Esc — 返回物品列表</li>
 * </ul>
 */
public class ItemActionMenuScreen extends MenuScreen {

    private final Player player;
    private final GameWorld world;
    private final ItemStack tool;
    private final List<ItemAction> actions;
    private final InputStateMachine inputStateMachine;

    public ItemActionMenuScreen(GameEngine engine, Player player,
                                GameWorld world, ItemStack tool,
                                InputStateMachine inputStateMachine) {
        super(engine);
        this.player = player;
        this.world = world;
        this.tool = tool;
        this.actions = ItemActionRegistry.getActionsFor(tool);
        this.inputStateMachine = inputStateMachine;
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
            return;
        }

        if (action.needsDirection()) {
            // 关闭两级菜单，回到主游戏界面
            // 由输入状态机在主界面处理方向选择
            inputStateMachine.startDirectionSelection(action, tool);
            engine.getScreenManager().popScreen(); // 弹出 ItemActionMenuScreen
            engine.getScreenManager().popScreen(); // 弹出 ItemUseScreen
        } else {
            // 不需要方向的直接执行
            action.execute(player, world, tool);
            engine.getScreenManager().popScreen(); // 弹出 ItemActionMenuScreen
            engine.getScreenManager().popScreen(); // 弹出 ItemUseScreen
        }
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

            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 18, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
            String prefix = sel ? "▶ " : "  ";
            renderer.setColor(!canExec ? Color.DARK_GRAY
                    : sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(prefix + action.getName(), 30, listStartY + vi * itemHeight);

            renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
            renderer.setColor(!canExec ? new Color(100, 100, 100)
                    : sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
            String desc = canExec ? action.getDescription() : action.getDescription() + "（无法执行）";
            renderer.drawText(desc, 50, listStartY + vi * itemHeight + 16);
        }

        drawHintBar(renderer, "↑↓ 选择 | Enter 执行 | Esc 返回");
    }
}
