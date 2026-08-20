package com.github.game.cdda.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 起始装备包单元测试。
 */
class StartingPackageTest {

    /**
     * 验证所有起始包的物品 ID 在 ItemRegistry 中存在。
     */
    @Test
    void allItemIdsExistInRegistry() {
        com.github.game.cdda.item.registry.ItemRegistry.loadAll();

        for (StartingPackage pkg : StartingPackage.values()) {
            if (pkg == StartingPackage.NONE) continue;
            for (StartingPackage.ItemEntry entry : pkg.getItems()) {
                assertNotNull(
                        com.github.game.cdda.item.registry.ItemRegistry.getByName(entry.itemId),
                        "物品 '" + entry.itemId + "' 在起始包 " + pkg.getDisplayName() + " 中不存在于注册表");
            }
        }
    }

    /**
     * 验证 NONE 包无物品。
     */
    @Test
    void nonePackageHasNoItems() {
        assertEquals(0, StartingPackage.NONE.getItems().length);
        assertEquals("无", StartingPackage.NONE.getDisplayName());
    }

    /**
     * 验证 EXPLORER 包含基础物品。
     */
    @Test
    void explorerPackageHasBasics() {
        StartingPackage.ItemEntry[] items = StartingPackage.EXPLORER.getItems();
        assertTrue(items.length >= 3, "探索者包至少应有 3 件物品");
        assertTrue(hasItem(items, "rusty_knife"), "探索者包应包含小刀");
        assertTrue(hasItem(items, "canned_food"), "探索者包应包含罐头食物");
        assertTrue(hasItem(items, "water_bottle"), "探索者包应包含水瓶");
    }

    /**
     * 验证 FARMER 包含种子。
     */
    @Test
    void farmerPackageHasSeeds() {
        StartingPackage.ItemEntry[] items = StartingPackage.FARMER.getItems();
        assertTrue(hasItem(items, "barley_seed"), "农夫包应包含大麦种子");
        assertTrue(hasItem(items, "turnip_seed"), "农夫包应包含芜菁种子");
        assertTrue(hasItem(items, "bean_seed"), "农夫包应包含豆子种子");
    }

    /**
     * 验证 HUNTER 包含武器和狩猎工具。
     */
    @Test
    void hunterPackageHasWeapons() {
        StartingPackage.ItemEntry[] items = StartingPackage.HUNTER.getItems();
        assertTrue(hasItem(items, "rusty_knife"), "猎人包应包含小刀");
        assertTrue(hasItem(items, "stone_axe"), "猎人包应包含石斧");
        assertTrue(hasItem(items, "fiber_cord"), "猎人包应包含绳索");
    }

    /**
     * 验证 CRAFTER 包含大量基础材料。
     */
    @Test
    void crafterPackageHasMaterials() {
        StartingPackage.ItemEntry[] items = StartingPackage.CRAFTER.getItems();
        assertTrue(hasItem(items, "small_branch"), "工匠包应包含树枝");
        assertTrue(hasItem(items, "stone"), "工匠包应包含石头");
        assertTrue(hasItem(items, "fiber_cord"), "工匠包应包含绳索");

        // 验证材料数量较多
        StartingPackage.ItemEntry branches = findItem(items, "small_branch");
        assertNotNull(branches);
        assertTrue(branches.count >= 5, "工匠包应至少有 5 个树枝");
    }

    /**
     * 验证 getAvailable 返回除 NONE 外的所有包。
     */
    @Test
    void getAvailableExcludesNone() {
        StartingPackage[] available = StartingPackage.getAvailable();
        for (StartingPackage pkg : available) {
            assertNotEquals(StartingPackage.NONE, pkg, "getAvailable 不应包含 NONE");
        }
        assertEquals(StartingPackage.values().length - 1, available.length);
    }

    /**
     * 验证 cycle 循环切换。
     */
    @Test
    void cycleWrapsAround() {
        StartingPackage first = StartingPackage.NONE;
        StartingPackage next = first.cycle(1);
        assertNotEquals(first, next);

        // 循环一圈应回到原点
        StartingPackage current = first;
        for (int i = 0; i < StartingPackage.values().length; i++) {
            current = current.cycle(1);
        }
        assertEquals(first, current, "循环一周应回到原点");

        // 反向循环也应回到原点
        current = first;
        for (int i = 0; i < StartingPackage.values().length; i++) {
            current = current.cycle(-1);
        }
        assertEquals(first, current, "反向循环一周应回到原点");
    }

    /**
     * 验证显示名称非空。
     */
    @Test
    void allDisplayNamesNonNull() {
        for (StartingPackage pkg : StartingPackage.values()) {
            assertNotNull(pkg.getDisplayName());
            assertFalse(pkg.getDisplayName().isEmpty());
        }
    }

    /**
     * CharacterSettings 起始包循环切换测试。
     */
    @Test
    void characterSettingsCycleStartingPackage() {
        CharacterSettings settings = new CharacterSettings();
        StartingPackage original = settings.getStartingPackage();
        assertNotNull(original);

        settings.cycleStartingPackage(1);
        assertNotEquals(original, settings.getStartingPackage());

        // 循环回到原点
        settings.cycleStartingPackage(-1);
        assertEquals(original, settings.getStartingPackage());
    }

    // ── 辅助方法 ──────────────────────────

    private boolean hasItem(StartingPackage.ItemEntry[] items, String itemId) {
        for (StartingPackage.ItemEntry entry : items) {
            if (entry.itemId.equals(itemId)) return true;
        }
        return false;
    }

    private StartingPackage.ItemEntry findItem(StartingPackage.ItemEntry[] items, String itemId) {
        for (StartingPackage.ItemEntry entry : items) {
            if (entry.itemId.equals(itemId)) return entry;
        }
        return null;
    }
}
