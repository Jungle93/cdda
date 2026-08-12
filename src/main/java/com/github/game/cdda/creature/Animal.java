package com.github.game.cdda.creature;

import com.github.game.engine.core.EngineServices;
import com.github.game.cdda.creature.ai.AnimalAI;
import com.github.game.cdda.creature.config.CreatureDefinition;
import com.github.game.cdda.creature.energy.DeathCause;
import com.github.game.cdda.creature.energy.EnergyConfig;
import com.github.game.cdda.creature.energy.EnergyFlowManager;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.i18n.I18nManager;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.util.List;
import java.util.Random;

/**
 * 动物类。
 *
 * <p>特征：
 * <ul>
 *   <li>AI 状态机驱动行为（游荡、觅食、逃跑）</li>
 *   <li>生命成长系统（幼崽 → 成年）</li>
 *   <li>感知系统（视觉 + 听觉）</li>
 *   <li>只能被玩家杀死</li>
 * </ul>
 */
public class Animal extends Creature {

    /** AI 状态机 */
    private final AnimalAI ai;

    /** 生物定义（模板） */
    private final CreatureDefinition definition;

    /** 当前生命阶段索引 */
    private int currentStageIndex = 0;

    /** 出生时的游戏时间（游戏秒数，-1 表示尚未设置） */
    private long birthTime = -1;

    /** 当前游戏时间（每次 takeTurn 从 context 更新） */
    private long currentGameSeconds = 0;

    /** 上次繁殖的游戏时间（秒，-9999 表示从未繁殖） */
    private long lastReproductionTime = -9999;

    // ── 能量系统 ──────────────────────────

    /** 代谢能量/饱腹度（0~100） */
    private int bodyEnergy;

    /** bodyEnergy=0 的持续回合计数 */
    private int turnsStarving = 0;

    /** 死亡原因 */
    private DeathCause deathCause;

    /** 是否已处理掉落（避免重复掉落） */
    private boolean lootDropped = false;

    // ── 逃跑耐力 ──────────────────────────

    /** 逃跑耐力（fleeing 状态下的爆发耐力，非 fleeing 时为 -1） */
    private int fleeStamina = -1;

    /** 最大逃跑耐力（基于 endurance 计算） */
    private int maxFleeStamina = 0;

    /** 逃跑初始耐力基数（每点 endurance 对应的逃跑步数） */
    private static final int FLEE_STAMINA_PER_ENDURANCE = 30;

    /** 每逃一步消耗的耐力 */
    private static final int FLEE_STAMINA_COST = 2;

    /** 逃跑速度加成系数（基于耐力剩余比例，最大 +50%） */
    private static final double FLEE_SPEED_BOOST_FACTOR = 0.5;

    /** 能量流动管理器 */
    private transient EnergyFlowManager energyFlowManager;

    /** 攻击此动物的捕食者（用于 PREDATION 死亡） */
    private transient Animal attackingPredator;

    /**
     * 从定义创建动物。
     *
     * @param definition 生物定义
     * @param tileX      初始瓦片 X
     * @param tileY      初始瓦片 Y
     */
    public Animal(CreatureDefinition definition, int tileX, int tileY) {
        this.definition = definition;
        this.tileX = tileX;
        this.tileY = tileY;
        this.ai = new AnimalAI();

        // 初始化属性
        initializeFromDefinition();

        // 初始化能量
        EnergyConfig ec = definition.getEnergyConfig();
        this.bodyEnergy = ec.getInitialEnergy();
    }

    /**
     * 从定义初始化属性。
     */
    private void initializeFromDefinition() {
        // 基础属性
        this.speed = definition.speed;
        this.strength = definition.stats.strength;
        this.agility = definition.stats.agility;
        this.endurance = definition.stats.endurance;

        // 感知
        this.visionRange = definition.perception.vision;
        this.hearingRange = definition.perception.hearing;

        // 渲染
        if (definition.displayChar != null && !definition.displayChar.isEmpty()) {
            this.displayChar = definition.displayChar.charAt(0);
        } else {
            this.displayChar = '?';
        }

        if (definition.displayColor != null && definition.displayColor.length >= 3) {
            this.displayColor = new Color(
                    definition.displayColor[0],
                    definition.displayColor[1],
                    definition.displayColor[2]
            );
        } else {
            this.displayColor = Color.WHITE;
        }

        // 生命阶段：从第一阶段开始
        applyLifeStage(0);
    }

