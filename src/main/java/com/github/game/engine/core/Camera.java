package com.github.game.engine.core;

/**
 * 2D 摄像机。管理视口在世界坐标中的偏移量。
 * 核心职责：世界坐标 → 视图局部坐标的变换。
 *
 * 输出视图局部坐标（viewport-local）：
 * - toViewX/Y 返回相对于视口左上角的坐标
 * - 视口在屏幕上的位置由 Scene/Viewport 负责（通过 Renderer.translate）
 *
 * 支持无限世界：不做边界钳制，视口可自由移动。
 * 摄像机不持有玩家引用——由调用者在 update 中
 * 传入玩家世界坐标，Camera 自行完成跟随。
 */
public class Camera {

    /** 视口左上角的世界坐标 X */
    private int x;
    /** 视口左上角的世界坐标 Y */
    private int y;

    /** 视口宽度（像素），用于居中计算和 TileMap 裁剪范围 */
    private int viewportWidth;
    /** 视口高度（像素） */
    private final int viewportHeight;

    /**
     * 创建摄像机（无限世界，无边界）。
     *
     * @param viewportWidth  视口宽度（像素）
     * @param viewportHeight 视口高度（像素）
     */
    public Camera(int viewportWidth, int viewportHeight) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    /**
     * 将摄像机跟随目标世界坐标。
     * 尝试将目标居中显示。无限世界不做边界钳制。
     *
     * @param targetWorldX 目标世界 X（左上角）
     * @param targetWorldY 目标世界 Y（左上角）
     * @param targetWidth  目标宽度（用于居中计算）
     * @param targetHeight 目标高度
     */
    public void follow(int targetWorldX, int targetWorldY, int targetWidth, int targetHeight) {
        // 居中：视口左上角 = 目标中心 - 视口中心
        this.x = targetWorldX + targetWidth / 2 - viewportWidth / 2;
        this.y = targetWorldY + targetHeight / 2 - viewportHeight / 2;
    }

    /** 世界坐标 X → 视图局部 X（相对于视口左上角） */
    public int toViewX(int worldX) {
        return worldX - x;
    }

    /** 世界坐标 Y → 视图局部 Y（相对于视口左上角） */
    public int toViewY(int worldY) {
        return worldY - y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getViewportWidth() { return viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
    public void setViewportWidth(int width) { this.viewportWidth = width; }
}
