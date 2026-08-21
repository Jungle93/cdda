package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.world.PlayerInventory;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.npc.Npc;
import com.github.game.cdda.npc.NpcManager;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.i18n.I18nManager;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.screen.Screen;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * NPC 交易界面（以物换物）。
 * <p>
 * 三栏布局：
 * <ul>
 *   <li>左栏：玩家背包（可选取物品放入中栏）</li>
 *   <li>中栏：玩家本次交易提供的物品</li>
 *   <li>右栏：NPC 本次交易的物品 + NPC 对玩家报价的态度（实时反馈）</li>
 * </ul>
 * <p>
 * 操作：
 * <ul>
 *   <li>Tab — 在左栏（背包）和中栏（提供物）之间切换焦点</li>
 *   <li>Enter — 左栏：添加物品到中栏；中栏：从提供列表移除物品</li>
 *   <li>←/→ — 在中栏调整选中物品的数量（±1）</li>
 *   <li>F — 确认交易（NPC 评价价值是否足够）</li>
 *   <li>Esc — 取消并离开交易界面</li>
 * </ul>
 * <p>
 * 注意：界面上不显示具体价值数值，玩家通过 NPC 态度来判断是否足够。
 */
public class TradeScreen extends Screen {

    // ── 内部数据类 ──────────────────────────────────────

    /**
     * 交易中某一物品的选择记录（可共享于 wantedItems 和 offeredItems）。
     * 持有原始 ItemStack 的引用（不复制数据），只记录本次交易的数量。
     */
    public static class TradeSelection {
        private final ItemStack stack;
        private int count;

        public TradeSelection(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }

        public ItemStack getStack() { return stack; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = Math.max(0, count); }
    }

    // ── 焦点枚举 ────────────────────────────────────────

    private enum Focus {
        PLAYER_ITEMS,   // 左栏：玩家背包
        OFFERED_ITEMS   // 中栏：玩家提供的物品
    }

    // ── 字段 ────────────────────────────────────────────

    private final GameWorld world;
    private final Player player;
    private final NpcManager npcManager;
    private final Npc targetNpc;

    /** NPC 本次交易的物品（来自 NpcInteractionScreen 传入的 wantedItems） */
    private final List<TradeSelection> wantedItems;

    /** 玩家已选中的交易提供物品（中栏） */
    private final List<TradeSelection> offeredItems = new ArrayList<>();

    /** 当前焦点面板 */
    private Focus currentFocus = Focus.PLAYER_ITEMS;

    /** 左栏光标索引 */
    private int playerCursor = 0;
    /** 中栏光标索引 */
    private int offeredCursor = 0;

    /** NPC 实时态度消息（根据价值比动态计算） */
    private String npcFeedback;

    /** 交易成功标志（用于关闭界面） */
    private boolean tradeSuccess = false;

    /** 滚动偏移（左栏） */
    private int playerScroll = 0;

    // ── 构造 ────────────────────────────────────────────

    /**
     * 创建交易界面。
     *
     * @param engine      游戏引擎
     * @param world       游戏世界
     * @param targetNpc   交易 NPC
     * @param wantedItems 玩家在 NpcInteractionScreen 中选择的 NPC 物品
     */
    public TradeScreen(GameEngine engine, GameWorld world, Npc targetNpc,
                       List<TradeSelection> wantedItems) {
        super(engine);
        this.world = world;
        this.player = world.getPlayer();
        this.npcManager = world.getNpcManager();
        this.targetNpc = targetNpc;
        this.wantedItems = wantedItems;
        updateNpcFeedback();
    }

    // ── 价值计算 ────────────────────────────────────────

    /** 计算一组选择的总价值 */
    private int calculateValue(List<TradeSelection> items) {
        int total = 0;
        for (TradeSelection sel : items) {
            total += sel.getStack().getType().getValue() * sel.getCount();
        }
        return total;
    }

