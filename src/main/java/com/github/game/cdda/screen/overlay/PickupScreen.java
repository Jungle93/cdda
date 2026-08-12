package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.world.GroundItem;
import com.github.game.cdda.item.world.GroundItemManager;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.world.PlayerInventory;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 拾取界面。
 * 显示玩家脚下的所有地面物品，支持多选后批量拾取。
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 移动光标</li>
 *   <li>Space — 切换当前项选中状态</li>
 *   <li>+ — 全选 / 全不选（切换）</li>
 *   <li>Enter — 拾取所有选中物品</li>
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

    /** 已选中的物品索引集合 */
    private final Set<Integer> selectedItems = new HashSet<>();

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
            boolean cursor = (i == selectedIndex);
            boolean checked = selectedItems.contains(i);

            String name = stack.getType().getDisplayName();
            int count = stack.getCount();
            int weight = (int) stack.getTotalWeightGrams();
            // 光标 '>' + 选中 '[✓]'
            String prefix = cursor ? ">" : " ";
            String check = checked ? "[✓]" : "[ ]";
            String line = String.format("%s %s %s x%d  (%dg)",
                    prefix, check, name, count, weight);

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            // 颜色优先级：光标(黄) > 选中(绿) > 默认(白)
            if (cursor) {
                renderer.setColor(Color.YELLOW);
            } else if (checked) {
                renderer.setColor(Color.GREEN);
            } else {
                renderer.setColor(Color.WHITE);
            }
            int lineX = (width - renderer.getTextWidth(line)) / 2;
            int lineY = listStartY + visibleIndex * itemHeight;
            renderer.drawText(line, lineX, lineY);
        }

        // 底部提示
        String hint = "Space 切换选中 | + 全选  Enter 拾取 | Esc 取消";
        drawHintBar(renderer, hint);
    }

    @Override
    public void onKeyPressed(int keyCode) {
        if (groundItems.isEmpty()) {
            if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_ENTER) {
                close();
            }
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_UP:
                selectedIndex--;
                if (selectedIndex < 0) selectedIndex = groundItems.size() - 1;
                break;
            case KeyEvent.VK_DOWN:
                selectedIndex++;
                if (selectedIndex >= groundItems.size()) selectedIndex = 0;
                break;
            case KeyEvent.VK_SPACE:
                // 空格切换当前项选中状态
                if (selectedItems.contains(selectedIndex)) {
                    selectedItems.remove(selectedIndex);
                } else {
                    selectedItems.add(selectedIndex);
                }
                break;
            case KeyEvent.VK_PLUS:      // 小键盘 +
            case KeyEvent.VK_EQUALS:    // = 也兼容
                toggleSelectAll();
                break;
            case KeyEvent.VK_ENTER:
                pickupSelected();
                break;
            case KeyEvent.VK_ESCAPE:
                close();
                break;
            default:
                break;
        }
    }

    @Override
    public void onKeyTyped(int charCode) {
        char c = (char) charCode;
        // 主键盘 + 通过 charCode 捕获（Shift+= 产生 '+' 字符）
        if (c == '+') {
            toggleSelectAll();
        }
    }

    /** 全选/全不选切换 */
    private void toggleSelectAll() {
        if (selectedItems.size() == groundItems.size()) {
            // 已全部选中 → 清空
            selectedItems.clear();
        } else {
            // 选中所有
            for (int i = 0; i < groundItems.size(); i++) {
                selectedItems.add(i);
            }
        }
    }

    /** 批量拾取所有选中物品 */
    private void pickupSelected() {
        if (selectedItems.isEmpty()) {
            GameLog.getInstance().log("没有选中任何物品");
            return;
        }

        PlayerInventory inventory = player.getInventory();

        // 按索引倒序收集要移除的项（避免正序删除时索引偏移）
        List<Integer> sorted = new ArrayList<>(selectedItems);
        sorted.sort((a, b) -> b - a);

        int pickedCount = 0;
        for (int idx : sorted) {
            GroundItem gi = groundItems.get(idx);
            ItemStack stack = gi.getItemStack();

            if (!inventory.canCarry(stack)) {
                GameLog.getInstance().log(String.format("%s 太重了，跳过（需 %dg，剩余 %dg）",
                        stack.getType().getDisplayName(),
                        (int) stack.getTotalWeightGrams(),
                        (int) inventory.getRemainingCapacity()));
                continue;
            }

            if (inventory.addItem(stack)) {
                groundItemManager.removeGroundItem(gi);
                pickedCount++;
            }
        }

        if (pickedCount > 0) {
            GameLog.getInstance().log(String.format("拾取了 %d 件物品", pickedCount));
        }

        // 重新获取地面物品列表
        List<GroundItem> remaining = groundItemManager.getItemsAt(
                player.getTileX(), player.getTileY());

        if (remaining.isEmpty()) {
            engine.getScreenManager().popScreen();
        } else {
            // 还有剩余物品，关闭当前界面让下次 G 重新打开
            engine.getScreenManager().popScreen();
        }
    }

    @Override
    protected void onSelect(int index) {
        // 不使用基类的 Enter→onSelect 流程，已在 onKeyPressed 中处理
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }

    /** 关闭界面 */
    private void close() {
        engine.getScreenManager().popScreen();
    }
}
