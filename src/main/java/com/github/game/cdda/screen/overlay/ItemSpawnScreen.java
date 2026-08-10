package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.item.ItemRegistry;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.item.ItemType;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * 物品生成菜单。
 *
 * <p>调试菜单"生成物品..."的子屏幕。
 * 支持按物品 ID（数字）或名称（字符串，支持部分匹配）搜索物品，
 * 输入数量后生成到玩家背包。
 *
 * <p>操作：
 * <ul>
 *   <li>↑↓ 选择输入框 / 按钮</li>
 *   <li>Enter 进入编辑 / 确认生成</li>
 *   <li>Esc 取消编辑 / 返回</li>
 *   <li>编辑中：输入文字，Backspace 删除</li>
 * </ul>
 */
public class ItemSpawnScreen extends MenuScreen {

    /** 菜单项索引常量 */
    private static final int ITEM_QUERY = 0;    // 物品查询（ID 或名称）
    private static final int ITEM_COUNT = 1;    // 生成数量
    private static final int ITEM_SPAWN = 2;    // 生成按钮
    private static final int ITEM_CANCEL = 3;   // 取消按钮
    private static final int MENU_SIZE = 4;

    /** 布局参数 */
    private static final int TITLE_Y = 40;
    private static final int FIELDS_Y = 90;
    private static final int LINE_SPACING = 32;
    private static final int PREVIEW_Y = 195;
    private static final int PREVIEW_LINE = 18;

    private final Player player;

    // ── 编辑状态 ──
    /** 当前正在编辑的项（-1 = 无） */
    private int editingItem = -1;
    /** 物品查询缓冲（ID 数字或名称字符串） */
    private String queryBuffer = "";
    /** 数量缓冲 */
    private String countBuffer = "1";
    /** 上次搜索匹配到的物品（用于预览） */
    private ItemType matchedItem = null;
    /** 记录上次查询字符串，避免每帧重复搜索 */
    private String lastSearchKey = null;

    public ItemSpawnScreen(GameEngine engine, GameWorld world) {
        super(engine);
        this.player = world.getPlayer();
    }

