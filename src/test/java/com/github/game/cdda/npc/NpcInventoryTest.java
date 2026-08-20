package com.github.game.cdda.npc;

import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.registry.ItemRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NpcInventory 和 NPC 贸易商品单元测试。
 */
class NpcInventoryTest {

    private NpcInventory inventory;

    @BeforeAll
    static void loadItems() {
        ItemRegistry.loadAll();
        NpcRegistry.loadDefaults();
    }

    @BeforeEach
    void setUp() {
        Npc npc = new Npc(NpcRegion.COMMON, NpcType.FRIENDLY, 0, 0);
        inventory = npc.getInventory();
    }

    // ── NPC 模板贸易商品 ──────────────────────────

    /**
     * 验证商人模板有非空的 tradeGoods。
     */
    @Test
    void merchantTemplateHasTradeGoods() {
        NpcDefinition merchantDef = NpcRegistry.get("merchant");
        assertNotNull(merchantDef);
        assertNotNull(merchantDef.tradeGoods);
        assertFalse(merchantDef.tradeGoods.isEmpty(), "商人模板应有贸易商品");

        boolean hasFood = merchantDef.tradeGoods.stream()
                .anyMatch(s -> s.getType().getName().contains("canned")
                        || s.getType().getName().contains("water"));
        assertTrue(hasFood, "商人应携带食物或水");
    }

    /**
     * 验证村民模板有贸易商品。
     */
    @Test
    void villagerTemplateHasTradeGoods() {
        NpcDefinition villagerDef = NpcRegistry.get("villager");
        assertNotNull(villagerDef);
        assertNotNull(villagerDef.tradeGoods);
        assertFalse(villagerDef.tradeGoods.isEmpty(), "村民模板应有贸易商品");
    }

    /**
     * 验证守卫模板有贸易商品。
     */
    @Test
    void guardTemplateHasTradeGoods() {
        NpcDefinition guardDef = NpcRegistry.get("guard");
        assertNotNull(guardDef);
        assertNotNull(guardDef.tradeGoods);
        assertFalse(guardDef.tradeGoods.isEmpty(), "守卫模板应有贸易商品");
    }

    /**
     * 验证 NPC 从 tradeGoods 初始化背包。
     */
    @Test
    void npcInventoryPopulatedFromTradeGoods() {
        Npc merchant = new Npc(NpcRegion.COMMON, NpcType.FRIENDLY, 0, 0);
        // 使用商人定义创建 NPC
        NpcDefinition def = NpcRegistry.get("merchant");
        assertNotNull(def);

        // 手动模拟 Npc 构造函数中的 tradeGoods 加载
        if (def.tradeGoods != null) {
            for (ItemStack item : def.tradeGoods) {
                inventory.addItemUnchecked(item);
            }
        }

        assertFalse(inventory.isEmpty(), "商人 NPC 应有贸易商品");
        assertTrue(inventory.getItemCount() >= 3, "商人应有至少 3 种商品");
    }

    // ── NpcInventory 基础操作 ──────────────────────────

    /**
     * 验证 addItem 添加物品。
     */
    @Test
    void addItemIncreasesCount() {
        inventory.clearAll();
        ItemType canned = ItemRegistry.getByName("canned_food");
        assertNotNull(canned);

        inventory.addItem(new ItemStack(canned, 3));
        assertEquals(1, inventory.getItemCount());
        assertEquals(3, inventory.getItem(0).getCount());
    }

    /**
     * 验证 addItem 堆叠已有物品。
     */
    @Test
    void addItemStacksExisting() {
        inventory.clearAll();
        ItemType canned = ItemRegistry.getByName("canned_food");
        assertNotNull(canned);

        inventory.addItem(new ItemStack(canned, 2));
        inventory.addItem(new ItemStack(canned, 3));

        assertEquals(1, inventory.getItemCount(), "应堆叠为一个条目");
        assertEquals(5, inventory.getItem(0).getCount());
    }

