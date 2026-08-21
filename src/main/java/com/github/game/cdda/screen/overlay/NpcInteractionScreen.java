package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.npc.Npc;
import com.github.game.cdda.npc.NpcManager;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * NPC 交互菜单。
 * 按 C 键打开，提供多级菜单：对话、交易、观察。
 *
 * <p>主菜单选项：
 * <ol>
 *   <li>对话 — 显示 NPC 对话内容</li>
 *   <li>交易 — 选择想要的 NPC 物品，进入以物换物交易界面</li>
 *   <li>观察 — 获取 NPC 信息</li>
 *   <li>离开 — 关闭菜单</li>
 * </ol>
 */
public class NpcInteractionScreen extends MenuScreen {

    /** 子菜单类型 */
    private enum SubMenu {
        MAIN,          // 主菜单
        DIALOG,        // 对话子菜单
        TRADE_SELECT,  // 选择想要的 NPC 物品（进入 TradeScreen 前的选择步骤）
        INFO           // 信息子菜单
    }

    private static final String TITLE = "NPC 交互";

    /** 主菜单项 */
    private static final String[] MAIN_ACTIONS = {
            "对话",
            "交易",
            "观察",
            "离开"
    };

    /** 对话子菜单项（动态生成） */
    private String[] dialogActions;

    /** 信息子菜单：固定选项 */
    private static final String[] INFO_ACTIONS = {
            "返回"
    };

    private final GameWorld world;
    private final Player player;
    private final NpcManager npcManager;

    /** 当前子菜单 */
    private SubMenu currentSubMenu = SubMenu.MAIN;

    /** 当前交互的 NPC（由光标选中，构造时传入） */
    private final Npc targetNpc;

    /** 玩家在 TRADE_SELECT 中已选中的想要物品（ItemStack + 数量） */
    private final List<TradeScreen.TradeSelection> wantedItems = new ArrayList<>();

    /** TRADE_SELECT 面板的光标索引 */
    private int tradeSelectCursor = 0;

    /** TRADE_SELECT 面板的滚动偏移 */
    private int tradeSelectScroll = 0;

    /**
     * 创建 NPC 交互菜单。
     *
     * @param engine    游戏引擎
     * @param world     游戏世界
     * @param targetNpc 光标选中的 NPC（可为 null，表示无 NPC 可交互）
     */
    public NpcInteractionScreen(GameEngine engine, GameWorld world, Npc targetNpc) {
        super(engine);
        this.world = world;
        this.player = world.getPlayer();
        this.npcManager = world.getNpcManager();
        this.targetNpc = targetNpc;
    }

    @Override
    protected int getItemCount() {
        return switch (currentSubMenu) {
            case MAIN -> MAIN_ACTIONS.length;
            case DIALOG -> dialogActions != null ? dialogActions.length : 1;
            case TRADE_SELECT -> getNpcGoodsCount();
            case INFO -> INFO_ACTIONS.length;
        };
    }

    /** 获取 NPC 货物种类数 */
    private int getNpcGoodsCount() {
        if (targetNpc == null || !targetNpc.isAlive()) return 0;
        return targetNpc.getInventory().getItems().size();
    }

    /**
     * 获取指定索引的菜单文本（内部方法，非覆写）。
     */
    protected String getItemText(int index) {
        return switch (currentSubMenu) {
            case MAIN -> MAIN_ACTIONS[index];
            case DIALOG -> dialogActions != null && index < dialogActions.length
                    ? dialogActions[index] : "无对话内容";
            case TRADE_SELECT -> formatTradeSelectItem(index);
            case INFO -> INFO_ACTIONS[index];
        };
    }

    /** 格式化 TRADE_SELECT 中的物品条目（显示已选数量） */
    private String formatTradeSelectItem(int index) {
        var items = targetNpc.getInventory().getItems();
        if (index >= items.size()) return "";
        ItemStack stack = items.get(index);
        int selectedCount = getWantedCount(stack.getType().getId());
        String suffix = selectedCount > 0 ? "  [已选 ×" + selectedCount + "]" : "";
        return String.format("%s ×%d%s",
                stack.getType().getDisplayName(),
                stack.getCount(),
                suffix);
    }

    /** 查询某个物品在 wantedItems 中已选数量 */
    private int getWantedCount(int itemId) {
        for (TradeScreen.TradeSelection sel : wantedItems) {
            if (sel.getStack().getType().getId() == itemId) {
                return sel.getCount();
            }
        }
        return 0;
    }

