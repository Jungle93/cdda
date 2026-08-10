package com.github.game.cdda.creature;

import com.github.game.cdda.creature.ai.AnimalAI;
import com.github.game.cdda.creature.config.CreatureDefinition;
import com.github.game.engine.core.Camera;
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

    /** 已存活回合数 */
    private int turnsLived = 0;

    /** 上次繁殖的回合数（-9999 表示从未繁殖） */
    private int lastReproductionTurn = -9999;

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

        // AI 更新
        ai.update(this, context);

        // 更新存活回合
        turnsLived++;

        // 检查成长
        checkGrowth();
    }

    /**
     * 检查是否成长到下一阶段。
     */
    private void checkGrowth() {
        List<CreatureDefinition.LifeStage> stages = definition.lifeStages;
        if (stages == null || currentStageIndex >= stages.size() - 1) {
            return;  // 已经是最终阶段
        }

        CreatureDefinition.LifeStage currentStage = stages.get(currentStageIndex);
        if (currentStage.growTurns == null) {
            return;  // 当前阶段无成长条件
        }

        if (turnsLived >= currentStage.growTurns) {
            // 成长到下一阶段
            int nextStage = currentStageIndex + 1;
            applyLifeStage(nextStage);
            // 可在此添加日志或事件通知
        }
    }

    /**
     * 尝试繁殖。
     * 检查成熟度、冷却时间、概率，成功时返回后代。
     *
     * @param currentTurn 当前回合数
     * @param random      随机数生成器
     * @return 后代动物，繁殖失败返回 null
     */
    public Animal tryReproduce(int currentTurn, Random random) {
        CreatureDefinition.Reproduction repro = definition.reproduction;
        if (repro == null) return null;

        // 必须达到成熟期
        if (turnsLived < repro.matureTurns) return null;

        // 冷却检查
        if (currentTurn - lastReproductionTurn < repro.cooldownTurns) return null;

        // 概率判定
        if (random.nextDouble() > repro.chance) return null;

        // 繁殖成功
        lastReproductionTurn = currentTurn;

        // 创建后代（同物种，幼年阶段）
        // 后代初始位置与父母相同，实际偏移由 CreatureManager.placeNearby() 处理
        return new Animal(definition, tileX, tileY);
    }

    /**
     * 获取上次繁殖的回合数。
     *
     * @return 回合数
     */
    public int getLastReproductionTurn() {
        return lastReproductionTurn;
    }

    /**
     * 是否为成年（达到成熟期）。
     *
     * @return true 如果已成熟
     */
    public boolean isMature() {
        CreatureDefinition.Reproduction repro = definition.reproduction;
        if (repro == null) return true; // 无繁殖参数的视为成熟
        return turnsLived >= repro.matureTurns;
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
        // 死亡逻辑（掉落物品等，后续实现）
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
     * 获取当前生命阶段名称。
     *
     * @return 阶段名称
     */
    public String getStageName() {
        List<CreatureDefinition.LifeStage> stages = definition.lifeStages;
        if (stages != null && currentStageIndex < stages.size()) {
            return stages.get(currentStageIndex).name;
        }
        return definition.name;
    }

    /**
     * 获取已存活回合数。
     *
     * @return 回合数
     */
    public int getTurnsLived() {
        return turnsLived;
    }

    /**
     * 获取 AI 状态（调试用）。
     *
     * @return AI 状态
     */
    public String getAIState() {
        return ai.getCurrentState().name();
    }
}
