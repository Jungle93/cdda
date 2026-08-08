package com.github.game.engine.core;

/**
 * 引擎配置。
 * 封装引擎运行所需的全部参数，由游戏层构造后注入 GameEngine。
 * <p>
 * 引擎运行过程中可能修改某些配置（如窗口大小改变），
 * 通过 {@link OnChangeListener} 回调通知游戏层进行持久化等处理。
 */
public class EngineConfig {

    /** 目标帧率 */
    private int targetFps = 30;

    /** 窗口宽度（像素） */
    private int windowWidth = 600;

    /** 窗口高度（像素） */
    private int windowHeight = 400;

    /** 渲染字体大小（pt） */
    private int fontSize = 14;

    /** 外部资源基准目录（空字符串表示不指定） */
    private String resourceBase = "";

    /** 配置变更监听器 */
    private OnChangeListener changeListener;

    public EngineConfig() {}

    // ── 变更监听 ──────────────────────────────────────

    /**
     * 配置变更回调接口。
     * 引擎在运行过程中修改配置时（如窗口缩放），通过此接口通知游戏层。
     */
    public interface OnChangeListener {
        /** 窗口尺寸变更 */
        void onWindowResized(int width, int height);
    }

    public void setOnChangeListener(OnChangeListener listener) {
        this.changeListener = listener;
    }

    public OnChangeListener getOnChangeListener() {
        return changeListener;
    }

    /** 通知监听器窗口尺寸变化（由引擎内部调用） */
    public void fireWindowResized(int width, int height) {
        if (changeListener != null) {
            changeListener.onWindowResized(width, height);
        }
    }

    // ── 访问器 ──────────────────────────────────────

    public int getTargetFps() {
        return targetFps;
    }

    public void setTargetFps(int targetFps) {
        this.targetFps = targetFps;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public String getResourceBase() {
        return resourceBase;
    }

    public void setResourceBase(String resourceBase) {
        this.resourceBase = resourceBase;
    }
}
