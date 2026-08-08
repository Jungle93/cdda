package com.github.game.cdda.screen.hud;

import com.github.game.engine.core.render.Renderer;

/**
 * 信息面板组件接口。
 * 用于构建可扩展的 HUD 系统——每个面板独立控制位置、尺寸和启用状态。
 *
 * MainScreen 持有 {@code List<StatusPanel>}，按序垂直排列渲染。
 * 后续添加装备栏、消息日志等面板时，只需实现此接口并注册到列表。
 *
 * 设计要点：
 * - 面板高度由自身决定（{@link #getHeight()}）
 * - 禁用的面板不渲染、不占空间
 * - 渲染坐标由外部传入（MainScreen 负责布局排列）
 */
public interface StatusPanel {

    /**
     * 渲染面板内容。
     *
     * @param renderer 渲染器
     * @param x        面板左上角 X（屏幕坐标）
     * @param y        面板左上角 Y（屏幕坐标）
     * @param width    面板可用宽度
     * @param height   面板可用高度
     */
    void render(Renderer renderer, int x, int y, int width, int height);

    /**
     * 面板高度（像素）。
     * MainScreen 根据此值进行垂直排列。
     */
    int getHeight();

    /** 是否启用（禁用的面板不渲染、不占空间） */
    boolean isEnabled();

    /** 设置启用状态 */
    void setEnabled(boolean enabled);
}
