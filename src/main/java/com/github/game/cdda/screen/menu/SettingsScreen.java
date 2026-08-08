package com.github.game.cdda.screen.menu;

import com.github.game.cdda.config.GameConfig;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import javax.swing.*;
import java.io.File;

/**
 * 设置屏幕。
 * 可配置：窗口大小、字体大小、存档位置。
 * ↑↓ 选择设置项，←→ 调整数值，Enter 进入编辑（存档路径），Esc 放弃修改。
 */
public class SettingsScreen extends MenuScreen {

    /** 窗口大小预设选项 */
    private static final int[][] WINDOW_PRESETS = {
            {600, 400}, {800, 600}, {1024, 768}, {1280, 720}
    };

    /** 字体大小范围 */
    private static final int FONT_MIN = 10;
    private static final int FONT_MAX = 24;
    private static final int FONT_STEP = 2;

    /** 信息面板宽度范围 */
    private static final int PANEL_WIDTH_MIN = 120;
    private static final int PANEL_WIDTH_MAX = 300;
    private static final int PANEL_WIDTH_STEP = 10;

    private static final String[] LABELS = {"窗口大小", "字体大小", "信息面板宽度", "存档位置", "保存"};
    private static final int ITEM_COUNT = LABELS.length;
    private static final int SAVE_INDEX = ITEM_COUNT - 1;
    private static final int PATH_INDEX = SAVE_INDEX - 1;

    private final GameConfig config = new GameConfig();

    // 当前值（本地缓存，Esc 取消编辑时恢复）
    private int windowPresetIndex;
    private int fontSize;
    private int infoPanelWidth;
    private String savePath;

    public SettingsScreen(GameEngine engine) {
        super(engine);
    }

    @Override
    public void init() {
        // 从配置加载当前值
        int w = config.getWindowWidth();
        int h = config.getWindowHeight();
        windowPresetIndex = findPresetIndex(w, h);
        fontSize = config.getFontSize();
        infoPanelWidth = config.getInfoPanelWidth();
        savePath = config.getSavePath();
        selectedIndex = 0;
    }

    /** 查找最接近当前窗口尺寸的预设索引 */
    private int findPresetIndex(int w, int h) {
        for (int i = 0; i < WINDOW_PRESETS.length; i++) {
            if (WINDOW_PRESETS[i][0] == w && WINDOW_PRESETS[i][1] == h) {
                return i;
            }
        }
        return 0; // 默认第一个
    }

    @Override
    protected int getItemCount() {
        return ITEM_COUNT;
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 标题
        drawTitle(renderer, "设置", 24, 40);

        // 设置项
        int startY = 100;
        int lineSpacing = 36;
        for (int i = 0; i < ITEM_COUNT; i++) {
            String value = (i == SAVE_INDEX) ? null : getValueString(i);
            renderMenuItem(renderer, i, LABELS[i], value,
                    startY + i * lineSpacing, 16);
        }

        // 底部提示
        drawHintBar(renderer, "↑↓ 选择   ←→ 调整   Enter 确认/浏览   Esc 放弃修改");
    }

    /** 获取指定设置项的显示值 */
    private String getValueString(int index) {
        switch (index) {
            case 0:
                int[] preset = WINDOW_PRESETS[windowPresetIndex];
                return preset[0] + " × " + preset[1];
            case 1:
                return fontSize + " pt";
            case 2:
                return infoPanelWidth + " px";
            case 3: // 存档位置 (PATH_INDEX)
                return savePath;
            default:
                return "";
        }
    }

    @Override
    protected void onAdjust(int index, int direction) {
        switch (index) {
            case 0: // 窗口大小
                windowPresetIndex = Math.max(0,
                        Math.min(WINDOW_PRESETS.length - 1, windowPresetIndex + direction));
                break;
            case 1: // 字体大小
                fontSize = Math.max(FONT_MIN,
                        Math.min(FONT_MAX, fontSize + direction * FONT_STEP));
                break;
            case 2: // 信息面板宽度
                infoPanelWidth = Math.max(PANEL_WIDTH_MIN,
                        Math.min(PANEL_WIDTH_MAX, infoPanelWidth + direction * PANEL_WIDTH_STEP));
                break;
        }
    }

    @Override
    protected void onSelect(int index) {
        if (index == SAVE_INDEX) {
            saveAndGoBack();
        } else if (index == PATH_INDEX) {
            browseSavePath();
        }
    }

    @Override
    protected void onCancel() {
        goBack();
    }

    /** 弹出目录选择对话框选择存档位置 */
    private void browseSavePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择存档位置");

        // 以当前存档路径为初始目录
        File currentDir = new File(savePath);
        if (currentDir.exists()) {
            chooser.setCurrentDirectory(currentDir);
        }

        int result = chooser.showOpenDialog(engine.getFrame());
        if (result == JFileChooser.APPROVE_OPTION) {
            savePath = chooser.getSelectedFile().getAbsolutePath();
        }
    }

    /** 放弃修改，返回主菜单 */
    private void goBack() {
        engine.getScreenManager().switchScreen(new MainMenuScreen(engine));
    }

    /** 保存配置并返回主菜单 */
    private void saveAndGoBack() {
        config.setWindowWidth(WINDOW_PRESETS[windowPresetIndex][0]);
        config.setWindowHeight(WINDOW_PRESETS[windowPresetIndex][1]);
        config.setFontSize(fontSize);
        config.setInfoPanelWidth(infoPanelWidth);
        config.setSavePath(savePath);
        config.saveAll();

        engine.getScreenManager().switchScreen(new MainMenuScreen(engine));
    }
}
