package com.github.game.cdda.screen.scene;

import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.Scene;
import com.github.game.engine.core.scene.Viewport;
import com.github.game.cdda.screen.hud.StatusPanel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * HUD 信息面板场景。管理 {@link StatusPanel} 列表，
 * 渲染深色背景 + 分隔线 + 各面板垂直排列。
 *
 * 从 MainScreen 迁移的渲染逻辑：
 * - 背景填充（深色）
 * - 左侧分隔线
 * - StatusPanel 列表垂直排列
 */
public class HudScene extends Scene {

    /** HUD 面板列表（可扩展：装备栏、消息日志等） */
    private final List<StatusPanel> panels = new ArrayList<>();

    /** 背景色 */
    private static final Color BG_COLOR = new Color(20, 20, 30);
    /** 分隔线颜色 */
    private static final Color SEPARATOR_COLOR = new Color(60, 60, 80);
    /** 面板间距 */
    private static final int PANEL_PADDING = 4;
    /** 顶部内边距 */
    private static final int TOP_PADDING = 4;

    /**
     * 创建 HUD 场景。
     *
     * @param viewport 屏幕视口区域（信息面板区域）
     */
    public HudScene(Viewport viewport) {
        super(viewport);
    }

    /**
     * 添加一个 HUD 面板。
     * 面板按添加顺序从上到下排列。
     */
    public void addPanel(StatusPanel panel) {
        panels.add(panel);
    }

    /**
     * 移除一个 HUD 面板。
     */
    public void removePanel(StatusPanel panel) {
        panels.remove(panel);
    }

    @Override
    public void render(Renderer renderer) {
        int w = viewport.getWidth();
        int h = viewport.getHeight();

        // 背景
        renderer.setColor(BG_COLOR);
        renderer.fillRect(0, 0, w, h);

        // 各面板垂直排列（局部坐标，0,0 = viewport 左上角）
        int y = TOP_PADDING;
        for (StatusPanel panel : panels) {
            if (!panel.isEnabled()) continue;
            int panelH = panel.getHeight();
            panel.render(renderer, 0, y, w, panelH);
            y += panelH + PANEL_PADDING;
        }

        // 左侧分隔线（最后绘制，确保在所有面板之上）
        renderer.setColor(SEPARATOR_COLOR);
        renderer.drawLine(0, 0, 0, h - 1);
    }

    /** 获取面板列表（只读，用于调试） */
    public List<StatusPanel> getPanels() {
        return panels;
    }
}
