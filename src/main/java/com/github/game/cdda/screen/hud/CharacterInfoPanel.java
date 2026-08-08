package com.github.game.cdda.screen.hud;

import com.github.game.engine.core.render.Renderer;

import java.awt.*;

/**
 * 角色状态信息面板。
 * 显示角色的核心生存指标，使用游戏专业缩写：
 * <ul>
 *   <li><b>HP</b> — 生命值 (Hit Points)</li>
 *   <li><b>HGR</b> — 饥饿值 (Hunger)</li>
 *   <li><b>THR</b> — 口渴值 (Thirst)</li>
 *   <li><b>SPD</b> — 速度 (Speed)</li>
 *   <li><b>TEMP</b> — 体温 (Temperature)</li>
 * </ul>
 *
 * 当前使用占位值，待 Character 模型接入后通过 setter 更新。
 */
public class CharacterInfoPanel implements StatusPanel {

    private boolean enabled = true;

    /** 每行高度（由字体大小决定） */
    private final int lineHeight;

    // ── 占位值（待 Character 模型接入后替换） ──
    private int hp = 100, maxHp = 100;
    private int hunger = 100, maxHunger = 100;
    private int thirst = 100, maxThirst = 100;
    /** 口渴值显示颜色（动态，由 HydrationManager 驱动） */
    private Color thirstColor = new Color(50, 130, 220);
    private int speedPercent = 100;

    /** 体温等级索引（0=极寒 ... 8=极热），默认 4=适宜 */
    private int temperatureLevel = 4;

    /** 体温挡位名称（从极寒到极热） */
    private static final String[] TEMP_LEVELS = {
            "极寒", "严寒", "寒冷", "微凉", "适宜", "温热", "炎热", "酷热", "极热"
    };

    /** 体温挡位对应颜色（冷→暖渐变） */
    private static final Color[] TEMP_COLORS = {
            new Color(100, 100, 255),  // 极寒 — 深蓝
            new Color(100, 150, 255),  // 严寒 — 蓝
            new Color(130, 200, 255),  // 寒冷 — 浅蓝
            new Color(150, 220, 220),  // 微凉 — 青
            new Color(200, 200, 200),  // 适宜 — 灰白
            new Color(240, 200, 100),  // 温热 — 浅黄
            new Color(240, 160, 50),   // 炎热 — 橙
            new Color(230, 100, 30),   // 酷热 — 橙红
            new Color(220, 40, 40),    // 极热 — 红
    };

    /** 内容字体大小 */
    private static final int STAT_FONT_SIZE = 12;
    /** 内边距 */
    private static final int PADDING = 6;
    /** 状态行数 */
    private static final int STAT_COUNT = 5;

    /**
     * 创建角色信息面板。
     *
     * @param fontSize 基准字体大小（用于计算行高）
     */
    public CharacterInfoPanel(int fontSize) {
        this.lineHeight = fontSize + 4;
    }

    @Override
    public int getHeight() {
        // 状态行 + 上下边距
        return lineHeight * STAT_COUNT + PADDING * 2;
    }

    @Override
    public void render(Renderer r, int x, int y, int width, int height) {
        int cy = y + PADDING;

        // ── 状态行 ──
        r.setFont(new Font("Monospaced", Font.PLAIN, STAT_FONT_SIZE));
        int statAscent = r.getFontMetrics().getAscent();
        drawStat(r, x + PADDING, cy + statAscent, "HP", hp + "/" + maxHp,
                new Color(220, 50, 50));
        cy += lineHeight;

        drawStat(r, x + PADDING, cy + statAscent, "HGR", hunger + "/" + maxHunger,
                new Color(220, 160, 30));
        cy += lineHeight;

        drawStat(r, x + PADDING, cy + statAscent, "THR", thirst + "/" + maxThirst,
                thirstColor);
        cy += lineHeight;

        drawStat(r, x + PADDING, cy + statAscent, "SPD", speedPercent + "%",
                Color.WHITE);
        cy += lineHeight;

        drawStat(r, x + PADDING, cy + statAscent, "TEMP",
                TEMP_LEVELS[temperatureLevel], TEMP_COLORS[temperatureLevel]);
    }

    /**
     * 绘制一行状态：缩写标签(灰色) + 值(状态色)。
     */
    private void drawStat(Renderer r, int x, int y,
                           String label, String value, Color valueColor) {
        r.setColor(Color.GRAY);
        String labelStr = label + ":";
        r.drawText(labelStr, x, y);
        int labelWidth = r.getTextWidth(labelStr);
        r.setColor(valueColor);
        r.drawText(value, x + labelWidth + 4, y);
    }

    // ── 状态访问器（待 Character 模型接入后由 MainScreen 调用更新） ──

    /** 设置生命值 */
    public void setHp(int hp, int maxHp) {
        this.hp = hp;
        this.maxHp = maxHp;
    }

    /** 设置饥饿值 */
    public void setHunger(int hunger, int maxHunger) {
        this.hunger = hunger;
        this.maxHunger = maxHunger;
    }

    /** 设置口渴值 */
    public void setThirst(int thirst, int maxThirst) {
        this.thirst = thirst;
        this.maxThirst = maxThirst;
    }

    /** 设置口渴值颜色（由 HydrationManager 的脱水等级驱动） */
    public void setThirstColor(Color color) {
        this.thirstColor = color;
    }

    /** 设置速度百分比 */
    public void setSpeed(int percent) {
        this.speedPercent = percent;
    }

    /**
     * 设置体温等级。
     *
     * @param level 0=极寒, 1=严寒, 2=寒冷, 3=微凉, 4=适宜,
     *              5=温热, 6=炎热, 7=酷热, 8=极热
     */
    public void setTemperatureLevel(int level) {
        this.temperatureLevel = Math.max(0, Math.min(TEMP_LEVELS.length - 1, level));
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
