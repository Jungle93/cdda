package com.github.game.engine.core.scene;

import com.github.game.engine.core.render.Renderer;

import java.awt.*;

/**
 * 游戏覆盖层抽象基类。
 *
 * <p>覆盖层是一种特殊的 {@link Scene}，以半透明方式叠加在游戏画面上方，
 * 不遮盖底层游戏场景。用于物品菜单、快捷面板等需要看到游戏世界的交互。
 *
 * <p>使用方式：
 * <pre>
 * // 在 MainScreen 中：
 * GameOverlay overlay = new MyOverlay(viewport);
 * overlay.setOnDismiss(() -> hideOverlay());
 * showOverlay(overlay);
 * </pre>
 *
 * <p>子类负责：
 * <ul>
 *   <li>实现 {@link #render(Renderer)} — 先调用 {@link #renderOverlayBackground(Renderer)} 画半透明背景，再绘制内容</li>
 *   <li>实现 {@link #onKeyPressed(int)} — 处理用户输入</li>
 *   <li>在适当时机调用 {@link #dismiss()} 关闭自身</li>
 * </ul>
 */
public abstract class GameOverlay extends Scene {

    /** 关闭回调（由父容器设置，dismiss 时调用） */
    private Runnable onDismiss;

    /** 覆盖层全局半透明背景色（40% 不透明度） */
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 0);

    /** 面板背景色（80% 不透明度） */
    private static final Color PANEL_BG = new Color(10, 10, 20);

    /** 面板边框色 */
    private static final Color PANEL_BORDER = new Color(80, 80, 100,100);

    public GameOverlay(Viewport viewport) {
        super(viewport);
    }

    // ── 关闭机制 ──────────────────────────────────

    /**
     * 设置关闭回调。
     * 通常由父容器（如 MainScreen）调用，在 dismiss() 时触发。
     *
     * @param onDismiss 关闭回调
     */
    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    /**
     * 关闭覆盖层。
     * 调用 onDismiss 回调通知父容器移除自身。
     */
    public void dismiss() {
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    // ── 渲染辅助方法 ──────────────────────────────────

    /**
     * 渲染覆盖层全局半透明背景。
     * 子类在 render() 开头调用此方法，再绘制自身内容。
     *
     * @param renderer 渲染器
     */
    protected void renderOverlayBackground(Renderer renderer) {
        int w = viewport.getWidth();
        int h = viewport.getHeight();
        renderer.setColor(OVERLAY_BG);
        renderer.fillRect(0, 0, w, h);
    }

    /**
     * 渲染一个带边框的深色面板。
     * 用于在覆盖层中绘制菜单/对话框背景。
     *
     * @param renderer 渲染器
     * @param x        面板左上角 X（局部坐标）
     * @param y        面板左上角 Y（局部坐标）
     * @param width    面板宽度
     * @param height   面板高度
     */
    protected void renderPanel(Renderer renderer, int x, int y, int width, int height) {
        // 面板背景
        renderer.setColor(PANEL_BG);
        renderer.fillRect(x, y, width, height);
        // 面板边框
        renderer.setColor(PANEL_BORDER);
        renderer.drawRect(x, y, width, height);
    }

    /**
     * 绘制居中文本。
     *
     * @param renderer 渲染器（字体和颜色需已设置）
     * @param text     文本内容
     * @param centerX  中心 X 坐标
     * @param y        基线 Y 坐标
     */
    protected void drawCentered(Renderer renderer, String text, int centerX, int y) {
        int textWidth = renderer.getTextWidth(text);
        renderer.drawText(text, centerX - textWidth / 2, y);
    }
}
