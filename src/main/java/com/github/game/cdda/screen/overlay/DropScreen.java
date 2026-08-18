package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.creature.Player;
import com.github.game.cdda.item.world.GroundItemManager;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.world.PlayerInventory;
import com.github.game.cdda.log.GameLog;
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
 * 丢弃物品界面。
 * 从背包中选择物品并指定数量丢弃到脚下。
 *
 * <p>操作流程（两步）：
 * <ol>
 *   <li>↑/↓ 选择物品，Enter 进入数量输入</li>
 *   <li>输入数字，Enter 确认丢弃</li>
 * </ol>
 *
 * <p>不可堆叠物品直接整件丢弃（跳过数量输入）。
 */
public class DropScreen extends MenuScreen {

    private static final String TITLE = "丢弃物品";

    private final Player player;
    private final GroundItemManager groundItemManager;
    private final PlayerInventory inventory;

    /** 每项当前选定的丢弃数量 */
    private int[] dropCounts;

    /** 是否处于数量输入模式 */
    private boolean quantityInputMode = false;
    /** 数量输入缓冲 */
    private StringBuilder numberBuffer = new StringBuilder();

    /** 是否正在查看物品详情 */
    private boolean showingDetail = false;

    public DropScreen(GameEngine engine, Player player, GroundItemManager groundItemManager) {
        super(engine);
        this.player = player;
        this.groundItemManager = groundItemManager;
        this.inventory = player.getInventory();
        refreshDropCounts();
    }

    private void refreshDropCounts() {
        int count = inventory.getItemCount();
        dropCounts = new int[count];
        for (int i = 0; i < count; i++) {
            dropCounts[i] = 1;
        }
    }

    private boolean isCurrentStackable() {
        if (selectedIndex < 0 || selectedIndex >= inventory.getItemCount()) return false;
        ItemStack stack = inventory.getItem(selectedIndex);
        return stack != null && isStackable(stack.getType());
    }

    private boolean isStackable(ItemType type) {
        return type.getMaxStackSize() > 1 && !type.isUnique();
    }

    @Override
    protected int getItemCount() {
        return inventory.getItemCount();
    }

    @Override
    public void onKeyPressed(int keyCode) {
        if (inventory.isEmpty()) {
            super.onKeyPressed(keyCode);
            return;
        }

        // ── 物品详情查看 ──
        if (showingDetail) {
            if (keyCode == KeyEvent.VK_ESCAPE) {
                showingDetail = false;
            }
            return;
        }

        // ── 数量输入模式 ──
        if (quantityInputMode) {
            handleQuantityInput(keyCode);
            return;
        }

        // ── 选择模式 ──
        switch (keyCode) {
            case KeyEvent.VK_E:
                // 查看物品详情
                if (selectedIndex >= 0 && selectedIndex < inventory.getItemCount()) {
                    showingDetail = true;
                }
                return;
            case KeyEvent.VK_ENTER:
                if (isCurrentStackable()) {
                    // 可堆叠物品：进入数量输入模式
                    quantityInputMode = true;
                    numberBuffer.setLength(0);
                } else {
                    // 不可堆叠物品：直接整件丢弃
                    dropCounts[selectedIndex] = 1;
                    confirmDrop();
                }
                return;
            default:
                super.onKeyPressed(keyCode);
        }
    }

    /** 数量输入模式下的按键处理 */
    private void handleQuantityInput(int keyCode) {
        int digit = toDigit(keyCode);
        if (digit >= 0) {
            numberBuffer.append(digit);
            int max = inventory.getItem(selectedIndex).getCount();
            int value = parseClamped(max);
            dropCounts[selectedIndex] = Math.max(1, Math.min(value, max));
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE:
                if (numberBuffer.length() > 0) {
                    numberBuffer.deleteCharAt(numberBuffer.length() - 1);
                    int max = inventory.getItem(selectedIndex).getCount();
                    dropCounts[selectedIndex] = numberBuffer.length() == 0
                            ? 1
                            : Math.max(1, Math.min(parseClamped(max), max));
                }
                return;
            case KeyEvent.VK_ENTER:
                quantityInputMode = false;
                numberBuffer.setLength(0);
                confirmDrop();
                return;
            case KeyEvent.VK_ESCAPE:
                quantityInputMode = false;
                numberBuffer.setLength(0);
                dropCounts[selectedIndex] = 1;
                return;
            default:
                break;
        }
    }

