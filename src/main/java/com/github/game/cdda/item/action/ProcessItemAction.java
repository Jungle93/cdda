package com.github.game.cdda.item.action;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.crafting.ProcessingRecipe;
import com.github.game.cdda.crafting.RecipeRegistry;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.registry.ItemRegistry;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.log.GameLog;

import java.util.List;

/**
 * 物品加工动作。
 *
 * <p>将原材料加工为成品。根据 {@link RecipeRegistry} 中的配方，
 * 消耗输入物品，生成输出物品。某些配方需要特定工具（如斧头）。
 *
 * <p>执行流程：
 * <ol>
 *   <li>查找当前物品的所有可用配方</li>
 *   <li>选择第一个有效配方（有输入物品 + 有所需工具）</li>
 *   <li>消耗输入物品，生成输出物品到背包</li>
 *   <li>消耗加工时间</li>
 * </ol>
 *
 * <p>绑定标签："processing"。
 */
public class ProcessItemAction implements ItemAction {

    @Override
    public String getName() {
        return "加工";
    }

    @Override
    public String getDescription() {
        return "将原材料加工为成品";
    }

    @Override
    public boolean canExecute(Player player, GameWorld world) {
        // 检查是否有任何可用配方（ simplistic: 只检查是否有配方，不检查工具）
        // 实际上 execute 中会再做完整检查
        return true;
    }

    @Override
    public void execute(Player player, GameWorld world, ItemStack material) {
        if (material == null || material.isEmpty()) {
            GameLog.getInstance().log("没有可加工的物品");
            return;
        }

        String materialId = material.getType().getName();
        List<ProcessingRecipe> recipes = RecipeRegistry.getRecipesFor(materialId);

        if (recipes.isEmpty()) {
            GameLog.getInstance().log("没有可用的加工配方");
            return;
        }

        // 查找第一个有效配方（输入数量足够 + 有所需工具 + 有额外材料）
        ProcessingRecipe selectedRecipe = null;
        for (ProcessingRecipe recipe : recipes) {
            if (material.getCount() < recipe.inputCount) continue;
            if (!hasRequiredTool(player, recipe)) continue;
            if (!hasAdditionalInputs(player, recipe)) continue;
            selectedRecipe = recipe;
            break;
        }

        if (selectedRecipe == null) {
            GameLog.getInstance().log("无法加工：缺少材料或工具");
            return;
        }

        // 消耗输入物品
        player.getInventory().removeItem(
                player.getInventory().getItems().indexOf(material),
                selectedRecipe.inputCount);

        // 消耗额外输入物品
        if (selectedRecipe.additionalInputs != null) {
            for (ProcessingRecipe.Output extra : selectedRecipe.additionalInputs) {
                consumeItemFromInventory(player, extra.itemId, extra.count);
            }
        }

        // 生成输出物品
        for (ProcessingRecipe.Output output : selectedRecipe.outputs) {
            ItemType outputType = ItemRegistry.getByName(output.itemId);
            if (outputType == null) continue;

            ItemStack outputStack = new ItemStack(outputType, output.count);
            if (player.getInventory().addItem(outputStack)) {
                // 成功添加
            } else {
                GameLog.getInstance().log("背包已满，部分物品无法放入");
            }
        }

        // 消耗加工时间
        long timeCost = selectedRecipe.processingTime > 0
                ? selectedRecipe.processingTime
                : Constants.CRAFT_BASE_TIME;
        world.getTurnManager().addAction(player, timeCost);
        world.getTurnManager().processRound();

        GameLog.getInstance().log(String.format("加工完成：%s → %s",
                selectedRecipe.name, formatOutputs(selectedRecipe)));
    }

    /**
     * 检查玩家是否拥有配方所需的工具。
     */
    private boolean hasRequiredTool(Player player, ProcessingRecipe recipe) {
        if (recipe.toolRequired == null) return true;

        // 检查背包中是否有带有所需标签的物品
        for (ItemStack stack : player.getInventory().getItems()) {
            if (stack != null && stack.getType().getTags().contains(recipe.toolRequired)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查玩家是否拥有配方的额外输入材料。
     */
    private boolean hasAdditionalInputs(Player player, ProcessingRecipe recipe) {
        if (recipe.additionalInputs == null || recipe.additionalInputs.isEmpty()) return true;

        for (ProcessingRecipe.Output extra : recipe.additionalInputs) {
            if (countItemInInventory(player, extra.itemId) < extra.count) {
                return false;
            }
        }
        return true;
    }

    /**
     * 统计背包中指定物品的总数量。
     */
    private int countItemInInventory(Player player, String itemId) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getItems()) {
            if (stack != null && stack.getType().getName().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 从背包中消耗指定数量的物品。
     */
    private void consumeItemFromInventory(Player player, String itemId, int count) {
        List<ItemStack> items = player.getInventory().getItems();
        int remaining = count;
        for (int i = items.size() - 1; i >= 0 && remaining > 0; i--) {
            ItemStack stack = items.get(i);
            if (stack == null || !stack.getType().getName().equals(itemId)) continue;
            int take = Math.min(stack.getCount(), remaining);
            player.getInventory().removeItem(i, take);
            remaining -= take;
        }
    }

    /**
     * 格式化输出物品描述。
     */
    private String formatOutputs(ProcessingRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recipe.outputs.size(); i++) {
            if (i > 0) sb.append(", ");
            ProcessingRecipe.Output out = recipe.outputs.get(i);
            ItemType type = ItemRegistry.getByName(out.itemId);
            String name = (type != null) ? type.getName() : out.itemId;
            sb.append(out.count).append("x ").append(name);
        }
        return sb.toString();
    }
}
