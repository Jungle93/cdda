package com.github.game.cdda.screen.scene;

import com.github.game.cdda.game.time.GameCalendar;
import com.github.game.engine.core.render.Renderer;

import java.awt.Color;

/**
 * 昼夜色调叠加层。
 * 根据游戏时间计算并渲染半透明颜色叠加，营造昼夜氛围。
 *
 * <p>色调表：
 * <ul>
 *   <li>黎明(6-8): 暖橙色，alpha 从 30 线性递减到 0</li>
 *   <li>白天(8-17): 无叠加</li>
 *   <li>黄昏(17-20): 橙红→紫蓝渐变，alpha 从 0 线性递增到 50</li>
 *   <li>夜晚(20-6): 深蓝，午夜(alpha55)最深</li>
 * </ul>
 *
 * <p>性能：仅一次 fillRect 调用，开销极小。
 */
public class DayNightOverlay {

    /**
     * 渲染昼夜色调叠加。
     * 仅覆盖游戏区域（不覆盖 HUD）。
     *
     * @param renderer 渲染器
     * @param calendar 游戏日历
     * @param width    游戏区域宽度
     * @param height   游戏区域高度
     */
    public static void render(Renderer renderer, GameCalendar calendar,
                              int width, int height) {
        Color color = calculateColor(calendar);
        if (color != null && color.getAlpha() > 0) {
            renderer.setColor(color);
            renderer.fillRect(0, 0, width, height);
        }
    }

    /**
     * 根据当前时间计算叠加颜色。
     * 使用分段线性插值实现平滑过渡。
     *
     * @return 叠加颜色（白天返回 null）
     */
    static Color calculateColor(GameCalendar calendar) {
        int hour = calendar.getHour();
        int minute = calendar.getMinute();
        float t = hour + minute / 60.0f; // 0.0 ~ 24.0

        // ── 白天 (8:00-17:00): 无叠加 ──
        if (t >= 8.0f && t < 17.0f) {
            return null;
        }

        // ── 黎明 (6:00-8:00): 暖橙 → 透明 ──
        if (t >= 6.0f && t < 8.0f) {
            float progress = (t - 6.0f) / 2.0f; // 0→1
            int alpha = (int) (30 * (1.0f - progress));
            return new Color(255, 180, 100, alpha);
        }

        // ── 黄昏 (17:00-20:00): 透明 → 橙红 → 紫蓝 ──
        if (t >= 17.0f && t < 20.0f) {
            float progress = (t - 17.0f) / 3.0f; // 0→1
            if (progress < 0.5f) {
                // 17:00-18:30: 透明 → 暖橙
                float p = progress / 0.5f;
                int alpha = (int) (35 * p);
                return new Color(255, 140, 60, alpha);
            } else {
                // 18:30-20:00: 暖橙 → 深蓝紫
                float p = (progress - 0.5f) / 0.5f;
                int alpha = 35 + (int) (15 * p); // 35→50
                int r = (int) (255 - 215 * p);   // 255→40
                int g = (int) (140 - 100 * p);   // 140→40
                int b = (int) (60 + 140 * p);    // 60→200
                return new Color(r, g, b, alpha);
            }
        }

        // ── 夜晚 (20:00-6:00): 深蓝色调 ──
        // 午夜(0:00)最深，日出前逐渐变淡
        float nightProgress;
        if (t >= 20.0f) {
            nightProgress = (t - 20.0f) / 10.0f; // 20:00→0.0, 0:00→0.4, 6:00→1.0
        } else {
            nightProgress = (t + 4.0f) / 10.0f;
        }

        // 深度曲线：午夜最深（对称钟形）
        float depth = 1.0f - Math.abs(nightProgress - 0.4f) / 0.6f;
        int alpha = (int) (35 + 20 * depth); // 35-55

        return new Color(30, 50, 120, alpha);
    }
}
