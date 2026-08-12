package com.github.game.cdda.game;

import com.github.game.cdda.Constants;
import com.github.game.cdda.game.time.GameCalendar;
import com.github.game.cdda.game.time.Month;

import java.awt.*;
import java.util.Random;

/**
 * 环境温度管理器。计算并跟踪游戏世界的环境温度。
 *
 * <h3>三层温度模型：</h3>
 * <ol>
 *   <li><b>月均温</b> — 基于月份的基础温度（欧洲气候参考），月度间线性插值保证平滑</li>
 *   <li><b>日内波动</b> — 正弦曲线模拟日内温度变化，峰值在14:00，黎明最冷</li>
 *   <li><b>随机漂移</b> — 每10游戏分钟更新一次目标值，线性逼近实现"连续几回合不变"</li>
 * </ol>
 *
 * <h3>温度公式：</h3>
 * <pre>temperature = monthlyBase + dailyVariation + randomDrift</pre>
 *
 * <h3>扩展性：</h3>
 * <ul>
 *   <li>未来物品可有自身温度 → {@link #getTemperatureDescriptor()} / {@link #getTemperatureColor()}</li>
 *   <li>未来空间温度变化 → 可扩展 per-chunk 温度查询</li>
 * </ul>
 */
public class TemperatureManager {

    private final GameCalendar calendar;
    private final Random random;

    // ── 当前温度状态 ──────────────────────────────────
    private double currentTemperature = 10.0;
    private double driftOffset = 0.0;
    private double driftTarget = 0.0;
    /** 上次漂移计算时的游戏秒数（用于时间驱动插值） */
    private long lastDriftCalcSeconds = -1;
    /** 上次更新漂移目标的游戏分钟数 */
    private long lastDriftUpdateMinute = -1;

    // ── 月均温表（欧洲温带气候参考，°C） ──────────────────────────────────
    private static final double[] MONTH_AVG_TEMPS = {
        -1.0,  // 一月
         0.0,  // 二月
         4.0,  // 三月
         9.0,  // 四月
        14.0,  // 五月
        18.0,  // 六月
        21.0,  // 七月
        20.0,  // 八月
        16.0,  // 九月
        10.0,  // 十月
         4.0,  // 十一月
         0.0   // 十二月
    };

    /**
     * 创建温度管理器。
     *
     * @param calendar 游戏日历（提供时间信息）
     */
    public TemperatureManager(GameCalendar calendar) {
        this.calendar = calendar;
        this.random = new Random();
    }

    /**
     * 创建温度管理器（指定随机种子，用于可重复测试）。
     */
    public TemperatureManager(GameCalendar calendar, long seed) {
        this.calendar = calendar;
        this.random = new Random(seed);
    }

    // ── 温度查询 ──────────────────────────────────

    /**
     * 获取当前环境温度（°C）。
     * 每次调用时自动更新漂移状态。
     *
     * @return 当前温度（摄氏度）
     */
    public double getTemperature() {
        updateDriftIfNeeded();
        return currentTemperature;
    }

    /**
     * 获取温度中文描述。
     *
     * @return 如 "严寒", "寒冷", "凉爽", "微凉", "舒适", "温暖", "炎热", "酷热"
     */
    public String getTemperatureDescriptor() {
        double temp = getTemperature();
        if (temp < -10) return "严寒";
        if (temp < -2)  return "寒冷";
        if (temp < 5)   return "凉爽";
        if (temp < 15)  return "微凉";
        if (temp < 25)  return "舒适";
        if (temp < 30)  return "温暖";
        if (temp < 35)  return "炎热";
        return "酷热";
    }

    /**
     * 获取温度对应的颜色（蓝→青→绿→黄→橙→红渐变）。
     *
     * @return 温度对应的 RGB 颜色
     */
    public Color getTemperatureColor() {
        double temp = getTemperature();
        // 温度范围：-20 ~ 45°C 映射到颜色
        double t = Math.max(0, Math.min(1, (temp + 20) / 65.0));

        if (t < 0.2) {
            // 深红→红（极寒区域偏蓝）
            float f = (float) (t / 0.2);
            return new Color(f * 0.4f, f * 0.4f, 0.6f + f * 0.4f);
        } else if (t < 0.4) {
            // 蓝→青
            float f = (float) ((t - 0.2) / 0.2);
            return new Color(0.4f, 0.6f + f * 0.4f, 1.0f);
        } else if (t < 0.6) {
            // 青→绿
            float f = (float) ((t - 0.4) / 0.2);
            return new Color(0.4f + f * 0.6f, 1.0f, 1.0f - f * 0.6f);
        } else if (t < 0.8) {
            // 绿→黄
            float f = (float) ((t - 0.6) / 0.2);
            return new Color(1.0f, 1.0f, f * 0.3f);
        } else {
            // 黄→红
            float f = (float) ((t - 0.8) / 0.2);
            return new Color(1.0f, 1.0f - f * 0.7f, 0);
        }
    }

