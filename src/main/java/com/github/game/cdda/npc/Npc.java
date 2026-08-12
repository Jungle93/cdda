package com.github.game.cdda.npc;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.creature.Creature;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.item.world.GroundItemManager;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.registry.ItemRegistry;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.npc.ai.NpcAI;
import com.github.game.cdda.npc.ai.NpcAIState;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.util.List;
import java.util.Random;

/**
 * NPC（非玩家角色）。
 *
 * <p>继承自 {@link Creature}，具备以下特征：
 * <ul>
 *   <li>AI 状态机驱动行为（游荡、追击、攻击、逃跑、睡眠）</li>
 *   <li>地域背景系统 — 基础属性由地域基线 + 随机波动决定</li>
 *   <li>社交系统 — 对玩家的态度，动态变化</li>
 *   <li>背包系统 — 携带物品，死亡后掉落</li>
 *   <li>交互系统 — 对话、交易、信息展示</li>
 * </ul>
 */
public class Npc extends Creature {

    /** NPC 名称（随机生成） */
    private String name;

    /** NPC 类型（友好/中立/敌对/功能型） */
    private final NpcType npcType;

    /** 地域背景 */
    private final NpcRegion region;

    /** NPC 定义（模板数据，可为 null 表示默认模板） */
    private NpcDefinition definition;

    /** AI 状态机 */
    private final NpcAI ai;

    /** 社交数据 */
    private final NpcSocial social;

    /** NPC 背包 */
    private final NpcInventory inventory;

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 是否已处理掉落 */
    private boolean lootDropped = false;

    /**
     * 创建 NPC。
     *
     * @param region 地域背景（决定基础属性和显示）
     * @param npcType NPC 类型
     * @param tileX  初始瓦片 X
     * @param tileY  初始瓦片 Y
     */
    public Npc(NpcRegion region, NpcType npcType, int tileX, int tileY) {
        this.region = region;
        this.npcType = npcType;
        this.tileX = tileX;
        this.tileY = tileY;

        this.ai = new NpcAI();
        this.social = new NpcSocial();
        this.social.initializeForType(npcType);

        // 随机名字
        this.name = NpcNameGenerator.generateName(region);

        // 根据地域设置属性（±2 随机波动）
        this.strength = region.baseStr + random.nextInt(5) - 2;
        this.agility = region.baseAgi + random.nextInt(5) - 2;
        this.endurance = region.baseCon + random.nextInt(5) - 2;
        this.visionRange = region.basePer + random.nextInt(5) - 2;
        this.hearingRange = Math.max(4, this.visionRange - 2);

        // NPC 默认速度 100
        this.speed = Constants.ENTITY_DEFAULT_SPEED;

        // 生命值
        this.maxHp = 50 + this.endurance * 10;
        this.hp = this.maxHp;

        // 显示
        this.displayChar = region.displayChar;
        this.displayColor = region.getColorForType(npcType);

        // 背包
        this.inventory = new NpcInventory(this);
    }

    /**
     * 设置 NPC 定义（可选，用于自定义装备和对话等）。
     */
    public void setDefinition(NpcDefinition definition) {
        this.definition = definition;

        // 如果有自定义名称则覆盖
        if (definition != null && definition.name != null && !definition.name.isEmpty()) {
            // 保留随机生成的名字，定义中的 name 作为类型称呼
        }

        // 如果有定义的速度则覆盖
        if (definition != null && definition.speed > 0) {
            this.speed = definition.speed;
        }

        // 加载起始装备
        if (definition != null && definition.equipment != null) {
            loadEquipment(definition.equipment);
        }

        // 加载商品
        if (definition != null && definition.tradeGoods != null) {
            for (ItemStack item : definition.tradeGoods) {
                inventory.addItem(item);
            }
        }
    }

    /**
     * 加载起始装备。
     * 从物品注册表查找装备并放入背包（忽略重量限制）。
     */
    private void loadEquipment(List<NpcDefinition.EquipmentSlot> equipment) {
        for (NpcDefinition.EquipmentSlot slot : equipment) {
            if (slot.itemId == null || slot.itemId.isEmpty()) continue;
            ItemType itemType = ItemRegistry.getByName(slot.itemId);
            if (itemType != null) {
                inventory.addItemUnchecked(new ItemStack(itemType, 1));
            } else {
                com.github.game.cdda.log.GameLog.getInstance().log(
                        String.format("警告: NPC %s 装备物品 '%s' 未找到", name, slot.itemId));
            }
        }
    }

    // ── 回合行动 ──────────────────────────────────

    @Override
    public void takeTurn(CreatureActionContext context) {
        if (!alive) return;

        ai.update(this, context);
    }

    // ── 战斗 ──────────────────────────────────

    /**
     * 近战攻击目标。
     * 伤害公式：基础伤害 = 25 + STR × 1.5，浮动 ±15%。
     *
     * @param target 目标生物
     * @return 实际造成的伤害值
     */
    public int meleeAttack(com.github.game.cdda.creature.Creature target) {
        if (target == null || !target.isAlive()) return 0;

        // 基础伤害 = 25 + STR × 1.5
        int baseDamage = 25 + (int) (strength * 1.5);
        baseDamage = Math.max(1, baseDamage);

        // ±15% 随机浮动
        double variance = 0.85 + random.nextDouble() * 0.3;
        int finalDamage = (int) Math.round(baseDamage * variance);
        finalDamage = Math.max(1, finalDamage);

        target.takeDamage(finalDamage);
        return finalDamage;
    }