    private int toDigit(int keyCode) {
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
            return keyCode - KeyEvent.VK_0;
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9)
            return keyCode - KeyEvent.VK_NUMPAD0;
        return -1;
    }

    private int parseClamped(int max) {
        if (numberBuffer.length() == 0) return 1;
        try {
            return Integer.parseInt(numberBuffer.toString());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 物品详情查看模式
        if (showingDetail) {
            renderItemDetail(renderer);
            return;
        }

        renderer.setColor(new Color(10, 10, 20, 240));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int height = getHeight();

        drawTitle(renderer, TITLE, 24, height / 5);

        if (inventory.isEmpty()) {
            renderer.setColor(Color.GRAY);
            String msg = "背包中没有可丢弃的物品";
            renderer.drawText(msg, (width - renderer.getTextWidth(msg)) / 2, height / 2);
            drawHintBar(renderer, "Esc 返回");
            return;
        }

        // 重量信息
        int totalWeight = (int) inventory.getTotalWeight();
        int capacity = inventory.getCarryCapacity();
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
        renderer.setColor(Color.CYAN);
        String weightStr = String.format("背包: %dg / %dg", totalWeight, capacity);
        renderer.drawText(weightStr, (width - renderer.getTextWidth(weightStr)) / 2, height / 5 + 28);

        // 物品列表
        int listStartY = height / 5 + 55;
        int itemHeight = 24;
        int fontSize = 14;
        int maxVisible = (height * 3 / 5 - 60) / itemHeight;

        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        List<ItemStack> items = inventory.getItems();
        for (int i = 0; i < items.size(); i++) {
            int vi = i - scrollOffset;
            if (vi < 0 || vi >= maxVisible) continue;

            ItemStack stack = items.get(i);
            boolean sel = (i == selectedIndex);
            int dropCount = dropCounts[i];
            int totalCount = stack.getCount();
            int singleWeight = (int) (stack.getTotalWeightGrams() / totalCount);
            int dropWeight = singleWeight * dropCount;
            boolean stackable = isStackable(stack.getType());

            if (sel) {
                renderer.setColor(new Color(60, 60, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 14, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            String prefix = sel ? "▶ " : "  ";
            String name = stack.getType().getDisplayName();

            String nameStr = stackable
                    ? String.format("%s%s (共%d)", prefix, name, totalCount)
                    : String.format("%s%s", prefix, name);
            renderer.setColor(sel ? Color.YELLOW : Color.WHITE);
            renderer.drawText(nameStr, 30, listStartY + vi * itemHeight);

            if (stackable) {
                String countStr = String.format("丢弃: %d", dropCount);
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.LIGHT_GRAY);
                renderer.drawText(countStr, 30 + renderer.getTextWidth(nameStr) + 20,
                        listStartY + vi * itemHeight);
            } else {
                renderer.setColor(sel ? new Color(255, 200, 60) : Color.LIGHT_GRAY);
                renderer.drawText("(整件丢弃)", 30 + renderer.getTextWidth(nameStr) + 20,
                        listStartY + vi * itemHeight);
            }

            String wStr = String.format("(%dg)", dropWeight);
            renderer.setColor(Color.GRAY);
            renderer.drawText(wStr, width - 80, listStartY + vi * itemHeight);
        }

        // ── 数量输入提示框 ──
        if (quantityInputMode) {
            int promptY = height - 55;
            renderer.setColor(new Color(30, 30, 10, 200));
            renderer.fillRect(20, promptY - 18, width - 40, 36);

            renderer.setFont(new Font("Monospaced", Font.BOLD, 14));
            renderer.setColor(Color.YELLOW);
            String prompt = String.format("输入数量 (最多 %d): %s_",
                    inventory.getItem(selectedIndex).getCount(),
                    numberBuffer.length() > 0 ? numberBuffer.toString() : "");
            renderer.drawText(prompt, 30, promptY);
        }

        // 底部提示
        if (quantityInputMode) {
            drawHintBar(renderer, "数字键输入 | Backspace 删除 | Enter 确认 | Esc 取消");
        } else {
            String hint = isCurrentStackable()
                    ? "↑↓ 选择 | Enter 输入数量 | E 查看 | Esc 返回"
                    : "↑↓ 选择 | Enter 整件丢弃 | E 查看 | Esc 返回";
            drawHintBar(renderer, hint);
        }
    }

    private void confirmDrop() {
        if (selectedIndex < 0 || selectedIndex >= inventory.getItemCount()) return;

        int dropCount = dropCounts[selectedIndex];
        if (dropCount <= 0) return;

        ItemStack stack = inventory.getItem(selectedIndex);
        if (stack == null) return;

        ItemStack dropped = inventory.removeItem(selectedIndex, dropCount);
        if (dropped != null) {
            groundItemManager.dropItem(dropped, player.getTileX(), player.getTileY());
            GameLog.getInstance().log(String.format("丢弃了 %s x%d",
                    dropped.getType().getDisplayName(), dropped.getCount()));

            refreshDropCounts();
            if (selectedIndex >= inventory.getItemCount() && selectedIndex > 0) {
                selectedIndex--;
            }
        }
    }

    @Override
    protected void onSelect(int index) {
        // Enter 由 onKeyPressed 处理
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }

    // ── 物品详情渲染 ──────────────────────────────

    /** 渲染物品详情查看面板 */
    private void renderItemDetail(Renderer renderer) {
        int height = getHeight();
        int width = getWidth();

        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, width, height);

        ItemStack stack = inventory.getItem(selectedIndex);
        if (stack == null) {
            showingDetail = false;
            return;
        }

        // ── 居中面板 ──
        int panelW = Math.min(400, width - 40);
        int panelH = Math.min(340, height - 40);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        renderer.setColor(new Color(15, 15, 25));
        renderer.fillRect(panelX, panelY, panelW, panelH);
        renderer.setColor(new Color(80, 80, 110));
        renderer.drawRect(panelX, panelY, panelW, panelH);

        int contentX = panelX + 20;
        int contentW = panelW - 40;
        int curY = panelY + 16;

        // ── 图标 ──
        String spriteId = "item." + stack.getType().getName();
        Sprite sprite = SpriteManager.hasActivePack()
                ? SpriteManager.getSprite(spriteId) : null;

        if (sprite != null) {
            int iconSize = 48;
            int iconX = panelX + (panelW - iconSize) / 2;
            renderer.drawImage(sprite.getImage(), iconX, curY, iconSize, iconSize);
            curY += iconSize + 16;
        } else {
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

        // ── 描述 ──
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

        String countLine = String.format("数量: %d", stack.getCount());
        renderer.drawText(countLine, contentX, curY);
        curY += 18;

        int weight = (int) stack.getTotalWeightGrams();
        String weightLine = String.format("重量: %dg", weight);
        renderer.drawText(weightLine, contentX, curY);
        if (stack.getCount() > 1) {
            int unitWeight = (int) stack.getType().getWeightGrams();
            renderer.setColor(new Color(120, 120, 140));
            renderer.drawText(String.format("(单件 %dg)", unitWeight),
                    contentX + renderer.getTextWidth(weightLine) + 8, curY);
            renderer.setColor(new Color(140, 200, 255));
        }
        curY += 18;

        double volume = stack.getTotalVolumeMl();
        if (volume > 0) {
            renderer.drawText(String.format("体积: %.0fml", volume), contentX, curY);
            curY += 18;
        }

        if (stack.getType().isConsumable()) {
            curY += 4;
            renderer.setColor(new Color(100, 200, 140));
            renderer.drawText("── 营养 ──", contentX, curY);
            curY += 18;
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
            double cal = stack.getTotalCalories();
            double sat = stack.getTotalSatiety();
            double water = stack.getTotalWaterContent();
            if (cal > 0) { renderer.drawText(String.format("热量: %.0fkcal", cal), contentX, curY); curY += 18; }
            if (sat > 0) { renderer.drawText(String.format("饱腹: %.0f", sat), contentX, curY); curY += 18; }
            if (water > 0) { renderer.drawText(String.format("水分: %.0fml", water), contentX, curY); curY += 18; }
        }

        drawHintBar(renderer, "Esc 返回");
    }

    /** 文本自动换行 */
    private List<String> wrapText(Renderer renderer, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) { lines.add(""); continue; }
            String[] words = paragraph.split("(?<=\\s)|(?=\\s)");
            StringBuilder cur = new StringBuilder();
            for (String word : words) {
                String test = cur + word;
                if (renderer.getTextWidth(test) <= maxWidth) {
                    cur.append(word);
                } else {
                    if (cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(word); }
                    else {
                        for (int i = 0; i < word.length(); ) {
                            int cp = word.codePointAt(i);
                            String ch = new String(Character.toChars(cp));
                            i += Character.charCount(cp);
                            if (renderer.getTextWidth(cur + ch) <= maxWidth) { cur.append(ch); }
                            else {
                                if (cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(ch); }
                                else { cur.append(ch); lines.add(cur.toString()); cur = new StringBuilder(); }
                            }
                        }
                    }
                }
            }
            if (cur.length() > 0) lines.add(cur.toString());
        }
        return lines;
    }
}
