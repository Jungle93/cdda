package com.github.game.cdda.creature.energy;

/**
 * 能量配置。
 *
 * <p>定义物种的能量参数，包括代谢、进食、繁殖、寿命等。
 * 从 creature JSON 文件中反序列化加载。
 *
 * <p>时间单位：游戏秒（1 游戏秒 ≈ 1 游戏分钟）。
 */
public class EnergyConfig {

    /** 最大能量（100） */
    public int maxEnergy = 100;

    /** 出生时初始能量 */
    public int initialEnergy = 70;

    /** 代谢间隔（每 N 游戏秒 -1 bodyEnergy） */
    public int metabolismInterval = 10;

    /** 吃草获得的能量（食草动物 >0，捕食者 =0） */
    public int grazeGain = 0;

    /** 成功捕食获得的能量 */
    public int huntGain = 0;

    /** 食腐获得的能量 */
    public int scavengeGain = 0;

    /** 繁殖消耗 */
    public int reproduceCost = 30;

    /** 繁殖所需最低 bodyEnergy */
    public int reproduceThreshold = 60;

    /** bodyEnergy=0 后存活游戏秒数（饿死前缓冲） */
    public long starvationSeconds = 24000;

    /** 寿命（游戏秒数） */
    public long lifespanSeconds = 960000;

    // ── 访问器 ──────────────────────────────────

    public int getMaxEnergy() { return maxEnergy; }
    public int getInitialEnergy() { return initialEnergy; }
    public int getMetabolismInterval() { return metabolismInterval; }
    public int getGrazeGain() { return grazeGain; }
    public int getHuntGain() { return huntGain; }
    public int getScavengeGain() { return scavengeGain; }
    public int getReproduceCost() { return reproduceCost; }
    public int getReproduceThreshold() { return reproduceThreshold; }
    public long getStarvationSeconds() { return starvationSeconds; }
    public long getLifespanSeconds() { return lifespanSeconds; }
}
