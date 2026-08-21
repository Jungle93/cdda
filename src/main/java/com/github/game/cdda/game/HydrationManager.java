package com.github.game.cdda.game;

import com.github.game.cdda.Constants;
import com.github.game.cdda.game.time.GameCalendar;

/**
 * 口渴/水分管理系统。模拟人体水分收支平衡：
 *
 * <ol>
 *   <li><b>基础时间流逝</b> — 呼吸、皮肤蒸发（无感失水）和排尿持续流失水分</li>
 *   <li><b>体温与环境联动</b> — 高温出汗 / 极寒干燥加速呼吸道失水</li>
 *   <li><b>物理运动消耗</b> — 剧烈运动加速新陈代谢，迫使身体大量排汗降温</li>
 * </ol>
 *
 * <h3>核心公式：</h3>
 * <pre>
 * 当前流失速率 = 基础流失率 × 温度倍率 × 动作倍率
 * 温度倍率 = f(环境温度, 舒适区)
 * 动作倍率 = f(行动类型)：站立=1.0, 行走=1.5, 跑步=2.0, 战斗=3.0
 * </pre>
 *
 * <h3>与代谢系统的关系：</h3>
 * <p>口渴系统与能量代谢（{@link MetabolismManager}）平行但独立：
 * <ul>
 *   <li>两者都受环境温度影响（通过 TemperatureManager）</li>
 *   <li>两者都在玩家行动后 update</li>
 *   <li>未来衣物保暖值可同时影响两个系统</li>
 * </ul>
 *
 * <h3>更新时机：</h3>
 * <p>⚠️ 时间只在玩家行动时流逝。{@link #update()} 在每次行动后由 GameScene 调用。
 * 站着不动不做任何操作时，口渴值不变。
 */
public class HydrationManager {

    // ── 外部引用 ──────────────────────────────────
    private final GameCalendar calendar;
    private final TemperatureManager temperatureManager;

    // ── 水分池 ──────────────────────────────────
    /** 当前水分值 */
    private double waterLevel;
    /** 最大水分值 */
    private final double maxWater;
    /** 水分缓冲值（充足时恢复，脱水时消耗，体现肾脏节水机制） */
    private double buffer;

    // ── 时间追踪 ──────────────────────────────────
    /** 上次更新时的 totalSeconds */
    private long lastUpdateTime = -1;

    // ── 动作倍率累积 ──────────────────────────────────
    /**
     * 本回合的动作脱水倍率。
     * 由 addAction 设置，update 时结算后归零。
     * 多次行动时取最大值（避免叠加过猛）。
     */
    private double pendingActionMultiplier = 0;

    /**
     * 创建水分管理器。
     *
     * @param calendar           游戏日历
     * @param temperatureManager 环境温度管理器
     */
    public HydrationManager(GameCalendar calendar, TemperatureManager temperatureManager) {
        this.calendar = calendar;
        this.temperatureManager = temperatureManager;
        this.maxWater = Constants.WATER_MAX;
        this.waterLevel = maxWater * Constants.WATER_INITIAL_PERCENT;
        this.buffer = Constants.WATER_BUFFER_MAX;
    }

    // ── 核心更新 ──────────────────────────────────

    /**
     * 更新水分状态。在每次玩家行动后调用。
     * 根据 gameTime 的时间差和当前环境温度计算水分流失。
     */
    public void update() {
        long currentTime = calendar.getTotalSeconds();
        if (lastUpdateTime < 0) {
            lastUpdateTime = currentTime;
            return;
        }

        long dt = currentTime - lastUpdateTime;
        if (dt <= 0) return;
        lastUpdateTime = currentTime;

        // 1) 基础流失（呼吸 + 无感蒸发 + 排尿）
        double baseDrain = Constants.WATER_BASE_DRAIN_RATE * dt;

        // 2) 温度倍率（高温出汗，极寒呼吸失水）
        double tempMultiplier = getTemperatureMultiplier();

        // 3) 动作倍率（运动产热排汗）
        double actionMultiplier = pendingActionMultiplier;
        pendingActionMultiplier = 0;

        // 综合流失 = 基础 × 温度 × 动作
        // 若无动作，仅基础 × 温度（静态流失）
        double totalDrain = (actionMultiplier > 0)
                ? baseDrain * tempMultiplier * actionMultiplier
                : baseDrain * tempMultiplier;

        // 4) 扣减水分
        waterLevel = Math.max(0, waterLevel - totalDrain);

        // 5) 水分调节（详见 updateWaterRegulation）
        updateWaterRegulation(dt, totalDrain);
    }

    /**
     * 计算环境温度对脱水速率的倍率。
     *
     * <p>规则：
     * <ul>
     *   <li>舒适区（18~25°C）：倍率 = 1.0（仅基础无感失水）</li>
     *   <li>高温（>25°C）：每超1°C增加15%，最高3倍</li>
     *   <li>低温（<18°C）：每低1°C增加3%（干燥冷空气加速呼吸失水）</li>
     * </ul>
     */
    private double getTemperatureMultiplier() {
        double envTemp = temperatureManager.getTemperature();
        double multiplier = 1.0;

        if (envTemp > Constants.COMFORT_TEMP_MAX) {
            // 高温 → 出汗散热，水分流失加快
            double excess = envTemp - Constants.COMFORT_TEMP_MAX;
            multiplier += excess * Constants.TEMP_THIRST_MULTIPLIER;
        } else if (envTemp < Constants.COMFORT_TEMP_MIN) {
            // 低温 → 干燥冷空气加速呼吸失水（效果弱于高温）
            double deficit = Constants.COMFORT_TEMP_MIN - envTemp;
            multiplier += deficit * Constants.COLD_THIRST_MULTIPLIER;
        }
        // 舒适区内：multiplier = 1.0

        return Math.min(multiplier, Constants.TEMP_THIRST_MAX_MULTIPLIER);
    }

