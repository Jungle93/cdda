package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.input.InputStateMachine;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.ItemActionRegistry;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.PlayerInventory;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.GameOverlay;
import com.github.game.engine.core.scene.Viewport;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 物品使用覆盖层。
 * 以半透明方式叠加在游戏画面上方，显示物品列表和动作菜单。
 *
 * <p>内部两个状态：
 * <ul>
 *   <li>{@link State#ITEM_LIST} — 显示有可用动作的物品</li>
 *   <li>{@link State#ACTION_MENU} — 显示选中物品的动作列表</li>
 * </ul>
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 导航</li>
 *   <li>Enter — 物品列表→动作菜单 / 动作菜单→执行动作</li>
 *   <li>Esc — 动作菜单→物品列表 / 物品列表→关闭</li>
 * </ul>
 */
public class ItemUseOverlay extends GameOverlay {

    /** 内部状态 */
    private enum State { ITEM_LIST, ACTION_MENU }

    private State state = State.ITEM_LIST;

    // ── 依赖 ──
    private final Player player;
    private final GameWorld world;
    private final PlayerInventory inventory;
    private final InputStateMachine inputStateMachine;

    // ── 物品列表状态 ──
    /** 有可用动作的物品索引映射（列表索引 → 背包索引） */
    private int[] actionableIndices;
    private int itemIndex = 0;

    // ── 动作菜单状态 ──
    private ItemStack selectedTool;
    private List<ItemAction> currentActions;
    private int actionIndex = 0;

    /**
     * 创建物品使用覆盖层。
     *
     * @param viewport         视口（与游戏区域相同）
     * @param player           玩家
     * @param world            游戏世界
     * @param inputStateMachine 输入状态机（用于方向选择）
     */
    public ItemUseOverlay(Viewport viewport, Player player, GameWorld world,
                          InputStateMachine inputStateMachine) {
        super(viewport);
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
        if (itemIndex >= actionableIndices.length) {
            itemIndex = Math.max(0, actionableIndices.length - 1);
        }
    }

    private boolean hasActionableItems() {
        return actionableIndices.length > 0;
    }

    private ItemStack getItemStack() {
        if (itemIndex < 0 || itemIndex >= actionableIndices.length) return null;
        return inventory.getItem(actionableIndices[itemIndex]);
    }

    // ── 输入处理 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        switch (state) {
            case ITEM_LIST:
                handleItemListKey(keyCode);
                break;
            case ACTION_MENU:
                handleActionMenuKey(keyCode);
                break;
        }
    }

    private void handleItemListKey(int keyCode) {
        if (!hasActionableItems()) {
            if (keyCode == KeyEvent.VK_ESCAPE) dismiss();
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_UP:
                itemIndex = (itemIndex - 1 + actionableIndices.length) % actionableIndices.length;
                break;
            case KeyEvent.VK_DOWN:
                itemIndex = (itemIndex + 1) % actionableIndices.length;
                break;
            case KeyEvent.VK_ENTER:
                // 进入动作菜单
                selectedTool = getItemStack();
                if (selectedTool != null) {
                    currentActions = ItemActionRegistry.getActionsFor(selectedTool);
                    actionIndex = 0;
                    state = State.ACTION_MENU;
                }
                break;
            case KeyEvent.VK_ESCAPE:
                dismiss();
                break;
            default:
                break;
        }
    }

    private void handleActionMenuKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_UP:
                if (!currentActions.isEmpty()) {
                    actionIndex = (actionIndex - 1 + currentActions.size()) % currentActions.size();
                }
                break;
            case KeyEvent.VK_DOWN:
                if (!currentActions.isEmpty()) {
                    actionIndex = (actionIndex + 1) % currentActions.size();
                }
                break;
            case KeyEvent.VK_ENTER:
                if (actionIndex >= 0 && actionIndex < currentActions.size()) {
                    ItemAction action = currentActions.get(actionIndex);
                    if (action.canExecute(player, world)) {
                        if (action.needsDirection()) {
                            // 关闭覆盖层，进入主界面方向选择
                            dismiss();
                            inputStateMachine.startDirectionSelection(action, selectedTool);
                        } else {
                            // 直接执行
                            action.execute(player, world, selectedTool);
                            dismiss();
                        }
                    }
                }
                break;
            case KeyEvent.VK_ESCAPE:
                // 退回物品列表
                state = State.ITEM_LIST;
                selectedTool = null;
                currentActions = null;
                break;
            default:
                break;
        }
    }

    // ── 渲染 ──────────────────────────────────

    @Override
    public void render(Renderer renderer) {
        // 半透明背景（游戏画面可见）
        renderOverlayBackground(renderer);

        switch (state) {
            case ITEM_LIST:
                renderItemList(renderer);
                break;
            case ACTION_MENU:
                renderActionMenu(renderer);
                break;
        }
    }

    private void renderItemList(Renderer renderer) {
        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();

        // 居中面板
        int panelW = Math.min(320, vpW - 40);
        int panelH = Math.min(300, vpH - 60);
        int panelX = (vpW - panelW) / 2;
        int panelY = (vpH - panelH) / 2;
        renderPanel(renderer, panelX, panelY, panelW, panelH);

        // 标题
        renderer.setFont(new Font("Monospaced", Font.BOLD, 18));
        renderer.setColor(Color.WHITE);
        drawCentered(renderer, "使用物品", panelX + panelW / 2, panelY + 24);

        if (!hasActionableItems()) {
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
            renderer.setColor(Color.GRAY);
            String msg = "背包中没有可使用的物品";
            drawCentered(renderer, msg, panelX + panelW / 2, panelY + panelH / 2);
            return;
        }

        // 物品列表
        int listX = panelX + 16;
        int listY = panelY + 48;
        int itemHeight = 22;
        int fontSize = 13;
        int maxVisible = (panelH - 70) / itemHeight;

        int scrollOffset = 0;
        if (itemIndex >= maxVisible) {
            scrollOffset = itemIndex - maxVisible + 1;
        }

        for (int i = 0; i < actionableIndices.length; i++) {
            int vi = i - scrollOffset;
            if (vi < 0 || vi >= maxVisible) continue;

            ItemStack stack = inventory.getItem(actionableIndices[i]);
            boolean sel = (i == itemIndex);

            // 高亮背景
            if (sel) {
                renderer.setColor(new Color(60, 60, 0, 150));
                renderer.fillRect(listX - 4, listY + vi * itemHeight - 14, panelW - 24, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";

            // 物品名（优先用描述，截断防止溢出）
            String name = stack.getType().getDescription() != null
                    && !stack.getType().getDescription().isBlank()
                    ? stack.getType().getDescription()
                    : stack.getType().getName();
            int maxNameWidth = panelW - 100; // 预留右侧动作提示空间
            while (renderer.getTextWidth(name) > maxNameWidth && name.length() > 4) {
                name = name.substring(0, name.length() - 2) + "…";
            }
            String nameStr = stack.getCount() > 1
                    ? String.format("%s%s ×%d", prefix, name, stack.getCount())
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, listX, listY + vi * itemHeight);

            // 动作提示
            List<ItemAction> actions = ItemActionRegistry.getActionsFor(stack);
            if (!actions.isEmpty()) {
                String hint = String.format("[%s]", actions.get(0).getName());
                renderer.setColor(sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
                int hintX = panelX + panelW - 16 - renderer.getTextWidth(hint);
                renderer.drawText(hint, hintX, listY + vi * itemHeight);
            }
        }

        // 底部提示
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        renderer.setColor(Color.GRAY);
        drawCentered(renderer, "↑↓ 选择 | Enter 使用 | Esc 关闭",
                panelX + panelW / 2, panelY + panelH - 12);
    }

    private void renderActionMenu(Renderer renderer) {
        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();

        // 居中面板
        int panelW = Math.min(280, vpW - 40);
        int panelH = Math.min(240, vpH - 60);
        int panelX = (vpW - panelW) / 2;
        int panelY = (vpH - panelH) / 2;
        renderPanel(renderer, panelX, panelY, panelW, panelH);

        // 标题：物品描述（截断防止溢出）
        String toolName = selectedTool.getType().getDescription() != null
                && !selectedTool.getType().getDescription().isBlank()
                ? selectedTool.getType().getDescription()
                : selectedTool.getType().getName();
        renderer.setFont(new Font("Monospaced", Font.BOLD, 16));
        renderer.setColor(Color.WHITE);
        String title = "使用: " + toolName;
        int maxTitleWidth = panelW - 20;
        while (renderer.getTextWidth(title) > maxTitleWidth && title.length() > 8) {
            title = title.substring(0, title.length() - 2) + "…";
        }
        drawCentered(renderer, title, panelX + panelW / 2, panelY + 24);

        if (currentActions.isEmpty()) {
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
            renderer.setColor(Color.GRAY);
            String msg = "该物品没有可用动作";
            drawCentered(renderer, msg, panelX + panelW / 2, panelY + panelH / 2);
            return;
        }

        // 动作列表
        int listX = panelX + 16;
        int listY = panelY + 50;
        int itemHeight = 28;
        int maxVisible = (panelH - 80) / itemHeight;

        for (int i = 0; i < currentActions.size() && i < maxVisible; i++) {
            ItemAction action = currentActions.get(i);
            boolean sel = (i == actionIndex);
            boolean canExec = action.canExecute(player, world);

            if (sel) {
                renderer.setColor(new Color(60, 60, 0, 150));
                renderer.fillRect(listX - 4, listY + i * itemHeight - 14, panelW - 24, itemHeight);
            }

            // 动作名称
            renderer.setFont(new Font("Monospaced", Font.BOLD, 14));
            String prefix = sel ? "▶ " : "  ";
            renderer.setColor(!canExec ? Color.DARK_GRAY
                    : sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(prefix + action.getName(), listX, listY + i * itemHeight);

            // 描述
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
            renderer.setColor(!canExec ? new Color(100, 100, 100)
                    : sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
            String desc = canExec ? action.getDescription() : action.getDescription() + "（无法执行）";
            renderer.drawText(desc, listX + 20, listY + i * itemHeight + 14);
        }

        // 底部提示
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        renderer.setColor(Color.GRAY);
        drawCentered(renderer, "↑↓ 选择 | Enter 执行 | Esc 返回",
                panelX + panelW / 2, panelY + panelH - 12);
    }
}
