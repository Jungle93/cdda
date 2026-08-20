package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.creature.Player;
import com.github.game.cdda.crafting.ProcessingRecipe;
import com.github.game.cdda.crafting.RecipeRegistry;
import com.github.game.cdda.game.TurnManager;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.registry.ItemRegistry;
import com.github.game.cdda.item.world.PlayerInventory;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 制作界面 — 总览式空手制作菜单。
 *
 * <p>显示所有已知配方，按分类（武器/工具/材料/食物/其他）分组展示。
 * 玩家可浏览配方、查看详情、执行制作。
 *
 * <p>操作流程：
 * <ol>
 *   <li>RECIPE_LIST — ↑↓ 选择配方，←→ 切换分类，Enter 进入详情</li>
 *   <li>RECIPE_DETAIL — 显示材料/工具需求，Enter 执行制作，Esc 返回列表</li>
 * </ol>
 */
public class CraftingScreen extends MenuScreen {

    private static final String TITLE = "制作";

    /** 分类定义：{key, displayName} */
    private static final String[][] CATEGORIES = {
            {"all", "全部"}, {"weapon", "武器"}, {"tool", "工具"},
            {"material", "材料"}, {"food", "食物"}, {"misc", "其他"}
    };

    private enum State { RECIPE_LIST, RECIPE_DETAIL }

    private final Player player;
    private final PlayerInventory inventory;
    private final TurnManager turnManager;

    /** 所有配方（全量） */
    private List<ProcessingRecipe> allRecipes;
    /** 当前分类过滤后的配方 */
    private List<ProcessingRecipe> filteredRecipes;
    /** 当前分类 Tab 索引 */
    private int currentCategory = 0;
    /** 当前界面状态 */
    private State state = State.RECIPE_LIST;

    /**
     * 创建制作界面。
     *
     * @param engine 游戏引擎
     * @param world  游戏世界（用于获取玩家、背包、回合管理器）
     */
    public CraftingScreen(GameEngine engine, GameWorld world) {
        super(engine);
        this.player = world.getPlayer();
        this.inventory = player.getInventory();
        this.turnManager = world.getTurnManager();
        refreshRecipes();
    }

    // ── 数据刷新 ──────────────────────────────────────

    /** 从 RecipeRegistry 加载全部配方并按当前分类过滤 */
    private void refreshRecipes() {
        allRecipes = new ArrayList<>(RecipeRegistry.getAll());
        applyFilter();
    }

    /** 按当前分类过滤配方（可制作的排在前面，其次是接近可制作的） */
    private void applyFilter() {
        String categoryKey = CATEGORIES[currentCategory][0];
        List<ProcessingRecipe> matched = new ArrayList<>();
        for (ProcessingRecipe recipe : allRecipes) {
            if ("all".equals(categoryKey) || categoryKey.equals(recipe.category)) {
                matched.add(recipe);
            }
        }
        // 排序：READY → NEARLY → MISSING，同类按名称排序
        matched.sort((a, b) -> {
            CraftStatus sa = getCraftStatus(a);
            CraftStatus sb = getCraftStatus(b);
            if (sa != sb) return sa.ordinal() - sb.ordinal();
            return a.name.compareTo(b.name);
        });
        filteredRecipes = matched;
        // 调整选中索引，防止越界
        if (selectedIndex >= filteredRecipes.size()) {
            selectedIndex = Math.max(0, filteredRecipes.size() - 1);
        }
    }

    /** 统计指定分类下的配方数量 */
    private int countInCategory(String categoryKey) {
        if ("all".equals(categoryKey)) return allRecipes.size();
        int count = 0;
        for (ProcessingRecipe r : allRecipes) {
            if (categoryKey.equals(r.category)) count++;
        }
        return count;
    }

    // ── MenuScreen 抽象方法实现 ──────────────────────────

    @Override
    protected int getItemCount() {
        return filteredRecipes.size();
    }

    @Override
    protected void onSelect(int index) {
        if (state == State.RECIPE_LIST) {
            if (!filteredRecipes.isEmpty()) {
                state = State.RECIPE_DETAIL;
            }
        } else if (state == State.RECIPE_DETAIL) {
            executeCraft(selectedIndex);
        }
    }

