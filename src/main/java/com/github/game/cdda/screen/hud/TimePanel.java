package com.github.game.cdda.screen.hud;

import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.game.time.GameCalendar;

import java.awt.*;

/**
 * 游戏时间显示面板。在 HUD 中显示当前的游戏日期和时间。
 *
 * <p>显示三行信息：
 * <ul>
 *   <li>年份 + 季节（季节用对应颜色高亮）</li>
 *   <li>月份 + 日期</li>
 *   <li>时间（HH:MM）</li>
 * </ul>
 *
 * <p>数据来源：{@link GameCalendar}（由 TurnManager 驱动，仅在角色行动时推进）。
 */
public class TimePanel implements StatusPanel {

    private boolean enabled = true;

    /** 游戏时钟引用 */
    private final GameCalendar gameTime;

    /** 每行高度 */
    private final int lineHeight;

    /** 内容字体大小 */
    private static final int TIME_FONT_SIZE = 12;
    /** 内边距 */
    private static final int PADDING = 6;
    /** 行数（年份+季节、月份+日期、时间） */
    private static final int LINE_COUNT = 3;

    /** 标签颜色（灰色） */
    private static final Color LABEL_COLOR = Color.GRAY;
    /** 年份文本颜色 */
    private static final Color YEAR_COLOR = new Color(200, 200, 150);
    /** 时间文本颜色（白色高亮） */
    private static final Color TIME_COLOR = Color.WHITE;

    /**
     * 创建时间面板。
     *
     * @param gameTime 游戏时钟实例
     * @param fontSize 基准字体大小（用于计算行高）
     */
    public TimePanel(GameCalendar gameTime, int fontSize) {
        this.gameTime = gameTime;
        this.lineHeight = fontSize + 4;
    }

    @Override
    public int getHeight() {
        return lineHeight * LINE_COUNT + PADDING * 2;
    }

    @Override
    public void render(Renderer r, int x, int y, int width, int height) {
        int cy = y + PADDING;
        int textX = x + PADDING;

        r.setFont(new Font("Monospaced", Font.PLAIN, TIME_FONT_SIZE));
        int ascent = r.getFontMetrics().getAscent();

        // 第一行：年份 + 季节（季节用对应颜色）
        r.setColor(LABEL_COLOR);
        String yearLabel = "年份:";
        r.drawText(yearLabel, textX, cy + ascent);
        r.setColor(YEAR_COLOR);
        String yearStr = "第" + gameTime.getYear() + "年";
        r.drawText(yearStr, textX + r.getTextWidth(yearLabel) + 4, cy + ascent);
        // 季节紧跟年份
        int seasonX = textX + r.getTextWidth(yearLabel) + 4 + r.getTextWidth(yearStr) + 8;
        r.setColor(gameTime.getSeason().getColor());
        r.drawText(gameTime.getSeason().getFullName(), seasonX, cy + ascent);
        cy += lineHeight;

        // 第二行：月份 + 日期
        r.setColor(LABEL_COLOR);
        String dateLabel = "日期:";
        r.drawText(dateLabel, textX, cy + ascent);
        r.setColor(YEAR_COLOR);
        String dateStr = gameTime.getMonth().getChineseName() + " 第" + gameTime.getDayOfMonth() + "天";
        r.drawText(dateStr, textX + r.getTextWidth(dateLabel) + 4, cy + ascent);
        cy += lineHeight;

        // 第三行：时间
        r.setColor(LABEL_COLOR);
        String timeLabel = "时间:";
        r.drawText(timeLabel, textX, cy + ascent);
        r.setColor(TIME_COLOR);
        r.drawText(gameTime.formatTime(), textX + r.getTextWidth(timeLabel) + 4, cy + ascent);
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
