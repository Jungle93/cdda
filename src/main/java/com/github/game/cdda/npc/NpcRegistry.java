package com.github.game.cdda.npc;

import com.github.game.cdda.item.model.ItemDefinition;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.registry.ItemRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NPC 模板注册表。
 * 静态单例，管理所有 NPC 定义模板。
 */
public class NpcRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NpcRegistry.class);

    /** 按 ID 索引 */
    private static final Map<String, NpcDefinition> BY_ID = new LinkedHashMap<>();

    /**
     * 注册 NPC 定义。
     */
    public static void register(NpcDefinition definition) {
        if (definition == null || definition.id == null) return;
        BY_ID.put(definition.id, definition);
        logger.debug("注册 NPC 定义: {}", definition.id);
    }

    /**
     * 根据 ID 获取 NPC 定义。
     */
    public static NpcDefinition get(String id) {
        return BY_ID.get(id);
    }

    /**
     * 获取所有已注册的 NPC 定义。
     */
    public static Collection<NpcDefinition> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * 清空注册表（测试用）。
     */
    public static void clear() {
        BY_ID.clear();
    }

    /**
     * 注册内置默认 NPC 定义。
     * 在游戏初始化时调用。
     */
    public static void loadDefaults() {
        // 确保物品注册表已加载
        ItemRegistry.loadAll();

        // 商人 — 带各种商品
        NpcDefinition merchant = new NpcDefinition();
        merchant.id = "merchant";
        merchant.name = "商人";
        merchant.type = NpcType.FRIENDLY;
        merchant.speed = 90;
        merchant.equipment = new ArrayList<>();
        merchant.tradeGoods = new ArrayList<>();
        addTradeGood(merchant, "canned_food", 5);
        addTradeGood(merchant, "water_bottle", 3);
        addTradeGood(merchant, "fiber_cord", 4);
        addTradeGood(merchant, "rusty_knife", 1);
        addTradeGood(merchant, "barley_seed", 6);
        merchant.dialogLines = List.of(
                "欢迎来到我的小店！想买点什么？",
                "这些都是我精心准备的货物。",
                "最近路不太平，生意不好做啊。"
        );
        merchant.infoText = "一个走南闯北的商人，背包里装着各种商品。";
        register(merchant);

        // 守卫 — 带武器和急救物品
        NpcDefinition guard = new NpcDefinition();
        guard.id = "guard";
        guard.name = "守卫";
        guard.type = NpcType.FRIENDLY;
        guard.speed = 100;
        guard.equipment = new ArrayList<>();
        guard.tradeGoods = new ArrayList<>();
        addTradeGood(guard, "canned_food", 2);
        addTradeGood(guard, "water_bottle", 1);
        guard.dialogLines = List.of(
                "这片区域很安全，放心走。",
                "北边最近有些不太平，小心点。",
                "有事找村长，他在村子中央。"
        );
        guard.infoText = "负责保护这片区域的守卫，看起来很强壮。";
        register(guard);

        // 土匪 — 带抢来的物资
        NpcDefinition bandit = new NpcDefinition();
        bandit.id = "bandit";
        bandit.name = "土匪";
        bandit.type = NpcType.HOSTILE;
        bandit.speed = 110;
        bandit.equipment = new ArrayList<>();
        bandit.tradeGoods = new ArrayList<>();
        addTradeGood(bandit, "canned_food", 1);
        addTradeGood(bandit, "small_branch", 3);
        bandit.dialogLines = List.of(
                "交出你的东西，饶你不死！",
                "哼，又是一个倒霉蛋。"
        );
        bandit.infoText = "一个凶神恶煞的土匪，身上带着武器。";
        register(bandit);

        // 向导 — 带地图和工具
        NpcDefinition guide = new NpcDefinition();
        guide.id = "guide";
        guide.name = "向导";
        guide.type = NpcType.FUNCTIONAL;
        guide.speed = 100;
        guide.equipment = new ArrayList<>();
        guide.tradeGoods = new ArrayList<>();
        addTradeGood(guide, "water_bottle", 2);
        addTradeGood(guide, "canned_food", 2);
        guide.dialogLines = List.of(
                "欢迎来到这里！让我来为你介绍。",
                "往东边走有片森林，里面资源丰富。",
                "如果你需要补给，可以去北边的营地。"
        );
        guide.infoText = "一位经验丰富的向导，对周围地形了如指掌。";
        register(guide);

        // 村民（通用）— 带少量食物
        NpcDefinition villager = new NpcDefinition();
        villager.id = "villager";
        villager.name = "村民";
        villager.type = NpcType.FRIENDLY;
        villager.speed = 80;
        villager.equipment = new ArrayList<>();
        villager.tradeGoods = new ArrayList<>();
        addTradeGood(villager, "canned_food", 1);
        addTradeGood(villager, "barley_seed", 3);
        villager.dialogLines = List.of(
                "你好啊，外乡人。",
                "今天天气不错。",
                "你是从哪来的？"
        );
        villager.infoText = "一个普通的村民，过着平静的生活。";
        register(villager);

        // 路人（通用）— 几乎没东西
        NpcDefinition wanderer = new NpcDefinition();
        wanderer.id = "wanderer";
        wanderer.name = "路人";
        wanderer.type = NpcType.NEUTRAL;
        wanderer.speed = 100;
        wanderer.equipment = new ArrayList<>();
        wanderer.tradeGoods = new ArrayList<>();
        addTradeGood(wanderer, "small_branch", 1);
        wanderer.dialogLines = List.of(
                "……",
                "我在赶路。",
                "别挡路。"
        );
        wanderer.infoText = "一个匆匆赶路的旅人，看起来不想被打扰。";
        register(wanderer);

        logger.info("NPC 注册表加载完成，共 {} 个模板", BY_ID.size());
    }

    /**
     * 向 NPC 的贸易商品列表添加物品。
     * 自动处理堆叠限制，超出 maxStackSize 时拆分为多个条目。
     */
    private static void addTradeGood(NpcDefinition npc, String itemId, int count) {
        ItemType type = ItemRegistry.getByName(itemId);
        if (type == null) {
            logger.warn("NPC 贸易物品 '{}' 不存在", itemId);
            return;
        }

        int maxStack = type.getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            int stackCount = Math.min(remaining, maxStack);
            npc.tradeGoods.add(new ItemStack(type, stackCount));
            remaining -= stackCount;
        }
    }
}