    /**
     * 应用生命阶段。
     *
     * @param stageIndex 阶段索引
     */
    private void applyLifeStage(int stageIndex) {
        List<CreatureDefinition.LifeStage> stages = definition.lifeStages;
        if (stages == null || stages.isEmpty() || stageIndex >= stages.size()) {
            // 无阶段数据，使用默认 HP
            this.maxHp = definition.hp;
            this.hp = definition.hp;
            return;
        }

        CreatureDefinition.LifeStage stage = stages.get(stageIndex);
        this.maxHp = stage.hp;
        this.hp = stage.hp;
        this.currentStageIndex = stageIndex;
    }

    @Override
    public void takeTurn(CreatureActionContext context) {
        if (!alive) return;

        // 更新当前游戏时间（首次调用时初始化出生时间）
        currentGameSeconds = context.getCurrentGameSeconds();
        if (birthTime < 0) {
            birthTime = currentGameSeconds;
        }

        // AI 更新
        ai.update(this, context);

        // 代谢消耗
        updateMetabolism();

        // 检查成长
        checkGrowth();

        // 检查寿命
        checkLifespan();

        // 检查饥饿
        checkStarvation();
    }

    /**
     * 获取存活时长（游戏秒数）。
     */
    public long getAgeSeconds() {
        return birthTime >= 0 ? currentGameSeconds - birthTime : 0;
    }

    /**
     * 代谢更新：每 N 游戏秒 -1 bodyEnergy。
     */
    private void updateMetabolism() {
        EnergyConfig ec = definition.getEnergyConfig();
        int interval = ec.getMetabolismInterval();
        if (interval <= 0) return;

        long age = getAgeSeconds();
        if (age > 0 && age % interval == 0) {
            bodyEnergy = Math.max(0, bodyEnergy - 1);
        }
    }

    /**
     * 检查寿命：达到寿命上限 → 自然老死。
     */
    private void checkLifespan() {
        EnergyConfig ec = definition.getEnergyConfig();
        if (getAgeSeconds() >= ec.getLifespanSeconds()) {
            deathCause = DeathCause.NATURAL_AGE;
            alive = false;
            onDeath();
        }
    }

    /**
     * 检查饥饿：bodyEnergy=0 持续足够久 → 饿死。
     */
    private void checkStarvation() {
        if (bodyEnergy == 0) {
            turnsStarving++;
            EnergyConfig ec = definition.getEnergyConfig();
            if (turnsStarving >= ec.getStarvationSeconds()) {
                deathCause = DeathCause.STARVATION;
                alive = false;
                onDeath();
            }
        } else {
            turnsStarving = 0;
        }
    }

    /**
     * 检查是否成长到下一阶段（基于游戏时间）。
     */
    private void checkGrowth() {
        List<CreatureDefinition.LifeStage> stages = definition.lifeStages;
        if (stages == null || currentStageIndex >= stages.size() - 1) {
            return;  // 已经是最终阶段
        }

        CreatureDefinition.LifeStage currentStage = stages.get(currentStageIndex);
        if (currentStage.growSeconds == null) {
            return;  // 当前阶段无成长条件
        }

        if (getAgeSeconds() >= currentStage.growSeconds) {
            // 成长到下一阶段
            int nextStage = currentStageIndex + 1;
            applyLifeStage(nextStage);
        }
    }

    /**
     * 尝试繁殖。
     * 检查成熟度、冷却时间、能量门槛、概率，成功时返回后代。
     * 冷却判定基于游戏时间。
     *
     * @param currentTurn 当前回合数（未使用，保留兼容）
     * @param random      随机数生成器
     * @return 后代动物，繁殖失败返回 null
     */
    public Animal tryReproduce(int currentTurn, Random random) {
        CreatureDefinition.Reproduction repro = definition.reproduction;
        if (repro == null) return null;

        // 必须达到成熟期（基于游戏时间）
        if (getAgeSeconds() < repro.matureSeconds) return null;

        // 冷却检查（基于游戏时间）
        if (currentGameSeconds - lastReproductionTime < repro.cooldownSeconds) return null;

        // 能量门槛
        EnergyConfig ec = definition.getEnergyConfig();
        if (bodyEnergy < ec.getReproduceThreshold()) return null;

        // 概率判定
        if (random.nextDouble() > repro.chance) return null;

        // 繁殖成功
        lastReproductionTime = currentGameSeconds;
        bodyEnergy -= ec.getReproduceCost();

        // 创建后代（同物种，幼年阶段）
        // 后代的 birthTime 会在首次 takeTurn 时初始化为当前时间
        return new Animal(definition, tileX, tileY);
    }

