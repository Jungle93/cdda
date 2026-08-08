package com.github.game.cdda;

import com.github.game.cdda.config.ConfigManager;
import com.github.game.cdda.screen.menu.MainMenuScreen;
import com.github.game.engine.core.EngineConfig;
import com.github.game.engine.core.GameApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * CDDA 游戏入口。
 * 继承 GameApplication，只负责提供配置和初始化游戏内容，
 * 窗口管理、游戏循环等底层实现全部由引擎处理。
 */
public class CddaGame extends GameApplication {

    private static final Logger logger = LoggerFactory.getLogger(CddaGame.class);

    @Override
    protected EngineConfig createConfig() {
        ConfigManager cm = ConfigManager.getInstance();

        EngineConfig config = new EngineConfig();
        config.setWindowWidth(cm.getWindowWidth());
        config.setWindowHeight(cm.getWindowHeight());
        config.setFontSize(cm.getFontSize());
        config.setResourceBase(cm.getResourceBase());

        // 监听引擎配置变更，持久化到游戏配置
        config.setOnChangeListener(new EngineConfig.OnChangeListener() {
            @Override
            public void onWindowResized(int width, int height) {
                cm.setGameInt(ConfigManager.KEY_WINDOW_WIDTH, width);
                cm.setGameInt(ConfigManager.KEY_WINDOW_HEIGHT, height);
            }
        });

        logger.info("窗口尺寸: {}x{}, 字体大小: {}pt, 存档路径: {}",
                config.getWindowWidth(), config.getWindowHeight(),
                config.getFontSize(), cm.getSavePath());

        return config;
    }

    @Override
    protected void init() {
        // 设置初始屏幕为主菜单
        engine.getScreenManager().switchScreen(new MainMenuScreen(engine));
    }

    public static void main(String[] args) {
        logger.info("CDDA 启动中...");
        SwingUtilities.invokeLater(() -> {
            CddaGame game = new CddaGame();
            game.start();
        });
    }
}
