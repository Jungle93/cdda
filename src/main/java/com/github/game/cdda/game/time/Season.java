package com.github.game.cdda.game.time;

import java.awt.*;

/**
 * 季节枚举。定义四个季节的中文名、显示名和代表色。
 *
 * <p>温度参考欧洲温带气候：
 * <ul>
 *   <li>春季 — 万物复苏，气温回暖 (2~14°C)</li>
 *   <li>夏季 — 炎热，日照长 (14~27°C)</li>
 *   <li>秋季 — 凉爽，落叶 (5~17°C)</li>
 *   <li>冬季 — 寒冷，日照短 (-5~3°C)</li>
 * </ul>
 */
public enum Season {

    SPRING("春", "春季", new Color(100, 200, 100)),
    SUMMER("夏", "夏季", new Color(240, 200, 50)),
    AUTUMN("秋", "秋季", new Color(200, 150, 50)),
    WINTER("冬", "冬季", new Color(150, 200, 240));

    /** 单字名（春/夏/秋/冬） */
    private final String shortName;
    /** 全名（春季/夏季/秋季/冬季） */
    private final String fullName;
    /** 代表色（用于 UI 渲染） */
    private final Color color;

    Season(String shortName, String fullName, Color color) {
        this.shortName = shortName;
        this.fullName = fullName;
        this.color = color;
    }

    /**
     * 获取单字名。
     *
     * @return 单字名（春/夏/秋/冬）
     */
    public String getShortName() { return shortName; }

    /**
     * 获取全名。
     *
     * @return 全名（春季/夏季/秋季/冬季）
     */
    public String getFullName() { return fullName; }

    /**
     * 获取代表色。
     *
     * @return UI 渲染用的代表色
     */
    public Color getColor() { return color; }

    /**
     * 获取下一个季节（春→夏→秋→冬→春...）。
     *
     * @return 下一个季节
     */
    public Season next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * 获取上一个季节。
     *
     * @return 上一个季节
     */
    public Season previous() {
        return values()[(ordinal() + 3) % values().length];
    }
}