    /** 在 wantedItems 中为某物品增加数量（+1），返回是否成功 */
    private boolean addToWanted(int npcItemIndex) {
        var items = targetNpc.getInventory().getItems();
        if (npcItemIndex >= items.size()) return false;
        ItemStack npcStack = items.get(npcItemIndex);

        // 检查是否已在 wantedItems 中（用 ID 比较，避免引用不一致问题）
        for (TradeScreen.TradeSelection sel : wantedItems) {
            if (sel.getStack().getType().getId() == npcStack.getType().getId()) {
                if (sel.getCount() < npcStack.getCount()) {
                    sel.setCount(sel.getCount() + 1);
                    return true;
                }
                GameLog.getInstance().log("已达到该物品的可用数量上限");
                return false; // 已达上限
            }
        }
        // 新增
        wantedItems.add(new TradeScreen.TradeSelection(npcStack, 1));
        return true;
    }

    /** 在 wantedItems 中为某物品减少数量（-1），为 0 则移除 */
    private void removeFromWanted(int npcItemIndex) {
        var items = targetNpc.getInventory().getItems();
        if (npcItemIndex >= items.size()) return;
        ItemStack npcStack = items.get(npcItemIndex);

        for (int i = 0; i < wantedItems.size(); i++) {
            TradeScreen.TradeSelection sel = wantedItems.get(i);
            if (sel.getStack().getType().getId() == npcStack.getType().getId()) {
                sel.setCount(sel.getCount() - 1);
                if (sel.getCount() <= 0) {
                    wantedItems.remove(i);
                }
                return;
            }
        }
    }

    @Override
    protected void renderMenu(Renderer renderer) {

        // 半透明深色背景
        renderer.setColor(new Color(0, 0, 20, 230));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int height = getHeight();
        int fontSize = 13;
        int lineHeight = 20;

        // ── 标题 ──
        renderer.setFont(new Font("Monospaced", Font.BOLD, 18));
        renderer.setColor(new Color(0, 255, 128));
        String title = "[ " + TITLE + " ]";
        renderer.drawText(title, (width - renderer.getTextWidth(title)) / 2, 28);

        // ── NPC 信息栏 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        int y = 52;

        if (targetNpc != null && targetNpc.isAlive()) {
            renderer.setColor(new Color(180, 220, 180));
            renderer.drawText("NPC: " + targetNpc.getName()
                    + "  [" + targetNpc.getTypeDisplayName() + "]", 15, y);
            y += lineHeight;

            renderer.setColor(targetNpc.getRegion().getColorForType(targetNpc.getNpcType()));
            renderer.drawText("地域: " + targetNpc.getRegion().name
                    + "  |  态度: " + targetNpc.getAttitudeDescription(), 15, y);
            y += lineHeight;
        } else {
            renderer.setColor(Color.GRAY);
            renderer.drawText("附近没有可交互的 NPC", 15, y);
            y += lineHeight;
        }

        // ── 分隔线 ──
        renderer.setColor(new Color(100, 180, 100));
        String divider = currentSubMenu == SubMenu.MAIN ? "── 选择操作 ──────────────────────────"
                : currentSubMenu == SubMenu.DIALOG ? "── 对话 ──────────────────────────────────"
                : currentSubMenu == SubMenu.TRADE_SELECT ? "── 选择想要的物品（F 确认交易）───────────"
                : "── 观察信息 ──────────────────────────────";
        renderer.drawText(divider, 15, y);
        y += lineHeight + 2;

        // ── 菜单项 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        int maxVisible = (height - y - 40) / lineHeight;

        if (currentSubMenu == SubMenu.INFO && targetNpc != null) {
            renderInfoPanel(renderer, y, lineHeight, maxVisible);
        } else if (currentSubMenu == SubMenu.TRADE_SELECT) {
            renderTradeSelectPanel(renderer, y, lineHeight, maxVisible);
        } else {
            renderMenuItems(renderer, y, lineHeight, maxVisible);
        }

        // ── 底部提示 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        renderer.setColor(Color.GRAY);
        String hint = currentSubMenu == SubMenu.MAIN
                ? "↑↓ 选择 | Enter 确认 | Esc 关闭"
                : currentSubMenu == SubMenu.TRADE_SELECT
                ? "↑↓ 选择 | Enter 添加 | D 移除 | F 确认 | Esc 返回"
                : "↑↓ 选择 | Enter 确认 | Esc 返回";
        renderer.drawText(hint, (width - renderer.getTextWidth(hint)) / 2, height - 12);
    }