    // ── 温度计算（内部） ──────────────────────────────────

    /**
     * 计算月均温（相邻月份间线性插值，保证月度过渡平滑）。
     */
    private double getMonthlyBaseTemperature() {
        Month month = calendar.getMonth();
        int currentIdx = month.ordinal();
        int nextIdx = (currentIdx + 1) % 12;

        // 月内进度（0.0 ~ 1.0）
        double dayInMonth = calendar.getDayOfMonth() - 1;
        double t = dayInMonth / 30.0;

        return lerp(MONTH_AVG_TEMPS[currentIdx], MONTH_AVG_TEMPS[nextIdx], t);
    }

    /**
     * 计算日内温度波动（正弦曲线，峰值在14:00，黎明最冷）。
     *
     * <p>公式：{@code -amplitude × cos(2π × (hour - 14) / 24)}
     * <ul>
     *   <li>14:00 → 峰值 +amplitude</li>
     *   <li>02:00 → 谷值 -amplitude</li>
     * </ul>
     */
    private double getDailyVariation() {
        double hour = calendar.getHour() + calendar.getMinute() / 60.0;
        return -Constants.TEMP_DAILY_AMPLITUDE
                * Math.cos(2 * Math.PI * (hour - 14) / 24.0);
    }

    /**
     * 更新随机漂移。
     *
     * <p>两层机制：
     * <ol>
     *   <li>每 {@code TEMP_DRIFT_UPDATE_INTERVAL_MINUTES} 游戏分钟更新一次漂移目标</li>
     *   <li>漂移值基于游戏时间（非帧率）指数平滑逼近目标，
     *       时间常数 {@code TEMP_DRIFT_SMOOTH_SECONDS} 控制趋近速度</li>
     * </ol>
     *
     * <p>使用指数衰减公式：{@code offset += (target - offset) × (1 - e^(-dt/τ))}
     * 其中 dt 为经过的游戏秒数，τ 为平滑时间常数。
     * 无论帧率如何，相同游戏时间内漂移量一致。
     */
    private void updateDriftIfNeeded() {
        long totalSeconds = calendar.getTotalSeconds();

        // 检查是否需要更新漂移目标
        long currentMinute = totalSeconds / 60;
        long interval = Constants.TEMP_DRIFT_UPDATE_INTERVAL_MINUTES;

        if (lastDriftUpdateMinute < 0
                || currentMinute / interval != lastDriftUpdateMinute / interval) {
            // 进入新的漂移周期 → 生成新目标
            driftTarget = (random.nextDouble() * 2 - 1) * Constants.TEMP_DRIFT_RANGE;
            lastDriftUpdateMinute = currentMinute;
        }

        // 基于游戏时间平滑逼近目标（帧率无关）
        if (lastDriftCalcSeconds < 0) {
            lastDriftCalcSeconds = totalSeconds;
        }
        long dtSeconds = totalSeconds - lastDriftCalcSeconds;
        if (dtSeconds > 0) {
            // 指数衰减系数：dt=τ 时逼近 63.2%，dt=3τ 时逼近 95%
            double factor = 1.0 - Math.exp(-dtSeconds / Constants.TEMP_DRIFT_SMOOTH_SECONDS);
            driftOffset += (driftTarget - driftOffset) * factor;
            lastDriftCalcSeconds = totalSeconds;
        }

        // 重算完整温度
        recalculateTemperature();
    }

    /**
     * 重新计算当前温度（不含漂移更新）。
     */
    private void recalculateTemperature() {
        currentTemperature = getMonthlyBaseTemperature()
                + getDailyVariation()
                + driftOffset;
    }

    /** 线性插值 */
    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ── 扩展接口（未来使用） ──────────────────────────────────

    /**
     * 获取指定坐标的温度（当前返回全局温度，未来可接入空间变化）。
     *
     * @param worldX 世界X坐标（瓦片）
     * @param worldY 世界Y坐标（瓦片）
     * @return 该位置的环境温度
     */
    public double getTemperatureAt(int worldX, int worldY) {
        // 当前：返回全局温度
        // 未来：可加入纬度/海拔/Perlin噪声等空间因素
        return getTemperature();
    }
}