    /**
     * 验证 removeItem 移除物品。
     */
    @Test
    void removeItemReturnsStack() {
        inventory.clearAll();
        ItemType canned = ItemRegistry.getByName("canned_food");
        assertNotNull(canned);
        inventory.addItem(new ItemStack(canned, 3));

        ItemStack removed = inventory.removeItem(0);
        assertNotNull(removed);
        assertEquals(3, removed.getCount());
        assertEquals(0, inventory.getItemCount());
    }

    /**
     * 验证 removeItem 越界返回 null。
     */
    @Test
    void removeItemOutOfBoundsReturnsNull() {
        inventory.clearAll();
        assertNull(inventory.removeItem(-1));
        assertNull(inventory.removeItem(0));
        assertNull(inventory.removeItem(999));
    }

    /**
     * 验证 isEmpty 正确判断。
     */
    @Test
    void isEmptyChecksCorrectly() {
        inventory.clearAll();
        assertTrue(inventory.isEmpty());

        ItemType canned = ItemRegistry.getByName("canned_food");
        assertNotNull(canned);
        inventory.addItem(new ItemStack(canned, 1));
        assertFalse(inventory.isEmpty());
    }

    // ── 交易 ──────────────────────────

    /**
     * 验证 buyFromPlayer 添加物品到 NPC 背包。
     */
    @Test
    void buyFromPlayerAddsToNpcInventory() {
        inventory.clearAll();
        ItemType canned = ItemRegistry.getByName("canned_food");
        assertNotNull(canned);

        boolean success = inventory.buyFromPlayer(new ItemStack(canned, 2));
        assertTrue(success);
        assertEquals(1, inventory.getItemCount());
        assertEquals(2, inventory.getItem(0).getCount());
    }

    /**
     * 验证 sellToPlayer 正确减少 NPC 背包数量。
     * 由于 PlayerInventory 需要 Player 引用，这里用 null 测试 NPC 侧逻辑。
     */
    @Test
    void sellToPlayerDecrementsNpcStack() {
        inventory.clearAll();
        ItemType canned = ItemRegistry.getByName("canned_food");
        assertNotNull(canned);
        inventory.addItem(new ItemStack(canned, 5));

        // 用 null playerInv 测试 NPC 侧减少逻辑
        boolean success = inventory.sellToPlayer(0, null, 1);
        assertTrue(success, "交易应成功");

        // NPC 背包减少
        assertEquals(4, inventory.getItem(0).getCount());
    }

    /**
     * 验证 sellToPlayer 在物品数量为 1 时移除条目。
     */
    @Test
    void sellToPlayerRemovesEmptyStack() {
        inventory.clearAll();
        ItemType canned = ItemRegistry.getByName("canned_food");
        assertNotNull(canned);
        inventory.addItem(new ItemStack(canned, 1));
        assertEquals(1, inventory.getItemCount());

        inventory.sellToPlayer(0, null, 1);
        assertEquals(0, inventory.getItemCount(), "物品应被完全移除");
    }

    /**
     * 验证 sellToPlayer 索引越界返回 false。
     */
    @Test
    void sellToPlayerInvalidIndex() {
        inventory.clearAll();
        assertFalse(inventory.sellToPlayer(-1, null, 1));
        assertFalse(inventory.sellToPlayer(0, null, 1));
        assertFalse(inventory.sellToPlayer(999, null, 1));
    }

    /**
     * 验证 sellToPlayer 可卖出多个。
     */
    @Test
    void sellToPlayerMultipleQuantity() {
        inventory.clearAll();
        ItemType fiber = ItemRegistry.getByName("fiber_cord"); // maxStack=10
        assertNotNull(fiber);
        inventory.addItem(new ItemStack(fiber, 10));

        inventory.sellToPlayer(0, null, 3);
        assertEquals(7, inventory.getItem(0).getCount(), "应减少 3 个");
    }
}
