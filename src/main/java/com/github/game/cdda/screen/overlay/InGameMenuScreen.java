package com.github.game.cdda.screen.overlay;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.save.SaveManager;
import com.github.game.cdda.screen.menu.MainMenuScreen;
import com.github.game.cdda.screen.menu.MenuScreen;

import java.awt.*;

/**
 * 游戏内菜单屏幕。
 * 按 ESC 时通过 pushScreen 切换到此全屏菜单（不透明背景）。
 * ESC/返回游戏 通过 popScreen 恢复 MainScreen 及其游戏状态。
 * <p>
 * 选项：
 * <ul>
 *   <li>返回游戏 — popScreen 恢复游戏</li>
 *   <li>保存游戏 — 保存后自动关闭菜单</li>
 *   <li>回到主菜单 — 确认后保存并返回主菜单</li>
 *   <li>设置 — 打开游戏内设置（InGameSettingsScreen）</li>
 *   <li>退出游戏 — 退出程序</li>
 * </ul>
 */
public class InGameMenuScreen extends MenuScreen {

    private static final String TITLE = "游戏菜单";
    private static final String[] ITEMS = {"返回游戏", "保存游戏", "回到主菜单", "设置", "退出游戏"};
    private static final int ITEM_RETURN = 0;
    private static final int ITEM_SAVE = 1;
    private static final int ITEM_MAIN_MENU = 2;
    private static final int ITEM_SETTINGS = 3;
    private static final int ITEM_QUIT = 4;

    /** 游戏场景引用（用于获取 GameWorld） */
    private final com.github.game.cdda.screen.scene.GameScene gameScene;

    /** 保存反馈消息（null 表示无消息） */
    private String saveMessage;

    /** 保存反馈消息剩余显示时间（毫秒），0 表示立即关闭 */
    private long saveMessageTimer;

    /** 反馈消息是否成功（控制颜色） */
    private boolean saveSuccess;

    /** 是否处于确认模式（回到主菜单的二次确认） */
    private boolean inConfirmation = false;

    /** 确认模式下的选项 */
    private static final String[] CONFIRM_ITEMS = {"是，保存并返回", "否，继续游戏"};

    /**
     * 创建游戏内菜单。
     *
     * @param engine    游戏引擎
     * @param gameScene 游戏场景（用于获取 GameWorld，可为 null）
     */
    public InGameMenuScreen(GameEngine engine, com.github.game.cdda.screen.scene.GameScene gameScene) {
        super(engine);
        this.gameScene = gameScene;
    }

    @Override
    protected int getItemCount() {
        return inConfirmation ? CONFIRM_ITEMS.length : ITEMS.length;
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 清屏不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int height = getHeight();
        int startY = height / 3;

        if (inConfirmation) {
            // 确认模式：显示确认提示
            drawTitle(renderer, "回到主菜单", 28, startY);

            int menuStartY = startY + 60;

            // 确认提示文字
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 16));
            renderer.setColor(Color.WHITE);
            drawCentered(renderer, "是否保存游戏并返回主菜单？", menuStartY - 30);

            // 确认选项
            for (int i = 0; i < CONFIRM_ITEMS.length; i++) {
                renderMenuItem(renderer, i, CONFIRM_ITEMS[i], null, menuStartY + i * 40, 18);
            }
        } else {
            // 正常模式
            drawTitle(renderer, TITLE, 28, startY);

            int menuStartY = startY + 60;
            for (int i = 0; i < ITEMS.length; i++) {
                renderMenuItem(renderer, i, ITEMS[i], null, menuStartY + i * 40, 18);
            }

            // 保存反馈消息（居中显示在菜单上方）
            if (saveMessage != null && saveMessageTimer > 0) {
                renderer.setFont(new Font("Monospaced", Font.BOLD, 16));
                renderer.setColor(saveSuccess ? new Color(0, 220, 0) : new Color(220, 0, 0));
                drawCentered(renderer, saveMessage, menuStartY - 40);
            }
        }

        // 底部提示
        String hint = inConfirmation
                ? "↑↓ 选择   Enter 确认   Esc 取消"
                : "↑↓ 选择   Enter 确认   Esc 返回游戏";
        drawHintBar(renderer, hint);
    }

    @Override
    public void update(long deltaTime) {
        // 倒计时保存反馈消息
        if (saveMessage != null && saveMessageTimer > 0) {
            saveMessageTimer -= deltaTime;
            if (saveMessageTimer <= 0) {
                saveMessage = null;
                saveMessageTimer = 0;
                // 失败时保留菜单（timer 已置 0，不会再次触发 popScreen）
                if (!saveSuccess) {
                    return;
                }
            }
        }
        // 保存成功后消息已清除，关闭菜单返回游戏
        if (saveMessage == null && saveSuccess) {
            saveSuccess = false;
            engine.getScreenManager().popScreen();
        }
    }

    @Override
    protected void onSelect(int index) {
        // 如果正在显示保存反馈消息，忽略输入
        if (saveMessage != null && saveMessageTimer > 0) {
            return;
        }

        if (inConfirmation) {
            // 确认模式处理
            if (index == 0) {
                // 是 — 保存游戏并返回主菜单
                if (gameScene != null && gameScene.getWorld() != null) {
                    boolean success = SaveManager.saveGame(gameScene.getWorld(), 1);
                    com.github.game.cdda.log.GameLog.getInstance().log(
                            success ? "游戏已保存" : "保存失败，请查看日志");
                }
                // 切换到主菜单（替换当前所有屏幕）
                engine.getScreenManager().switchScreen(new MainMenuScreen(engine));
            } else {
                // 否 — 取消，返回菜单
                inConfirmation = false;
            }
            return;
        }

        switch (index) {
            case ITEM_RETURN:
                engine.getScreenManager().popScreen();
                break;
            case ITEM_SAVE:
                if (gameScene != null && gameScene.getWorld() != null) {
                    boolean success = SaveManager.saveGame(gameScene.getWorld(), 1);
                    saveSuccess = success;
                    if (success) {
                        // 成功：显示短暂消息后由 update() 立即关闭
                        saveMessage = "✓ 游戏已保存";
                        saveMessageTimer = 500;
                    } else {
                        // 失败：显示较长时间让用户看到错误
                        saveMessage = "✗ 保存失败，请查看日志";
                        saveMessageTimer = 2500;
                    }
                    com.github.game.cdda.log.GameLog.getInstance().log(
                            success ? "游戏已保存到槽位 1" : "保存失败，请查看日志");
                }
                break;
            case ITEM_MAIN_MENU:
                // 进入确认模式
                inConfirmation = true;
                selectedIndex = 0;
                break;
            case ITEM_SETTINGS:
                // 打开游戏内设置（叠加在菜单之上）
                engine.getScreenManager().pushScreen(
                        new InGameSettingsScreen(engine, gameScene));
                break;
            case ITEM_QUIT:
                System.exit(0);
                break;
        }
    }

    @Override
    protected void onCancel() {
        if (inConfirmation) {
            // 确认模式下 Esc 取消确认，返回菜单
            inConfirmation = false;
        } else {
            engine.getScreenManager().popScreen();
        }
    }
}
