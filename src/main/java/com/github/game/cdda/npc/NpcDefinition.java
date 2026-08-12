package com.github.game.cdda.npc;

import com.github.game.cdda.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 定义（模板数据）。
 * 描述一种 NPC 类型的公共属性，由 {@link NpcRegistry} 从 JSON 或直接注册。
 */
public class NpcDefinition {

    /** 唯一标识符 */
    public String id;

    /** 显示名称（通用称呼，如"商人"、"守卫"） */
    public String name;

    /** NPC 类型 */
    public NpcType type;

    /** 基础速度 */
    public int speed;

    /** 起始装备列表 */
    public List<EquipmentSlot> equipment;

    /** 商人库存（仅商人 NPC） */
    public List<ItemStack> tradeGoods;

    /** 对话内容列表 */
    public List<String> dialogLines;

    /** 信息内容（观察时显示） */
    public String infoText;

    /**
     * 装备槽位。
     */
    public static class EquipmentSlot {
        /** 装备位置（"weapon", "armor", "helmet", "shield" 等） */
        public String slot;
        /** 物品 ID */
        public String itemId;

        public EquipmentSlot() {}

        public EquipmentSlot(String slot, String itemId) {
            this.slot = slot;
            this.itemId = itemId;
        }
    }

    /**
     * 创建默认 NPC 定义。
     */
    public static NpcDefinition createDefault(NpcType type) {
        NpcDefinition def = new NpcDefinition();
        def.id = "default_" + type.name().toLowerCase();
        def.name = type == NpcType.FRIENDLY ? "村民" :
                   type == NpcType.HOSTILE ? "土匪" :
                   type == NpcType.FUNCTIONAL ? "向导" : "路人";
        def.type = type;
        def.speed = 100;
        def.equipment = new ArrayList<>();
        def.tradeGoods = new ArrayList<>();
        def.dialogLines = new ArrayList<>();
        return def;
    }
}
