package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.npc.Npc;
import com.github.game.cdda.npc.NpcManager;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.util.List;

/**
 * NPC 交互菜单。
 * 按 C 键打开，提供多级菜单：对话、交易、观察。
 *
 * <p>主菜单选项：
 * <ol>
 *   <li>对话 — 显示 NPC 对话内容</li>
 *   <li>交易 — 查看 NPC 背包商品（可买卖）</li>
 *   <li>观察 — 获取 NPC 信息</li>
 *   <li>离开 — 关闭菜单</li>
 * </ol>
 */
public class NpcInteractionScreen extends MenuScreen {

    /** 子菜单类型 */
    private enum SubMenu {
        MAIN,       // 主菜单
        DIALOG,     // 对话子菜单
        TRADE,      // 交易子菜单
        INFO        // 信息子菜单
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

    /** 交易子菜单项：显示 NPC 背包物品 */
    private String[] tradeActions;

    /** 信息子菜单：固定选项 */
    private static final String[] INFO_ACTIONS = {
            "返回"
    };

    private final GameWorld world;
    private final Player player;
    private final NpcManager npcManager;

    /** 当前子菜单 */
    private SubMenu currentSubMenu = SubMenu.MAIN;

    /** 当前交互的 NPC */
    private Npc targetNpc;

    public NpcInteractionScreen(GameEngine engine, GameWorld world) {
        super(engine);
        this.world = world;
        this.player = world.getPlayer();
        this.npcManager = world.getNpcManager();
    }

    @Override
    protected int getItemCount() {
        return switch (currentSubMenu) {
            case MAIN -> MAIN_ACTIONS.length;
            case DIALOG -> dialogActions != null ? dialogActions.length : 1;
            case TRADE -> tradeActions != null ? tradeActions.length : 1;
            case INFO -> INFO_ACTIONS.length;
        };
    }

    /**
     * 获取指定索引的菜单文本（内部方法，非覆写）。
     */
    protected String getItemText(int index) {
        return switch (currentSubMenu) {
            case MAIN -> MAIN_ACTIONS[index];
            case DIALOG -> dialogActions != null && index < dialogActions.length
                    ? dialogActions[index] : "无对话内容";
            case TRADE -> tradeActions != null && index < tradeActions.length
                    ? tradeActions[index] : "背包空空如也";
            case INFO -> INFO_ACTIONS[index];
        };
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 找到最近的 NPC
        if (targetNpc == null || !targetNpc.isAlive()) {
            targetNpc = npcManager.findNearestNpcToPlayer();
        }

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
                : currentSubMenu == SubMenu.TRADE ? "── 交易 ──────────────────────────────────"
                : "── 观察信息 ──────────────────────────────";
        renderer.drawText(divider, 15, y);
        y += lineHeight + 2;

        // ── 菜单项 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        int maxVisible = (height - y - 40) / lineHeight;

        // 特殊处理：对话和信息子菜单需要显示额外内容
        if (currentSubMenu == SubMenu.INFO && targetNpc != null) {
            renderInfoPanel(renderer, y, lineHeight, maxVisible);
        } else if (currentSubMenu == SubMenu.TRADE) {
            renderTradePanel(renderer, y, lineHeight, maxVisible);
        } else {
            renderMenuItems(renderer, y, lineHeight, maxVisible);
        }

        // ── 底部提示 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        renderer.setColor(Color.GRAY);
        String hint = currentSubMenu == SubMenu.MAIN
                ? "↑↓ 选择 | Enter 确认 | Esc 关闭"
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
     * 渲染交易面板。
     */
    private void renderTradePanel(Renderer renderer, int startY, int lineHeight, int maxVisible) {
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

        // 构建交易菜单项
        tradeActions = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            tradeActions[i] = String.format("%s ×%d  (%dg)",
                    stack.getType().getDisplayName(),
                    stack.getCount(),
                    (int) stack.getTotalWeightGrams());
        }

        renderMenuItems(renderer, startY, lineHeight, maxVisible);
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
            case TRADE -> handleTradeMenuItem(index);
            case INFO -> onCancel();
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
            case 1 -> { // 交易
                currentSubMenu = SubMenu.TRADE;
                selectedIndex = 0;
                npcManager.startInteraction(targetNpc);
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

    /**
     * 处理交易菜单项选择。
     */
    private void handleTradeMenuItem(int index) {
        if (targetNpc == null) return;

        var items = targetNpc.getInventory().getItems();
        if (items.isEmpty() || index >= items.size()) return;

        ItemStack stack = items.get(index);
        // TODO: 实现购买逻辑（需要金币系统）
        GameLog.getInstance().log(
                String.format("%s 携带了 %s ×%d（暂未实现交易）",
                        targetNpc.getName(),
                        stack.getType().getDisplayName(),
                        stack.getCount()));
    }

    @Override
    protected void onCancel() {
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