    /**
     * 渲染菜单项列表。
     */
    private void renderMenuItems(Renderer renderer, int startY, int lineHeight, int maxVisible) {
        int count = getItemCount();
        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        for (int i = 0; i < count; i++) {
            int visibleIndex = i - scrollOffset;
            if (visibleIndex < 0 || visibleIndex >= maxVisible) continue;

            boolean sel = (i == selectedIndex);
            String prefix = sel ? "▶ " : "  ";
            String line = prefix + getItemText(i);

            renderer.setColor(sel ? Color.YELLOW : new Color(180, 220, 180));
            renderer.drawText(line, 15, startY + visibleIndex * lineHeight);
        }
    }

    /**
     * 渲染物品选择面板（TRADE_SELECT 专用，使用 tradeSelectCursor）。
     */
    private void renderTradeSelectPanel(Renderer renderer, int startY, int lineHeight, int maxVisible) {
        if (targetNpc == null || !targetNpc.isAlive()) {
            renderer.setColor(Color.GRAY);
            renderer.drawText("无法交易", 15, startY);
            return;
        }

        var items = targetNpc.getInventory().getItems();
        if (items.isEmpty()) {
            renderer.setColor(Color.GRAY);
            renderer.drawText("这个 NPC 没有携带任何物品", 15, startY);
            return;
        }

        // 更新滚动偏移
        if (tradeSelectCursor >= maxVisible) {
            tradeSelectScroll = tradeSelectCursor - maxVisible + 1;
        } else {
            tradeSelectScroll = 0;
        }

        for (int i = 0; i < items.size(); i++) {
            int visibleIndex = i - tradeSelectScroll;
            if (visibleIndex < 0 || visibleIndex >= maxVisible) continue;

            boolean sel = (i == tradeSelectCursor);
            ItemStack stack = items.get(i);
            int selectedCount = getWantedCount(stack.getType().getId());
            String suffix = selectedCount > 0 ? "  [已选 ×" + selectedCount + "]" : "";
            String prefix = sel ? "▶ " : "  ";
            String line = prefix + stack.getType().getDisplayName() + " ×" + stack.getCount() + suffix;

            renderer.setColor(sel ? Color.YELLOW : new Color(180, 220, 180));
            renderer.drawText(line, 15, startY + visibleIndex * lineHeight);
        }

        // 已选提示
        if (!wantedItems.isEmpty()) {
            renderer.setColor(new Color(255, 200, 100));
            int totalWanted = wantedItems.stream().mapToInt(TradeScreen.TradeSelection::getCount).sum();
            renderer.drawText("已选 " + totalWanted + " 件物品，按 F 进入交易",
                    15, startY + items.size() * lineHeight + 4);
        }
    }

