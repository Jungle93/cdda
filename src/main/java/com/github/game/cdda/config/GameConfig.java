package com.github.game.cdda.config;

import com.github.game.cdda.item.model.DisplayUnit;

/**
 * 游戏配置封装。
 * 提供类型安全的访问器，代理到 ConfigManager。
 */
public class GameConfig {

    private final ConfigManager cm = ConfigManager.getInstance();

    // ── 窗口 ──────────────────────────────────────────

    public int getWindowWidth() {
        return cm.getWindowWidth();
    }

    public void setWindowWidth(int width) {
        cm.setGameInt(ConfigManager.KEY_WINDOW_WIDTH, width);
    }

    public int getWindowHeight() {
        return cm.getWindowHeight();
    }

    public void setWindowHeight(int height) {
        cm.setGameInt(ConfigManager.KEY_WINDOW_HEIGHT, height);
    }

    // ── 字体 ──────────────────────────────────────────

    public int getFontSize() {
        return cm.getFontSize();
    }

    public void setFontSize(int size) {
        cm.setGameInt(ConfigManager.KEY_FONT_SIZE, size);
    }

    // ── 信息面板 ──────────────────────────────────────────

    public int getInfoPanelWidth() {
        return cm.getInfoPanelWidth();
    }

    public void setInfoPanelWidth(int width) {
        cm.setGameInt(ConfigManager.KEY_INFO_PANEL_WIDTH, width);
    }

    // ── 显示单位 ──────────────────────────────────────────

    /** 获取质量显示单位 */
    public DisplayUnit getMassUnit() {
        String name = cm.getGame(ConfigManager.KEY_UNIT_MASS);
        try {
            return DisplayUnit.valueOf(name);
        } catch (Exception e) {
            return DisplayUnit.GRAM;
        }
    }

    /** 设置质量显示单位 */
    public void setMassUnit(DisplayUnit unit) {
        cm.setGame(ConfigManager.KEY_UNIT_MASS, unit.name());
    }

    /** 获取体积显示单位 */
    public DisplayUnit getVolumeUnit() {
        String name = cm.getGame(ConfigManager.KEY_UNIT_VOLUME);
        try {
            return DisplayUnit.valueOf(name);
        } catch (Exception e) {
            return DisplayUnit.MILLILITER;
        }
    }

    /** 设置体积显示单位 */
    public void setVolumeUnit(DisplayUnit unit) {
        cm.setGame(ConfigManager.KEY_UNIT_VOLUME, unit.name());
    }

    // ── 存档 ──────────────────────────────────────────

    public String getSavePath() {
        return cm.getSavePath();
    }

    public void setSavePath(String path) {
        cm.setSystem(ConfigManager.KEY_SAVE_PATH, path);
    }

    // ── 资源 ──────────────────────────────────────────

    public String getResourceBase() {
        return cm.getResourceBase();
    }

    public void setResourceBase(String path) {
        cm.setSystem(ConfigManager.KEY_RESOURCE_BASE, path);
    }

    // ── 保存 ──────────────────────────────────────────

    /** 保存所有配置（系统 + 游戏） */
    public void saveAll() {
        cm.saveSystemConfig();
        cm.saveGameConfig();
    }

    /** 仅保存系统配置 */
    public void saveSystem() {
        cm.saveSystemConfig();
    }

    /** 仅保存游戏配置 */
    public void saveGame() {
        cm.saveGameConfig();
    }

    // ── 国际化 ──────────────────────────────────────────

    /** 获取当前语言设置 */
    public String getLocale() {
        return cm.getLocale();
    }

    /** 设置语言（如 "en", "zh"） */
    public void setLocale(String locale) {
        cm.setLocale(locale);
    }
}
