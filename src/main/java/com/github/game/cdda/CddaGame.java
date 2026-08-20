package com.github.game.cdda;

import com.github.game.engine.core.EngineServices;
import com.github.game.cdda.config.ConfigManager;
import com.github.game.cdda.screen.menu.MainMenuScreen;
import com.github.game.engine.core.EngineConfig;
import com.github.game.engine.core.GameApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;

import javax.swing.*;

/**
 * CDDA 游戏入口。
 * 继承 GameApplication，只负责提供配置和初始化游戏内容，
 * 窗口管理、游戏循环等底层实现全部由引擎处理。
 */
public class CddaGame extends GameApplication {

    /** 日志目录（绝对路径，基于 user.dir） */
    private static final File LOG_DIR;

    private static final Logger logger;

    static {
        // 设置日志目录（绝对路径，基于 user.dir）
        String baseDir = System.getProperty("user.dir", ".");
        LOG_DIR = new File(baseDir, "logs");
        try {
            if (!LOG_DIR.exists()) {
                LOG_DIR.mkdirs();
            }
        } catch (Exception ignored) {
        }

        // 将 System.err 重定向为 Tee 输出：同时写控制台和日志文件。
        // slf4j-simple 在没有 logFile 配置时默认输出到 System.err，
        // 通过这种方式保证所有日志（包括 EDT 异常栈）都会写入日志文件。
        File logFile = new File(LOG_DIR, "game.log");
        try {
            FileOutputStream fos = new FileOutputStream(logFile, false);
            PrintStream origErr = System.err;
            PrintStream teeStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    fos.write(b);
                    origErr.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    fos.write(b, off, len);
                    origErr.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    fos.flush();
                    origErr.flush();
                }

                @Override
                public void close() throws IOException {
                    fos.close();
                    // 不关闭 origErr
                }
            }, true); // autoFlush
            System.setErr(teeStream);
            // slf4j-simple 可能已经在 init 时缓存了 System.err 引用，
            // 所以同时设置 logFile 系统属性作为备用
            System.setProperty("org.slf4j.simpleLogger.logFile", logFile.getAbsolutePath());
        } catch (Exception e) {
            // 如果失败，至少保证控制台输出
            System.err.println("无法初始化日志文件: " + e.getMessage());
        }

        logger = LoggerFactory.getLogger(CddaGame.class);
        logger.info("日志文件: {}", logFile.getAbsolutePath());

        // 安装 EDT 未捕获异常处理器
        installEdtExceptionHandler();
    }

    /**
     * 安装 EDT 异常处理器。
     * 通过替换 AWT 事件队列，在 dispatchEvent 层捕获异常并记录到日志。
     */
    private static void installEdtExceptionHandler() {
        try {
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
                @Override
                protected void dispatchEvent(AWTEvent event) {
                    try {
                        super.dispatchEvent(event);
                    } catch (Throwable t) {
                        System.err.println("[EDT 异常] " + t.getClass().getName() + ": " + t.getMessage());
                        t.printStackTrace();
                        try {
                            Logger edtLogger = LoggerFactory.getLogger("EDT");
                            edtLogger.error("EDT 未捕获异常: {}", t.getMessage(), t);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("安装 EDT 异常处理器失败: " + e.getMessage());
        }
    }

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
        // 初始化服务门面（一次性注册 GameEngine，暴露所有子系统）
        EngineServices.init(engine);

        // 从配置中读取语言设置并应用到 I18nManager
        ConfigManager cm = ConfigManager.getInstance();
        String locale = cm.getLocale();
        EngineServices.i18n.setLocale(locale);
        logger.info("服务门面初始化完成，当前语言: {} ({})",
                EngineServices.i18n.getLocale(), EngineServices.i18n.getLocaleDisplayName(EngineServices.i18n.getLocale()));

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
