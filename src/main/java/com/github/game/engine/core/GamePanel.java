package com.github.game.engine.core;

import com.github.game.engine.core.render.Graphics2DRenderer;
import com.github.game.engine.core.render.RenderContext;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.screen.Screen;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 游戏渲染面板，同时作为输入事件入口。
 * 在 paintComponent 中创建 Renderer 并委托给当前 Screen 渲染。
 * 尺寸和字体由 DisplayConfig 驱动（通过 GameEngine 获取），支持运行时动态调整。
 */
public class GamePanel extends JPanel
        implements MouseListener, MouseMotionListener, KeyListener {

    private final GameEngine engine;

    public GamePanel(GameEngine engine) {
        this.engine = engine;
        setFocusable(true);

        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);

        // 监听面板尺寸变化，通知配置层持久化 + 通知屏幕更新布局
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                EngineConfig config = engine.getConfig();
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    config.setWindowWidth(w);
                    config.setWindowHeight(h);
                    config.fireWindowResized(w, h);
                    // 通知所有 Screen 更新布局（Viewport、Camera 等）
                    engine.getScreenManager().onWindowResized(w, h);
                }
            }
        });
    }

    /** 动态返回配置中的窗口尺寸 */
    @Override
    public Dimension getPreferredSize() {
        EngineConfig config = engine.getConfig();
        return new Dimension(config.getWindowWidth(), config.getWindowHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        Renderer renderer = new Graphics2DRenderer(g2d);

        // 清屏
        renderer.setColor(RenderContext.DEFAULT_BACKGROUND);
        renderer.fillRect(0, 0, getWidth(), getHeight());

        // 委托给当前 Screen 渲染
        Screen screen = engine.getScreenManager().getCurrentScreen();
        if (screen != null) {
            // 使用配置中的字体大小（而非硬编码常量）
            int fontSize = engine.getConfig().getFontSize();
            renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
            renderer.setColor(RenderContext.DEFAULT_TEXT_COLOR);
            screen.render(renderer);
        }

        renderer.dispose();
    }

    // ── MouseListener ──────────────────────────────────

    @Override
    public void mousePressed(MouseEvent e) {
        engine.getInputManager().onMousePressed(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        engine.getInputManager().onMouseReleased(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        engine.getInputManager().onMouseClicked(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    // ── MouseMotionListener ──────────────────────────────

    @Override
    public void mouseMoved(MouseEvent e) {
        engine.getInputManager().onMouseMoved(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        engine.getInputManager().onMouseDragged(e);
    }

    // ── KeyListener ──────────────────────────────────────

    @Override
    public void keyPressed(KeyEvent e) {
        engine.getInputManager().onKeyPressed(e);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        engine.getInputManager().onKeyReleased(e);
    }

    @Override
    public void keyTyped(KeyEvent e) { engine.getInputManager().onKeyTyped(e); }
}
