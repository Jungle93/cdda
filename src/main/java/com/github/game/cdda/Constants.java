package com.github.game.cdda;

/**
 * 游戏全局常量。所有魔法数字集中管理。
 */
public final class Constants {

    private Constants() {}

    // ── 窗口 ──────────────────────────────────────
    /** 窗口宽度（像素） */
    public static final int SCREEN_WIDTH = 600;
    /** 窗口高度（像素） */
    public static final int SCREEN_HEIGHT = 400;

    // ── 布局 ──────────────────────────────────────
    /** 右侧信息面板宽度（像素） */
    public static final int INFO_PANEL_WIDTH = 180;
    /** 游戏区域宽度（像素）= 窗口宽度 - 信息面板宽度 */
    public static final int GAME_VIEWPORT_WIDTH = SCREEN_WIDTH - INFO_PANEL_WIDTH;

    // ── 地图 ──────────────────────────────────────
    /** 地图列数（瓦片数）— 已废弃，改为 CHUNK_SIZE */
    @Deprecated
    public static final int MAP_COLS = 64;
    /** 地图行数（瓦片数）— 已废弃，改为 CHUNK_SIZE */
    @Deprecated
    public static final int MAP_ROWS = 64;

    // ── 区块 ──────────────────────────────────────
    /** 区块大小（瓦片数） */
    public static final int CHUNK_SIZE = 64;
    /** 默认预加载半径（以区块为单位） */
    public static final int DEFAULT_PRELOAD_RADIUS = 1;

    // ── 区域 ──────────────────────────────────────
    /** 区域大小（以区块为单位） */
    public static final int REGION_CHUNK_SIZE = 4;

    // ── 玩家 ──────────────────────────────────────
    // 注：玩家速度已迁移到时间系统常量 ENTITY_DEFAULT_SPEED

    // ── 时间系统 ──────────────────────────────────────
    /** 默认实体速度（= 正常步行，映射 1.2 m/s） */
    public static final int ENTITY_DEFAULT_SPEED = 100;
    /** 移动一步的基础时间（游戏秒）。speed=100时每步=10游戏秒 */
    public static final int MOVE_BASE_TIME = 10;
    /** 每次行动消耗的能量点数 */
    public static final int ENERGY_PER_ACTION = 100;

    // ── 温度系统 ──────────────────────────────────────
    /** 日内温度波动振幅（°C）。14:00 峰值+amplitude，02:00 谷值-amplitude */
    public static final double TEMP_DAILY_AMPLITUDE = 3.0;
    /** 随机漂移更新间隔（游戏分钟）。每 N 分钟重新选择漂移目标 */
    public static final int TEMP_DRIFT_UPDATE_INTERVAL_MINUTES = 10;
    /** 随机漂移范围（°C）。漂移值在 [-range, +range] 内 */
    public static final double TEMP_DRIFT_RANGE = 2.0;
    /** 漂移平滑时间常数（游戏秒）。越大漂移越慢，600=10游戏分钟趋于稳定 */
    public static final double TEMP_DRIFT_SMOOTH_SECONDS = 600.0;

    // ── 物品系统 ──────────────────────────────────────
    /** 每点力量的携带量（克）。strength × 此值 = 最大携带重量 */
    public static final int CARRY_PER_STRENGTH = 5000;
    /** 拾取动作的基础时间（游戏秒） */
    public static final int PICKUP_BASE_TIME = 50;
    /** 丢弃动作的基础时间（游戏秒） */
    public static final int DROP_BASE_TIME = 30;
    /** 地面物品显示字符 */
    public static final char GROUND_ITEM_CHAR = '~';
    /** 拾取动作的脱水倍率 */
    public static final double ADD_THIRST_PICKUP = 1.0;
    /** 进食/饮水动作的基础时间（游戏秒） */
    public static final int EAT_BASE_TIME = 50;
    /** 药品默认回复量（HP） */
    public static final int MEDICINE_HEAL_AMOUNT = 20;

