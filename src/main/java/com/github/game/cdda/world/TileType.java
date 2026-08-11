package com.github.game.cdda.world;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 地形类型注册表（替代枚举，支持 mod 扩展）。
 *
 * 每种地形类型具有：
 * - 稳定数字 ID（用于序列化/存档/mod 引用）
 * - 稳定字符串 name（用于配置文件/mod 查找）
 * - ASCII 渲染字符 + 颜色（当前皮肤，未来可替换为贴图）
 * - 通行性标记（是否可通过）
 *
 * 内置类型在静态初始化时注册。Mod 可通过 {@link #registerMod} 在运行时注册新类型。
 */
public class TileType {

    /** 数字 ID → TileType 注册表（有序） */
    private static final Map<Integer, TileType> REGISTRY = new LinkedHashMap<>();
    /** 字符串 name → TileType 注册表（有序） */
    private static final Map<String, TileType> NAME_REGISTRY = new LinkedHashMap<>();

    // ── 基础地形（地面层） ────────────────────────────
    /** 草地 — 默认可通行地形 */
    public static final TileType GRASS = register(0, "grass", '.', new Color(76, 180, 0), true);
    /** 泥土 */
    public static final TileType DIRT = register(1, "dirt", '#', new Color(160, 110, 60), true);
    /** 沙地 */
    public static final TileType SAND = register(2, "sand", ',', new Color(210, 185, 140), true);
    /** 水 — 不可通过 */
    public static final TileType WATER = register(3, "water", '~', new Color(60, 130, 220), false);
    /** 石头/岩石 — 不可通过 */
    public static final TileType STONE = register(4, "stone", '*', new Color(160, 160, 160), false);

    // ── 植物（地表物体） ──────────────────────────────
    /** 树木 — 不可通过 */
    public static final TileType TREE = register(5, "tree", '&', new Color(0, 120, 0), false);
    /** 灌木 — 不可通过 */
    public static final TileType BUSH = register(6, "bush", '%', new Color(0, 128, 0), false);
    /** 花 — 可通过 */
    public static final TileType FLOWER = register(7, "flower", '"', Color.MAGENTA, true);
    /** 高草 — 可通过 */
    public static final TileType TALL_GRASS = register(8, "tall_grass", ';', new Color(100, 200, 100), true);

    // ── 人为实体（后续建筑系统使用，暂不生成） ─────────
    /** 墙 — 不可通过 */
    public static final TileType WALL = register(9, "wall", '#', Color.LIGHT_GRAY, false);
    /** 栅栏 — 不可通过 */
    public static final TileType FENCE = register(10, "fence", '|', new Color(139, 69, 19), false);
    /** 门 — 可通过 */
    public static final TileType DOOR = register(11, "door", '+', new Color(160, 82, 45), true);
    /** 地板 — 可通过 */
    public static final TileType FLOOR = register(12, "floor", '.', Color.GRAY, true);

    // ── 水生植被 ──────────────────────────────────
    /** 芦苇/水生植物 — 不可通过，生长于水边 */
    public static final TileType REEDS = register(13, "reeds", '‖', new Color(60, 150, 60), false);

    // ── 过渡地形 ──────────────────────────────────
    /** 泥土地 — 可通过（平原/沼泽的常见地面） */
    public static final TileType MUD = register(14, "mud", ':', new Color(120, 100, 70), true);

    // ── 枯萎植物（肥力不足导致植物枯死后的残留） ────
    /** 枯树 — 不可通过（枯萎的树木残留） */
    public static final TileType WITHERED_TREE = register(15, "withered_tree", '†', new Color(139, 90, 43), false);
    /** 枯灌木 — 可通过（枯萎的灌木残留） */
    public static final TileType WITHERED_BUSH = register(16, "withered_bush", '·', new Color(128, 100, 60), true);
    /** 枯草 — 可通过（干枯的草/花/苔藓） */
    public static final TileType DEAD_GRASS = register(17, "dead_grass", ',', new Color(180, 160, 100), true);

    // ── 字段 ──────────────────────────────────────────

    /** 稳定数字标识 */
    private final int id;
    /** 稳定字符串标识 */
    private final String name;
    /** ASCII 渲染字符 */
    private final char ch;
    /** 渲染颜色 */
    private final Color color;
    /** 是否可通过 */
    private final boolean passable;

    private TileType(int id, String name, char ch, Color color, boolean passable) {
        this.id = id;
        this.name = name;
        this.ch = ch;
        this.color = color;
        this.passable = passable;
    }

    // ── 注册 API ──────────────────────────────────────

    /** 注册内置类型（静态初始化时调用） */
    private static TileType register(int id, String name, char ch, Color color, boolean passable) {
        TileType type = new TileType(id, name, ch, color, passable);
        REGISTRY.put(id, type);
        NAME_REGISTRY.put(name, type);
        return type;
    }

    /**
     * Mod 注册新地形类型（运行时调用）。
     *
     * @param id       数字 ID（不能与已有冲突）
     * @param name     字符串标识（不能与已有冲突）
     * @param ch       渲染字符
     * @param color    渲染颜色
     * @param passable 是否可通过
     * @return 注册的 TileType 实例
     * @throws IllegalArgumentException 如果 ID 或 name 已存在
     */
    public static TileType registerMod(int id, String name, char ch, Color color, boolean passable) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("TileType ID " + id + " 已被注册: " + REGISTRY.get(id).name);
        }
        if (NAME_REGISTRY.containsKey(name)) {
            throw new IllegalArgumentException("TileType name '" + name + "' 已被注册，ID: " + NAME_REGISTRY.get(name).id);
        }
        return register(id, name, ch, color, passable);
    }

    // ── 查询 API ──────────────────────────────────────

    /** 根据数字 ID 查找地形类型 */
    public static TileType getById(int id) {
        return REGISTRY.get(id);
    }

    /** 根据字符串 name 查找地形类型 */
    public static TileType getByName(String name) {
        return NAME_REGISTRY.get(name);
    }

    /** 获取所有已注册的地形类型（有序） */
    public static Collection<TileType> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    // ── 实例访问器 ──────────────────────────────────────

    public int getId() { return id; }
    public String getName() { return name; }
    public char getChar() { return ch; }
    public Color getColor() { return color; }
    public boolean isPassable() { return passable; }

    @Override
    public String toString() {
        return "TileType{" + name + "(id=" + id + ")}";
    }
}
