package com.github.game.engine.core.render;

import java.awt.*;

/**
 * 渲染抽象接口，封装绘制操作。
 * 当前实现基于 Graphics2D，后期可替换为精灵图/图像渲染而不影响 Screen 代码。
 */
public interface Renderer {

    void setColor(Color color);

    void setFont(Font font);

    void clear(Color color);

    void drawText(String text, int x, int y);

    void drawRect(int x, int y, int width, int height);

    void fillRect(int x, int y, int width, int height);

    void drawLine(int x1, int y1, int x2, int y2);

    void drawOval(int x, int y, int width, int height);

    void fillOval(int x, int y, int width, int height);

    void drawImage(Image image, int x, int y);

    void drawImage(Image image, int x, int y, int width, int height);

    int getTextWidth(String text);

    int getTextHeight(String text);

    FontMetrics getFontMetrics();

    /**
     * 推入裁剪区域。后续的绘制操作仅在指定矩形区域内生效。
     * 支持嵌套：多次 pushClip 会交叉裁剪。
     * 必须与 {@link #popClip()} 配对调用。
     *
     * @param x      裁剪区域左上角 X
     * @param y      裁剪区域左上角 Y
     * @param width  裁剪区域宽度
     * @param height 裁剪区域高度
     */
    void pushClip(int x, int y, int width, int height);

    /**
     * 弹出裁剪区域，恢复到上一次 pushClip 之前的裁剪状态。
     */
    void popClip();

    /**
     * 推入坐标平移。后续的绘制操作坐标会偏移 (dx, dy)。
     * 用于 Scene 渲染：将局部坐标 (0,0) 映射到屏幕上的 viewport 位置。
     * 支持嵌套。必须与 {@link #popTranslate()} 配对调用。
     *
     * @param dx X 方向偏移量
     * @param dy Y 方向偏移量
     */
    void pushTranslate(int dx, int dy);

    /**
     * 弹出坐标平移，恢复到上一次 pushTranslate 之前的状态。
     */
    void popTranslate();

    void dispose();
}