    @Override
    protected void onAdjust(int index, int direction) {
        if (state != State.RECIPE_LIST) return;
        currentCategory = (currentCategory + direction + CATEGORIES.length) % CATEGORIES.length;
        applyFilter();
        selectedIndex = 0;
    }

    @Override
    protected void onCancel() {
        if (state == State.RECIPE_DETAIL) {
            state = State.RECIPE_LIST;
        } else {
            engine.getScreenManager().popScreen();
        }
    }

    /**
     * 处理按键输入。
     *
     * <p>RECIPE_DETAIL 状态下：Esc 返回列表，Enter 执行制作。
     * 其他情况委托给 {@link MenuScreen} 的默认导航处理。
     */
    @Override
    public void onKeyPressed(int keyCode) {
        // RECIPE_DETAIL 状态下 Esc 返回列表（而非直接关闭）
        if (state == State.RECIPE_DETAIL && keyCode == KeyEvent.VK_ESCAPE) {
            state = State.RECIPE_LIST;
            return;
        }
        // RECIPE_DETAIL 状态下 Enter 执行制作
        if (state == State.RECIPE_DETAIL && keyCode == KeyEvent.VK_ENTER) {
            executeCraft(selectedIndex);
            return;
        }
        // 其他情况走默认导航（↑↓←→/Enter/Esc）
        super.onKeyPressed(keyCode);
    }

    // ── 制作判定 ──────────────────────────────────────

    /** 配方制作状态 */
    private enum CraftStatus {
        /** 材料齐全 */
        READY,
        /** 缺失非关键材料（额外输入） */
        NEARLY,
        /** 缺失关键材料（主输入/工具） */
        MISSING;

        /** 列表显示颜色 */
        Color getListColor(boolean selected) {
            if (selected) return Color.YELLOW;
            return switch (this) {
                case READY -> new Color(100, 220, 100);
                case NEARLY -> new Color(220, 200, 60);
                case MISSING -> new Color(220, 100, 100);
            };
        }

        /** 状态文本 */
        String getLabel() {
            return switch (this) {
                case READY -> "可制作";
                case NEARLY -> "材料不足";
                case MISSING -> "缺关键材料";
            };
        }
    }

    /** 计算配方的制作状态 */
    private CraftStatus getCraftStatus(ProcessingRecipe recipe) {
        boolean mainOk = countItemInInventory(recipe.inputItemId) >= recipe.inputCount;
        boolean toolOk = recipe.toolRequired == null || hasToolWithTag(recipe.toolRequired);

        // 关键材料缺失 → RED
        if (!mainOk || !toolOk) return CraftStatus.MISSING;

        // 检查额外输入
        if (recipe.additionalInputs != null) {
            for (ProcessingRecipe.Output extra : recipe.additionalInputs) {
                if (countItemInInventory(extra.itemId) < extra.count) {
                    return CraftStatus.NEARLY; // 非关键材料缺失 → YELLOW
                }
            }
        }
        return CraftStatus.READY;
    }

    /** 检查配方是否可制作 */
    private boolean canCraft(ProcessingRecipe recipe) {
        return getCraftStatus(recipe) == CraftStatus.READY;
    }

    /** 获取不可制作的原因描述（列出所有缺失材料） */
    private String getCraftFailureReason(ProcessingRecipe recipe) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        java.util.List<String> missingExtra = new java.util.ArrayList<>();

        // 主输入
        ItemType inputType = ItemRegistry.getByName(recipe.inputItemId);
        String inputName = inputType != null ? inputType.getDisplayName() : recipe.inputItemId;
        int haveMain = countItemInInventory(recipe.inputItemId);
        if (haveMain < recipe.inputCount) {
            missing.add(inputName + "(缺" + (recipe.inputCount - haveMain) + ")");
        }

