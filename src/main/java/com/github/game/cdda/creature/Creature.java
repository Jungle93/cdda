package com.github.game.cdda.creature;

import com.github.game.cdda.Entity;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;

/**
 * 生物抽象基类。所有有生命的实体的公共父类。
 *
 * <p>继承自 {@link Entity}，在回合系统的基础上添加：
 * <ul>
 *   <li><b>生命系统</b> — hp、受伤、治疗、死亡</li>
 *   <li><b>基础属性</b> — 力量、敏捷、耐力</li>
 *   <li><b>感知系统</b> — 视觉范围、听觉范围</li>
 *   <li><b>位置</b> — 瓦片坐标（tileX, tileY）</li>
 *   <li><b>渲染</b> — 字符显示</li>
 * </ul>
 *
 * <p>子类需实现：
 * <ul>
 *   <li>{@link #takeTurn(CreatureActionContext)} — 回合行动（AI 或玩家输入）</li>
 *   <li>{@link #render(Renderer, Camera, int, int)} — 渲染</li>
 * </ul>
 */
public abstract class Creature extends Entity {

    // ── 生命系统 ──────────────────────────────────

    /** 当前生命值 */
    protected int hp;

    /** 最大生命值 */
    protected int maxHp;

    /** 是否存活 */
    protected boolean alive = true;

    // ── 基础属性 ──────────────────────────────────

    /** 力量（影响近战伤害、负重） */
    protected int strength;

    /** 敏捷（影响命中率、闪避） */
    protected int agility;

    /** 耐力（影响 HP、抗疲劳） */
    protected int endurance;

    // ── 感知系统 ──────────────────────────────────

    /** 视觉范围（瓦片） */
    protected int visionRange;

    /** 听觉范围（瓦片） */
    protected int hearingRange;

    // ── 位置（瓦片坐标） ──────────────────────────────────

    /** 瓦片 X 坐标 */
    protected int tileX;

    /** 瓦片 Y 坐标 */
    protected int tileY;

    // ── 渲染信息 ──────────────────────────────────

    /** 显示字符 */
    protected char displayChar;

    /** 显示颜色 */
    protected Color displayColor;

    // ── 抽象方法 ──────────────────────────────────

    /**
     * 执行回合行动。
     * 由 CreatureManager 在生物有足够能量时调用。
     *
     * @param context 行动上下文（提供玩家位置、地图等信息）
     */
    public abstract void takeTurn(CreatureActionContext context);

    /**
     * 渲染生物。
     *
     * @param renderer   渲染器
     * @param camera     摄像机
     * @param tileWidth  瓦片像素宽度
     * @param tileHeight 瓦片像素高度
     */
    public abstract void render(Renderer renderer, Camera camera, int tileWidth, int tileHeight);

    /**
     * 获取渲染时的水平偏移量（待机动画）。
     * <p>
     * 每秒随机变化 ±2 像素偏移，让生物看起来更有生命力。
     * 每个生物根据自身坐标产生不同随机种子，避免同步晃动。
     * 实际位置（tileX/tileY）不受影响。
     * </p>
     *
     * @return 水平偏移像素数（-2 到 2）
     */
    protected int getRenderOffsetX() {
        long tick = System.currentTimeMillis() / 1000;
        long seed = tileX * 7919L + tileY * 104729L + tick * 6364136223846793005L + 1442695040888963407L;
        return (int) ((seed >>> 33) % 5) - 2;
    }

    /**
     * 获取渲染时的垂直偏移量（待机动画）。
     *
     * @return 垂直偏移像素数（-2 到 2）
     */
    protected int getRenderOffsetY() {
        long tick = System.currentTimeMillis() / 1000;
        long seed = tileX * 104729L + tileY * 7919L + tick * 6364136223846793005L + 1442695040888963407L;
        return (int) ((seed >>> 33) % 5) - 2;
    }

    // ── 生命管理 ──────────────────────────────────

    /**
     * 受到伤害。
     *
     * @param damage 伤害值
     */
    public void takeDamage(int damage) {
        if (!alive) return;
        hp = Math.max(0, hp - damage);
        if (hp <= 0) {
            alive = false;
            onDeath();
        }
    }

    /**
     * 治疗。
     *
     * @param amount 治疗量
     */
    public void heal(int amount) {
        if (!alive) return;
        hp = Math.min(maxHp, hp + amount);
    }

    /**
     * 死亡回调。子类可重写以执行死亡逻辑（掉落物品等）。
     */
    protected void onDeath() {
        // 默认空实现
    }

    // ── 感知 ──────────────────────────────────

    /**
     * 检测是否能感知到目标位置。
     * 使用曼哈顿距离判断。
     *
     * @param targetX 目标瓦片 X
     * @param targetY 目标瓦片 Y
     * @return 是否在感知范围内
     */
    public boolean canPerceive(int targetX, int targetY) {
        int distance = distanceTo(targetX, targetY);
        // 取视觉和听觉中较大的范围
        int maxRange = Math.max(visionRange, hearingRange);
        return distance <= maxRange;
    }

    /**
     * 计算到目标位置的曼哈顿距离。
     *
     * @param otherTileX 目标瓦片 X
     * @param otherTileY 目标瓦片 Y
     * @return 曼哈顿距离
     */
    public int distanceTo(int otherTileX, int otherTileY) {
        return Math.abs(tileX - otherTileX) + Math.abs(tileY - otherTileY);
    }

    // ── 访问器 ──────────────────────────────────

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public boolean isAlive() { return alive; }

    public int getStrength() { return strength; }
    public int getAgility() { return agility; }
    public int getEndurance() { return endurance; }

    public int getVisionRange() { return visionRange; }
    public int getHearingRange() { return hearingRange; }

    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }

    public void setTileX(int tileX) { this.tileX = tileX; }
    public void setTileY(int tileY) { this.tileY = tileY; }

    /** 设置当前 HP（用于存档恢复） */
    public void setHp(int hp) { this.hp = Math.max(0, Math.min(hp, maxHp)); }

    /** 设置最大 HP（会同步调整当前 HP） */
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; this.hp = Math.min(hp, maxHp); }

    /** 设置力量（用于存档恢复） */
    public void setStrength(int strength) { this.strength = strength; }

    /** 设置敏捷（用于存档恢复） */
    public void setAgility(int agility) { this.agility = agility; }

    /** 设置耐力（用于存档恢复） */
    public void setEndurance(int endurance) { this.endurance = endurance; }

    public char getDisplayChar() { return displayChar; }
    public Color getDisplayColor() { return displayColor; }
}
