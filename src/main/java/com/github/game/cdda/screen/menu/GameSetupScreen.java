package com.github.game.cdda.screen.menu;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.game.CharacterSettings;
import com.github.game.cdda.game.WorldSettings;
import com.github.game.cdda.screen.MainScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * 游戏开始设置界面。
 * 配置世界设置（种子）和角色设置（名称、性别），确认后进入游戏。
 *
 * 操作方式：
 * - ↑↓ 选择设置项
 * - ←→ 调整数值（性别循环）
 * - Enter 进入文本编辑（种子/名称）或确认操作
 * - Esc 取消编辑或返回主菜单
 * - 编辑中：输入文字，Backspace 删除，Enter 确认，Esc 取消
 */
public class GameSetupScreen extends MenuScreen {

    private static final Logger logger = LoggerFactory.getLogger(GameSetupScreen.class);

    /** 设置项索引常量 */
    private static final int ITEM_SEED = 0;
    private static final int ITEM_NAME = 1;
    private static final int ITEM_GENDER = 2;
    private static final int ITEM_START = 3;
    private static final int ITEM_BACK = 4;
    private static final int ITEM_COUNT = 5;

    /** 布局参数 */
    private static final int TITLE_Y = 40;
    private static final int SECTION1_Y = 85;
    private static final int ITEMS_START_Y = 115;
    private static final int SECTION2_Y = 185;
    private static final int ACTION_START_Y = 270;
    private static final int LINE_SPACING = 36;

    private final WorldSettings worldSettings;
    private final CharacterSettings characterSettings;

    // ── 文本编辑状态（统一用于种子和名称） ──
    /** 当前正在编辑的项（-1 = 无） */
    private int editingItem = -1;
    /** 编辑中的文本缓冲 */
    private String editBuffer = "";
    /** 编辑前的原始值（Esc 取消时恢复） */
    private String editBackup = "";

    public GameSetupScreen(GameEngine engine) {
        super(engine);
        this.worldSettings = new WorldSettings();
        this.characterSettings = new CharacterSettings();
    }

    @Override
    public void init() {
        selectedIndex = 0;
        editingItem = -1;
    }

    @Override
    protected int getItemCount() {
        return ITEM_COUNT;
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        int width = getWidth();

        // ── 标题 ──
        drawTitle(renderer, "新游戏", 24, TITLE_Y);

        // ── 世界设置段落 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 14));
        renderer.setColor(Color.GRAY);
        drawCentered(renderer, "── 世界设置 ──", SECTION1_Y);

        // 种子：编辑中显示缓冲区+光标，否则显示当前值
        String seedDisplay = (editingItem == ITEM_SEED)
                ? editBuffer + "_"
                : String.valueOf(worldSettings.getSeed());
        renderMenuItem(renderer, ITEM_SEED, "世界种子", seedDisplay,
                ITEMS_START_Y, 16);

        // ── 角色设置段落 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 14));
        renderer.setColor(Color.GRAY);
        drawCentered(renderer, "── 角色设置 ──", SECTION2_Y);

        // 名称：编辑中显示缓冲区+光标
        String nameDisplay = (editingItem == ITEM_NAME)
                ? editBuffer + "|"
                : characterSettings.getName();
        renderMenuItem(renderer, ITEM_NAME, "角色名称", nameDisplay,
                ITEMS_START_Y + LINE_SPACING, 16);
        renderMenuItem(renderer, ITEM_GENDER, "角色性别",
                characterSettings.getGender(),
                ITEMS_START_Y + LINE_SPACING * 2, 16);

        // ── 操作按钮 ──
        renderMenuItem(renderer, ITEM_START, "开始游戏", null,
                ACTION_START_Y, 16);
        renderMenuItem(renderer, ITEM_BACK, "返回", null,
                ACTION_START_Y + LINE_SPACING, 16);

        // ── 底部提示 ──
        String hint = (editingItem >= 0)
                ? "输入文字   Backspace 删除   Enter 确认   Esc 取消"
                : "↑↓ 选择   ←→ 调整   Enter 编辑   Esc 返回";
        drawHintBar(renderer, hint);
    }

