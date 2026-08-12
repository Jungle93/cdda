package com.github.game.cdda.item.model;

/**
 * 物品堆叠实例。
 * 持有 ItemType 引用和当前数量。运行时对象，可序列化到存档。
 */
public class ItemStack {

    /** 物品类型 */
    private final ItemType type;
    /** 当前数量 */
    private int count;

    /**
     * 创建物品堆叠。
     *
     * @param type  物品类型（不能为 null）
     * @param count 数量（必须 &gt; 0，且 &lt;= type.maxStackSize）
     */
    public ItemStack(ItemType type, int count) {
        if (type == null) throw new IllegalArgumentException("type 不能为 null");
        if (count < 1) throw new IllegalArgumentException("count 至少为 1");
        if (count > type.getMaxStackSize()) {
            throw new IllegalArgumentException(
                    "count " + count + " 超过最大堆叠 " + type.getMaxStackSize());
        }
        this.type = type;
        this.count = count;
    }

    /**
     * 创建单个物品的便捷工厂方法。
     *
     * @param type 物品类型（不能为 null）
     * @return 数量为 1 的物品堆
     */
    public static ItemStack single(ItemType type) {
        return new ItemStack(type, 1);
    }

    // ── 堆叠操作 ──────────────────────────────────

    /**
     * 能否与另一个堆叠合并。
     * 条件：同类型 + 非唯一 + 合并后不超限。
     */
    public boolean canMerge(ItemStack other) {
        if (other == null) return false;
        if (this.type.isUnique() || other.type.isUnique()) return false;
        if (this.type != other.type) return false;
        return this.count + other.count <= this.type.getMaxStackSize();
    }

    /**
     * 尝试合并另一个堆叠到此堆叠。
     *
     * @return 合并后剩余的数量（0 = 全部合入）
     */
    public int merge(ItemStack other) {
        if (!canMerge(other)) return other.count;
        int space = type.getMaxStackSize() - count;
        int toAdd = Math.min(other.count, space);
        count += toAdd;
        return other.count - toAdd;
    }

    /** 增加数量（不超过上限） */
    public void addCount(int amount) {
        count = Math.min(count + amount, type.getMaxStackSize());
    }

    /** 减少数量 */
    public void removeCount(int amount) {
        count = Math.max(count - amount, 0);
    }

    /** 是否已耗尽 */
    public boolean isEmpty() { return count <= 0; }

    // ── 属性查询（代理到 ItemType） ──
    public ItemType getType() { return type; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    /** 总重量 = 单件重量 × 数量（克） */
    public double getTotalWeightGrams() { return type.getWeightGrams() * count; }

    /** 总体积 = 单件体积 × 数量（毫升） */
    public double getTotalVolumeMl() { return type.getVolumeMl() * count; }

    /** 总热量 = 单件热量 × 数量（千卡） */
    public double getTotalCalories() { return type.getCalories() * count; }

    /** 总饱腹度 = 单件饱腹度 × 数量 */
    public double getTotalSatiety() { return type.getSatiety() * count; }

    /** 总含水量 = 单件含水量 × 数量（毫升） */
    public double getTotalWaterContent() { return type.getWaterContent() * count; }

    /** 格式化总重量 */
    public String formatTotalWeight(DisplayUnit unit) {
        return unit.format(getTotalWeightGrams());
    }

    /** 格式化总体积 */
    public String formatTotalVolume(DisplayUnit unit) {
        return unit.format(getTotalVolumeMl());
    }

    @Override
    public String toString() {
        return "ItemStack{" + type.getName() + " x" + count + "}";
    }
}
