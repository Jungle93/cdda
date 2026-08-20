package com.github.game.cdda.screen.menu;

import com.github.game.cdda.GameWorld;
import com.github.game.cdda.game.WorldSettings;
import com.github.game.cdda.game.time.Month;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.save.SaveManager;
import com.github.game.cdda.screen.MainScreen;
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
                loadGame();
                break;
            case ITEM_SETTINGS:
                engine.getScreenManager().switchScreen(new SettingsScreen(engine));
                break;
            case ITEM_QUIT:
                System.exit(0);
                break;
        }
    }

    /**
     * 加载存档（槽位 1）。
     * 创建新的 GameWorld 并加载存档数据。
     */
    private void loadGame() {
        com.github.game.cdda.game.CharacterSettings charSettings =
                new com.github.game.cdda.game.CharacterSettings();
        GameWorld world = new GameWorld(new WorldSettings(), charSettings, Month.MARCH, 8);
        boolean success = SaveManager.loadGame(world, 1);
        if (success) {
            GameLog.getInstance().log("游戏已从槽位 1 加载");
            engine.getScreenManager().switchScreen(new MainScreen(engine,
                    null, new com.github.game.cdda.game.CharacterSettings()));
        } else {
            GameLog.getInstance().log("加载失败，存档可能不存在");
        }
    }
}
