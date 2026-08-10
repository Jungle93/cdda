package com.github.game.cdda.screen.menu;

import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

/**
 * 主菜单屏幕。
 * 提供四个选项：新游戏、加载存档、设置、退出游戏。
 * 使用 ↑↓ 导航，Enter 确认。
 */
public class MainMenuScreen extends MenuScreen {

    private static final String TITLE = "CDDA";

    /** 菜单项索引常量 */
    private static final int ITEM_NEW_GAME = 0;
    private static final int ITEM_LOAD_GAME = 1;
    private static final int ITEM_SETTINGS = 2;
    private static final int ITEM_QUIT = 3;

    private static final String[] ITEMS = {"新游戏", "加载存档", "设置", "退出游戏"};

    public MainMenuScreen(GameEngine engine) {
        super(engine);
    }

    @Override
    public void init() {}

    @Override
    protected int getItemCount() {
        return ITEMS.length;
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        int height = getHeight();

        // 标题
        drawTitle(renderer, TITLE, 36, height / 4);

        // 菜单选项
        int startY = height / 2;
        int lineSpacing = 40;
        for (int i = 0; i < ITEMS.length; i++) {
            renderMenuItem(renderer, i, ITEMS[i], null,
                    startY + i * lineSpacing, 18);
        }

        // 底部提示
        drawHintBar(renderer, "↑↓ 选择   Enter 确认");
    }

    @Override
    protected void onSelect(int index) {
        switch (index) {
            case ITEM_NEW_GAME:
                engine.getScreenManager().switchScreen(new GameSetupScreen(engine));
                break;
            case ITEM_LOAD_GAME:
                // TODO: 加载存档功能待实现
                break;
            case ITEM_SETTINGS:
                engine.getScreenManager().switchScreen(new SettingsScreen(engine));
                break;
            case ITEM_QUIT:
                System.exit(0);
                break;
        }
    }
}