    // ── 代谢系统 ──────────────────────────────────────
    /** 最大能量储备（cal）。≈ 2500 kcal × 1000倍率，约2.5天基础消耗 */
    public static final int CALORIE_MAX_POOL = 2_500_000;
    /** 初始能量百分比（0.0~1.0）。游戏开始时不饿但也不满 */
    public static final double CALORIE_INITIAL_PERCENT = 0.8;
    /** 基础代谢率（cal/游戏秒）。28 × 86400 ≈ 2,419,200 cal/天 */
    public static final int BASAL_METABOLISM_RATE = 28;
    /** 舒适区温度下限（°C）。在此范围内无额外环境代偿消耗 */
    public static final double COMFORT_TEMP_MIN = 18.0;
    /** 舒适区温度上限（°C） */
    public static final double COMFORT_TEMP_MAX = 25.0;
    /** 环境代偿系数（cal/秒/°C）。偏离舒适区每°C的额外消耗 */
    public static final double ENV_COMPENSATION_RATE = 5.0;
    /** 移动一步消耗的热量（cal） */
    public static final int MOVE_CALORIE_COST = 500;
    /** 正常体温（°C） */
    public static final double NORMAL_BODY_TEMP = 37.0;
    /** 体温漂移速率（°C/秒）。能量耗尽时体温向环境漂移的速度 */
    public static final double BODY_TEMP_DRIFT_RATE = 0.0002;
    /** 体温缓冲最大值。有能量时用于延缓体温偏离 */
    public static final int BODY_TEMP_BUFFER_MAX = 5000;
    /** 等待一回合的基础时间（游戏秒）。≈ 10步的时间 */
    public static final int WAIT_BASE_TIME = 100;

    // ── 口渴系统 ──────────────────────────────────────
    /** 最大水分值。100% = 完全水合 */
    public static final int WATER_MAX = 10000;
    /** 初始水分百分比（0.0~1.0）。游戏开始时水分充足 */
    public static final double WATER_INITIAL_PERCENT = 1.0;
    /**
     * 基础水分流失率（单位/游戏秒）。
     * 模拟呼吸、无感蒸发和排尿。
     * 10000 / 0.03 / 86400 ≈ 3.86 游戏天完全脱水（舒适温度/静息状态）。
     */
    public static final double WATER_BASE_DRAIN_RATE = 0.03;
    /** 高温脱水倍率（每超舒适区1°C增加的倍率）。25°C以上，每度+15% */
    public static final double TEMP_THIRST_MULTIPLIER = 0.15;
    /** 低温脱水倍率（每低舒适区1°C增加的倍率）。18°C以下，每度+3%（呼吸失水） */
    public static final double COLD_THIRST_MULTIPLIER = 0.03;
    /** 温度脱水倍率上限（防止极端温度瞬间脱水） */
    public static final double TEMP_THIRST_MAX_MULTIPLIER = 3.0;
    /** 水分缓冲最大值（水分充足时恢复，脱水时消耗） */
    public static final double WATER_BUFFER_MAX = 2000;
    /**
     * 动作脱水倍率 — 站立/等待（1.0x 基础流失）
     */
    public static final double ADD_THIRST_IDLE = 1.0;
    /**
     * 动作脱水倍率 — 行走（1.5x 基础流失，轻度运动）
     */
    public static final double ADD_THIRST_WALK = 1.5;
    /**
     * 动作脱水倍率 — 跑步（2.0x，中度运动）
     */
    public static final double ADD_THIRST_RUN = 2.0;
    /**
     * 动作脱水倍率 — 战斗/负重（3.0x，剧烈运动）
     */
    public static final double ADD_THIRST_COMBAT = 3.0;

    // ── 调试信息 ──────────────────────────────────────
    /** 是否显示调试信息叠加层（总开关） */
    public static boolean SHOW_DEBUG_INFO = true;
    /** 显示玩家所在瓦片坐标 */
    public static boolean DEBUG_SHOW_TILE_POS = true;
    /** 显示摄像机世界坐标 */
    public static boolean DEBUG_SHOW_CAMERA = true;
    /** 显示已加载区块数 */
    public static boolean DEBUG_SHOW_CHUNK_COUNT = true;
    /** 显示 FPS */
    public static boolean DEBUG_SHOW_FPS = true;
    /** 显示环境温度和季节 */
    public static boolean DEBUG_SHOW_TEMPERATURE = true;
}
