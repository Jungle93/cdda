package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.config.ConfigManager;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.cdda.screen.scene.GameScene;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;

/**
 * 游戏内设置屏幕。
 * 从游戏内菜单（ESC）打开，提供相机缩放等游戏内可调设置。
 * <p>
 * 与主菜单设置不同，此处设置立即生效并保存到配置：
 * <ul>
 *   <li>相机缩放 — 调整当前游戏的视野远近</li>
 * </ul>
 * <p>
 * Esc 返回游戏内菜单（popScreen）。
 */
public class InGameSettingsScreen extends MenuScreen {

    private static final String TITLE = "游戏设置";
    private static final String[] LABELS = {"相机缩放"};
    private static final int ITEM_ZOOM = 0;
    private static final int ITEM_COUNT = LABELS.length;

    /** 当前缩放级别索引（本地缓存，与 Camera 同步） */
    private int zoomIndex;

    /** 游戏场景引用（用于直接访问 Camera） */
    private final GameScene gameScene;

    /**
     * 创建游戏内设置屏幕。
     *
     * @param engine    游戏引擎
     * @param gameScene 游戏场景（用于直接访问 Camera，可为 null）
     */
    public InGameSettingsScreen(GameEngine engine, GameScene gameScene) {
        super(engine);
        this.gameScene = gameScene;
    }

    @Override
    public void init() {
        // 从当前 Camera 读取缩放级别（若已初始化），否则从配置读取
        Camera camera = getCamera();
        if (camera != null) {
            zoomIndex = camera.getZoomIndex();
        } else {
            zoomIndex = ConfigManager.getInstance().getCameraZoomLevel();
        }
    }

    @Override
    protected int getItemCount() {
        return ITEM_COUNT;
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 清屏不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, getWidth(), getHeight());

        // 标题
        int height = getHeight();
        int startY = height / 3;
        drawTitle(renderer, TITLE, 28, startY);

        // 菜单项（带当前值显示）
        int menuStartY = startY + 60;
        for (int i = 0; i < ITEM_COUNT; i++) {
            String value = getValueString(i);
            renderMenuItem(renderer, i, LABELS[i], value, menuStartY + i * 40, 18);
        }

        // 底部提示
        drawHintBar(renderer, "↑↓ 选择   ←→ 调整   Esc 返回");
    }

    /**
     * 获取指定设置项的当前值显示字符串。
     */
    private String getValueString(int index) {
        switch (index) {
            case ITEM_ZOOM:
                double zoom = Camera.ZOOM_LEVELS[zoomIndex];
                // 格式化缩放倍率（如 "1x"、"1.5x"）
                String zoomStr = (zoom == (int) zoom)
                        ? String.format("%.0fx", zoom)
                        : String.format("%.2fx", zoom);
                return zoomStr;
            default:
                return "";
        }
    }

    @Override
    protected void onSelect(int index) {
        // 设置项不需要 Enter 确认，调整通过 ←→ 完成
    }

    @Override
    protected void onAdjust(int index, int direction) {
        switch (index) {
            case ITEM_ZOOM:
                int newIndex = Math.max(0, Math.min(zoomIndex + direction,
                        Camera.ZOOM_LEVELS.length - 1));
                if (newIndex != zoomIndex) {
                    zoomIndex = newIndex;
                    applyZoom();
                }
                break;
        }
    }

    @Override
    protected void onCancel() {
        // 返回游戏内菜单
        engine.getScreenManager().popScreen();
    }

    /**
     * 将当前缩放级别应用到 Camera 并保存到配置。
     */
    private void applyZoom() {
        // 保存到配置
        ConfigManager configManager = ConfigManager.getInstance();
        configManager.setCameraZoomLevel(zoomIndex);
        configManager.saveGameConfig();

        // 立即应用到当前 Camera
        Camera camera = getCamera();
        if (camera != null) {
            camera.setZoomLevel(zoomIndex);
        }
    }

    /**
     * 获取游戏场景的 Camera（如果游戏已初始化）。
     */
    private Camera getCamera() {
        if (gameScene != null) {
            return gameScene.getCamera();
        }
        return null;
    }
}