    /**
     * 水分调节逻辑。
     *
     * <p>人体通过口渴感和肾脏调节维持水分平衡：
     * <ul>
     *   <li>水分充足 → 正常排尿，buffer 恢复</li>
     *   <li>水分偏低 → 肾脏减少排尿（buffer 消耗减缓）</li>
     *   <li>严重脱水 → 身体停止一切非必需水分流失</li>
     * </ul>
     *
     * @param dt        时间差（游戏秒）
     * @param totalDrain 本周期总流失量
     */
    private void updateWaterRegulation(long dt, double totalDrain) {
        double ratio = getWaterPercent() / 100.0;

        if (ratio > 0.6) {
            // 水分充足（>60%）：缓冲缓慢恢复
            buffer = Math.min(
                    Constants.WATER_BUFFER_MAX,
                    buffer + dt * 5);
        }
        // 水分偏低时 buffer 自然不恢复，体现身体节水机制
    }

    // ── 外部接口 ──────────────────────────────────

    /**
     * 记录动作导致的脱水倍率。
     * 倍率在下次 update() 时结算。
     *
     * <p>若同一回合多次调用，取最大值（避免叠加）。
     *
     * @param multiplier 动作倍率：
     *                   1.0=站立/等待, 1.5=行走, 2.0=跑步, 3.0=战斗/负重
     */
    public void addAction(double multiplier) {
        pendingActionMultiplier = Math.max(pendingActionMultiplier, multiplier);
    }

    /**
     * 补充水分（喝水/吃含水食物）。
     *
     * @param amount 补充的水分量
     */
    public void addWater(double amount) {
        waterLevel = Math.min(maxWater, waterLevel + amount);
    }

    // ── 状态查询 ──────────────────────────────────

    /**
     * 获取当前水分值。
     *
     * @return 当前水分值
     */
    public double getWaterLevel() { return waterLevel; }

    /**
     * 获取最大水分值。
     *
     * @return 最大水分值
     */
    public double getMaxWater() { return maxWater; }

    /**
     * 获取口渴百分比（0-100），100=水分充足，0=严重脱水。
     * 用于 HUD 显示 THR 数值。
     *
     * @return 水分百分比（0-100）
     */
    public int getWaterPercent() {
        return (int) (waterLevel / maxWater * 100);
    }

    /**
     * 获取脱水程度等级（0-5），映射到 CharacterInfoPanel 的 THR 显示颜色。
     *
     * <pre>
     * 0 = 水合充足 (>80%)      — 蓝
     * 1 = 正常      (60~80%)   — 浅蓝
     * 2 = 轻度脱水  (40~60%)   — 白
     * 3 = 中度脱水  (25~40%)   — 浅黄
     * 4 = 重度脱水  (10~25%)   — 橙
     * 5 = 极度脱水  (<10%)     — 红
     * </pre>
     *
     * @return 脱水等级（0-5）
     */
    public int getThirstLevel() {
        int percent = getWaterPercent();
        if (percent > 80) return 0;
        if (percent > 60) return 1;
        if (percent > 40) return 2;
        if (percent > 25) return 3;
        if (percent > 10) return 4;
        return 5;
    }

    /**
     * 获取脱水状态中文描述。
     *
     * @return 如 "水合充足", "正常", "轻度脱水", "中度脱水", "重度脱水", "极度脱水"
     */
    public String getThirstDescriptor() {
        String[] descriptors = {
                "水合充足", "正常", "轻度脱水", "中度脱水", "重度脱水", "极度脱水"
        };
        return descriptors[getThirstLevel()];
    }

    /**
     * 获取脱水状态对应的颜色。
     * 蓝（充足）→ 白（正常）→ 黄（轻）→ 橙（中）→ 红（重/极）
     *
     * @return 脱水状态对应的 RGB 颜色
     */
    public java.awt.Color getThirstColor() {
        java.awt.Color[] colors = {
                new java.awt.Color(50, 130, 220),   // 水合充足 — 蓝
                new java.awt.Color(100, 170, 240),   // 正常 — 浅蓝
                new java.awt.Color(200, 200, 200),   // 轻度脱水 — 白/灰
                new java.awt.Color(240, 200, 100),   // 中度脱水 — 浅黄
                new java.awt.Color(240, 160, 50),    // 重度脱水 — 橙
                new java.awt.Color(220, 40, 40),     // 极度脱水 — 红
        };
        return colors[getThirstLevel()];
    }

    /**
     * 是否有严重脱水（需要警告）。
     *
     * @return 水分低于10%时返回 {@code true}
     */
    public boolean hasCriticalThirst() {
        return waterLevel < maxWater * 0.1;
    }

    /**
     * 是否处于脱水状态（水分低于40%）。
     *
     * @return 水分低于40%时返回 {@code true}
     */
    public boolean isDehydrated() {
        return waterLevel < maxWater * 0.4;
    }

    /**
     * 计算极度脱水导致的 HP 伤害（每回合）。
     *
     * <p>判定逻辑：
     * <ul>
     *   <li><b>危险</b>：水分 &lt; 10% → 1 HP/回合</li>
     *   <li><b>致命</b>：水分 &lt; 3% → 3 HP/回合</li>
     * </ul>
     *
     * @return 本回合应扣除的 HP 值（0 表示无伤害）
     */
    public int calcDehydrationDamage() {
        double ratio = waterLevel / maxWater;
        if (ratio < 0.03) {
            return 3; // 致命阶段
        }
        if (ratio < 0.10) {
            return 1; // 危险阶段
        }
        return 0;
    }
}