    /**
     * 获取上次繁殖的游戏时间。
     *
     * @return 游戏秒数
     */
    public long getLastReproductionTime() {
        return lastReproductionTime;
    }

    /**
     * 是否为成年（达到成熟期）。
     *
     * @return true 如果已成熟
     */
    public boolean isMature() {
        CreatureDefinition.Reproduction repro = definition.reproduction;
        if (repro == null) return true; // 无繁殖参数的视为成熟
        return getAgeSeconds() >= repro.matureSeconds;
    }

    @Override
    public void render(Renderer renderer, Camera camera, int tileWidth, int tileHeight) {
        if (!alive) return;

        // 计算世界坐标（像素）
        int worldX = tileX * tileWidth;
        int worldY = tileY * tileHeight;

        // 转换为视图坐标
        int viewX = camera.toViewX(worldX);
        int viewY = camera.toViewY(worldY);

        // 渲染字符
        renderer.setColor(displayColor);
        int baselineY = viewY + renderer.getFontMetrics().getAscent();
        renderer.drawText(String.valueOf(displayChar), viewX, baselineY);
    }

    @Override
    protected void onDeath() {
        alive = false;

        if (energyFlowManager != null) {
            if (deathCause == null) {
                deathCause = DeathCause.NATURAL_AGE;
            }

            switch (deathCause) {
                case NATURAL_AGE:
                case STARVATION:
                    // 自然死亡，不掉落，尸体分解
                    energyFlowManager.recordNaturalDeath(this);
                    break;
                case PREDATION:
                    // 被上层捕食者吃掉，能量转移
                    if (attackingPredator != null) {
                        energyFlowManager.recordPredation(this, attackingPredator);
                    }
                    break;
                case PLAYER_KILL:
                    // 玩家杀死，记录掉落
                    energyFlowManager.recordPlayerKill(this);
                    break;
            }
        }
    }

    /**
     * 被玩家杀死。
     */
    public void killByPlayer() {
        if (!alive) return;
        deathCause = DeathCause.PLAYER_KILL;
        alive = false;
        onDeath();
    }

    /**
     * 被上层捕食者吃掉。
     *
     * @param predator 捕食者
     */
    public void eatenBy(Animal predator) {
        if (!alive) return;
        deathCause = DeathCause.PREDATION;
        this.attackingPredator = predator;
        alive = false;
        onDeath();
    }

    /** 设置能量流动管理器 */
    void setEnergyFlowManager(EnergyFlowManager manager) {
        this.energyFlowManager = manager;
    }

    /** 获取能量流动管理器（内部使用） */
    EnergyFlowManager getEnergyFlowManager() {
        return energyFlowManager;
    }

    // ── 访问器 ──────────────────────────────────

    /**
     * 获取生物定义。
     *
     * @return 定义
     */
    public CreatureDefinition getDefinition() {
        return definition;
    }

    /**
     * 获取当前生命阶段名称（优先从 i18n 获取，回退到定义中的名称）。
     *
     * @return 阶段名称
     */
    public String getStageName() {
        List<CreatureDefinition.LifeStage> stages = definition.lifeStages;
        if (stages != null && currentStageIndex < stages.size()) {
            CreatureDefinition.LifeStage stage = stages.get(currentStageIndex);
            // 优先从 i18n 获取
            String key = "creature." + definition.id + ".stage." + stage.stage + ".name";
            String i18nValue = resolveI18n(key);
            if (i18nValue != null) return i18nValue;
            // 回退到定义中的名称
            return stage.name;
        }
        // 回退到物种名称
        return getLocalizedName();
    }

    /**
     * 获取物种显示名（优先从 i18n 获取，回退到定义中的 name）。
     */
    public String getLocalizedName() {
        String key = "creature." + definition.id + ".name";
        String value = resolveI18n(key);
        return value != null ? value : definition.name;
    }