    // ── 死亡 ──────────────────────────────────

    @Override
    protected void onDeath() {
        alive = false;

        if (lootDropped) return;
        lootDropped = true;

        // 掉落背包中所有物品
        dropAllItems();
    }

    /**
     * 掉落所有携带物品到地面。
     */
    private void dropAllItems() {
        if (inventory == null || inventory.isEmpty()) return;

        // 尝试通过 GameWorld 获取 GroundItemManager
        GroundItemManager groundItemManager = getGroundItemManager();
        if (groundItemManager == null) return;

        for (ItemStack stack : inventory.getItems()) {
            groundItemManager.dropItem(stack, tileX, tileY);
        }

        if (!inventory.isEmpty()) {
            GameLog.getInstance().log(
                    String.format("%s 死亡后掉落了 %d 件物品", name, inventory.getItemCount()));
        }
    }

    /**
     * 获取地面物品管理器（通过 GameWorld 全局实例访问）。
     */
    private GroundItemManager getGroundItemManager() {
        GameWorld world = GameWorld.getInstance();
        return world != null ? world.getGroundItemManager() : null;
    }

    // ── 交互 ──────────────────────────────────

    /**
     * 与玩家开始对话。
     */
    public void startTalk(Player player) {
        ai.enterStateForInteraction(NpcAIState.TALK);
        social.recordInteraction(getCurrentGameSeconds());
    }

    /**
     * 与玩家开始交易。
     */
    public void startTrade(Player player) {
        ai.enterStateForInteraction(NpcAIState.TRADE);
        social.recordInteraction(getCurrentGameSeconds());
    }

    /**
     * 结束当前交互。
     */
    public void endInteraction() {
        ai.endInteraction();
    }

    /**
     * 获取观察信息。
     *
     * @return 描述 NPC 的文本
     */
    public String getObservationText() {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(name).append("】\n");
        sb.append("类型: ").append(getTypeDisplayName()).append("\n");
        sb.append("地域: ").append(region.name).append("\n");
        sb.append("态度: ").append(getAttitudeDescription()).append("\n");

        if (definition != null && definition.infoText != null) {
            sb.append("信息: ").append(definition.infoText);
        }

        return sb.toString();
    }

    /**
     * 获取对话内容。
     *
     * @return 对话行列表
     */
    public List<String> getDialogLines() {
        if (definition != null && definition.dialogLines != null
                && !definition.dialogLines.isEmpty()) {
            return definition.dialogLines;
        }
        return getDefaultDialogLines();
    }

    /**
     * 获取默认对话内容。
     */
    private List<String> getDefaultDialogLines() {
        return switch (npcType) {
            case FRIENDLY -> List.of(
                    "你好，旅行者。需要帮忙吗？",
                    "这一带最近不太太平。",
                    "小心北边的森林，那里有危险。"
            );
            case NEUTRAL -> List.of(
                    "……",
                    "你有什么事？",
                    "我只是路过。"
            );
            case HOSTILE -> List.of(
                    "你最好赶紧离开这里。",
                    "这片地盘是我的。",
                    "哼，又一个送死的。"
            );
            case FUNCTIONAL -> List.of(
                    "欢迎来到这里，让我来帮助你。",
                    "你需要了解什么？",
                    "往前走有你要找的东西。"
            );
        };
    }

    // ── 渲染 ──────────────────────────────────

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

    // ── 辅助方法 ──────────────────────────────────

    /**
     * 获取 NPC 类型显示名称。
     */
    public String getTypeDisplayName() {
        if (definition != null && definition.name != null) {
            return definition.name;
        }
        return switch (npcType) {
            case FRIENDLY -> "村民";
            case NEUTRAL -> "路人";
            case HOSTILE -> "敌人";
            case FUNCTIONAL -> "向导";
        };
    }

    /**
     * 获取态度描述文本。
     */
    public String getAttitudeDescription() {
        int attitude = social.getAttitudeToPlayer();
        if (attitude >= 80) return "非常友好";
        if (attitude >= 60) return "友好";
        if (attitude >= 40) return "中立";
        if (attitude >= 20) return "警惕";
        return "敌对";
    }

    /**
     * 获取当前游戏时间（秒）。
     * 通过 GameWorld 全局实例获取。
     */
    private long getCurrentGameSeconds() {
        GameWorld world = GameWorld.getInstance();
        return world != null ? world.getGameTime().getTotalSeconds() : -1;
    }

    // ── 访问器 ──────────────────────────────────

    public String getName() { return name; }
    public NpcType getNpcType() { return npcType; }
    public NpcRegion getRegion() { return region; }
    public NpcSocial getSocial() { return social; }
    public NpcInventory getInventory() { return inventory; }
    public NpcDefinition getDefinition() { return definition; }
    public NpcAI getAi() { return ai; }
    public boolean isLootDropped() { return lootDropped; }
}
