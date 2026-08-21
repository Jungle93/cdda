package com.github.game.cdda.game;

import com.github.game.cdda.Constants;
import com.github.game.cdda.game.time.GameCalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 人体代谢与热平衡管理器。模拟能量代谢系统的五大子系统：
 *
 * <ol>
 *   <li><b>时间驱动</b> — 游戏时间是所有状态变化的底层计时器</li>
 *   <li><b>环境温度 vs 体温</b> — 温差决定热量散失速率</li>
 *   <li><b>热量消耗</b> — 基础代谢 + 环境代偿 + 运动消耗</li>
 *   <li><b>能量池/饥饿</b> — 食物热量输入 vs 消耗热量的收支平衡</li>
 *   <li><b>体温</b> — 能量收支的最终健康指标</li>
 * </ol>
 *
 * <h3>核心公式：</h3>
 * <pre>
 * 消耗 = 基础代谢 + |环境温度差| × 代偿系数 + 动作消耗
 * 能量池 = 上一秒能量池 + 食物摄入 - 消耗
 * 体温偏离 = f(能量储备不足, 环境极端程度)
 * </pre>
 *
 * <h3>更新时机：</h3>
 * <p>⚠️ 时间只在玩家行动时流逝。{@link #update()} 在每次行动后由 GameScene 调用，
 * 根据 {@link GameCalendar} 的时间差计算所有代谢变化。站着不动不做任何操作时，代谢不更新。
 */
public class MetabolismManager {

    private static final Logger logger = LoggerFactory.getLogger(MetabolismManager.class);

    // ── 外部引用 ──────────────────────────────────
    private final GameCalendar calendar;
    private final TemperatureManager temperatureManager;

    // ── 能量池 ──────────────────────────────────
    /** 当前能量储备（cal） */
    private double energyPool;
    /** 最大能量储备 */
    private final double maxEnergy;

    // ── 体温 ──────────────────────────────────
    /** 核心体温（°C） */
    private double bodyTemperature;
    /** 体温缓冲值（有能量时用于延缓体温偏离） */
    private double temperatureBuffer;

    // ── 时间追踪 ──────────────────────────────────
    /** 上次更新时的 totalSeconds */
    private long lastUpdateTime = -1;

    // ── 动作消耗累积 ──────────────────────────────────
    /** 本回合额外的动作消耗（cal），由 addActionCost 添加，update 时结算 */
    private double pendingActionCost = 0;

    /**
     * 创建代谢管理器。
     *
     * @param calendar           游戏日历
     * @param temperatureManager 环境温度管理器
     */
    public MetabolismManager(GameCalendar calendar, TemperatureManager temperatureManager) {
        this.calendar = calendar;
        this.temperatureManager = temperatureManager;
        this.maxEnergy = Constants.CALORIE_MAX_POOL;
        this.energyPool = maxEnergy * Constants.CALORIE_INITIAL_PERCENT;
        this.bodyTemperature = Constants.NORMAL_BODY_TEMP;
        this.temperatureBuffer = Constants.BODY_TEMP_BUFFER_MAX;
    }

    // ── 核心更新 ──────────────────────────────────

    /**
     * 更新代谢状态。在每次玩家行动后调用。
     * 根据 gameTime 的时间差计算基础代谢、环境代偿和体温变化。
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

        // 1) 基础代谢消耗
        double basalBurn = Constants.BASAL_METABOLISM_RATE * dt;

        // 2) 环境代偿消耗
        double envCost = calculateEnvironmentalCost(dt);

        // 3) 动作消耗（移动等，由 addActionCost 累积）
        double actionCost = pendingActionCost;
        pendingActionCost = 0;

        // 4) 总消耗
        double totalBurn = basalBurn + envCost + actionCost;

        // 5) 扣减能量池
        energyPool = Math.max(0, energyPool - totalBurn);

        // 6) 更新体温（记录更新前的值以便日志对比）
        double prevBodyTemp = bodyTemperature;
        double prevBuffer = temperatureBuffer;
        updateBodyTemperature(dt);

        // 调试日志
        logger.debug("代谢更新: dt={}秒, 基础={}cal, 环境={}cal, 动作={}cal, 总消耗={}cal",
                dt, (int) basalBurn, (int) envCost, (int) actionCost, (int) totalBurn);
        logger.debug("  能量池: {}cal ({}%), 体温: {}→{}°C, buffer: {}→{}, 环境温度: {}°C",
                (int) energyPool, getHungerPercent(),
                String.format("%.2f", prevBodyTemp), String.format("%.2f", bodyTemperature),
                (int) prevBuffer, (int) temperatureBuffer,
                String.format("%.1f", temperatureManager.getTemperature()));
    }

    /**
     * 计算环境代偿消耗。
     * 环境温度偏离舒适区时，需要额外消耗热量来维持体温。
     *
     * @param dt 时间差（游戏秒）
     * @return 环境代偿消耗（cal）
     */
    private double calculateEnvironmentalCost(long dt) {
        double envTemp = temperatureManager.getTemperature();
        double tempDiff;

        if (envTemp < Constants.COMFORT_TEMP_MIN) {
            tempDiff = Constants.COMFORT_TEMP_MIN - envTemp;
        } else if (envTemp > Constants.COMFORT_TEMP_MAX) {
            tempDiff = envTemp - Constants.COMFORT_TEMP_MAX;
        } else {
            return 0; // 在舒适区内，无额外消耗
        }

        return tempDiff * Constants.ENV_COMPENSATION_RATE * dt;
    }

    /**
     * 更新体温。
     *
     * <p>体温调节逻辑：
     * <ul>
     *   <li>体温正常（37°C附近）→ buffer 缓慢恢复</li>
     *   <li>体温偏离正常 → 消耗 buffer 拉回正常</li>
     *   <li>buffer 耗尽或能量不足 → 体温向环境温度漂移（失温/中暑）</li>
     * </ul>
     *
     * @param dt 时间差（游戏秒）
     */
    private void updateBodyTemperature(long dt) {
        double envTemp = temperatureManager.getTemperature();
        boolean hasEnergy = energyPool > maxEnergy * 0.05;
        double deviation = bodyTemperature - Constants.NORMAL_BODY_TEMP;

        if (hasEnergy && Math.abs(deviation) < 0.3) {
            // 体温正常（36.7~37.3°C）→ buffer 缓慢恢复
            temperatureBuffer = Math.min(
                    Constants.BODY_TEMP_BUFFER_MAX,
                    temperatureBuffer + dt * 10);
        } else if (hasEnergy && temperatureBuffer > 0 && Math.abs(deviation) > 0) {
            // 体温偏离正常 → 消耗 buffer 拉回 37°C
            double correction = -deviation * 0.1;  // 反方向修正
            double bufferCost = Math.abs(correction) * dt * 5;

            if (temperatureBuffer >= bufferCost) {
                bodyTemperature += correction;
                temperatureBuffer -= bufferCost;
            } else {
                // buffer 不够 → 部分修正后开始漂移
                logger.warn("体温缓冲耗尽! 体温={}°C, 环境={}°C, 开始漂移",
                        String.format("%.2f", bodyTemperature),
                        String.format("%.1f", envTemp));
                double partialCorrection = -Math.signum(deviation)
                        * temperatureBuffer / (dt * 5);
                bodyTemperature += partialCorrection;
                temperatureBuffer = 0;
                applyDrift(envTemp, dt);
            }
        } else {
            // 无能量或 buffer 耗尽 → 体温向环境漂移
            if (!hasEnergy) {
                logger.warn("能量不足! 能量池={}cal ({}%), 体温开始漂移",
                        (int) energyPool, getHungerPercent());
            }
            applyDrift(envTemp, dt);
        }

        // 钳制体温范围
        bodyTemperature = Math.max(20, Math.min(45, bodyTemperature));

        // 危险体温警告
        if (bodyTemperature < 35 || bodyTemperature > 39) {
            logger.warn("危险体温! 体温={}°C, 状态={}",
                    String.format("%.2f", bodyTemperature), getBodyTempDescriptor());
        }
    }

    /**
     * 体温向环境温度漂移（产热/散热机制失效时）。
     */
    private void applyDrift(double envTemp, long dt) {
        double drift = (envTemp - bodyTemperature) * Constants.BODY_TEMP_DRIFT_RATE * dt;
        bodyTemperature += drift;
    }

    // ── 外部接口 ──────────────────────────────────

    /**
     * 添加动作消耗（移动、战斗等额外热量消耗）。
     * 消耗在下次 update() 时结算。
     *
     * @param calories 消耗的热量（cal）
     */
    public void addActionCost(double calories) {
        pendingActionCost += calories;
    }

    /**
     * 摄入食物（增加能量池）。
     *
     * @param calories 食物热量（cal）
     */
    public void addCalories(double calories) {
        energyPool = Math.min(maxEnergy, energyPool + calories);
    }

    // ── 状态查询 ──────────────────────────────────

    /**
     * 获取当前能量储备。
     *
     * @return 当前能量储备（cal）
     */
    public double getEnergyPool() { return energyPool; }

    /**
     * 获取最大能量储备。
     *
     * @return 最大能量储备（cal）
     */
    public double getMaxEnergy() { return maxEnergy; }

    /**
     * 获取饥饿百分比（0-100），100=满，0=极饿。
     *
     * @return 能量百分比（0-100）
     */
    public int getHungerPercent() {
        return (int) (energyPool / maxEnergy * 100);
    }

    /**
     * 获取当前核心体温（°C）。
     *
     * @return 核心体温（摄氏度）
     */
    public double getBodyTemperature() { return bodyTemperature; }

    /**
     * 获取体温缓冲值。
     *
     * @return 体温缓冲值
     */
    public double getTemperatureBuffer() { return temperatureBuffer; }

    /**
     * 获取体温等级（0-8），映射到 CharacterInfoPanel 的 TEMP 显示。
     *
     * <pre>
     * 0 = 极寒 (<32°C)    5 = 温热 (37.5~38.5°C)
     * 1 = 严寒 (32~34°C)  6 = 炎热 (38.5~40°C)
     * 2 = 寒冷 (34~35.5°C) 7 = 酷热 (40~42°C)
     * 3 = 微凉 (35.5~36.5°C) 8 = 极热 (>42°C)
     * 4 = 正常 (36.5~37.5°C)
     * </pre>
     *
     * @return 体温等级（0-8）
     */
    public int getBodyTempLevel() {
        double temp = bodyTemperature;
        if (temp < 32) return 0;
        if (temp < 34) return 1;
        if (temp < 35.5) return 2;
        if (temp < 36.5) return 3;
        if (temp < 37.5) return 4;
        if (temp < 38.5) return 5;
        if (temp < 40) return 6;
        if (temp < 42) return 7;
        return 8;
    }

    /**
     * 获取体温状态描述。
     *
     * @return 如 "极寒", "严寒", ..., "正常", ..., "酷热", "极热"
     */
    public String getBodyTempDescriptor() {
        String[] descriptors = {
                "极寒", "严寒", "寒冷", "微凉", "正常",
                "温热", "炎热", "酷热", "极热"
        };
        return descriptors[getBodyTempLevel()];
    }

    /**
     * 是否有严重体温异常（需要警告）。
     *
     * @return 体温低于34°C或高于39°C时返回 {@code true}
     */
    public boolean hasCriticalTemperature() {
        return bodyTemperature < 34 || bodyTemperature > 39;
    }

    /**
     * 是否处于饥饿状态（能量低于20%）。
     *
     * @return 能量储备低于20%时返回 {@code true}
     */
    public boolean isStarving() {
        return energyPool < maxEnergy * 0.2;
    }

    /**
     * 计算体温异常导致的 HP 伤害（每回合）。
     *
     * <p>判定逻辑：
     * <ul>
     *   <li><b>危险</b>：能量 &lt; 5% 且体温 &lt; 34°C 或 &gt; 39°C → 1 HP/回合</li>
     *   <li><b>致命</b>：能量 &lt; 2% 且体温 &lt; 32°C 或 &gt; 41°C → 3 HP/回合</li>
     *   <li>能量充足时不会因体温异常扣血（靠体温缓冲抵御）</li>
     * </ul>
     *
     * @return 本回合应扣除的 HP 值（0 表示无伤害）
     */
    public int calcTemperatureDamage() {
        boolean energyEmpty = energyPool < maxEnergy * 0.05;
        boolean criticallyEmpty = energyPool < maxEnergy * 0.02;

        if (criticallyEmpty && (bodyTemperature < 32 || bodyTemperature > 41)) {
            return 3; // 致命阶段
        }
        if (energyEmpty && (bodyTemperature < 34 || bodyTemperature > 39)) {
            return 1; // 危险阶段
        }
        return 0;
    }

    /**
     * 获取体温伤害的死亡原因描述。
     *
     * @return 如 "饥饿导致体温过低", "饥饿导致体温过高"
     */
    public String getTemperatureDeathReason() {
        if (bodyTemperature < 34) return "饥饿导致体温过低";
        if (bodyTemperature > 39) return "饥饿导致体温过高";
        return "";
    }
}
