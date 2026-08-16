package com.github.game.cdda.game;

/**
 * 植物生长系统常量配置。
 *
 * <p>定义植物生长阶段、肥力需求、传播概率、肥力恢复等参数。
 * 所有数值参照现实世界植物生长速度（按游戏时间换算）。
 *
 * <h3>时间换算基准：</h3>
 * <ul>
 *   <li>1 游戏秒 ≈ 1 现实秒（30 游戏帧 = 1 游戏秒）</li>
 *   <li>1 游戏日 = 24 游戏小时 = 24 游戏秒（≈ 现实 24 秒）</li>
 * </ul>
 *
 * <h3>生长阶段时长（游戏日）：</h3>
 * <pre>
 *   植物类型    幼苗期   生长期   成熟期    现实对应
 *   草/苔藓     2日     3日     持续     数天~数周
 *   花          3日     5日     持续     数周
 *   灌木        7日    15日    180日     数月~数年
 *   树木       15日    30日    365日     数年~数十年
 * </pre>
 */
public final class PlantGrowthConstants {

    private PlantGrowthConstants() {}

    // ════════════════════════════════════════════════════════
    // 生长阶段时长（游戏日）
    // ════════════════════════════════════════════════════════

    /** 草/苔藓：幼苗期天数（现实：1~3 周） */
    public static final int GRASS_SEEDLING_DAYS = 2;
    /** 草/苔藓：生长期天数（现实：2~4 周） */
    public static final int GRASS_GROWING_DAYS = 3;
    /** 草/苔藓：成熟后枯萎天数（无足够肥力时） */
    public static final int GRASS_MATURE_LIFESPAN_DAYS = 30;

    /** 花：幼苗期天数（现实：2~4 周） */
    public static final int FLOWER_SEEDLING_DAYS = 3;
    /** 花：生长期天数（现实：4~8 周） */
    public static final int FLOWER_GROWING_DAYS = 5;
    /** 花：成熟后枯萎天数 */
    public static final int FLOWER_MATURE_LIFESPAN_DAYS = 45;

    /** 灌木：幼苗期天数（现实：数月） */
    public static final int SHRUB_SEEDLING_DAYS = 7;
    /** 灌木：生长期天数（现实：1~3 年） */
    public static final int SHRUB_GROWING_DAYS = 15;
    /** 灌木：成熟期天数（现实：5~20 年） */
    public static final int SHRUB_MATURE_LIFESPAN_DAYS = 180;

    /** 树木：幼苗期天数（现实：1~3 年） */
    public static final int TREE_SEEDLING_DAYS = 15;
    /** 树木：生长期天数（现实：5~15 年） */
    public static final int TREE_GROWING_DAYS = 30;
    /** 树木：成熟期天数（现实：数十年~百年） */
    public static final int TREE_MATURE_LIFESPAN_DAYS = 365;

    // ════════════════════════════════════════════════════════
    // 肥力需求（0~100）— 各阶段最低肥力
    // ════════════════════════════════════════════════════════

    /** 草/花/苔藓：发芽所需最低肥力 */
    public static final double HERB_GERMINATE_FERTILITY = 5.0;
    /** 草/花/苔藓：幼苗期所需最低肥力 */
    public static final double HERB_SEEDLING_FERTILITY = 5.0;
    /** 草/花/苔藓：生长期所需最低肥力 */
    public static final double HERB_GROWING_FERTILITY = 8.0;
    /** 草/花/苔藓：成熟期所需最低肥力 */
    public static final double HERB_MATURE_FERTILITY = 3.0;

    /** 灌木：发芽所需最低肥力 */
    public static final double SHRUB_GERMINATE_FERTILITY = 15.0;
    /** 灌木：幼苗期所需最低肥力 */
    public static final double SHRUB_SEEDLING_FERTILITY = 12.0;
    /** 灌木：生长期所需最低肥力 */
    public static final double SHRUB_GROWING_FERTILITY = 18.0;
    /** 灌木：成熟期所需最低肥力 */
    public static final double SHRUB_MATURE_FERTILITY = 8.0;

    /** 树木：发芽所需最低肥力 */
    public static final double TREE_GERMINATE_FERTILITY = 25.0;
    /** 树木：幼苗期所需最低肥力 */
    public static final double TREE_SEEDLING_FERTILITY = 20.0;
    /** 树木：生长期所需最低肥力 */
    public static final double TREE_GROWING_FERTILITY = 30.0;
    /** 树木：成熟期所需最低肥力 */
    public static final double TREE_MATURE_FERTILITY = 10.0;

    // ════════════════════════════════════════════════════════
    // 肥力消耗（每游戏日，单位：肥力点）
    // ════════════════════════════════════════════════════════

    /** 草/苔藓每日肥力消耗（很低） */
    public static final double HERB_DAILY_FERTILITY_COST = 0.3;
    /** 花每日肥力消耗（低） */
    public static final double FLOWER_DAILY_FERTILITY_COST = 0.4;
    /** 灌木每日肥力消耗（中等） */
    public static final double SHRUB_DAILY_FERTILITY_COST = 0.8;
    /** 树木每日肥力消耗（较高） */
    public static final double TREE_DAILY_FERTILITY_COST = 1.5;

    // ════════════════════════════════════════════════════════
    // 肥力恢复
    // ════════════════════════════════════════════════════════

    /** 无植被地块每日肥力恢复量（0~100） */
    public static final double DAILY_FERTILITY_RECOVERY = 0.15;
    /** 有植被地块每日肥力恢复量（植物腐殖质补充，较低） */
    public static final double VEGETATED_DAILY_FERTILITY_RECOVERY = 0.05;
    /** 枯萎植物地块每日肥力恢复量（枯萎植物分解，较高） */
    public static final double WITHERED_DAILY_FERTILITY_RECOVERY = 0.10;
    /** 肥力恢复上限（超过此值不再自然恢复，防止无限堆积） */
    public static final double FERTILITY_RECOVERY_CAP = 85.0;

