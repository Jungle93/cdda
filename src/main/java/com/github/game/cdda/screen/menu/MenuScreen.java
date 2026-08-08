package com.github.game.cdda.screen.menu;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.screen.Screen;

import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * 菜单屏幕抽象基类。
 * 提取所有菜单屏幕的公共逻辑：
 * <ul>
 *   <li>选中索引管理 + ↑↓ 导航（含循环回绕）</li>
 *   <li>Enter → onSelect()，Esc → onCancel()，←→ → onAdjust()</li>
 *   <li>渲染工具方法：drawCentered、drawTitle、drawHintBar、renderMenuItem</li>
 *   <li>默认空 update()/dispose()（子类按需覆写）</li>
 * </ul>
 *
 * 子类只需实现 renderMenu()、getItemCount()、onSelect()，
 * 并可选覆写 onAdjust()、onCancel()、onKeyPressed()。
 */
public abstract class MenuScreen extends Screen {

    /** 当前选中项索引 */
    protected int selectedIndex = 0;

    protected MenuScreen(GameEngine engine) {
        super(engine);
    }

    // ── 子类必须实现 ──────────────────────────────────

    /** 菜单项总数 */
    protected abstract int getItemCount();

    /** 渲染完整屏幕内容 */
    protected abstract void renderMenu(Renderer renderer);

    /** 确认/选择当前项 */
    protected abstract void onSelect(int index);

    // ── 子类可选覆写 ──────────────────────────────────

    /** ←/→ 调整数值（默认空实现） */
    protected void onAdjust(int index, int direction) {}

    /** Esc 返回/取消（默认空实现） */
    protected void onCancel() {}

    /** 字符输入（默认空实现，用于文本编辑场景） */
    @Override
    public void onKeyTyped(int charCode) {}

    // ── 默认生命周期 ──────────────────────────────────

    @Override
    public void update(long deltaTime) {}

    @Override
    public void dispose() {}

    @Override
    public final void render(Renderer renderer) {
        renderMenu(renderer);
    }

    // ── 默认导航输入 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        int count = getItemCount();
        switch (keyCode) {
            case KeyEvent.VK_UP:
                selectedIndex = (selectedIndex - 1 + count) % count;
                break;
            case KeyEvent.VK_DOWN:
                selectedIndex = (selectedIndex + 1) % count;
                break;
            case KeyEvent.VK_LEFT:
                onAdjust(selectedIndex, -1);
                break;
            case KeyEvent.VK_RIGHT:
                onAdjust(selectedIndex, 1);
                break;
            case KeyEvent.VK_ENTER:
                onSelect(selectedIndex);
                break;
            case KeyEvent.VK_ESCAPE:
                onCancel();
                break;
            default:
                break;
        }
    }

    // ── 渲染工具方法 ──────────────────────────────────

    /**
     * 水平居中绘制文本。
     *
     * @param r    渲染器（字体和颜色需已设置）
     * @param text 文本内容
     * @param y    基线 Y 坐标
     */
    protected void drawCentered(Renderer r, String text, int y) {
        int x = (getWidth() - r.getTextWidth(text)) / 2;
        r.drawText(text, x, y);
    }

    /**
     * 绘制标题（白色粗体 Monospaced 居中）。
     *
     * @param r        渲染器
     * @param title    标题文本
     * @param fontSize 字体大小（pt）
     * @param y        基线 Y 坐标
     */
    protected void drawTitle(Renderer r, String title, int fontSize, int y) {
        r.setFont(new Font("Monospaced", Font.BOLD, fontSize));
        r.setColor(Color.WHITE);
        drawCentered(r, title, y);
    }

    /**
     * 绘制底部提示栏（灰色 12pt Monospaced 居中于 height - 20）。
     *
     * @param r    渲染器
     * @param hint 提示文本
     */
    protected void drawHintBar(Renderer r, String hint) {
        r.setFont(new Font("Monospaced", Font.PLAIN, 12));
        r.setColor(Color.GRAY);
        drawCentered(r, hint, getHeight() - 20);
    }

    /**
     * 渲染一个标准菜单项（前缀 + 标签 + 可选值，水平居中）。
     * <p>
     * 选中项：黄色 + "&gt; " 前缀；未选中：白色 + "  " 前缀。
     * value 为 null 或空时不显示冒号和值。
     *
     * @param r        渲染器
     * @param index    菜单项索引（用于判断是否选中）
     * @param label    标签文本
     * @param value    显示值（null 或空则仅显示标签）
     * @param y        基线 Y 坐标
     * @param fontSize 字体大小（pt）
     */
    protected void renderMenuItem(Renderer r, int index, String label,
                                   String value, int y, int fontSize) {
        boolean sel = (index == selectedIndex);
        String prefix = sel ? "> " : "  ";
        String line = (value != null && !value.isEmpty())
                ? prefix + label + ": " + value
                : prefix + label;

        r.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        r.setColor(sel ? Color.YELLOW : Color.WHITE);
        drawCentered(r, line, y);
    }
}
