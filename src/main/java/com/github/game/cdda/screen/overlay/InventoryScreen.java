package com.github.game.cdda.screen.overlay;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.screen.menu.MenuScreen;

import java.awt.*;

/**
 * 物品栏屏幕（占位）。
 * 按 I 打开。当前无物品系统，仅显示占位信息。
 * ESC 返回游戏。
 */
public class InventoryScreen extends MenuScreen {

    private static final String TITLE = "背包";

    public InventoryScreen(GameEngine engine) {
        super(engine);
    }

    @Override
    protected int getItemCount() {
        return 0;  // 无可选菜单项
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 清屏不透明黑色背景
        renderer.setColor(Color.BLACK);
        renderer.fillRect(0, 0, getWidth(), getHeight());

        // 标题
        int height = getHeight();
        drawTitle(renderer, TITLE, 28, height / 3);

        // 占位信息
        renderer.setColor(Color.GRAY);
        String msg = "背包空空如也...";
        int msgY = height / 3 + 50;
        int msgX = (getWidth() - renderer.getTextWidth(msg)) / 2;
        renderer.drawText(msg, msgX, msgY);

        // 底部提示
        drawHintBar(renderer, "Esc 返回");
    }

    @Override
    protected void onSelect(int index) {
        // 无选项，不处理
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }
}