    @Override
    protected int getItemCount() {
        return MENU_SIZE;
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 半透明深色背景
        renderer.setColor(new Color(0, 0, 20, 230));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int fontMono = 14;

        // ── 标题 ──
        renderer.setFont(new Font("Monospaced", Font.BOLD, 20));
        renderer.setColor(new Color(0, 255, 128));
        String title = "[ 生成物品 ]";
        renderer.drawText(title, (width - renderer.getTextWidth(title)) / 2, TITLE_Y);

        // 实时搜索（只要查询内容变化就重新查找）
        updateSearch();

        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontMono));

        // ── 物品查询行 ──
        boolean selQuery = (selectedIndex == ITEM_QUERY);
        String prefixQ = selQuery ? "> " : "  ";
        String queryDisplay = (editingItem == ITEM_QUERY)
                ? queryBuffer + "|"
                : (queryBuffer.isEmpty() ? "<输入物品ID或名称>" : queryBuffer);
        Color queryColor = (editingItem == ITEM_QUERY) ? Color.CYAN
                : (selQuery ? Color.YELLOW : Color.WHITE);
        renderer.setColor(queryColor);
        renderer.drawText(prefixQ + "物品: " + queryDisplay, 40, FIELDS_Y);

        // ── 数量行 ──
        boolean selCount = (selectedIndex == ITEM_COUNT);
        String prefixC = selCount ? "> " : "  ";
        String countDisplay = (editingItem == ITEM_COUNT)
                ? countBuffer + "|"
                : countBuffer;
        Color countColor = (editingItem == ITEM_COUNT) ? Color.CYAN
                : (selCount ? Color.YELLOW : Color.WHITE);
        renderer.setColor(countColor);
        renderer.drawText(prefixC + "数量: " + countDisplay, 40, FIELDS_Y + LINE_SPACING);

        // ── 生成按钮 ──
        renderMenuItem(renderer, ITEM_SPAWN, "生成", null,
                FIELDS_Y + LINE_SPACING * 2, fontMono);

        // ── 取消按钮 ──
        renderMenuItem(renderer, ITEM_CANCEL, "取消", null,
                FIELDS_Y + LINE_SPACING * 3, fontMono);

        // ── 物品预览区 ──
        int py = PREVIEW_Y;
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
        renderer.setColor(new Color(100, 100, 100));
        renderer.drawText("────────────────────────────────────", 40, py);
        py += PREVIEW_LINE;

        if (matchedItem != null) {
            // 匹配成功：显示物品详情
            renderer.setColor(Color.GREEN);
            renderer.drawText(String.format("✓ %s  (id=%d)",
                    matchedItem.getName(), matchedItem.getId()), 40, py);
            py += PREVIEW_LINE;

            renderer.setColor(new Color(180, 180, 180));
            renderer.drawText(matchedItem.getDescription(), 40, py);
            py += PREVIEW_LINE;

            renderer.setColor(Color.GRAY);
            renderer.drawText(String.format("重量: %.0fg  体积: %.0fml  堆叠: %d%s",
                    matchedItem.getWeightGrams(),
                    matchedItem.getVolumeMl(),
                    matchedItem.getMaxStackSize(),
                    matchedItem.isUnique() ? "  [唯一]" : ""), 40, py);
            py += PREVIEW_LINE;

            // 显示功能标签（便于调试）
            if (!matchedItem.getTags().isEmpty()) {
                renderer.drawText("标签: " + matchedItem.getTags(), 40, py);
            }
        } else if (!queryBuffer.isEmpty()) {
            // 输入了内容但未找到匹配
            renderer.setColor(Color.RED);
            renderer.drawText("✗ 未找到匹配的物品", 40, py);
        } else {
            // 空输入
            renderer.setColor(new Color(80, 80, 80));
            renderer.drawText("输入数字按 ID 查找，输入文字按名称查找（支持部分匹配）", 40, py);
        }

        // ── 底部提示 ──
        String hint = (editingItem >= 0)
                ? "输入文字   Backspace 删除   Enter 确认   Esc 取消"
                : "↑↓ 选择   Enter 编辑/执行   Esc 返回";
        drawHintBar(renderer, hint);
    }

    // ── 搜索逻辑 ──────────────────────────────────────

    /**
     * 根据当前查询缓冲更新匹配物品。
     * 纯数字 → 按 ID 精确查找；含字母/下划线 → 按名称部分匹配（不区分大小写）。
     */
    private void updateSearch() {
        if (queryBuffer.equals(lastSearchKey)) return;
        lastSearchKey = queryBuffer;

        if (queryBuffer.isEmpty()) {
            matchedItem = null;
            return;
        }

        // 纯数字 → 按 ID 查找
        if (queryBuffer.matches("\\d+")) {
            try {
                matchedItem = ItemRegistry.getById(Integer.parseInt(queryBuffer));
                return;
            } catch (NumberFormatException ignored) {
                // 不可能发生（matches 已验证）
            }
        }

        // 精确按 name 查找
        ItemType exact = ItemRegistry.getByName(queryBuffer);
        if (exact != null) {
            matchedItem = exact;
            return;
        }

        // 部分匹配（不区分大小写）
        String lowerQuery = queryBuffer.toLowerCase();
        for (ItemType type : ItemRegistry.getAll()) {
            if (type.getName().toLowerCase().contains(lowerQuery)) {
                matchedItem = type;
                return;
            }
        }

        matchedItem = null;
    }

    // ── 输入处理 ──────────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        // ── 文本编辑模式：拦截输入 ──
        if (editingItem >= 0) {
            switch (keyCode) {
                case KeyEvent.VK_BACK_SPACE:
                case KeyEvent.VK_DELETE:
                    if (editingItem == ITEM_QUERY && !queryBuffer.isEmpty()) {
                        queryBuffer = queryBuffer.substring(0, queryBuffer.length() - 1);
                    } else if (editingItem == ITEM_COUNT && !countBuffer.isEmpty()) {
                        countBuffer = countBuffer.substring(0, countBuffer.length() - 1);
                    }
                    break;
                case KeyEvent.VK_ENTER:
                    confirmEdit();
                    break;
                case KeyEvent.VK_ESCAPE:
                    cancelEdit();
                    break;
                default:
                    // 其他键（方向键等）在编辑中忽略，字符由 onKeyTyped 处理
                    break;
            }
            return;
        }

        // ── 正常导航模式：委托基类 ──
        super.onKeyPressed(keyCode);
    }

    @Override
    public void onKeyTyped(int charCode) {
        if (editingItem < 0) return;

        char c = (char) charCode;
        // 过滤控制字符
        if (c < 32 || c == 127) return;

        if (editingItem == ITEM_QUERY) {
            // 物品查询：接受字母、数字、下划线、连字符
            if (queryBuffer.length() < 40
                    && (Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                queryBuffer += c;
            }
        } else if (editingItem == ITEM_COUNT) {
            // 数量：只接受数字
            if (Character.isDigit(c) && countBuffer.length() < 4) {
                countBuffer += c;
            }
        }
    }

    // ── 编辑模式操作 ──────────────────────────────────

    /** 进入编辑模式 */
    private void startEdit(int item) {
        editingItem = item;
    }

    /** 确认编辑 */
    private void confirmEdit() {
        if (editingItem == ITEM_COUNT) {
            // 空或无效数量回退为 1
            if (countBuffer.isEmpty()) countBuffer = "1";
            try {
                int n = Integer.parseInt(countBuffer);
                if (n <= 0) countBuffer = "1";
            } catch (NumberFormatException e) {
                countBuffer = "1";
            }
        }
        editingItem = -1;
    }

    /** 取消编辑 */
    private void cancelEdit() {
        editingItem = -1;
    }

    // ── 菜单操作 ──────────────────────────────────────

    @Override
    protected void onSelect(int index) {
        switch (index) {
            case ITEM_QUERY:
            case ITEM_COUNT:
                startEdit(index);
                break;
            case ITEM_SPAWN:
                doSpawn();
                break;
            case ITEM_CANCEL:
                engine.getScreenManager().popScreen();
                break;
        }
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }

    /** 执行物品生成 */
    private void doSpawn() {
        if (matchedItem == null) {
            log("未找到匹配的物品");
            return;
        }

        int count;
        try {
            count = Integer.parseInt(countBuffer);
        } catch (NumberFormatException e) {
            count = 1;
        }
        if (count <= 0) count = 1;

        ItemStack stack = new ItemStack(matchedItem, count);
        if (player.getInventory().addItem(stack)) {
            log(String.format("生成了 %s x%d", matchedItem.getName(), count));
        } else {
            log("背包超重，无法添加 " + matchedItem.getName());
        }
    }

    /** 记录调试日志 */
    private void log(String msg) {
        GameLog.getInstance().log("[调试] " + msg);
    }
}
