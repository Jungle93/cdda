package com.github.game.engine.core.scene;

import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;

/**
 * 抽象场景基类。管理屏幕上的一个矩形区域（Viewport），
 * 拥有独立的生命周期、渲染逻辑和输入钩子。
 *
 * 使用模式：
 * - Screen 持有多个 Scene，通过 addScene() 注册
 * - Screen.render() 遍历 Scene，每个 Scene 在 pushClip 后渲染
 * - Scene 内部使用局部坐标（0,0 = viewport 左上角）
 * - 世界场景可持有 Camera，Camera 的 screenOffset 应设为 viewport 位置
 *
 * 输入钩子接收的鼠标坐标为局部坐标（已由 Screen 转换），
 * 键盘事件为广播模式（所有 Scene 同时收到）。
 */
public abstract class Scene {

    /** 屏幕视口区域 */
    protected final Viewport viewport;

    /** 摄像机（可选，世界场景使用） */
    protected Camera camera;

    /**
     * 创建场景。
     *
     * @param viewport 屏幕视口区域
     */
    public Scene(Viewport viewport) {
        this.viewport = viewport;
    }

    // ── 生命周期 ──────────────────────────────────

    /** 首次进入时调用，用于初始化资源 */
    public void init() {}

    /** 每帧逻辑更新 */
    public void update(long deltaTime) {}

    /**
     * 每帧渲染。
     * 调用时 Renderer 已裁剪到 viewport 区域。
     * 使用局部坐标绘制（0,0 = viewport 左上角）。
     */
    public abstract void render(Renderer renderer);

    /** 离开时调用，释放资源 */
    public void dispose() {}

    // ── 输入钩子（局部坐标） ──────────────────────────────

    public void onMousePressed(int localX, int localY) {}
    public void onMouseReleased(int localX, int localY) {}
    public void onMouseClicked(int localX, int localY) {}
    public void onMouseMoved(int localX, int localY) {}
    public void onMouseDragged(int localX, int localY) {}

    public void onKeyPressed(int keyCode) {}
    public void onKeyReleased(int keyCode) {}
    public void onKeyTyped(int charCode) {}

    // ── 访问器 ──────────────────────────────────

    public Viewport getViewport() { return viewport; }

    public Camera getCamera() { return camera; }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }
}