        // 额外输入
        if (recipe.additionalInputs != null) {
            for (ProcessingRecipe.Output extra : recipe.additionalInputs) {
                ItemType extraType = ItemRegistry.getByName(extra.itemId);
                String extraName = extraType != null ? extraType.getDisplayName() : extra.itemId;
                int extraHave = countItemInInventory(extra.itemId);
                if (extraHave < extra.count) {
                    missingExtra.add(extraName + "(缺" + (extra.count - extraHave) + ")");
                }
            }
        }

        // 工具
        if (recipe.toolRequired != null && !hasToolWithTag(recipe.toolRequired)) {
            missing.add("工具[" + recipe.toolRequired + "]");
        }

        if (missing.isEmpty() && missingExtra.isEmpty()) return "";

        // 关键缺失优先显示
        if (!missing.isEmpty()) {
            return "缺: " + String.join(", ", missing);
        }
        return "缺: " + String.join(", ", missingExtra);
    }

    /** 执行制作 */
    private void executeCraft(int index) {
        if (index < 0 || index >= filteredRecipes.size()) return;
        ProcessingRecipe recipe = filteredRecipes.get(index);
        if (!canCraft(recipe)) {
            GameLog.getInstance().log("材料不足，无法制作: " + recipe.name);
            return;
        }

        // 消耗主输入
        consumeItem(recipe.inputItemId, recipe.inputCount);

        // 消耗额外输入
        if (recipe.additionalInputs != null) {
            for (ProcessingRecipe.Output extra : recipe.additionalInputs) {
                consumeItem(extra.itemId, extra.count);
            }
        }

        // 产出
        for (ProcessingRecipe.Output output : recipe.outputs) {
            ItemType outputType = ItemRegistry.getByName(output.itemId);
            if (outputType != null) {
                ItemStack outputStack = new ItemStack(outputType, output.count);
                if (!inventory.addItem(outputStack)) {
                    GameLog.getInstance().log("背包已满，部分物品丢失");
                }
            }
        }

        // 消耗时间
        long timeCost = recipe.processingTime > 0 ? recipe.processingTime : Constants.CRAFT_BASE_TIME;
        turnManager.addAction(player, timeCost);
        turnManager.processRound();

        GameLog.getInstance().log(String.format("制作完成: %s", recipe.name));

        // 刷新列表（材料变化可能影响可制作状态）
        refreshRecipes();
        state = State.RECIPE_LIST;
    }

    // ── 背包工具方法 ──────────────────────────────────

    /** 统计背包中指定物品的总数量 */
    private int countItemInInventory(String itemId) {
        int total = 0;
        for (ItemStack stack : inventory.getItems()) {
            if (stack != null && stack.getType().getName().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 检查背包中是否有带指定标签的物品 */
    private boolean hasToolWithTag(String tag) {
        for (ItemStack stack : inventory.getItems()) {
            if (stack != null && stack.getType().hasTag(tag)) {
                return true;
            }
        }
        return false;
    }

    /** 从背包中消耗指定数量的物品 */
    private void consumeItem(String itemId, int count) {
        List<ItemStack> items = inventory.getItems();
        int remaining = count;
        for (int i = items.size() - 1; i >= 0 && remaining > 0; i--) {
            ItemStack stack = items.get(i);
            if (stack == null || !stack.getType().getName().equals(itemId)) continue;
            int take = Math.min(stack.getCount(), remaining);
            inventory.removeItem(i, take);
            remaining -= take;
        }
    }

    // ── 渲染 ──────────────────────────────────────────

    @Override
    protected void renderMenu(Renderer renderer) {
        // 暗色背景
        renderer.setColor(new Color(10, 10, 20, 240));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int height = getHeight();

        // 标题
        drawTitle(renderer, TITLE, 24, height / 7);

        if (state == State.RECIPE_LIST) {
            renderRecipeList(renderer, width, height);
        } else {
            renderRecipeDetail(renderer, width, height);
        }
    }

    /** 渲染配方列表（含 Tab 栏） */
    private void renderRecipeList(Renderer renderer, int width, int height) {
        // Tab 栏
        int tabY = height / 7 + 20;
        int tabFontSize = 12;
        renderer.setFont(new Font("Monospaced", Font.PLAIN, tabFontSize));
        int tabX = 30;
        int tabSpacing = 8;
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean active = (i == currentCategory);
            String label = CATEGORIES[i][1];
            int count = countInCategory(CATEGORIES[i][0]);
            renderer.setColor(active ? new Color(255, 200, 60) : Color.DARK_GRAY);
            String display;
            if (active) {
                display = String.format("[%s(%d)]", label, count);
            } else {
                display = String.format(" %s(%d) ", label, count);
            }
            renderer.drawText(display, tabX, tabY);
            tabX += renderer.getTextWidth(display) + tabSpacing;
        }

        // 分隔线
        int separatorY = tabY + 12;
        renderer.setColor(new Color(60, 60, 80));
        renderer.drawLine(30, separatorY, width - 30, separatorY);

        if (filteredRecipes.isEmpty()) {
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
            renderer.setColor(Color.GRAY);
            String msg = "该分类下暂无配方";
            renderer.drawText(msg, (width - renderer.getTextWidth(msg)) / 2, height / 2);
            drawHintBar(renderer, "←→ 切换分类 | Esc 返回");
            return;
        }

        // 配方列表
        int listStartY = separatorY + 18;
        int itemHeight = 36;
        int maxVisible = (height - listStartY - 80) / itemHeight;

        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        for (int i = 0; i < filteredRecipes.size(); i++) {
            int vi = i - scrollOffset;
            if (vi < 0 || vi >= maxVisible) continue;

            ProcessingRecipe recipe = filteredRecipes.get(i);
            boolean sel = (i == selectedIndex);
            CraftStatus status = getCraftStatus(recipe);

            // 选中高亮
            if (sel) {
                renderer.setColor(new Color(50, 50, 0, 120));
                renderer.fillRect(20, listStartY + vi * itemHeight - 16, width - 40, itemHeight);
            }

            renderer.setFont(new Font("Monospaced", Font.PLAIN, 13));
            String prefix = sel ? "▶ " : "  ";

            // 配方名称 — 三级颜色
            renderer.setColor(status.getListColor(sel));
            renderer.drawText(prefix + recipe.name, 30, listStartY + vi * itemHeight);

            // 右侧状态标记
            String statusText = "[" + status.getLabel() + "]";
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
            Color rightColor = sel ? status.getListColor(true) : switch (status) {
                case READY -> new Color(80, 160, 80);
                case NEARLY -> new Color(160, 140, 50);
                case MISSING -> new Color(160, 80, 80);
            };
            renderer.setColor(rightColor);
            renderer.drawText(statusText, width - 30 - renderer.getTextWidth(statusText),
                    listStartY + vi * itemHeight);

            // 材料简述
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
            String materials = buildMaterialSummary(recipe);
            renderer.setColor(sel ? new Color(180, 220, 255) : Color.DARK_GRAY);
            renderer.drawText(materials, 50, listStartY + vi * itemHeight + 15);
        }

        // 底部提示
        drawHintBar(renderer, "↑↓ 选择 | ←→ 分类 | Enter 详情 | Esc 返回");
    }

    /** 渲染配方详情 */
    private void renderRecipeDetail(Renderer renderer, int width, int height) {
        if (filteredRecipes.isEmpty()) return;
        ProcessingRecipe recipe = filteredRecipes.get(selectedIndex);
        boolean craftable = canCraft(recipe);

        // 面板区域
        int panelX = 40;
        int panelY = height / 5;
        int panelW = width - 80;
        int panelH = height * 3 / 5;
        renderer.setColor(new Color(20, 20, 40, 200));
        renderer.fillRect(panelX, panelY, panelW, panelH);
        renderer.setColor(new Color(80, 80, 120));
        renderer.drawRect(panelX, panelY, panelW, panelH);

        int textX = panelX + 20;
        int textY = panelY + 30;
        int lineGap = 22;

        // 配方名称
        renderer.setFont(new Font("Monospaced", Font.BOLD, 16));
        renderer.setColor(Color.WHITE);
        renderer.drawText(recipe.name, textX, textY);
        textY += lineGap + 8;

        // 分隔线
        renderer.setColor(new Color(60, 60, 80));
        renderer.drawLine(textX, textY - 4, panelX + panelW - 20, textY - 4);
        textY += 6;

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // 主输入材料
        ItemType inputType = ItemRegistry.getByName(recipe.inputItemId);
        String inputName = inputType != null ? inputType.getDisplayName() : recipe.inputItemId;
        int have = countItemInInventory(recipe.inputItemId);
        boolean enough = have >= recipe.inputCount;
        renderer.setColor(enough ? new Color(180, 255, 180) : new Color(255, 150, 150));
        renderer.drawText(String.format("输入: %dx %s (拥有: %d)", recipe.inputCount, inputName, have),
                textX, textY);
        textY += lineGap;

        // 额外输入材料
        if (recipe.additionalInputs != null) {
            for (ProcessingRecipe.Output extra : recipe.additionalInputs) {
                ItemType extraType = ItemRegistry.getByName(extra.itemId);
                String extraName = extraType != null ? extraType.getDisplayName() : extra.itemId;
                int extraHave = countItemInInventory(extra.itemId);
                boolean extraEnough = extraHave >= extra.count;
                renderer.setColor(extraEnough ? new Color(180, 255, 180) : new Color(255, 150, 150));
                renderer.drawText(String.format("     + %dx %s (拥有: %d)", extra.count, extraName, extraHave),
                        textX, textY);
                textY += lineGap;
            }
        }

        // 工具需求
        textY += 4;
        if (recipe.toolRequired != null) {
            boolean hasTool = hasToolWithTag(recipe.toolRequired);
            renderer.setColor(hasTool ? new Color(180, 255, 180) : new Color(255, 150, 150));
            renderer.drawText(String.format("工具: 需要 [%s] 标签 (%s)",
                    recipe.toolRequired, hasTool ? "已具备" : "未具备"), textX, textY);
        } else {
            renderer.setColor(Color.LIGHT_GRAY);
            renderer.drawText("工具: 无需", textX, textY);
        }
        textY += lineGap;

        // 产出
        textY += 4;
        renderer.setColor(new Color(180, 220, 255));
        StringBuilder outStr = new StringBuilder("产出: ");
        for (int i = 0; i < recipe.outputs.size(); i++) {
            if (i > 0) outStr.append(", ");
            ProcessingRecipe.Output out = recipe.outputs.get(i);
            ItemType outType = ItemRegistry.getByName(out.itemId);
            String outName = outType != null ? outType.getDisplayName() : out.itemId;
            outStr.append(out.count).append("x ").append(outName);
        }
        renderer.drawText(outStr.toString(), textX, textY);
        textY += lineGap;

        // 制作状态
        textY += 8;
        renderer.setFont(new Font("Monospaced", Font.BOLD, 14));
        if (craftable) {
            renderer.setColor(new Color(100, 255, 100));
            renderer.drawText("✓ 可制作 — Enter 确认", textX, textY);
        } else {
            renderer.setColor(new Color(255, 100, 100));
            renderer.drawText("✗ " + getCraftFailureReason(recipe), textX, textY);
        }

        // 底部提示
        drawHintBar(renderer, "Enter 制作 | Esc 返回列表");
    }

    /** 构建材料简述（用于列表中的材料行） */
    private String buildMaterialSummary(ProcessingRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        ItemType inputType = ItemRegistry.getByName(recipe.inputItemId);
        String inputName = inputType != null ? inputType.getDisplayName() : recipe.inputItemId;
        sb.append(recipe.inputCount).append("x ").append(inputName);

        if (recipe.additionalInputs != null) {
            for (ProcessingRecipe.Output extra : recipe.additionalInputs) {
                ItemType extraType = ItemRegistry.getByName(extra.itemId);
                String extraName = extraType != null ? extraType.getDisplayName() : extra.itemId;
                sb.append(" + ").append(extra.count).append("x ").append(extraName);
            }
        }

        if (recipe.toolRequired != null) {
            sb.append("  需要: ").append(recipe.toolRequired);
        }

        return sb.toString();
    }
}