    /** 尝试通过 I18nManager 解析翻译键，未找到时返回 null */
    private String resolveI18n(String key) {
        try {
            I18nManager i18n = EngineServices.i18n;
            if (i18n == null) return null;
            String value = i18n.t(key);
            return key.equals(value) ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取已存活游戏秒数。
     *
     * @return 存活秒数
     */
    public long getAgeInSeconds() {
        return getAgeSeconds();
    }

    /**
     * 获取 AI 状态（调试用）。
     *
     * @return AI 状态
     */
    public String getAIState() {
        return ai.getCurrentState().name();
    }

    // ── 能量系统访问器 ──────────────────────────

    /** 获取当前 bodyEnergy */
    public int getBodyEnergy() {
        return bodyEnergy;
    }

    /** 获取最大 bodyEnergy */
    public int getMaxBodyEnergy() {
        return definition.getEnergyConfig().getMaxEnergy();
    }

    /** 设置 bodyEnergy（用于进食、捕食等） */
    public void addBodyEnergy(int amount) {
        this.bodyEnergy = Math.min(getMaxBodyEnergy(), Math.max(0, this.bodyEnergy + amount));
        if (bodyEnergy > 0) {
            turnsStarving = 0;
        }
    }

    /** 获取死亡原因 */
    public DeathCause getDeathCause() {
        return deathCause;
    }

    /** 是否已处理掉落 */
    public boolean isLootDropped() {
        return lootDropped;
    }

    /** 标记掉落已处理（避免重复掉落） */
    public void setLootDropped(boolean dropped) {
        this.lootDropped = dropped;
    }

    /** 获取饥饿回合数 */
    public int getTurnsStarving() {
        return turnsStarving;
    }

    /** 获取能量配置 */
    public EnergyConfig getEnergyConfig() {
        return definition.getEnergyConfig();
    }

    /** 是否处于饥饿状态 */
    public boolean isStarving() {
        return bodyEnergy < 30;
    }

    /** 是否健康（能量充足） */
    public boolean isHealthy() {
        return bodyEnergy > 60;
    }

    // ── 逃跑耐力 ──────────────────────────

    /**
     * 进入逃跑状态，初始化逃跑耐力。
     * 耐力基于 endurance 计算：endurance × 3。
     */
    public void startFleeing() {
        maxFleeStamina = endurance * FLEE_STAMINA_PER_ENDURANCE;
        fleeStamina = maxFleeStamina;
    }

    /**
     * 逃跑中消耗耐力。
     * 每逃一步消耗 FLEE_STAMINA_COST 点耐力。
     *
     * @return 剩余耐力百分比 (0.0 ~ 1.0)
     */
    public double consumeFleeStamina() {
        if (fleeStamina <= 0) return 0.0;
        fleeStamina = Math.max(0, fleeStamina - FLEE_STAMINA_COST);
        return maxFleeStamina > 0 ? (double) fleeStamina / maxFleeStamina : 0.0;
    }

    /**
     * 获取当前逃跑耐力百分比。
     */
    public double getFleeStaminaRatio() {
        if (fleeStamina < 0 || maxFleeStamina <= 0) return 1.0;
        return (double) fleeStamina / maxFleeStamina;
    }

    /**
     * 重写行动时间计算：逃跑时根据剩余耐力获得速度加成。
     * 耐力充足时，行动耗时最多缩短为原来的 2/3（速度 +50%）；
     * 耐力耗尽时，加成消失（疲惫的逃跑者）。
     */
    @Override
    public int getActionTime(int baseTime) {
        int effectiveSpeed = speed;
        if (fleeStamina > 0 && maxFleeStamina > 0) {
            double ratio = getFleeStaminaRatio();
            effectiveSpeed = (int) (speed * (1.0 + FLEE_SPEED_BOOST_FACTOR * ratio));
        }
        if (effectiveSpeed <= 0) return baseTime;
        return baseTime * 100 / effectiveSpeed;
    }

    /**
     * 是否应该停止逃跑。
     * 耐力耗尽（< 10%）或达到安全距离时返回 true。
     *
     * @param playerTileX 玩家瓦片 X
     * @param playerTileY 玩家瓦片 Y
     * @param safeDistance 安全距离（默认 10）
     * @return 是否应停止逃跑
     */
    public boolean shouldStopFleeing(int playerTileX, int playerTileY, int safeDistance) {
        // 耐力耗尽 → 停止
        if (fleeStamina < maxFleeStamina * 0.1) {
            return true;
        }
        // 达到安全距离 → 停止
        if (distanceTo(playerTileX, playerTileY) >= safeDistance) {
            return true;
        }
        return false;
    }

    /**
     * 结束逃跑状态。
     */
    public void endFleeing() {
        fleeStamina = -1;
        maxFleeStamina = 0;
    }
}
