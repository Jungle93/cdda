package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.input.InputStateMachine;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.registry.ItemActionRegistry;
import com.github.game.cdda.item.world.PlayerInventory;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.sprite.Sprite;
import com.github.game.engine.core.sprite.SpriteManager;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 背包界面。
 * 显示玩家携带的所有物品及其重量，支持查看物品详情和执行物品动作。
 *
 * <p>内部三个状态：
 * <ul>
 *   <li>{@link State#ITEM_LIST} — 物品列表</li>
 *   <li>{@link State#ACTION_MENU} — 选中物品的操作菜单</li>
 *   <li>{@link State#ITEM_DETAIL} — 物品详情查看</li>
 * </ul>
 *
 * <p>操作：
 * <ul>
 *   <li>↑/↓ — 选择物品/菜单项</li>
 *   <li>Enter — 确认/进入下一级</li>
 *   <li>Esc — 返回上一级/关闭</li>
 * </ul>
 */
public class InventoryScreen extends MenuScreen {

    private static final String TITLE = "背包";

    /** 内部状态 */
    private enum State { ITEM_LIST, ACTION_MENU, ITEM_DETAIL }

    private State state = State.ITEM_LIST;

    /** 背包引用 */
    private final PlayerInventory inventory;
    /** 游戏世界引用（用于执行动作和切换界面） */
    private final GameWorld world;
    /** 玩家引用 */
    private final Player player;
    /** 输入状态机（用于方向选择） */
    private final InputStateMachine inputStateMachine;

    // ── 动作菜单状态 ──
    /** 当前选中物品的可用动作列表 */
    private List<ActionEntry> currentActions = new ArrayList<>();
    private int actionIndex = 0;

    // ── 详情查看状态 ──
    /** 详情面板滚动偏移 */
    private int detailScrollOffset = 0;

    /**
     * 内置动作类型标识。
     * 用于区分内置操作（食用/查看）和注册表中的 ItemAction。
     */
    private enum BuiltinAction { EAT, EXAMINE }

    /**
     * 动作条目。
     * 统一封装内置操作和注册表动作，简化菜单渲染与执行逻辑。
     */
    private static class ActionEntry {
        final String name;
        final String description;
        final BuiltinAction builtin;
        final ItemAction itemAction;

        /** 创建内置动作条目 */
        ActionEntry(String name, String description, BuiltinAction builtin) {
            this.name = name;
            this.description = description;
            this.builtin = builtin;
            this.itemAction = null;
        }

        /** 创建注册表动作条目 */
        ActionEntry(ItemAction action) {
            this.name = action.getName();
            this.description = action.getDescription();
            this.builtin = null;
            this.itemAction = action;
        }

        boolean canExecute(Player player, GameWorld world) {
            if (itemAction != null) return itemAction.canExecute(player, world);
            return true; // 内置动作默认可执行
        }
    }

    /**
     * 创建背包界面。
     *
     * @param engine            引擎
     * @param player            玩家
     * @param world             游戏世界
     * @param inputStateMachine 输入状态机（用于物品动作的方向选择）
     */
    public InventoryScreen(GameEngine engine, Player player, GameWorld world,
                           InputStateMachine inputStateMachine) {
        super(engine);
        this.player = player;
        this.inventory = player.getInventory();
        this.world = world;
        this.inputStateMachine = inputStateMachine;
    }

    @Override
    protected int getItemCount() {
        return switch (state) {
            case ITEM_LIST -> inventory.getItemCount();
            case ACTION_MENU -> currentActions.size();
            case ITEM_DETAIL -> 0;
        };
    }

    // ── 辅助方法 ──────────────────────────────────

    /** 获取当前选中的物品堆 */
    private ItemStack getSelectedStack() {
        if (selectedIndex < 0 || selectedIndex >= inventory.getItemCount()) return null;
        return inventory.getItems().get(selectedIndex);
    }

    /**
     * 刷新当前选中物品的可用动作列表。
     * 按顺序添加：食用（可消耗时）→ 查看（总是可用）→ 注册表动作
     */
    private void refreshActions() {
        currentActions.clear();
        ItemStack stack = getSelectedStack();
        if (stack == null) return;

        // 食用动作（仅对可消耗物品）
        if (stack.getType().isConsumable()) {
            currentActions.add(new ActionEntry("食用", "消耗此物品", BuiltinAction.EAT));
        }

        // 查看动作（总是可用）
        currentActions.add(new ActionEntry("查看", "查看物品详细信息", BuiltinAction.EXAMINE));

        // 注册表中的物品动作（如砍树、加工等）
        for (ItemAction action : ItemActionRegistry.getActionsFor(stack)) {
            currentActions.add(new ActionEntry(action));
        }
    }

    /** 执行动作菜单中选中的动作 */
    private void executeCurrentAction() {
        if (actionIndex < 0 || actionIndex >= currentActions.size()) return;
        ActionEntry entry = currentActions.get(actionIndex);

        if (entry.builtin == BuiltinAction.EAT) {
            // 切换到进食界面
            engine.getScreenManager().pushScreen(new EatingScreen(engine, world));
        } else if (entry.builtin == BuiltinAction.EXAMINE) {
            // 进入详情查看
            detailScrollOffset = 0;
            state = State.ITEM_DETAIL;
        } else if (entry.itemAction != null && entry.itemAction.canExecute(player, world)) {
            // 执行注册表动作
            ItemStack stack = getSelectedStack();
            if (stack != null) {
                if (entry.itemAction.needsDirection(player, world)) {
                    // 需要方向选择：关闭背包，回到主界面进入方向选择
                    engine.getScreenManager().popScreen();
                    inputStateMachine.startDirectionSelection(entry.itemAction, stack);
                } else {
                    entry.itemAction.execute(player, world, stack);
                    engine.getScreenManager().popScreen();
                }
            }
        }
    }

    // ── 输入处理 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        switch (state) {
            case ITEM_LIST -> handleItemListKey(keyCode);
            case ACTION_MENU -> handleActionMenuKey(keyCode);
            case ITEM_DETAIL -> handleItemDetailKey(keyCode);
        }
    }

    private void handleItemListKey(int keyCode) {
        int count = inventory.getItemCount();
        if (count == 0) {
            if (keyCode == KeyEvent.VK_ESCAPE) onCancel();
            return;
        }
        switch (keyCode) {
            case KeyEvent.VK_UP ->
                    selectedIndex = (selectedIndex - 1 + count) % count;
            case KeyEvent.VK_DOWN ->
                    selectedIndex = (selectedIndex + 1) % count;
            case KeyEvent.VK_ENTER -> {
                actionIndex = 0;
                refreshActions();
                state = State.ACTION_MENU;
            }
            case KeyEvent.VK_ESCAPE -> onCancel();
        }
    }

    private void handleActionMenuKey(int keyCode) {
        int count = currentActions.size();
        if (count == 0) {
            state = State.ITEM_LIST;
            return;
        }
        switch (keyCode) {
            case KeyEvent.VK_UP ->
                    actionIndex = (actionIndex - 1 + count) % count;
            case KeyEvent.VK_DOWN ->
                    actionIndex = (actionIndex + 1) % count;
            case KeyEvent.VK_ENTER -> executeCurrentAction();
            case KeyEvent.VK_ESCAPE -> {
                state = State.ITEM_LIST;
                currentActions.clear();
            }
        }
    }

    private void handleItemDetailKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE -> state = State.ACTION_MENU;
            // TODO: ↑/↓ 滚动长描述
        }
    }

    @Override
    protected void onSelect(int index) {
        // 由 onKeyPressed 中的状态机处理
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }

    // ── 渲染 ──────────────────────────────────

    @Override
    protected void renderMenu(Renderer renderer) {
        switch (state) {
            case ITEM_LIST -> renderItemList(renderer);
            case ACTION_MENU -> renderActionMenu(renderer);
            case ITEM_DETAIL -> renderItemDetail(renderer);
        }
    }

    /** 渲染物品列表（主界面） */
    private void renderItemList(Renderer renderer) {
        int height = getHeight();
        int width = getWidth();

        // 不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, width, height);

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
        }

        // 底部提示
        String hint = inventory.isEmpty() ? "Esc 返回" : "↑↓ 选择 | Enter 操作 | Esc 返回";
        drawHintBar(renderer, hint);
    }

    /** 渲染操作菜单（二级菜单） */
    private void renderActionMenu(Renderer renderer) {
        int height = getHeight();
        int width = getWidth();

        // 不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, width, height);

        ItemStack stack = getSelectedStack();
        if (stack == null) {
            state = State.ITEM_LIST;
            return;
        }

        // 标题
        drawTitle(renderer, stack.getType().getDisplayName(), 24, height / 5);

        // 分隔线
        renderer.setColor(new Color(60, 60, 80));
        int separatorY = height / 5 + 20;
        renderer.drawLine(width / 4, separatorY, width * 3 / 4, separatorY);

        // 动作列表
        int listStartY = separatorY + 24;
        int itemHeight = 28;
        int fontSize = 14;

        for (int i = 0; i < currentActions.size(); i++) {
            ActionEntry action = currentActions.get(i);
            boolean sel = (i == actionIndex);
            boolean canExec = action.canExecute(player, world);

            // 高亮背景
            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(width / 4, listStartY + i * itemHeight - 14,
                        width / 2, itemHeight);
            }

            // 动作名称
            String prefix = sel ? "▶ " : "  ";
            renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
            renderer.setColor(!canExec ? Color.DARK_GRAY
                    : sel ? Color.YELLOW : Color.WHITE);
            String nameStr = prefix + action.name;
            int nameX = (width - renderer.getTextWidth(nameStr)) / 2;
            renderer.drawText(nameStr, nameX, listStartY + i * itemHeight);

            // 动作描述
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
            renderer.setColor(!canExec ? new Color(100, 100, 100)
                    : sel ? new Color(180, 255, 180) : Color.LIGHT_GRAY);
            String desc = canExec ? action.description : action.description + "（无法执行）";
            int descX = (width - renderer.getTextWidth(desc)) / 2;
            renderer.drawText(desc, descX, listStartY + i * itemHeight + 14);
        }

        // 底部提示
        drawHintBar(renderer, "↑↓ 选择 | Enter 执行 | Esc 返回");
    }

    /** 渲染物品详情查看 */
    private void renderItemDetail(Renderer renderer) {
        int height = getHeight();
        int width = getWidth();

        // 不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, width, height);

        ItemStack stack = getSelectedStack();
        if (stack == null) {
            state = State.ACTION_MENU;
            return;
        }

        // ── 居中面板 ──
        int panelW = Math.min(400, width - 40);
        int panelH = Math.min(340, height - 40);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        // 面板背景
        renderer.setColor(new Color(15, 15, 25));
        renderer.fillRect(panelX, panelY, panelW, panelH);
        renderer.setColor(new Color(80, 80, 110));
        renderer.drawRect(panelX, panelY, panelW, panelH);

        int contentX = panelX + 20;
        int contentW = panelW - 40;
        int curY = panelY + 16;

        // ── 图标（精灵贴图优先，无贴图时用字符替代） ──
        String spriteId = "item." + stack.getType().getName();
        Sprite sprite = SpriteManager.hasActivePack()
                ? SpriteManager.getSprite(spriteId) : null;

        if (sprite != null) {
            // 渲染精灵图片（居中，放大到 48×48）
            int iconSize = 48;
            int iconX = panelX + (panelW - iconSize) / 2;
            renderer.drawImage(sprite.getImage(), iconX, curY, iconSize, iconSize);
            curY += iconSize + 16;
        } else {
            // 字符回退
            String icon = stack.getType().getIcon();
            renderer.setFont(new Font("Monospaced", Font.BOLD, 36));
            renderer.setColor(new Color(255, 220, 100));
            int iconX = panelX + (panelW - renderer.getTextWidth(icon)) / 2;
            renderer.drawText(icon, iconX, curY + 36);
            curY += 52;
        }

        // ── 物品名称 ──
        String name = stack.getType().getDisplayName();
        renderer.setFont(new Font("Monospaced", Font.BOLD, 18));
        renderer.setColor(Color.YELLOW);
        int nameX = panelX + (panelW - renderer.getTextWidth(name)) / 2;
        renderer.drawText(name, nameX, curY);
        curY += 12;

        // ── 分隔线 ──
        curY += 10;
        renderer.setColor(new Color(60, 60, 80));
        renderer.drawLine(contentX, curY, contentX + contentW, curY);
        curY += 14;

        // ── 描述（自动换行） ──
        String desc = stack.getType().getDescription();
        if (desc != null && !desc.isBlank()) {
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
            List<String> lines = wrapText(renderer, desc, contentW);
            renderer.setColor(new Color(200, 200, 210));
            for (String line : lines) {
                renderer.drawText(line, contentX, curY);
                curY += 18;
            }
        } else {
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
            renderer.setColor(Color.GRAY);
            renderer.drawText("（无描述）", contentX, curY);
            curY += 18;
        }

        curY += 8;

        // ── 分隔线 ──
        renderer.setColor(new Color(60, 60, 80));
        renderer.drawLine(contentX, curY, contentX + contentW, curY);
        curY += 16;

        // ── 物品属性 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
        renderer.setColor(new Color(140, 200, 255));

        // 数量
        String countLine = String.format("数量: %d", stack.getCount());
        renderer.drawText(countLine, contentX, curY);
        curY += 18;

        // 重量
        int weight = (int) stack.getTotalWeightGrams();
        String weightLine = String.format("重量: %dg", weight);
        renderer.drawText(weightLine, contentX, curY);

        // 单件重量（多件时显示）
        if (stack.getCount() > 1) {
            int unitWeight = (int) stack.getType().getWeightGrams();
            renderer.setColor(new Color(120, 120, 140));
            renderer.drawText(String.format("(单件 %dg)", unitWeight),
                    contentX + renderer.getTextWidth(weightLine) + 8, curY);
            renderer.setColor(new Color(140, 200, 255));
        }
        curY += 18;

        // 体积
        double volume = stack.getTotalVolumeMl();
        if (volume > 0) {
            String volLine = String.format("体积: %.0fml", volume);
            renderer.drawText(volLine, contentX, curY);
            curY += 18;
        }

        // 营养信息（仅对可消耗物品）
        if (stack.getType().isConsumable()) {
            curY += 4;
            renderer.setColor(new Color(100, 200, 140));
            renderer.drawText("── 营养 ──", contentX, curY);
            curY += 18;

            renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
            double cal = stack.getTotalCalories();
            double sat = stack.getTotalSatiety();
            double water = stack.getTotalWaterContent();

            if (cal > 0) {
                renderer.drawText(String.format("热量: %.0fkcal", cal), contentX, curY);
                curY += 18;
            }
            if (sat > 0) {
                renderer.drawText(String.format("饱腹: %.0f", sat), contentX, curY);
                curY += 18;
            }
            if (water > 0) {
                renderer.drawText(String.format("水分: %.0fml", water), contentX, curY);
                curY += 18;
            }
        }

        // 底部提示
        drawHintBar(renderer, "Esc 返回");
    }

    // ── 工具方法 ──────────────────────────────────

    /**
     * 将文本按像素宽度自动换行。
     * 按词（空格分隔）或按字符（中文无空格时逐字符）拆分。
     *
     * @param renderer 渲染器（用于测量文本宽度）
     * @param text     原始文本
     * @param maxWidth 最大行宽（像素）
     * @return 换行后的文本行列表
     */
    private List<String> wrapText(Renderer renderer, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        // 按换行符预分割
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            // 按空格拆分为词
            String[] words = paragraph.split("(?<=\\s)|(?=\\s)");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine.toString() + word;
                if (renderer.getTextWidth(testLine) <= maxWidth) {
                    currentLine.append(word);
                } else {
                    // 当前行已满，保存并换行
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        // 单个词超宽，逐字符拆分
                        for (int i = 0; i < word.length(); ) {
                            int cp = word.codePointAt(i);
                            String ch = new String(Character.toChars(cp));
                            i += Character.charCount(cp);
                            if (renderer.getTextWidth(currentLine + ch) <= maxWidth) {
                                currentLine.append(ch);
                            } else {
                                if (currentLine.length() > 0) {
                                    lines.add(currentLine.toString());
                                    currentLine = new StringBuilder(ch);
                                } else {
                                    // 单个字符也超宽（极端情况），强制加入
                                    currentLine.append(ch);
                                    lines.add(currentLine.toString());
                                    currentLine = new StringBuilder();
                                }
                            }
                        }
                    }
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }
}
