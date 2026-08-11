package com.github.game.engine.core;

import com.github.game.engine.core.audio.AudioEngine;
import com.github.game.engine.core.input.InputManager;
import com.github.game.engine.core.resource.ResourceManager;
import com.github.game.engine.core.screen.ScreenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.InputStream;

/**
 * 游戏引擎核心，管理游戏循环、子系统和窗口。
 * 使用 Swing Timer 驱动，所有逻辑在 EDT 上执行。
 * 引擎负责创建和管理 JFrame 窗口，游戏层只需配置 EngineConfig。
 */
public class GameEngine {

    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

    private static final int TARGET_FPS = 30;
    private static final int TIMER_DELAY = 1000 / TARGET_FPS; // ~33ms

    private final EngineConfig config;
    private final JFrame frame;
    private final GamePanel gamePanel;
    private final ScreenManager screenManager;
    private final InputManager inputManager;
    private final ResourceManager resourceManager;
    private final AudioEngine audioEngine;
    private final Timer timer;

    private long lastFrameTime;
    private boolean running;

    /**
     * 构造引擎。
     * 自动创建 JFrame 窗口并配置。
     * @param config 引擎配置（由游戏层构造并注入）
     */
    public GameEngine(EngineConfig config) {
        this.config = config;

        // 创建窗口
        frame = new JFrame();
        frame.setTitle("Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);
        frame.setMinimumSize(new java.awt.Dimension(400, 300));

        // 初始化子系统
        this.screenManager = new ScreenManager(this);
        this.inputManager = new InputManager(this);
        this.resourceManager = new ResourceManager(config.getResourceBase());
        this.audioEngine = new AudioEngine(this::loadAudioStream);
        this.gamePanel = new GamePanel(this);

        // 将 GamePanel 加入窗口
        frame.add(gamePanel);

        // 设置窗口尺寸
        frame.setSize(config.getWindowWidth(), config.getWindowHeight());

        // 初始化定时器
        this.timer = new Timer(TIMER_DELAY, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                tick();
            }
        });
    }

    /**
     * 启动游戏引擎。
     * 居中显示窗口并开始游戏循环。
     */
    public void start() {
        if (running) return;

        // 居中并显示窗口
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        running = true;
        lastFrameTime = System.currentTimeMillis();
        timer.start();
        logger.info("游戏引擎启动，目标帧率: {} FPS", config.getTargetFps());
    }

    public void stop() {
        running = false;
        timer.stop();
        audioEngine.dispose();
        frame.dispose();
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

    /** 单帧逻辑更新 */
    private void tick() {
        long now = System.currentTimeMillis();
        long deltaTime = now - lastFrameTime;
        lastFrameTime = now;

        // 音频更新
        audioEngine.update(deltaTime);

        // 逻辑更新
        if (screenManager.getCurrentScreen() != null) {
            screenManager.getCurrentScreen().update(deltaTime);
        }

        // 触发重绘
        gamePanel.repaint();
    }

    // ── 访问器 ──────────────────────────────────────

    public EngineConfig getConfig() {
        return config;
    }

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

    public AudioEngine getAudioEngine() {
        return audioEngine;
    }

    public boolean isRunning() {
        return running;
    }

    // ── 音频资源加载 ──────────────────────────────────────

    /**
     * 加载音频资源流。
     * 与 ResourceManager 类似，支持 classpath: 和 file: 前缀。
     */
    private InputStream loadAudioStream(String path) {
        // 处理 classpath: 前缀
        if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            if (!resourcePath.startsWith("/")) {
                resourcePath = "/" + resourcePath;
            }
            return getClass().getResourceAsStream(resourcePath);
        }

        // 处理 file: 前缀
        if (path.startsWith("file:")) {
            String filePath = path.substring("file:".length());
            try {
                return new java.io.FileInputStream(filePath);
            } catch (java.io.FileNotFoundException e) {
                return null;
            }
        }

        // 无前缀：先尝试 classpath，再尝试外部文件
        String resourcePath = path.startsWith("/") ? path : "/" + path;
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in != null) return in;

        // 尝试外部文件
        String filePath = config.getResourceBase() + java.io.File.separator + path;
        try {
            return new java.io.FileInputStream(filePath);
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
    }
}
