package com.github.game.cdda.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 配置管理器（单例）。
 * 管理两类配置：
 * - 系统配置（游戏无关）：存储在项目路径 config/ 目录，如存档位置
 * - 游戏配置（游戏相关）：存储在存档位置目录，如窗口大小、字体大小
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static final ConfigManager INSTANCE = new ConfigManager();

    /** 系统配置文件路径：项目路径/config/system.properties */
    private static final String SYSTEM_CONFIG_DIR = "config";
    private static final String SYSTEM_CONFIG_FILE = "system.properties";

    /** 游戏配置文件名（存放在存档目录下） */
    private static final String GAME_CONFIG_FILE = "game.properties";

    /** 系统配置 */
    private final Properties systemProps = new Properties();
    /** 游戏配置 */
    private final Properties gameProps = new Properties();

    /** 系统配置文件引用 */
    private Path systemConfigPath;
    /** 游戏配置文件引用 */
    private Path gameConfigPath;

    // ── 配置键名常量 ──────────────────────────────────

    /** 存档位置（系统配置） */
    public static final String KEY_SAVE_PATH = "save.path";
    /** 资源基准目录（系统配置） */
    public static final String KEY_RESOURCE_BASE = "resource.base";
    /** 窗口宽度（游戏配置） */
    public static final String KEY_WINDOW_WIDTH = "window.width";
    /** 窗口高度（游戏配置） */
    public static final String KEY_WINDOW_HEIGHT = "window.height";
    /** 字体大小（游戏配置） */
    public static final String KEY_FONT_SIZE = "font.size";
    /** 信息面板宽度（游戏配置） */
    public static final String KEY_INFO_PANEL_WIDTH = "info.panel.width";
    /** 质量显示单位（游戏配置） */
    public static final String KEY_UNIT_MASS = "display.unit.mass";
    /** 体积显示单位（游戏配置） */
    public static final String KEY_UNIT_VOLUME = "display.unit.volume";

    private ConfigManager() {
        initDefaults();
        loadSystemConfig();
        loadGameConfig();
    }

    /** 获取单例实例 */
    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    /** 初始化默认值 */
    private void initDefaults() {
        // 系统配置默认值
        systemProps.setProperty(KEY_SAVE_PATH, "saves");

        // 游戏配置默认值
        gameProps.setProperty(KEY_WINDOW_WIDTH, "600");
        gameProps.setProperty(KEY_WINDOW_HEIGHT, "400");
        gameProps.setProperty(KEY_FONT_SIZE, "14");
        gameProps.setProperty(KEY_INFO_PANEL_WIDTH, "180");
        gameProps.setProperty(KEY_UNIT_MASS, "GRAM");
        gameProps.setProperty(KEY_UNIT_VOLUME, "MILLILITER");
    }

    // ── 加载 ──────────────────────────────────────────

    /** 加载系统配置（项目路径/config/） */
    private void loadSystemConfig() {
        try {
            systemConfigPath = Paths.get(SYSTEM_CONFIG_DIR, SYSTEM_CONFIG_FILE);
            if (Files.exists(systemConfigPath)) {
                try (Reader reader = Files.newBufferedReader(systemConfigPath)) {
                    Properties loaded = new Properties();
                    loaded.load(reader);
                    // 合并到默认值上（已存在的默认值不覆盖）
                    for (String key : loaded.stringPropertyNames()) {
                        systemProps.setProperty(key, loaded.getProperty(key));
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("加载系统配置失败，使用默认值: {}", e.getMessage());
        }
    }

    /** 加载游戏配置（存档目录下） */
    private void loadGameConfig() {
        try {
            String savePath = systemProps.getProperty(KEY_SAVE_PATH);
            gameConfigPath = Paths.get(savePath, GAME_CONFIG_FILE);
            if (Files.exists(gameConfigPath)) {
                try (Reader reader = Files.newBufferedReader(gameConfigPath)) {
                    Properties loaded = new Properties();
                    loaded.load(reader);
                    for (String key : loaded.stringPropertyNames()) {
                        gameProps.setProperty(key, loaded.getProperty(key));
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("加载游戏配置失败，使用默认值: {}", e.getMessage());
        }
    }

    // ── 保存 ──────────────────────────────────────────

    /** 保存系统配置到文件 */
    public void saveSystemConfig() {
        try {
            Files.createDirectories(systemConfigPath.getParent());
            try (Writer writer = Files.newBufferedWriter(systemConfigPath)) {
                systemProps.store(writer, "系统配置（游戏无关）");
            }
        } catch (IOException e) {
            logger.error("保存系统配置失败: {}", e.getMessage(), e);
        }
    }

    /** 保存游戏配置到文件（存档目录） */
    public void saveGameConfig() {
        try {
            Files.createDirectories(gameConfigPath.getParent());
            try (Writer writer = Files.newBufferedWriter(gameConfigPath)) {
                gameProps.store(writer, "游戏配置（游戏相关）");
            }
        } catch (IOException e) {
            logger.error("保存游戏配置失败: {}", e.getMessage(), e);
        }
    }

    // ── 访问器 ──────────────────────────────────────────

    /** 获取系统配置值 */
    public String getSystem(String key) {
        return systemProps.getProperty(key);
    }

    /** 设置系统配置值 */
    public void setSystem(String key, String value) {
        systemProps.setProperty(key, value);
    }

    /** 获取游戏配置值 */
    public String getGame(String key) {
        return gameProps.getProperty(key);
    }

    /** 设置游戏配置值 */
    public void setGame(String key, String value) {
        gameProps.setProperty(key, value);
    }

    // ── 类型化便捷方法 ──────────────────────────────────

    public int getGameInt(String key) {
        try {
            return Integer.parseInt(gameProps.getProperty(key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setGameInt(String key, int value) {
        gameProps.setProperty(key, String.valueOf(value));
    }

    /** 获取存档路径 */
    public String getSavePath() {
        return getSystem(KEY_SAVE_PATH);
    }

    /** 获取资源基准目录 */
    public String getResourceBase() {
        return getSystem(KEY_RESOURCE_BASE);
    }

    /** 获取窗口宽度 */
    public int getWindowWidth() {
        return getGameInt(KEY_WINDOW_WIDTH);
    }

    /** 获取窗口高度 */
    public int getWindowHeight() {
        return getGameInt(KEY_WINDOW_HEIGHT);
    }

    /** 获取字体大小 */
    public int getFontSize() {
        return getGameInt(KEY_FONT_SIZE);
    }

    /** 获取信息面板宽度 */
    public int getInfoPanelWidth() {
        return getGameInt(KEY_INFO_PANEL_WIDTH);
    }
}