    /** 根据当前报价更新 NPC 态度消息（不显示具体数值） */
    private void updateNpcFeedback() {
        int offeredValue = calculateValue(offeredItems);
        int wantedValue = calculateValue(wantedItems);

        if (wantedValue == 0) {
            // NPC 没有目标物品，直接通过
            npcFeedback = t("ui.trade.feedback.full");
            return;
        }

        double ratio = (double) offeredValue / wantedValue;

        if (ratio >= 1.0) {
            npcFeedback = t("ui.trade.feedback.full");
        } else if (ratio >= 0.8) {
            npcFeedback = t("ui.trade.feedback.almost");
        } else if (ratio >= 0.5) {
            npcFeedback = t("ui.trade.feedback.half");
        } else if (ratio >= 0.3) {
            npcFeedback = t("ui.trade.feedback.thirty");
        } else if (ratio >= 0.1) {
            npcFeedback = t("ui.trade.feedback.ten");
        } else {
            npcFeedback = t("ui.trade.feedback.no");
        }
    }

    /** i18n 快捷方法 */
    private String t(String key) {
        try {
            I18nManager i18n = com.github.game.engine.core.EngineServices.i18n;
            if (i18n != null) return i18n.t(key);
        } catch (Exception ignored) {}
        return key;
    }

    // ── 输入处理 ────────────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        if (tradeSuccess) return; // 交易完成后不再响应

