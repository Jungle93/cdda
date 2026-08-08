package com.github.game.cdda;

import com.github.game.cdda.config.ConfigManager;
import com.github.game.engine.core.GameEngine;
import com.github.game.cdda.screen.menu.MainMenuScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * 游戏主入口。配置 JFrame 并启动游戏引擎。
 * 窗口尺寸从 ConfigManager 读取，支持运行时调整。
 */
public class Game extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(Game.class);

    private GameEngine engine;

    public Game() throws HeadlessException {
        setTitle("CDDA");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
//        setUndecorated(true);
    }

    private void initialize() {
        // 确保配置已加载
        ConfigManager cm = ConfigManager.getInstance();
        logger.info("窗口尺寸: {}x{}, 字体大小: {}pt, 存档路径: {}",
                cm.getWindowWidth(), cm.getWindowHeight(), cm.getFontSize(), cm.getSavePath());

        engine = new GameEngine(this);

        // 将 GamePanel 加入 JFrame
        add(engine.getGamePanel());

        // 设置窗口大小（从配置读取）
        setSize(cm.getWindowWidth(), cm.getWindowHeight());

        // 切换到主菜单屏幕
        engine.getScreenManager().switchScreen(new MainMenuScreen(engine));

        pack();
        // pack 之后再居中（此时窗口已有正确尺寸）
        setLocationRelativeTo(null);
        logger.info("游戏初始化完成");
    }

    private void start() {
        engine.start();
    }

    public GameEngine getEngine() {
        return engine;
    }

    public static void main(String[] args) {
        logger.info("CDDA 启动中...");
        SwingUtilities.invokeLater(() -> {
            Game game = new Game();
            game.initialize();
            game.setVisible(true);
            game.start();
        });
    }
}