    // ── 输入处理 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        // ── 文本编辑模式：拦截输入 ──
        if (editingItem >= 0) {
            switch (keyCode) {
                case KeyEvent.VK_BACK_SPACE:
                case KeyEvent.VK_DELETE:
                    if (!editBuffer.isEmpty()) {
                        editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                    }
                    break;
                case KeyEvent.VK_ENTER:
                    confirmEdit();
                    break;
                case KeyEvent.VK_ESCAPE:
                    cancelEdit();
                    break;
                default:
                    // 其他键（方向键等）在编辑中忽略，字符输入由 onKeyTyped 处理
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
        // 过滤控制字符（保留空格及以上的可打印字符）
        if (c < 32 || c == 127) return;

        int maxLen = getMaxEditLength(editingItem);
        if (editBuffer.length() < maxLen) {
            // 种子只接受数字
            if (editingItem == ITEM_SEED && !Character.isDigit(c)) return;
            editBuffer += c;
        }
    }

    // ── 编辑模式操作 ──────────────────────────────────

    /** 进入编辑模式 */
    private void startEdit(int item) {
        editingItem = item;
        editBackup = getItemValue(item);
        editBuffer = editBackup;
    }

    /** 确认编辑，将缓冲区写回对应设置 */
    private void confirmEdit() {
        switch (editingItem) {
            case ITEM_SEED:
                if (!editBuffer.isEmpty()) {
                    try {
                        worldSettings.setSeed(Long.parseLong(editBuffer));
                    } catch (NumberFormatException e) {
                        // 无效数字，恢复原值
                        worldSettings.setSeed(Long.parseLong(editBackup));
                    }
                }
                break;
            case ITEM_NAME:
                if (!editBuffer.isEmpty()) {
                    characterSettings.setName(editBuffer);
                }
                break;
        }
        editingItem = -1;
    }

    /** 取消编辑，恢复原始值 */
    private void cancelEdit() {
        switch (editingItem) {
            case ITEM_SEED:
                worldSettings.setSeed(Long.parseLong(editBackup));
                break;
            case ITEM_NAME:
                characterSettings.setName(editBackup);
                break;
        }
        editingItem = -1;
    }

    /** 获取设置项的当前显示值 */
    private String getItemValue(int item) {
        switch (item) {
            case ITEM_SEED:  return String.valueOf(worldSettings.getSeed());
            case ITEM_NAME:  return characterSettings.getName();
            default:         return "";
        }
    }

    /** 获取编辑项的最大文本长度 */
    private int getMaxEditLength(int item) {
        switch (item) {
            case ITEM_SEED:  return 19; // Long.MAX_VALUE 的位数
            case ITEM_NAME:  return CharacterSettings.NAME_MAX_LENGTH;
            default:         return 100;
        }
    }

    // ── 导航模式操作（基类回调） ──────────────────────────────

    @Override
    protected void onAdjust(int index, int direction) {
        switch (index) {
            case ITEM_GENDER:
                characterSettings.cycleGender(direction);
                break;
        }
    }

    @Override
    protected void onSelect(int index) {
        switch (index) {
            case ITEM_SEED:
            case ITEM_NAME:
                startEdit(index);
                break;
            case ITEM_START:
                startGame();
                break;
            case ITEM_BACK:
                goBack();
                break;
        }
    }

    @Override
    protected void onCancel() {
        goBack();
    }

    /** 使用当前设置启动游戏 */
    private void startGame() {
        logger.info("开始游戏 — 种子: {}, 角色: {}/{}",
                worldSettings.getSeed(),
                characterSettings.getName(),
                characterSettings.getGender());
        engine.getScreenManager().switchScreen(
                new MainScreen(engine, new WorldSettings(worldSettings),
                        new CharacterSettings(characterSettings)));
    }

    /** 返回主菜单 */
    private void goBack() {
        engine.getScreenManager().switchScreen(new MainMenuScreen(engine));
    }
}
