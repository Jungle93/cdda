package com.github.game.engine.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏应用基类。
 * 封装引擎初始化和生命周期管理，游戏层继承此类并实现特定逻辑。
 * <p>
 * 游戏层只需：
 * 1. 继承 GameApplication
 * 2. 实现 {@link #createConfig()} 提供引擎配置
 * 3. 实现 {@link #init()} 初始化游戏内容（如设置初始屏幕）
 * 4. 调用 {@link #start()} 启动
 */
public abstract class GameApplication {

    private static final Logger logger = LoggerFactory.getLogger(GameApplication.class);

    protected GameEngine engine;

    public GameApplication() {
        logger.info("初始化游戏应用...");

        // 由子类提供配置
        EngineConfig config = createConfig();

        // 创建引擎
        engine = new GameEngine(config);

        // 由子类初始化游戏内容
        init();

        logger.info("游戏应用初始化完成");
    }

    /**
     * 创建引擎配置。
     * 子类必须实现此方法，提供游戏特定的引擎配置。
     * @return 引擎配置
     */
    protected abstract EngineConfig createConfig();

    /**
     * 初始化游戏内容。
     * 子类在此设置初始屏幕、加载资源等。
     * 此时 engine 已创建，可通过 {@link #getEngine()} 访问。
     */
    protected abstract void init();

    /** 启动游戏 */
    public void start() {
        engine.start();
    }

    /** 停止游戏 */
    public void stop() {
        engine.stop();
    }

    /** 获取引擎实例 */
    public GameEngine getEngine() {
        return engine;
    }
}
