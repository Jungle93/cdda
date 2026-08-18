package com.github.game.cdda.world.biome;

import com.github.game.cdda.world.TileType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物群落类型注册表（替代枚举，支持 mod 扩展）。
 *
 * <p>每个生物群落定义了一个区域的生态特征，驱动区块（Chunk）的地形生成：
 * <ul>
 *   <li>{@code treeDensity} — 树木密度（0~1），越高森林越密</li>
 *   <li>{@code grassDensity} — 植被密度（0~1），越高高草/花越多</li>
 *   <li>{@code rockiness} — 岩石率（0~1），越高石头越多</li>
 *   <li>{@code waterLevel} — 水域倾向（0~1），越高越可能有水</li>
 *   <li>{@code baseElevation} — 基础海拔偏移（排水算法用），越高水越易流走</li>
 * </ul>
 *
 * <p>世界地图（{@link WorldMap}）为每个区块位置分配一个生物群落，
 * 区块生成时根据群落的密度参数放置地表物体，实现"大地图驱动小地图"的分层架构。
 *
 * <p>扩展方式：Mod 可调用 {@link #register(int, String, char, Color, float, float, float, float)}
 * 注册新的生物群落类型。
 */
public class BiomeType {

    /** 数字 ID → BiomeType 注册表（有序） */
    private static final Map<Integer, BiomeType> REGISTRY = new LinkedHashMap<>();
    /** 字符串 name → BiomeType 注册表 */
    private static final Map<String, BiomeType> NAME_REGISTRY = new LinkedHashMap<>();

    // ── 内置生物群落 ────────────────────────────

    /** 海洋 — 大面积水域，无法通行 */
    public static final BiomeType OCEAN = register(0, "ocean", '~',
            new Color(40, 100, 200), 0.0f, 0.0f, 0.0f, 1.0f, -0.5f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.MUD, 4),
                    new GroundCoverEntry(TileType.SAND, 2));

    /** 平原 — 开阔草地，稀疏植被（中等海拔，水易流走） */
    public static final BiomeType PLAINS = register(1, "plains", '.',
            new Color(100, 190, 40), 0.0f, 0.20f, 0.0f, 0.0f, 0.15f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.GRASS, 12),
                    new GroundCoverEntry(TileType.DIRT, 1));

    /** 森林 — 密集树木 */
    public static final BiomeType FOREST = register(2, "forest", 'F',
            Color.WHITE, 0.35f, 0.12f, 0.0f, 0.0f, 0.05f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.GRASS, 8),
                    new GroundCoverEntry(TileType.DIRT, 1));

    /** 密林 — 非常密集的树林 */
    public static final BiomeType DENSE_FOREST = register(3, "dense_forest", '♣',
            new Color(0, 80, 0), 0.55f, 0.08f, 0.0f, 0.0f, 0.00f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.GRASS, 6),
                    new GroundCoverEntry(TileType.MUD, 1),
                    new GroundCoverEntry(TileType.DIRT, 1));

    /** 沼泽 — 水与植被混合（低海拔，易积水） */
    public static final BiomeType SWAMP = register(4, "swamp", '≈',
            new Color(80, 130, 60), 0.08f, 0.25f, 0.0f, 0.25f, -0.20f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.MUD, 2),
                    new GroundCoverEntry(TileType.DIRT, 2),
                    new GroundCoverEntry(TileType.GRASS, 3));

    /** 丘陵 — 略有起伏，稀疏树木（较高海拔） */
    public static final BiomeType HILLS = register(5, "hills", '∩',
            new Color(120, 160, 80), 0.06f, 0.10f, 0.15f, 0.0f, 0.25f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.GRASS, 10),
                    new GroundCoverEntry(TileType.DIRT, 2),
                    new GroundCoverEntry(TileType.SAND, 1));

    /** 山地 — 多岩石，高海拔（河流源头） */
    public static final BiomeType MOUNTAIN = register(6, "mountain", '▲',
            new Color(140, 140, 140), 0.01f, 0.02f, 0.50f, 0.0f, 0.40f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.DIRT, 5),
                    new GroundCoverEntry(TileType.SAND, 3),
                    new GroundCoverEntry(TileType.GRASS, 2));

    /** 沙漠 — 干旱，几乎无植被（中等偏高海拔） */
    public static final BiomeType DESERT = register(7, "desert", '∴',
            new Color(220, 200, 140), 0.0f, 0.02f, 0.05f, 0.0f, 0.20f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.SAND, 12),
                    new GroundCoverEntry(TileType.DIRT, 1));

    /** 草原 — 开阔草地，中等海拔，高草密度 */
    public static final BiomeType GRASSLAND = register(8, "grassland", '∶',
            new Color(140, 185, 60), 0.02f, 0.30f, 0.0f, 0.0f, 0.10f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.GRASS, 12),
                    new GroundCoverEntry(TileType.DIRT, 1));

    /** 高原 — 高海拔平地，稀疏植被，多岩石 */
    public static final BiomeType PLATEAU = register(9, "plateau", '▬',
            new Color(170, 160, 120), 0.02f, 0.05f, 0.20f, 0.0f, 0.35f)
            .withGroundCover(
                    new GroundCoverEntry(TileType.GRASS, 5),
                    new GroundCoverEntry(TileType.SAND, 3),
                    new GroundCoverEntry(TileType.DIRT, 1));

    // ── 地面覆盖条目（加权列表） ────────────────────

    /**
     * 地面覆盖条目：一种地形类型及其在加权池中的权重。
     *
     * <p>当 biome 的"默认地面"需要解析时，通过加权随机从地面覆盖池中选取。
     * 权重越高的地形出现概率越大。
     *
     * <p>设计借鉴 Cataclysm-DDA 的 region_groundcover 间接解析模式。
     */
    public static class GroundCoverEntry {
        /** 地形类型 */
        public final TileType type;
        /** 权重（正整数，越大概率越高） */
        public final int weight;

        public GroundCoverEntry(TileType type, int weight) {
            this.type = type;
            this.weight = weight;
        }
    }

    // ── 实例字段 ────────────────────────────

    private final int id;
    private final String name;
    private final char mapChar;
    private final Color color;

    /** 树木密度（0~1）。值越高，区块中树越多 */
    private final float treeDensity;
    /** 草地/花密度（0~1）。值越高，高草/花越多 */
    private final float grassDensity;
    /** 岩石率（0~1）。值越高，裸露石头越多 */
    private final float rockiness;
    /** 水域倾向（0~1）。值越高，低洼处越容易积水 */
    private final float waterLevel;
    /** 基础海拔偏移（排水算法用）。越高水越易流走，越低越易积水 */
    private final float baseElevation;

    /**
     * 地面覆盖加权池（pseudo-terrain indirection）。
     *
     * <p>当 biome 的"默认地面"需要解析时，通过加权随机从该池中选取。
     * 使不同 biome 的"普通地面"看起来不同（森林有泥土/泥地斑块，沙漠以沙为主）。
     * 若为 null 或空，则 classifyTerrain() 默认返回 GRASS。
     */
    private List<GroundCoverEntry> groundCover;

    private BiomeType(int id, String name, char mapChar, Color color,
                      float treeDensity, float grassDensity,
                      float rockiness, float waterLevel, float baseElevation) {
        this.id = id;
        this.name = name;
        this.mapChar = mapChar;
        this.color = color;
        this.treeDensity = treeDensity;
        this.grassDensity = grassDensity;
        this.rockiness = rockiness;
        this.waterLevel = waterLevel;
        this.baseElevation = baseElevation;
    }

    // ── 注册 ────────────────────────────

    /**
     * 注册新的生物群落类型。
     *
     * @param id          唯一数字 ID
     * @param name        唯一字符串名
     * @param mapChar     大地图显示字符
     * @param color       大地图显示颜色
     * @param treeDensity 树木密度（0~1）
     * @param grassDensity 植被密度（0~1）
     * @param rockiness   岩石率（0~1）
     * @param waterLevel  水域倾向（0~1）
     * @param baseElevation 基础海拔偏移（-1~1，排水算法用）
     */
    public static BiomeType register(int id, String name, char mapChar, Color color,
                                      float treeDensity, float grassDensity,
                                      float rockiness, float waterLevel, float baseElevation) {
        BiomeType type = new BiomeType(id, name, mapChar, color,
                treeDensity, grassDensity, rockiness, waterLevel, baseElevation);
        REGISTRY.put(id, type);
        NAME_REGISTRY.put(name, type);
        return type;
    }

    // ── 查询 ────────────────────────────

    public static BiomeType getById(int id) { return REGISTRY.get(id); }
    public static BiomeType getByName(String name) { return NAME_REGISTRY.get(name); }
    public static Collection<BiomeType> getAll() { return Collections.unmodifiableCollection(REGISTRY.values()); }

    /** 根据 ID 获取，未注册时返回 PLAINS 作为默认 */
    public static BiomeType getOrDefault(int id) {
        return REGISTRY.getOrDefault(id, PLAINS);
    }

    // ── 访问器 ────────────────────────────

    public int getId() { return id; }
    public String getName() { return name; }
    public char getMapChar() { return mapChar; }
    public Color getColor() { return color; }
    public float getTreeDensity() { return treeDensity; }
    public float getGrassDensity() { return grassDensity; }
    public float getRockiness() { return rockiness; }
    public float getWaterLevel() { return waterLevel; }
    public float getBaseElevation() { return baseElevation; }

    /**
     * 获取地面覆盖加权池。
     *
     * @return 地面覆盖条目列表（不可变视图），null 表示使用默认（GRASS）
     */
    public List<GroundCoverEntry> getGroundCover() {
        return groundCover == null ? null : Collections.unmodifiableList(groundCover);
    }

    /**
     * 设置地面覆盖加权池（builder pattern，链式调用）。
     *
     * <p>用于为 biome 配置 pseudo-terrain indirection。
     * 典型用法：
     * <pre>
     * register(2, "forest", ...)
     *     .withGroundCover(
     *         new GroundCoverEntry(TileType.GRASS, 6),
     *         new GroundCoverEntry(TileType.DIRT, 3),
     *         new GroundCoverEntry(TileType.MUD, 1));
     * </pre>
     *
     * @param entries 地面覆盖条目（地形类型 + 权重）
     * @return this（支持链式调用）
     */
    public BiomeType withGroundCover(GroundCoverEntry... entries) {
        this.groundCover = new ArrayList<>(entries.length);
        for (GroundCoverEntry e : entries) {
            this.groundCover.add(e);
        }
        return this;
    }

    /** 该群落是否以水域为主（waterLevel > 0.5） */
    public boolean isAquatic() { return waterLevel > 0.5f; }

    /** 该群落是否适合树木生长（treeDensity > 0.1） */
    public boolean isWooded() { return treeDensity > 0.1f; }

    @Override
    public String toString() {
        return name;
    }
}
