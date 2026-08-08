package com.github.game.engine.core.scene;

/**
 * 屏幕视口矩形。定义屏幕上的一个矩形区域，提供坐标转换。
 *
 * 用于 Scene 的屏幕区域定义：
 * - 渲染时 Renderer 裁剪到视口区域
 * - 鼠标事件通过视口判断归属 + 转换为局部坐标
 * - 世界场景的 Camera 设置 screenOffset = viewport 位置
 */
public class Viewport {

    /** 视口左上角屏幕坐标 X */
    private int x;
    /** 视口左上角屏幕坐标 Y */
    private int y;
    /** 视口宽度（像素） */
    private int width;
    /** 视口高度（像素） */
    private int height;

    /**
     * 创建视口。
     *
     * @param x      左上角屏幕 X
     * @param y      左上角屏幕 Y
     * @param width  宽度（像素）
     * @param height 高度（像素）
     */
    public Viewport(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ── 坐标转换 ──────────────────────────────────

    /** 屏幕坐标 X → 局部坐标 X（相对于视口左上角） */
    public int toLocalX(int screenX) {
        return screenX - x;
    }

    /** 屏幕坐标 Y → 局部坐标 Y（相对于视口左上角） */
    public int toLocalY(int screenY) {
        return screenY - y;
    }

    /** 局部坐标 X → 屏幕坐标 X */
    public int toScreenX(int localX) {
        return localX + x;
    }

    /** 局部坐标 Y → 屏幕坐标 Y */
    public int toScreenY(int localY) {
        return localY + y;
    }

    /** 判断屏幕坐标点是否在视口内 */
    public boolean contains(int screenX, int screenY) {
        return screenX >= x && screenX < x + width
                && screenY >= y && screenY < y + height;
    }

    // ── 访问器 ──────────────────────────────────

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return String.format("Viewport(%d, %d, %d×%d)", x, y, width, height);
    }
}
