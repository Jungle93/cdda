package com.github.game.engine.core;

/**
 * 2D 摄像机。管理视口在世界坐标中的偏移量和缩放级别。
 * 核心职责：世界坐标 → 视图局部坐标的变换（含缩放）。
 *
 * 输出视图局部坐标（viewport-local）：
 * - toViewX/Y 返回相对于视口左上角的坐标（已应用缩放）
 * - 视口在屏幕上的位置由 Scene/Viewport 负责（通过 Renderer.translate）
 *
 * 支持缩放：
 * - 离散缩放级别数组 ZOOM_LEVELS，默认 1.0x（索引 2）
 * - zoom > 1.0 放大（tile 变大，可见区域变小）
 * - zoom < 1.0 缩小（tile 变小，可见区域变大）
 * - getZoomedViewportWidth/Height 返回缩放后的可见世界尺寸
 *
 * 支持无限世界：不做边界钳制，视口可自由移动。
 * 摄像机不持有玩家引用——由调用者在 update 中
 * 传入玩家世界坐标，Camera 自行完成跟随。
 */
public class Camera {

    /** 可用的缩放级别（离散） */
    public static final double[] ZOOM_LEVELS = {0.5, 0.75, 1.0, 1.5, 2.0, 3.0};

    /** 默认缩放级别索引（1.0x） */
    public static final int DEFAULT_ZOOM_INDEX = 2;

    /** 视口左上角的世界坐标 X */
    private int x;
    /** 视口左上角的世界坐标 Y */
    private int y;

    /** 视口宽度（像素），用于居中计算和 TileMap 裁剪范围 */
    private int viewportWidth;
    /** 视口高度（像素） */
    private int viewportHeight;

    /** 当前缩放级别索引 */
    private int zoomIndex = DEFAULT_ZOOM_INDEX;
    /** 当前缩放因子（从 ZOOM_LEVELS[zoomIndex] 同步） */
    private double zoom = 1.0;

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
     * 尝试将目标居中显示。考虑缩放因子：缩放后可见世界区域变小，
     * 居中计算基于缩放后的视口尺寸。无限世界不做边界钳制。
     *
     * @param targetWorldX 目标世界 X（左上角）
     * @param targetWorldY 目标世界 Y（左上角）
     * @param targetWidth  目标宽度（用于居中计算）
     * @param targetHeight 目标高度
     */
    public void follow(int targetWorldX, int targetWorldY, int targetWidth, int targetHeight) {
        // 居中：视口左上角 = 目标中心 - 缩放后可见区域中心
        int zoomedVW = getZoomedViewportWidth();
        int zoomedVH = getZoomedViewportHeight();
        this.x = targetWorldX + targetWidth / 2 - zoomedVW / 2;
        this.y = targetWorldY + targetHeight / 2 - zoomedVH / 2;
    }

    /**
     * 世界坐标 X → 视图局部 X（相对于视口左上角，已应用缩放）。
     * 计算：(worldX - cameraX) * zoom
     */
    public int toViewX(int worldX) {
        return (int) ((worldX - x) * zoom);
    }

    /**
     * 世界坐标 Y → 视图局部 Y（相对于视口左上角，已应用缩放）。
     * 计算：(worldY - cameraY) * zoom
     */
    public int toViewY(int worldY) {
        return (int) ((worldY - y) * zoom);
    }

    /** 获取缩放后可见世界宽度（用于 TileMap 等计算可见范围） */
    public int getZoomedViewportWidth() {
        return (int) (viewportWidth / zoom);
    }

    /** 获取缩放后可见世界高度（用于 TileMap 等计算可见范围） */
    public int getZoomedViewportHeight() {
        return (int) (viewportHeight / zoom);
    }

    // ── 缩放控制 ──────────────────────────────────

    /** 放大一档（zoom 增大，tile 变大，可见区域变小） */
    public void zoomIn() {
        if (zoomIndex < ZOOM_LEVELS.length - 1) {
            setZoomLevel(zoomIndex + 1);
        }
    }

    /** 缩小一档（zoom 减小，tile 变小，可见区域变大） */
    public void zoomOut() {
        if (zoomIndex > 0) {
            setZoomLevel(zoomIndex - 1);
        }
    }

    /**
     * 设置缩放级别索引。
     *
     * @param index ZOOM_LEVELS 数组的索引，超出范围会被钳制
     */
    public void setZoomLevel(int index) {
        this.zoomIndex = Math.max(0, Math.min(index, ZOOM_LEVELS.length - 1));
        this.zoom = ZOOM_LEVELS[this.zoomIndex];
    }

    public double getZoom() { return zoom; }
    public int getZoomIndex() { return zoomIndex; }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getViewportWidth() { return viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
    public void setViewportWidth(int width) { this.viewportWidth = width; }
    public void setViewportHeight(int height) { this.viewportHeight = height; }
    /** 同时设置视口宽高（窗口 resize 时便捷方法） */
    public void setViewportSize(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }
}
