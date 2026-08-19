package com.github.game.engine.core.screen;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.Scene;
import com.github.game.engine.core.scene.Viewport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 抽象屏幕基类。每个 Screen 代表一个独立的游戏状态/界面。
 *
 * Screen 同时是 {@link Scene} 的容器：
 * - 子类可通过 {@link #addScene(Scene)} 注册场景
 * - 默认的 render/update/dispose 自动遍历所有场景
 * - 鼠标输入按场景的 Viewport 分发（转换为局部坐标）
 * - 键盘输入广播给所有场景
 *
 * 不使用 Scene 的子类可直接覆写 render/update 等方法。
 */
public abstract class Screen {

    protected final GameEngine engine;

    /** 场景列表（按注册顺序渲染） */
    private final List<Scene> scenes = new ArrayList<>();

    public Screen(GameEngine engine) {
        this.engine = engine;
    }

    // ── 场景管理 ──────────────────────────────────

    /** 添加场景（按添加顺序渲染和更新） */
    protected void addScene(Scene scene) {
        scenes.add(scene);
    }

    /** 移除场景 */
    protected void removeScene(Scene scene) {
        scenes.remove(scene);
    }

    /** 获取场景列表（只读） */
    protected List<Scene> getScenes() {
        return Collections.unmodifiableList(scenes);
    }

    // ── 生命周期（默认实现遍历场景，子类可覆写） ──

    /**
     * 首次进入时调用。默认调用各场景的 init()。
     */
    public void init() {}

    /**
     * 每帧逻辑更新。默认调用各场景的 update()。
     */
    public void update(long deltaTime) {
        for (Scene scene : scenes) {
            scene.update(deltaTime);
        }
    }

    /**
     * 每帧渲染。默认遍历场景，每个场景在 pushClip + pushTranslate 后渲染。
     * 场景内部使用局部坐标（0,0 = viewport 左上角）。
     * <p>
     * 不使用 Scene 的子类应直接覆写此方法。
     */
    public void render(Renderer renderer) {
        for (Scene scene : scenes) {
            Viewport vp = scene.getViewport();
            renderer.pushClip(vp.getX(), vp.getY(), vp.getWidth(), vp.getHeight());
            renderer.pushTranslate(vp.getX(), vp.getY());
            scene.render(renderer);
            renderer.popTranslate();
            renderer.popClip();
        }
    }

    /**
     * 离开时调用。默认调用各场景的 dispose()。
     */
    public void dispose() {
        for (Scene scene : scenes) {
            scene.dispose();
        }
    }

    // ── 输入钩子（默认路由到场景，子类可覆写） ──

    /**
     * 鼠标按下。默认按 Viewport 分发，坐标转换为场景局部坐标。
     */
    public void onMousePressed(int screenX, int screenY) {
        for (Scene scene : scenes) {
            Viewport vp = scene.getViewport();
            if (vp.contains(screenX, screenY)) {
                scene.onMousePressed(vp.toLocalX(screenX), vp.toLocalY(screenY));
            }
        }
    }

    public void onMouseReleased(int screenX, int screenY) {
        for (Scene scene : scenes) {
            Viewport vp = scene.getViewport();
            if (vp.contains(screenX, screenY)) {
                scene.onMouseReleased(vp.toLocalX(screenX), vp.toLocalY(screenY));
            }
        }
    }

    public void onMouseClicked(int screenX, int screenY) {
        for (Scene scene : scenes) {
            Viewport vp = scene.getViewport();
            if (vp.contains(screenX, screenY)) {
                scene.onMouseClicked(vp.toLocalX(screenX), vp.toLocalY(screenY));
            }
        }
    }

    public void onMouseMoved(int screenX, int screenY) {
        for (Scene scene : scenes) {
            Viewport vp = scene.getViewport();
            if (vp.contains(screenX, screenY)) {
                scene.onMouseMoved(vp.toLocalX(screenX), vp.toLocalY(screenY));
            }
        }
    }

    public void onMouseDragged(int screenX, int screenY) {
        for (Scene scene : scenes) {
            Viewport vp = scene.getViewport();
            if (vp.contains(screenX, screenY)) {
                scene.onMouseDragged(vp.toLocalX(screenX), vp.toLocalY(screenY));
            }
        }
    }

    /**
     * 键盘按下。默认广播给所有场景。
     */
    public void onKeyPressed(int keyCode) {
        for (Scene scene : scenes) {
            scene.onKeyPressed(keyCode);
        }
    }

    public void onKeyReleased(int keyCode) {
        for (Scene scene : scenes) {
            scene.onKeyReleased(keyCode);
        }
    }

    public void onKeyTyped(int charCode) {
        for (Scene scene : scenes) {
            scene.onKeyTyped(charCode);
        }
    }

    /**
     * 鼠标滚轮向上（远离用户）。默认空实现，子类可覆写。
     * 通常用于缩放操作。
     */
    public void onMouseWheelUp() {}

    /**
     * 鼠标滚轮向下（朝向用户）。默认空实现，子类可覆写。
     * 通常用于缩放操作。
     */
    public void onMouseWheelDown() {}

    /**
     * 窗口尺寸变更通知。
     * 当用户拖拽调整窗口大小时，引擎通过 ScreenManager 调用此方法。
     * 默认空实现，子类可覆写以更新布局（如重新计算 Viewport 尺寸）。
     *
     * @param width  新窗口宽度（像素）
     * @param height 新窗口高度（像素）
     */
    public void onWindowResized(int width, int height) {}

    // ── 工具方法 ──────────────────────────────────

    public GameEngine getEngine() {
        return engine;
    }

    public int getWidth() {
        return engine.getGamePanel().getWidth();
    }

    public int getHeight() {
        return engine.getGamePanel().getHeight();
    }
}
