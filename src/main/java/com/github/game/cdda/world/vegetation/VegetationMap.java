package com.github.game.cdda.world.vegetation;

/**
 * 植被地图。存储每个瓦片的植被物种 ID 和生长状态。
 *
 * <p>与 TileMap 平行，记录每个瓦片上实际生长的植被物种。
 * TileMap 存储地形类型（TREE, BUSH, GRASS 等），VegetationMap 存储具体物种（"oak", "birch" 等）
 * 和生长状态（阶段、健康度、年龄等）。
 *
 * <p>用于：
 * <ul>
 *   <li>砍伐时查询物种 → 决定掉落物</li>
 *   <li>渲染时查询物种 → 决定显示字符/颜色（未来扩展）</li>
 *   <li>植物生长系统 → 追踪生长阶段和健康度</li>
 *   <li>生态模拟 → 物种竞争、蔓延等（未来扩展）</li>
 * </ul>
 */
public class VegetationMap {

    /** 地块边长（与 Chunk.SIZE 一致） */
    public static final int SIZE = 32;

    /** 地块坐标 */
    private final int chunkX;
    private final int chunkY;

    /** 植被物种 ID [row][col]（null 表示无植被） */
    private String[][] vegetation;

    /** 植被生长状态 [row][col]（null 表示无植被） */
    private VegetationState[][] growthStates;

    /**
     * 创建植被地图。
     *
     * @param chunkX 地块 X 坐标
     * @param chunkY 地块 Y 坐标
     */
    public VegetationMap(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.vegetation = new String[SIZE][SIZE];
        this.growthStates = new VegetationState[SIZE][SIZE];
    }

    /**
     * 获取指定瓦片的植被物种 ID。
     *
     * @param localCol 局部列号 [0, SIZE)
     * @param localRow 局部行号 [0, SIZE)
     * @return 物种 ID，无植被或越界返回 null
     */
    public String getVegetation(int localCol, int localRow) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return null;
        }
        return vegetation[localRow][localCol];
    }

    /**
     * 设置指定瓦片的植被物种。
     *
     * @param localCol  局部列号 [0, SIZE)
     * @param localRow  局部行号 [0, SIZE)
     * @param speciesId 物种 ID（null 表示清除植被）
     */
    public void setVegetation(int localCol, int localRow, String speciesId) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return;
        }
        vegetation[localRow][localCol] = speciesId;
        if (speciesId == null) {
            growthStates[localRow][localCol] = null;
        }
    }

    /**
     * 清除指定瓦片的植被。
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     */
    public void clear(int localCol, int localRow) {
        setVegetation(localCol, localRow, null);
    }

    /**
     * 检查指定瓦片是否有植被。
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     * @return true 如果有植被
     */
    public boolean hasVegetation(int localCol, int localRow) {
        return getVegetation(localCol, localRow) != null;
    }

    // ── 生长状态相关 ──────────────────────────────

    /**
     * 获取指定瓦片的生长状态。
     *
     * @param localCol 局部列号
     * @param localRow 局部行号
     * @return 生长状态，无植被或越界返回 null
     */
    public VegetationState getGrowthState(int localCol, int localRow) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return null;
        }
        return growthStates[localRow][localCol];
    }

    /**
     * 设置指定瓦片的生长状态。
     *
     * @param localCol  局部列号
     * @param localRow  局部行号
     * @param state     生长状态（null 表示清除）
     */
    public void setGrowthState(int localCol, int localRow, VegetationState state) {
        if (localCol < 0 || localCol >= SIZE || localRow < 0 || localRow >= SIZE) {
            return;
        }
        growthStates[localRow][localCol] = state;
    }

    /**
     * 检查指定瓦片是否有存活的植被（非枯萎）。
     */
    public boolean hasLivingVegetation(int localCol, int localRow) {
        VegetationState state = getGrowthState(localCol, localRow);
        return state != null && state.stage.isAlive();
    }

    /**
     * 检查指定瓦片是否有枯萎的植被。
     */
    public boolean hasWitheredVegetation(int localCol, int localRow) {
        VegetationState state = getGrowthState(localCol, localRow);
        return state != null && state.stage.isDead();
    }

    /**
     * 获取物种 ID（无论存活/枯萎）。
     */
    public String getSpeciesId(int localCol, int localRow) {
        return getVegetation(localCol, localRow);
    }

    public int getChunkX() { return chunkX; }
    public int getChunkY() { return chunkY; }
}
