package com.github.game.cdda.item;

/**
 * 显示单位枚举。
 * 内部存储一律使用公制基准（质量=克，体积=毫升），
 * 显示时按玩家偏好转换为对应单位。
 */
public enum DisplayUnit {

    // ── 质量 ──
    /** 克（基准） */
    GRAM("g", 1.0, UnitKind.MASS),
    /** 千克 */
    KILOGRAM("kg", 1000.0, UnitKind.MASS),
    /** 盎司 */
    OUNCE("oz", 28.3495, UnitKind.MASS),
    /** 磅 */
    POUND("lb", 453.592, UnitKind.MASS),

    // ── 体积 ──
    /** 毫升（基准） */
    MILLILITER("mL", 1.0, UnitKind.VOLUME),
    /** 升 */
    LITER("L", 1000.0, UnitKind.VOLUME),
    /** 液量盎司 */
    FLUID_OUNCE("fl oz", 29.5735, UnitKind.VOLUME);

    /** 单位符号（UI 显示） */
    private final String symbol;
    /** 1 个本单位 = 多少基准单位 */
    private final double toBaseFactor;
    /** 单位类别 */
    private final UnitKind kind;

    /** 单位类别（质量/体积） */
    public enum UnitKind { MASS, VOLUME }

    DisplayUnit(String symbol, double toBaseFactor, UnitKind kind) {
        this.symbol = symbol;
        this.toBaseFactor = toBaseFactor;
        this.kind = kind;
    }

    public String getSymbol() { return symbol; }
    public UnitKind getKind() { return kind; }

    /** 基准值 → 显示值 */
    public double fromBase(double baseValue) {
        return baseValue / toBaseFactor;
    }

    /** 显示值 → 基准值 */
    public double toBase(double displayValue) {
        return displayValue * toBaseFactor;
    }

    /**
     * 格式化输出。
     * 示例：GRAM.format(1500) → "1.50 kg"（如果切换到 KILOGRAM）
     */
    public String format(double baseValue) {
        double displayValue = fromBase(baseValue);
        // 大数值用整数，小数值保留两位小数
        if (displayValue >= 100) {
            return String.format("%.0f %s", displayValue, symbol);
        } else {
            return String.format("%.2f %s", displayValue, symbol);
        }
    }

    /** 同 kind 的质量单位组，用于设置界面循环切换 */
    public static DisplayUnit[] massUnits() {
        return new DisplayUnit[]{ GRAM, KILOGRAM, OUNCE, POUND };
    }

    /** 同 kind 的体积单位组，用于设置界面循环切换 */
    public static DisplayUnit[] volumeUnits() {
        return new DisplayUnit[]{ MILLILITER, LITER, FLUID_OUNCE };
    }
}