    /**
     * 渲染信息面板（直接显示 NPC 信息，不显示菜单项）。
     */
    private void renderInfoPanel(Renderer renderer, int startY, int lineHeight, int maxVisible) {
        if (targetNpc == null || !targetNpc.isAlive()) {
            renderer.setColor(Color.GRAY);
            renderer.drawText("没有可观察的对象", 15, startY);
            return;
        }

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));

        String[] lines = targetNpc.getObservationText().split("\n");
        int y = startY;
        for (int i = 0; i < Math.min(lines.length, maxVisible); i++) {
            renderer.setColor(new Color(200, 200, 180));
            renderer.drawText(lines[i], 15, y);
            y += lineHeight;
        }

        // 显示"返回"按钮
        y += 4;
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
        renderer.setColor(Color.YELLOW);
        renderer.drawText("  ▶ 返回", 15, y);
    }

    @Override
    protected void onSelect(int index) {
        if (targetNpc == null || !targetNpc.isAlive()) {
            GameLog.getInstance().log("附近没有可交互的 NPC");
            return;
        }

        switch (currentSubMenu) {
            case MAIN -> handleMainMenuItem(index);
            case DIALOG -> handleDialogMenuItem(index);
            case TRADE_SELECT -> handleTradeSelectItem(index);
            case INFO -> onCancel();
        }
    }

    @Override
    public void onKeyPressed(int keyCode) {
        if (currentSubMenu == SubMenu.TRADE_SELECT) {
            handleTradeSelectKey(keyCode);
            return;
        }
        super.onKeyPressed(keyCode);
    }

    /** 处理 TRADE_SELECT 面板的按键输入 */
    private void handleTradeSelectKey(int keyCode) {
        int count = getNpcGoodsCount();
        if (count == 0) return;

        switch (keyCode) {
            case KeyEvent.VK_UP -> {
                tradeSelectCursor = (tradeSelectCursor - 1 + count) % count;
            }
            case KeyEvent.VK_DOWN -> {
                tradeSelectCursor = (tradeSelectCursor + 1) % count;
            }
            case KeyEvent.VK_ENTER -> {
                // 添加到 wantedItems
                if (addToWanted(tradeSelectCursor)) {
                    GameLog.getInstance().log("已选择: " +
                            targetNpc.getInventory().getItems().get(tradeSelectCursor).getType().getDisplayName());
                } else {
                    GameLog.getInstance().log("已达到该物品的可用数量上限");
                }
            }
            case KeyEvent.VK_D -> {
                // 移除 wantedItems
                removeFromWanted(tradeSelectCursor);
            }
            case KeyEvent.VK_F -> {
                // 确认选择，进入 TradeScreen
                if (wantedItems.isEmpty()) {
                    GameLog.getInstance().log("还没有选择任何物品");
                    return;
                }
                // 验证 NPC 是否还有这些物品（防止 NPC 背包变动导致不一致）
                for (TradeScreen.TradeSelection sel : wantedItems) {
                    boolean found = false;
                    for (ItemStack npcStack : targetNpc.getInventory().getItems()) {
                        if (npcStack.getType().getId() == sel.getStack().getType().getId() && npcStack.getCount() >= sel.getCount()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        GameLog.getInstance().log("NPC 已没有足够的 " + sel.getStack().getType().getDisplayName());
                        return;
                    }
                }
                npcManager.startInteraction(targetNpc);
                engine.getScreenManager().pushScreen(
                        new TradeScreen(engine, world, targetNpc, wantedItems));
            }
            case KeyEvent.VK_ESCAPE -> {
                // 清空选择，返回主菜单
                wantedItems.clear();
                currentSubMenu = SubMenu.MAIN;
                selectedIndex = 0;
            }
        }
    }

    /**
     * 处理主菜单项选择。
     */
    private void handleMainMenuItem(int index) {
        switch (index) {
            case 0 -> { // 对话
                currentSubMenu = SubMenu.DIALOG;
                selectedIndex = 0;
                List<String> dialogs = targetNpc.getDialogLines();
                dialogActions = dialogs.toArray(new String[0]);
            }
            case 1 -> { // 交易 — 进入物品选择子流程
                if (targetNpc.getNpcType() == com.github.game.cdda.npc.NpcType.HOSTILE) {
                    GameLog.getInstance().log(targetNpc.getName() + " 不愿意和你交易！");
                    return;
                }
                // 检查 NPC 是否有物品可交易
                if (targetNpc.getInventory().getItems().isEmpty()) {
                    GameLog.getInstance().log(targetNpc.getName() + " 没有携带任何物品");
                    return;
                }
                currentSubMenu = SubMenu.TRADE_SELECT;
                tradeSelectCursor = 0;
                wantedItems.clear();
            }
            case 2 -> { // 观察
                currentSubMenu = SubMenu.INFO;
                selectedIndex = 0;
            }
            case 3 -> onCancel(); // 离开
        }
    }

    /**
     * 处理对话菜单项选择。
     */
    private void handleDialogMenuItem(int index) {
        if (dialogActions == null || index >= dialogActions.length) return;

        String dialog = dialogActions[index];
        GameLog.getInstance().log(targetNpc.getName() + "：" + dialog);
        targetNpc.getSocial().adjustAttitude(2); // 对话增加好感

        // 对话后返回主菜单
        currentSubMenu = SubMenu.MAIN;
        selectedIndex = 0;
    }

    /** 处理 TRADE_SELECT 中 Enter 确认（兼容 MenuScreen 的 onSelect） */
    private void handleTradeSelectItem(int index) {
        // Enter 在 TRADE_SELECT 中 = 添加物品到 wantedItems
        if (addToWanted(index)) {
            GameLog.getInstance().log("已选择: " +
                    targetNpc.getInventory().getItems().get(index).getType().getDisplayName());
        } else {
            GameLog.getInstance().log("已达到该物品的可用数量上限");
        }
    }

    @Override
    protected void onCancel() {
        if (currentSubMenu == SubMenu.TRADE_SELECT) {
            wantedItems.clear();
            currentSubMenu = SubMenu.MAIN;
            selectedIndex = 0;
            return;
        }

        if (currentSubMenu != SubMenu.MAIN) {
            // 返回主菜单
            currentSubMenu = SubMenu.MAIN;
            selectedIndex = 0;
            npcManager.endInteraction();
            return;
        }

        npcManager.endInteraction();
        engine.getScreenManager().popScreen();
    }
}