    // ════════════════════════════════════════════════════════
    // 野生植物定殖
    // ════════════════════════════════════════════════════════

    /** 野生植物定殖所需最低肥力（0~100） */
    public static final double WILD_COLONIZE_FERTILITY_MIN = 30.0;
    /** 野生植物每游戏日定殖概率基础值（0~1） */
    public static final double WILD_COLONIZE_BASE_PROBABILITY = 0.002;
    /** 定殖检查最大半径（格） */
    public static final int COLONIZE_RADIUS = 3;

    // ════════════════════════════════════════════════════════
    // 植物传播（从相邻格扩散）
    // ════════════════════════════════════════════════════════

    /** 草每日传播概率基础值（0~1） */
    public static final double GRASS_SPREAD_PROBABILITY = 0.08;
    /** 灌木每日传播概率基础值 */
    public static final double SHRUB_SPREAD_PROBABILITY = 0.01;
    /** 树木每日传播概率基础值（种子掉落） */
    public static final double TREE_SPREAD_PROBABILITY = 0.005;

    /** 传播所需最低肥力（0~100） */
    public static final double SPREAD_FERTILITY_MIN = 10.0;

    // ════════════════════════════════════════════════════════
    // 系统运行参数
    // ════════════════════════════════════════════════════════

    /** 植物生长更新间隔（游戏小时）— 每 2 游戏小时更新一次 */
    public static final int GROWTH_UPDATE_INTERVAL_HOURS = 2;
    /** 肥力恢复更新间隔（游戏小时）— 每 4 游戏小时更新一次 */
    public static final int FERTILITY_UPDATE_INTERVAL_HOURS = 4;
    /** 野生定殖检查间隔（游戏小时）— 每 12 游戏小时检查一次 */
    public static final int COLONIZE_CHECK_INTERVAL_HOURS = 12;

    // ════════════════════════════════════════════════════════
    // 工具方法
    // ════════════════════════════════════════════════════════

    /**
     * 根据植物类型获取发芽所需最低肥力。
     */
    public static double getGerminateFertility(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_GERMINATE_FERTILITY;
            case "shrub" -> SHRUB_GERMINATE_FERTILITY;
            case "crop" -> 15.0; // 农作物发芽需中等肥力
            default -> HERB_GERMINATE_FERTILITY;
        };
    }

    /**
     * 根据植物类型获取幼苗期所需最低肥力。
     */
    public static double getSeedlingFertility(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_SEEDLING_FERTILITY;
            case "shrub" -> SHRUB_SEEDLING_FERTILITY;
            case "crop" -> 12.0; // 农作物幼苗需中等肥力
            default -> HERB_SEEDLING_FERTILITY;
        };
    }

    /**
     * 根据植物类型获取生长期所需最低肥力。
     */
    public static double getGrowingFertility(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_GROWING_FERTILITY;
            case "shrub" -> SHRUB_GROWING_FERTILITY;
            case "crop" -> 10.0;
            default -> HERB_GROWING_FERTILITY;
        };
    }

    /**
     * 根据植物类型获取成熟期所需最低肥力。
     */
    public static double getMatureFertility(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_MATURE_FERTILITY;
            case "shrub" -> SHRUB_MATURE_FERTILITY;
            case "crop" -> 8.0;
            default -> HERB_MATURE_FERTILITY;
        };
    }

    /**
     * 根据植物类型获取每日肥力消耗。
     */
    public static double getDailyFertilityCost(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_DAILY_FERTILITY_COST;
            case "shrub" -> SHRUB_DAILY_FERTILITY_COST;
            case "flower" -> FLOWER_DAILY_FERTILITY_COST;
            case "crop" -> 0.8; // 农作物每日消耗少量肥力
            default -> HERB_DAILY_FERTILITY_COST;
        };
    }

    /**
     * 根据植物类型获取幼苗期天数。
     */
    public static int getSeedlingDays(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_SEEDLING_DAYS;
            case "shrub" -> SHRUB_SEEDLING_DAYS;
            case "flower" -> FLOWER_SEEDLING_DAYS;
            case "crop" -> 6; // 农作物幼苗期约 6 天
            default -> GRASS_SEEDLING_DAYS;
        };
    }

    /**
     * 根据植物类型获取生长期天数。
     */
    public static int getGrowingDays(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_GROWING_DAYS;
            case "shrub" -> SHRUB_GROWING_DAYS;
            case "flower" -> FLOWER_GROWING_DAYS;
            case "crop" -> 17; // 农作物生长期约 17 天
            default -> GRASS_GROWING_DAYS;
        };
    }

    /**
     * 根据植物类型获取成熟期总天数（含幼苗期+生长期）。
     */
    public static int getMatureLifespanDays(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "tree" -> TREE_MATURE_LIFESPAN_DAYS;
            case "shrub" -> SHRUB_MATURE_LIFESPAN_DAYS;
            case "flower" -> FLOWER_MATURE_LIFESPAN_DAYS;
            case "crop" -> 100; // 农作物成熟后可存活约 100 天
            default -> GRASS_MATURE_LIFESPAN_DAYS;
        };
    }

    /**
     * 根据植物类型获取传播概率。
     */
    public static double getSpreadProbability(String plantType) {
        return switch (plantType.toLowerCase()) {
            case "shrub" -> SHRUB_SPREAD_PROBABILITY;
            case "tree" -> TREE_SPREAD_PROBABILITY;
            case "crop" -> 0.0; // 农作物不自然传播
            default -> GRASS_SPREAD_PROBABILITY;
        };
    }
}
