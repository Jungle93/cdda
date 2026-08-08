package com.github.game.engine.core.input;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.screen.Screen;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * 输入事件管理器。
 * 接收来自 GamePanel 的原始 AWT 事件，提取坐标/键码后转发给当前 Screen。
 */
public class InputManager {

    private final GameEngine engine;

    public InputManager(GameEngine engine) {
        this.engine = engine;
    }

    private Screen screen() {
        return engine.getScreenManager().getCurrentScreen();
    }

    // ── 鼠标事件 ──────────────────────────────────────

    public void onMousePressed(MouseEvent e) {
        Screen s = screen();
        if (s != null) s.onMousePressed(e.getX(), e.getY());
    }

    public void onMouseReleased(MouseEvent e) {
        Screen s = screen();
        if (s != null) s.onMouseReleased(e.getX(), e.getY());
    }

    public void onMouseClicked(MouseEvent e) {
        Screen s = screen();
        if (s != null) s.onMouseClicked(e.getX(), e.getY());
    }

    public void onMouseMoved(MouseEvent e) {
        Screen s = screen();
        if (s != null) s.onMouseMoved(e.getX(), e.getY());
    }

    public void onMouseDragged(MouseEvent e) {
        Screen s = screen();
        if (s != null) s.onMouseDragged(e.getX(), e.getY());
    }

    // ── 键盘事件 ──────────────────────────────────────

    public void onKeyPressed(KeyEvent e) {
        Screen s = screen();
        if (s != null) s.onKeyPressed(e.getKeyCode());
    }

    public void onKeyReleased(KeyEvent e) {
        Screen s = screen();
        if (s != null) s.onKeyReleased(e.getKeyCode());
    }


    public void onKeyTyped(KeyEvent e) {
        Screen s = screen();
        if(s!=null){
            s.onKeyTyped(e.getKeyChar());  // 使用 getKeyChar 获取实际输入字符
        }
    }
}
