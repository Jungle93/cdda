package com.github.game.cdda.screen.overlay;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.screen.menu.MenuScreen;

import java.awt.*;

/**
 * 游戏内菜单屏幕。
 * 按 ESC 时通过 pushScreen 切换到此全屏菜单（不透明背景）。
 * ESC/返回游戏 通过 popScreen 恢复 MainScreen 及其游戏状态。
 * <p>
 * 当前选项：
 * <ul>
 *   <li>返回游戏 — popScreen 恢复游戏</li>
 *   <li>退出游戏 — 退出程序</li>
 * </ul>
 */
public class InGameMenuScreen extends MenuScreen {

    private static final String TITLE = "游戏菜单";
    private static final String[] ITEMS = {"返回游戏", "退出游戏"};
    private static final int ITEM_RETURN = 0;
    private static final int ITEM_QUIT = 1;

    public InGameMenuScreen(GameEngine engine) {
        super(engine);
    }

    @Override
    protected int getItemCount() {
        return ITEMS.length;
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

        // 菜单项
        int menuStartY = startY + 60;
        for (int i = 0; i < ITEMS.length; i++) {
            renderMenuItem(renderer, i, ITEMS[i], null, menuStartY + i * 40, 18);
        }

        // 底部提示
        drawHintBar(renderer, "↑↓ 选择   Enter 确认   Esc 返回游戏");
    }

    @Override
    protected void onSelect(int index) {
        switch (index) {
            case ITEM_RETURN:
                engine.getScreenManager().popScreen();
                break;
            case ITEM_QUIT:
                System.exit(0);
                break;
        }
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }
}
