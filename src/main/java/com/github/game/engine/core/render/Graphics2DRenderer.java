package com.github.game.engine.core.render;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 基于 Graphics2D 的 Renderer 实现。
 */
public class Graphics2DRenderer implements Renderer {

    private final Graphics2D g2d;

    /** 裁剪区域栈，用于 pushClip/popClip 嵌套 */
    private final Deque<Shape> clipStack = new ArrayDeque<>();

    /** 变换栈，用于 pushTranslate/popTranslate 嵌套 */
    private final Deque<AffineTransform> transformStack = new ArrayDeque<>();

    public Graphics2DRenderer(Graphics2D g2d) {
        this.g2d = g2d;
        // 开启抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    @Override
    public void setColor(Color color) {
        g2d.setColor(color);
    }

    @Override
    public void setFont(Font font) {
        g2d.setFont(font);
    }

    @Override
    public void clear(Color color) {
        Color old = g2d.getColor();
        g2d.setColor(color);
        g2d.fillRect(0, 0, g2d.getClipBounds().width, g2d.getClipBounds().height);
        g2d.setColor(old);
    }

    @Override
    public void drawText(String text, int x, int y) {
        g2d.drawString(text, x, y);
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        g2d.drawRect(x, y, width, height);
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        g2d.fillRect(x, y, width, height);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        g2d.drawLine(x1, y1, x2, y2);
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        g2d.drawOval(x, y, width, height);
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        g2d.fillOval(x, y, width, height);
    }

    @Override
    public void drawImage(Image image, int x, int y) {
        g2d.drawImage(image, x, y, null);
    }

    @Override
    public void drawImage(Image image, int x, int y, int width, int height) {
        g2d.drawImage(image, x, y, width, height, null);
    }

    @Override
    public int getTextWidth(String text) {
        return g2d.getFontMetrics().stringWidth(text);
    }

    @Override
    public int getTextHeight(String text) {
        return g2d.getFontMetrics().getHeight();
    }

    @Override
    public FontMetrics getFontMetrics() {
        return g2d.getFontMetrics();
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        clipStack.push(g2d.getClip());
        g2d.clipRect(x, y, width, height);
    }

    @Override
    public void popClip() {
        if (!clipStack.isEmpty()) {
            g2d.setClip(clipStack.pop());
        }
    }

    @Override
    public void pushTranslate(int dx, int dy) {
        transformStack.push(g2d.getTransform());
        g2d.translate(dx, dy);
    }

    @Override
    public void popTranslate() {
        if (!transformStack.isEmpty()) {
            g2d.setTransform(transformStack.pop());
        }
    }

    @Override
    public void dispose() {
        g2d.dispose();
    }
}
