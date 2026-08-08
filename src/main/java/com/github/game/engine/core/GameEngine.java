package com.github.game.engine.core;

import com.github.game.cdda.config.ConfigManager;
import com.github.game.engine.core.input.InputManager;
import com.github.game.engine.core.resource.ResourceManager;
import com.github.game.engine.core.screen.ScreenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 游戏引擎核心，管理游戏循环和子系统。
 * 使用 Swing Timer 驱动，所有逻辑在 EDT 上执行。
 */
public class GameEngine implements ActionListener {

    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

    private static final int TARGET_FPS = 30;
    private static final int TIMER_DELAY = 1000 / TARGET_FPS; // ~33ms

    private final JFrame frame;
    private final GamePanel gamePanel;
    private final ScreenManager screenManager;
    private final InputManager inputManager;
    private final ResourceManager resourceManager;
    private final Timer timer;

    private long lastFrameTime;
    private boolean running;

    public GameEngine(JFrame frame) {
        this.frame = frame;
        this.screenManager = new ScreenManager(this);
        this.inputManager = new InputManager(this);
        this.resourceManager = new ResourceManager(ConfigManager.getInstance().getResourceBase());
        this.gamePanel = new GamePanel(this);
        this.timer = new Timer(TIMER_DELAY, this);
    }

    public void start() {
        if (running) return;
        running = true;
        lastFrameTime = System.currentTimeMillis();
        timer.start();
        logger.info("游戏引擎启动，目标帧率: {} FPS", TARGET_FPS);
    }

    public void stop() {
        running = false;
        timer.stop();
        logger.info("游戏引擎停止");
    }

    public void pause() {
        timer.stop();
        logger.debug("游戏引擎暂停");
    }

    public void resume() {
        if (running) {
            lastFrameTime = System.currentTimeMillis();
            timer.start();
            logger.debug("游戏引擎恢复");
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis();
        long deltaTime = now - lastFrameTime;
        lastFrameTime = now;

        // 逻辑更新
        if (screenManager.getCurrentScreen() != null) {
            screenManager.getCurrentScreen().update(deltaTime);
        }

        // 触发重绘
        gamePanel.repaint();
    }

    // ── 访问器 ──────────────────────────────────────

    public JFrame getFrame() {
        return frame;
    }

    public GamePanel getGamePanel() {
        return gamePanel;
    }

    public ScreenManager getScreenManager() {
        return screenManager;
    }

    public InputManager getInputManager() {
        return inputManager;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public boolean isRunning() {
        return running;
    }
}