        switch (keyCode) {
            case KeyEvent.VK_TAB -> {
                // 切换焦点（Tab 或 Q/W 键都可以切换）
                currentFocus = (currentFocus == Focus.PLAYER_ITEMS)
                        ? Focus.OFFERED_ITEMS : Focus.PLAYER_ITEMS;
            }
            case KeyEvent.VK_Q -> {
                // Q 键也可以切换到左栏（防止 Tab 被系统/IDE 拦截）
                currentFocus = Focus.PLAYER_ITEMS;
            }
            case KeyEvent.VK_E -> {
                // E 键也可以切换到中栏
                currentFocus = Focus.OFFERED_ITEMS;
            }
            case KeyEvent.VK_UP -> handleUp();
            case KeyEvent.VK_DOWN -> handleDown();
            case KeyEvent.VK_LEFT -> handleLeft();
            case KeyEvent.VK_RIGHT -> handleRight();
            case KeyEvent.VK_ENTER -> handleEnter();
            case KeyEvent.VK_F -> handleConfirm();
            case KeyEvent.VK_ESCAPE -> handleCancel();
        }
    }

    /** 光标上移 */
    private void handleUp() {
        if (currentFocus == Focus.PLAYER_ITEMS) {
            int count = player.getInventory().getItems().size();
            if (count > 0) playerCursor = (playerCursor - 1 + count) % count;
        } else {
            int count = offeredItems.size();
            if (count > 0) offeredCursor = (offeredCursor - 1 + count) % count;
        }
    }

    /** 光标下移 */
    private void handleDown() {
        if (currentFocus == Focus.PLAYER_ITEMS) {
            int count = player.getInventory().getItems().size();
            if (count > 0) playerCursor = (playerCursor + 1) % count;
        } else {
            int count = offeredItems.size();
            if (count > 0) offeredCursor = (offeredCursor + 1) % count;
        }
    }

    /** 左键（中栏：减少数量） */
    private void handleLeft() {
        if (currentFocus == Focus.OFFERED_ITEMS && !offeredItems.isEmpty()) {
            TradeSelection sel = offeredItems.get(offeredCursor);
            sel.setCount(sel.getCount() - 1);
            if (sel.getCount() <= 0) {
                offeredItems.remove(offeredCursor);
                if (offeredCursor >= offeredItems.size() && !offeredItems.isEmpty()) {
                    offeredCursor = offeredItems.size() - 1;
                }
            }
            updateNpcFeedback();
        }
    }

    /** 右键（中栏：增加数量，不能超过拥有数量） */
    private void handleRight() {
        if (currentFocus == Focus.OFFERED_ITEMS && !offeredItems.isEmpty()) {
            TradeSelection sel = offeredItems.get(offeredCursor);
            int maxCount = getPlayerAvailableCount(sel.getStack().getType().getName());
            if (sel.getCount() < maxCount) {
                sel.setCount(sel.getCount() + 1);
                updateNpcFeedback();
            }
        }
    }

    /** Enter：添加/移除物品 */
    private void handleEnter() {
        if (currentFocus == Focus.PLAYER_ITEMS) {
            // 从左栏添加物品到中栏
            var items = player.getInventory().getItems();
            if (items.isEmpty() || playerCursor >= items.size()) return;
            ItemStack stack = items.get(playerCursor);
            int available = getPlayerAvailableCount(stack.getType().getName());
            if (available <= 0) {
                GameLog.getInstance().log("该物品已全部用于交易");
                return;
            }
            // 检查中栏是否已有该物品
            for (int i = 0; i < offeredItems.size(); i++) {
                TradeSelection sel = offeredItems.get(i);
                if (sel.getStack().getType() == stack.getType()) {
                    if (sel.getCount() < available) {
                        sel.setCount(sel.getCount() + 1);
                        updateNpcFeedback();
                    } else {
                        GameLog.getInstance().log("已达到该物品的最大可交易数量");
                    }
                    return;
                }
            }
            // 新增
            offeredItems.add(new TradeSelection(stack, 1));
            offeredCursor = offeredItems.size() - 1;  // 光标跳到新添加的物品
            updateNpcFeedback();
        } else {
            // 从中栏移除物品（Enter 效果同左键，减1）
            handleLeft();
        }
    }

    /** F：确认交易 */
    private void handleConfirm() {
        int offeredValue = calculateValue(offeredItems);
        int wantedValue = calculateValue(wantedItems);

        if (offeredItems.isEmpty()) {
            GameLog.getInstance().log("还没有提供任何物品");
            return;
        }
        if (wantedItems.isEmpty()) {
            GameLog.getInstance().log("没有想要交换的物品");
            return;
        }

        if (offeredValue >= wantedValue) {
            // 交易成功！
            executeTrade();
            tradeSuccess = true;
            GameLog.getInstance().log("交易成功！");
            targetNpc.getSocial().adjustAttitude(2);
            // 延迟关闭（让玩家看到成功状态）
        } else {
            // NPC 拒绝
            GameLog.getInstance().log(targetNpc.getName() + "：" + t("ui.trade.not_enough"));
        }
    }

    /** Esc：取消交易 */
    private void handleCancel() {
        npcManager.endInteraction();
        engine.getScreenManager().popScreen();
    }

    // ── 物品操作辅助 ────────────────────────────────────

    /**
     * 查询玩家背包中某物品可供交易的数量（总数量 - 已在 offeredItems 中的数量）。
     */
    private int getPlayerAvailableCount(String itemName) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getItems()) {
            if (stack.getType().getName().equals(itemName)) {
                total += stack.getCount();
            }
        }
        int offered = 0;
        for (TradeSelection sel : offeredItems) {
            if (sel.getStack().getType().getName().equals(itemName)) {
                offered += sel.getCount();
            }
        }
        return total - offered;
    }

    /**
     * 执行交易：转移物品。
     * 1. 从玩家背包移除 offeredItems
     * 2. 从 NPC 背包移除 wantedItems
     * 3. 向玩家背包添加 wantedItems
     * 4. 向 NPC 背包添加 offeredItems
     */
    private void executeTrade() {
        PlayerInventory playerInv = player.getInventory();

        // 从玩家背包移除 offeredItems
        for (TradeSelection sel : offeredItems) {
            String itemName = sel.getStack().getType().getName();
            int remaining = sel.getCount();
            Iterator<ItemStack> it = playerInv.getItems().iterator();
            while (it.hasNext() && remaining > 0) {
                ItemStack stack = it.next();
                if (stack.getType().getName().equals(itemName)) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.setCount(stack.getCount() - take);
                    remaining -= take;
                    if (stack.getCount() <= 0) it.remove();
                }
            }
        }

        // 从 NPC 背包移除 wantedItems，并添加到玩家背包
        for (TradeSelection sel : wantedItems) {
            String itemName = sel.getStack().getType().getName();
            int remaining = sel.getCount();

            // 从 NPC 移除
            Iterator<ItemStack> it = targetNpc.getInventory().getItems().iterator();
            while (it.hasNext() && remaining > 0) {
                ItemStack stack = it.next();
                if (stack.getType().getName().equals(itemName)) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.setCount(stack.getCount() - take);
                    remaining -= take;
                    if (stack.getCount() <= 0) it.remove();
                }
            }

            // 添加到玩家背包
            ItemStack toAdd = new ItemStack(sel.getStack().getType(), sel.getCount());
            playerInv.addItem(toAdd);
        }

        // 将玩家提供的物品添加到 NPC 背包
        for (TradeSelection sel : offeredItems) {
            ItemStack toAdd = new ItemStack(sel.getStack().getType(), sel.getCount());
            targetNpc.getInventory().addItem(toAdd);
        }
    }

    // ── 渲染 ────────────────────────────────────────────

    @Override
    public void update(long deltaTime) {
        // 交易成功后短暂延迟关闭
        if (tradeSuccess) {
            // 直接关闭（延迟效果可由 GameLog 消息体现）
            npcManager.endInteraction();
            engine.getScreenManager().popScreen();
        }
    }

    @Override
    public void render(Renderer renderer) {
        int width = getWidth();
        int height = getHeight();

        // 背景
        renderer.setColor(new Color(0, 0, 20, 235));
        renderer.fillRect(0, 0, width, height);

        // ── 标题栏 ──
        renderer.setFont(new Font("Monospaced", Font.BOLD, 18));
        renderer.setColor(new Color(255, 200, 100));
        String title = "[ " + t("ui.trade.title") + " ]";
        renderer.drawText(title, (width - renderer.getTextWidth(title)) / 2, 26);

        // NPC 信息
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
        renderer.setColor(new Color(180, 220, 180));
        String npcInfo = "NPC: " + targetNpc.getName() + "  [" + targetNpc.getTypeDisplayName() + "]";
        renderer.drawText(npcInfo, 15, 48);

        // ── 三栏布局 ──
        int panelTop = 68;
        int panelHeight = height - panelTop - 36;
        int leftWidth = width / 3;
        int midWidth = width / 3;
        int rightWidth = width - leftWidth - midWidth;
        int leftX = 0;
        int midX = leftWidth;
        int rightX = leftWidth + midWidth;

        // 绘制列分隔线
        renderer.setColor(new Color(60, 80, 60));
        renderer.drawLine(leftWidth, panelTop - 4, leftWidth, height - 36);
        renderer.drawLine(leftWidth + midWidth, panelTop - 4, leftWidth + midWidth, height - 36);

        // 绘制水平分隔线（标题与内容）
        renderer.drawLine(0, panelTop - 4, width, panelTop - 4);

        int lineHeight = 18;
        int fontSize = 12;
        int contentPadding = 6;

        // ── 左栏：我的物品 ──
        renderLeftPanel(renderer, leftX + contentPadding, panelTop, leftWidth - contentPadding * 2,
                panelHeight, lineHeight, fontSize);

        // ── 中栏：我出的 ──
        renderMiddlePanel(renderer, midX + contentPadding, panelTop, midWidth - contentPadding * 2,
                panelHeight, lineHeight, fontSize);

        // ── 右栏：NPC 给的 + NPC 态度 ──
        renderRightPanel(renderer, rightX + contentPadding, panelTop, rightWidth - contentPadding * 2,
                panelHeight, lineHeight, fontSize);

        // ── 底部提示栏 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        renderer.setColor(Color.GRAY);
        String hint = "Tab/Q/E 切换 | Enter 选择/移除 | ←→ 调整数量 | F 确认交易 | Esc 取消";
        renderer.drawText(hint, (width - renderer.getTextWidth(hint)) / 2, height - 14);
    }

    /** 渲染左栏：玩家背包 */
    private void renderLeftPanel(Renderer renderer, int x, int y, int w, int h,
                                  int lineHeight, int fontSize) {
        // 标题
        renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
        boolean focused = (currentFocus == Focus.PLAYER_ITEMS);
        renderer.setColor(focused ? new Color(255, 220, 100) : new Color(150, 180, 150));
        String header = t("ui.trade.my_items");
        renderer.drawText(header, x, y);
        int contentY = y + lineHeight + 4;

        // 物品列表
        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        var items = player.getInventory().getItems();
        if (items.isEmpty()) {
            renderer.setColor(Color.GRAY);
            renderer.drawText("（背包空空如也）", x, contentY);
            return;
        }

        int maxVisible = (h - lineHeight - 4) / lineHeight;
        // 更新滚动
        if (playerCursor >= maxVisible) {
            playerScroll = playerCursor - maxVisible + 1;
        } else {
            playerScroll = 0;
        }

        for (int i = 0; i < items.size(); i++) {
            int visibleIndex = i - playerScroll;
            if (visibleIndex < 0 || visibleIndex >= maxVisible) continue;

            ItemStack stack = items.get(i);
            boolean sel = (i == playerCursor && focused);
            int available = getPlayerAvailableCount(stack.getType().getName());
            String prefix = sel ? "▶ " : "  ";
            String suffix = available < stack.getCount() ? " (" + available + ")" : "";
            String line = prefix + stack.getType().getDisplayName()
                    + (stack.getCount() > 1 ? " ×" + stack.getCount() : "") + suffix;

            renderer.setColor(sel ? Color.YELLOW : new Color(200, 220, 200));
            renderer.drawText(line, x, contentY + visibleIndex * lineHeight);
        }
    }

    /** 渲染中栏：玩家提供的物品 */
    private void renderMiddlePanel(Renderer renderer, int x, int y, int w, int h,
                                    int lineHeight, int fontSize) {
        // 标题
        renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
        boolean focused = (currentFocus == Focus.OFFERED_ITEMS);
        renderer.setColor(focused ? new Color(255, 220, 100) : new Color(150, 180, 150));
        String header = t("ui.trade.i_offer");
        renderer.drawText(header, x, y);
        int contentY = y + lineHeight + 4;

        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        if (offeredItems.isEmpty()) {
            renderer.setColor(Color.GRAY);
            renderer.drawText("（尚未选择任何物品）", x, contentY);
            return;
        }

        int maxVisible = (h - lineHeight - 4) / lineHeight;
        for (int i = 0; i < offeredItems.size(); i++) {
            if (i >= maxVisible) break;
            TradeSelection sel = offeredItems.get(i);
            boolean sel2 = (i == offeredCursor && focused);
            String prefix = sel2 ? "▶ " : "  ";
            String line = prefix + sel.getStack().getType().getDisplayName()
                    + " ×" + sel.getCount();

            renderer.setColor(sel2 ? Color.YELLOW : new Color(220, 200, 150));
            renderer.drawText(line, x, contentY + i * lineHeight);
        }
    }

    /** 渲染右栏：NPC 本次交易的物品 + NPC 态度 */
    private void renderRightPanel(Renderer renderer, int x, int y, int w, int h,
                                   int lineHeight, int fontSize) {
        // 标题
        renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
        renderer.setColor(new Color(150, 180, 150));
        String header = t("ui.trade.npc_gives");
        renderer.drawText(header, x, y);
        int contentY = y + lineHeight + 4;

        // NPC 物品列表（只读展示）
        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        for (int i = 0; i < wantedItems.size(); i++) {
            TradeSelection sel = wantedItems.get(i);
            String line = "  " + sel.getStack().getType().getDisplayName()
                    + " ×" + sel.getCount();
            renderer.setColor(new Color(200, 200, 150));
            renderer.drawText(line, x, contentY + i * lineHeight);
        }

        // NPC 态度消息（显示在物品下方，醒目颜色）
        int feedbackY = contentY + Math.max(wantedItems.size(), 1) * lineHeight + lineHeight;
        renderer.setFont(new Font("Monospaced", Font.BOLD, 13));
        Color feedbackColor = getFeedbackColor();
        renderer.setColor(feedbackColor);
        // 多行换行处理
        String feedback = targetNpc.getName() + "：" + npcFeedback;
        renderer.drawText(feedback, x, feedbackY);
    }

    /** 根据 NPC 态度消息返回对应的显示颜色 */
    private Color getFeedbackColor() {
        if (npcFeedback == null) return Color.WHITE;
        if (npcFeedback.equals(t("ui.trade.feedback.full"))) {
            return new Color(100, 255, 100);  // 绿色：足够
        } else if (npcFeedback.equals(t("ui.trade.feedback.almost"))) {
            return new Color(180, 255, 100);  // 黄绿：接近
        } else if (npcFeedback.equals(t("ui.trade.feedback.half"))) {
            return new Color(255, 220, 100);  // 黄色：一半
        } else if (npcFeedback.equals(t("ui.trade.feedback.thirty"))) {
            return new Color(255, 160, 80);   // 橙色：三成
        } else if (npcFeedback.equals(t("ui.trade.feedback.ten"))) {
            return new Color(255, 100, 80);   // 红色偏橙：一成
        } else {
            return new Color(255, 80, 80);    // 红色：完全不够
        }
    }

}
