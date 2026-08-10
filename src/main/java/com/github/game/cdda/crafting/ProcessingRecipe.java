package com.github.game.cdda.crafting;

import java.util.List;

/**
 * 加工配方定义。
 *
 * <p>描述一个物品加工配方：输入物品、所需工具、输出物品。
 * 例如：原木 + 斧头 → 4 木板
 *
 * <p>配方通过 JSON 文件定义，由 {@link RecipeRegistry} 加载。
 */
public class ProcessingRecipe {

    /** 配方 ID（如 "oak_log_to_planks"） */
    public String id;

    /** 显示名称（如 "制作橡木板"） */
    public String name;

    /** 输入物品 ID（如 "oak_log"） */
    public String inputItemId;

    /** 输入物品数量 */
    public int inputCount;

    /** 所需工具标签（如 "chopping"），null 表示不需要工具 */
    public String toolRequired;

    /** 加工时间（游戏秒） */
    public int processingTime;

    /** 输出物品列表 */
    public List<Output> outputs;

    /**
     * 输出物品定义。
     */
    public static class Output {
        /** 物品 ID */
        public String itemId;

        /** 数量 */
        public int count;

        public Output() {}

        public Output(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    public ProcessingRecipe() {}

    /**
     * 获取配方的描述文本。
     *
     * @return 如 "1x 橡木原木 → 4x 橡木板"
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(inputCount).append("x ").append(inputItemId);
        if (toolRequired != null) {
            sb.append(" + ").append(toolRequired);
        }
        sb.append(" → ");
        for (int i = 0; i < outputs.size(); i++) {
            if (i > 0) sb.append(", ");
            Output out = outputs.get(i);
            sb.append(out.count).append("x ").append(out.itemId);
        }
        return sb.toString();
    }
}
