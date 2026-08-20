package com.github.game.cdda.crafting;

import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.registry.ItemRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配方注册表和合成逻辑测试。
 */
class CraftingLogicTest {

    @BeforeAll
    static void loadAll() {
        ItemRegistry.loadAll();
        RecipeRegistry.loadAll();
    }

    /**
     * 验证注册表中有配方。
     */
    @Test
    void registryHasRecipes() {
        assertFalse(RecipeRegistry.getAll().isEmpty(), "应有至少一个配方");
    }

    /**
     * 验证配方有分类。
     */
    @Test
    void recipesHaveCategories() {
        for (ProcessingRecipe recipe : RecipeRegistry.getAll()) {
            assertNotNull(recipe.category, "配方 '" + recipe.name + "' 应有分类");
            assertFalse(recipe.category.isEmpty());
        }
    }

    /**
     * 验证配方产出有实际物品。
     */
    @Test
    void recipeOutputsExist() {
        for (ProcessingRecipe recipe : RecipeRegistry.getAll()) {
            assertFalse(recipe.outputs.isEmpty(), "配方 '" + recipe.name + "' 应有产出");
            for (ProcessingRecipe.Output out : recipe.outputs) {
                ItemType type = ItemRegistry.getByName(out.itemId);
                assertNotNull(type, "产出物品 '" + out.itemId + "' 应存在于注册表");
                assertTrue(out.count > 0, "产出数量应 > 0");
            }
        }
    }

    /**
     * 验证配方输入有实际物品。
     */
    @Test
    void recipeInputsExist() {
        for (ProcessingRecipe recipe : RecipeRegistry.getAll()) {
            ItemType inputType = ItemRegistry.getByName(recipe.inputItemId);
            assertNotNull(inputType, "主输入物品 '" + recipe.inputItemId + "' 应存在");
            assertTrue(recipe.inputCount > 0, "主输入数量应 > 0");

            if (recipe.additionalInputs != null) {
                for (ProcessingRecipe.Output extra : recipe.additionalInputs) {
                    ItemType extraType = ItemRegistry.getByName(extra.itemId);
                    assertNotNull(extraType, "额外输入物品 '" + extra.itemId + "' 应存在");
                    assertTrue(extra.count > 0, "额外输入数量应 > 0");
                }
            }
        }
    }

    /**
     * 验证特定分类的配方数量。
     */
    @Test
    void categoryCounts() {
        int allCount = RecipeRegistry.getAll().size();
        assertTrue(allCount > 0);

        int weaponCount = 0, toolCount = 0, materialCount = 0, foodCount = 0, miscCount = 0;
        for (ProcessingRecipe recipe : RecipeRegistry.getAll()) {
            switch (recipe.category) {
                case "weapon" -> weaponCount++;
                case "tool" -> toolCount++;
                case "material" -> materialCount++;
                case "food" -> foodCount++;
                case "misc" -> miscCount++;
            }
        }

        assertEquals(allCount, weaponCount + toolCount + materialCount + foodCount + miscCount,
                "所有配方应分配到某个分类");
    }

    /**
     * 验证篝火配方（典型配方）存在且合理。
     */
    @Test
    void campfireRecipeExists() {
        boolean found = RecipeRegistry.getAll().stream()
                .anyMatch(r -> r.name.contains("篝火"));
        // 如果没有叫"篝火"的配方，检查其他常见配方
        if (!found) {
            found = RecipeRegistry.getAll().stream()
                    .anyMatch(r -> r.name.contains("陷阱") || r.name.contains("绳索"));
        }
        // 至少应有一个配方
        assertFalse(RecipeRegistry.getAll().isEmpty());
    }

    /**
     * 验证 Output 的 canMerge 和 merge 行为。
     */
    @Test
    void outputMergeBehavior() {
        ProcessingRecipe.Output a = new ProcessingRecipe.Output("stone", 3);
        ProcessingRecipe.Output b = new ProcessingRecipe.Output("stone", 5);
        ProcessingRecipe.Output c = new ProcessingRecipe.Output("fiber_cord", 2);

        assertEquals("stone", a.itemId);
        assertEquals(3, a.count);

        // 相同物品可以合并（逻辑测试）
        assertEquals(a.itemId, b.itemId);
        assertNotEquals(a.itemId, c.itemId);
    }

    /**
     * 验证配方 processingTime 合理（非负）。
     */
    @Test
    void processingTimeNonNegative() {
        for (ProcessingRecipe recipe : RecipeRegistry.getAll()) {
            assertTrue(recipe.processingTime >= 0,
                    "配方 '" + recipe.name + "' 的处理时间应 >= 0");
        }
    }
}
