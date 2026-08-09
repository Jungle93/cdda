package com.github.game.cdda.world.drainage;

/**
 * 排水图。存储区域范围内每瓦片的排水计算结果。
 *
 * <p>由 {@link DrainageCalculator} 计算，由 Chunk 在生成地形时查询。
 * 每个瓦片包含：
 * <ul>
 *   <li>原始高程（用于洼地检测）</li>
 *   <li>填充后高程（洼地被填平到什么高度）</li>
 *   <li>排水方向（水往哪个邻居流）</li>
 *   <li>流量累加值（多少上游瓦片的水汇到这里）</li>
 * </ul>
 */
public class DrainageMap {

    /** 排水方向枚举（8 方向 + 无方向/洼地） */
    public enum Direction {
        N(-1, 0), NE(-1, 1), E(0, 1), SE(1, 1),
        S(1, 0), SW(1, -1), W(0, -1), NW(-1, -1),
        NONE(0, 0);

        public final int dRow;
        public final int dCol;

        Direction(int dRow, int dCol) {
            this.dRow = dRow;
            this.dCol = dCol;
        }

        /** 对角线方向距离 ≈ √2 */
        public double distance() {
            return (dRow != 0 && dCol != 0) ? 1.41421356 : 1.0;
        }
    }

    /** 区域范围（世界瓦片坐标） */
    public final int minX;
    public final int minY;
    public final int maxX;
    public final int maxY;

    /** 区域尺寸 */
    public final int width;
    public final int height;

    /** 原始高程（噪声采样值） */
    private final double[] elevation;
    /** 填充后高程（洼地填平后） */
    private final double[] fillElevation;
    /** 排水方向（0~8，对应 Direction 枚举 ordinal） */
    private final byte[] flowDir;
    /** 流量累加值 */
    private final int[] flowAccum;

    /** 创建排水图 */
    public DrainageMap(int minX, int minY, int maxX, int maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.width = maxX - minX + 1;
        this.height = maxY - minY + 1;
        int size = width * height;
        this.elevation = new double[size];
        this.fillElevation = new double[size];
        this.flowDir = new byte[size];
        // 初始化为 NONE (ordinal=8)，byte 默认值是 0 (N)
        java.util.Arrays.fill(this.flowDir, (byte) Direction.NONE.ordinal());
        this.flowAccum = new int[size];
    }

    /** 区域坐标索引 */
    private int idx(int row, int col) {
        return row * width + col;
    }

    /** 世界坐标 → 区域坐标 */
    private boolean inBounds(int worldX, int worldY) {
        return worldX >= minX && worldX <= maxX && worldY >= minY && worldY <= maxY;
    }

    /** 设置瓦片原始高程 */
    public void setElevation(int worldX, int worldY, double value) {
        if (!inBounds(worldX, worldY)) return;
        int col = worldX - minX;
        int row = worldY - minY;
        int i = idx(row, col);
        elevation[i] = value;
        fillElevation[i] = value;
    }

    /** 设置填充后高程（洼地填平） */
    public void setFillElevation(int worldX, int worldY, double value) {
        if (!inBounds(worldX, worldY)) return;
        fillElevation[idx(worldY - minY, worldX - minX)] = value;
    }

    /** 获取原始高程 */
    public double getElevation(int worldX, int worldY) {
        if (!inBounds(worldX, worldY)) return Double.POSITIVE_INFINITY;
        return elevation[idx(worldY - minY, worldX - minX)];
    }

    /** 获取填充后高程 */
    public double getFillElevation(int worldX, int worldY) {
        if (!inBounds(worldX, worldY)) return Double.POSITIVE_INFINITY;
        return fillElevation[idx(worldY - minY, worldX - minX)];
    }

    /** 设置排水方向 */
    public void setFlowDir(int worldX, int worldY, Direction dir) {
        if (!inBounds(worldX, worldY)) return;
        flowDir[idx(worldY - minY, worldX - minX)] = (byte) dir.ordinal();
    }

    /** 获取排水方向 */
    public Direction getFlowDir(int worldX, int worldY) {
        if (!inBounds(worldX, worldY)) return Direction.NONE;
        return Direction.values()[flowDir[idx(worldY - minY, worldX - minX)]];
    }

    /** 获取流量累加值 */
    public int getFlowAccum(int worldX, int worldY) {
        if (!inBounds(worldX, worldY)) return 0;
        return flowAccum[idx(worldY - minY, worldX - minX)];
    }

    /** 增加流量累加值（拓扑排序累加时使用） */
    public void addFlowAccum(int worldX, int worldY, int amount) {
        if (!inBounds(worldX, worldY)) return;
        flowAccum[idx(worldY - minY, worldX - minX)] += amount;
    }

    // ── 水域查询（供 Chunk 使用） ──────────────────

    /** 海平面（低于此高程 → 海洋） */
    private static final double SEA_LEVEL = -0.20;
    /** 河流流量阈值（累加值 ≥ 此值 → 河流） */
    private static final int RIVER_THRESHOLD = 8;
    /** 河流宽度系数（对数缩放） */
    private static final double RIVER_WIDTH_SCALE = 0.25;
    /** 水域过渡带宽度（瓦片数，用于创建沙滩/浅水过渡） */
    private static final int WATER_TRANSITION_RADIUS = 2;

    /** 水域核心强度（0 = 无水，越大水越深） */
    private double[] waterCore;
    /** 是否已计算过渡梯度 */
    private boolean gradientComputed = false;
    /** 水域梯度值（考虑过渡带衰减） */
    private double[] waterGradient;

    /**
     * 获取指定瓦片的水域核心强度值（不考虑过渡带）。
     *
     * <p>由排水算法结果决定：
     * <ul>
     *   <li>高程 &lt; 海平面 → 海洋（2.0）</li>
     *   <li>洼地填充量 &gt; 0 → 湖泊（填充深度）</li>
     *   <li>流量 ≥ 阈值 → 河流（对数缩放宽度）</li>
     *   <li>其他 → 0（无水）</li>
     * </ul>
     *
     * @param worldX 世界瓦片 X
     * @param worldY 世界瓦片 Y
     * @return 水域核心强度（0 = 无水，越大水越深）
     */
    public double getWaterLevel(int worldX, int worldY) {
        if (!inBounds(worldX, worldY)) return 0.0;

        int col = worldX - minX;
        int row = worldY - minY;
        int i = idx(row, col);
        double elev = elevation[i];
        double fill = fillElevation[i];
        int accum = flowAccum[i];

        // 海洋（高程 < 海平面）
        if (elev < SEA_LEVEL) {
            return 2.0;
        }

        // 湖泊（洼地填充量 > 0.01）
        double lakeDepth = fill - elev;
        if (lakeDepth > 0.01) {
            return lakeDepth;
        }

        // 河流（流量累加值 ≥ 阈值）
        if (accum >= RIVER_THRESHOLD) {
            return Math.log(accum) * RIVER_WIDTH_SCALE;
        }

        return 0.0;
    }

    /**
     * 计算水域梯度（考虑过渡带衰减）。
     * 调用一次后，后续 getWaterGradient() 直接返回缓存值。
     */
    public void computeWaterGradient() {
        if (gradientComputed) return;
        int size = width * height;
        waterCore = new double[size];
        waterGradient = new double[size];

        // 第一步：计算每个瓦片的核心水域强度
        for (int i = 0; i < size; i++) {
            int col = i % width;
            int row = i / width;
            int worldX = minX + col;
            int worldY = minY + row;
            waterCore[i] = getWaterLevel(worldX, worldY);
        }

        // 第二步：从水域瓦片向外扩散，计算梯度衰减
        // 使用 BFS 从所有水域瓦片同时开始
        java.util.Queue<int[]> queue = new java.util.LinkedList<>();
        boolean[][] visited = new boolean[height][width];

        for (int i = 0; i < size; i++) {
            if (waterCore[i] > 0.0) {
                int col = i % width;
                int row = i / width;
                waterGradient[i] = waterCore[i];

                // 只将水域边界瓦片加入队列（相邻有非水域瓦片）
                if (isWaterBoundary(row, col)) {
                    queue.offer(new int[]{row, col});
                }
                visited[row][col] = true;
            }
        }

        // BFS 扩散，距离越远梯度越低
        int[] dRow = {-1, 0, 0, 1};
        int[] dCol = {0, -1, 1, 0};

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int curRow = cell[0];
            int curCol = cell[1];
            int curIdx = idx(curRow, curCol);
            double curGradient = waterGradient[curIdx];

            for (int d = 0; d < 4; d++) {
                int nr = curRow + dRow[d];
                int nc = curCol + dCol[d];
                if (nr < 0 || nr >= height || nc < 0 || nc >= width) continue;
                if (visited[nr][nc]) continue;

                // 计算距离（曼哈顿距离）
                int dist = Math.abs(nr - (curRow)) + Math.abs(nc - (curCol));
                // 梯度衰减：每远离 1 瓦片，降低 0.3（创建更宽的过渡带）
                double decay = 0.3;
                double newGradient = Math.max(0.0, curGradient - decay);

                if (newGradient > 0.0) {
                    int nIdx = idx(nr, nc);
                    waterGradient[nIdx] = newGradient;
                    queue.offer(new int[]{nr, nc});
                    visited[nr][nc] = true;
                }
            }
        }

        gradientComputed = true;
    }

    /**
     * 判断水域瓦片是否为边界（相邻有非水域瓦片）。
     */
    private boolean isWaterBoundary(int row, int col) {
        int[] dRow = {-1, 0, 0, 1};
        int[] dCol = {0, -1, 1, 0};
        for (int d = 0; d < 4; d++) {
            int nr = row + dRow[d];
            int nc = col + dCol[d];
            if (nr < 0 || nr >= height || nc < 0 || nc >= width) return true; // 地图边界
            int nIdx = idx(nr, nc);
            if (waterCore[nIdx] <= 0.0) return true; // 相邻非水域
        }
        return false;
    }

    /**
     * 获取指定瓦片的水域梯度值（考虑过渡带衰减）。
     * 首次调用时自动计算梯度。
     *
     * @param worldX 世界瓦片 X
     * @param worldY 世界瓦片 Y
     * @return 水域梯度值（0 = 无水，越大水越深）
     */
    public double getWaterGradient(int worldX, int worldY) {
        if (!gradientComputed) {
            computeWaterGradient();
        }
        if (!inBounds(worldX, worldY)) return 0.0;
        int col = worldX - minX;
        int row = worldY - minY;
        return waterGradient[idx(row, col)];
    }

    /**
     * 获取水域强度（带群落检查）。
     * 干燥群落（waterLevel==0）强制返回 0，确保大地图与小地图一致。
     *
     * @param worldX 世界瓦片 X
     * @param worldY 世界瓦片 Y
     * @param worldMap 世界地图（用于群落查询）
     * @return 水域强度（0 = 无水）
     */
    public double getWaterLevel(int worldX, int worldY, com.github.game.cdda.world.biome.WorldMap worldMap) {
        if (!inBounds(worldX, worldY)) return 0.0;

        // 检查群落类型
        var biome = worldMap.getBiomeAt(worldX, worldY);
        if (biome.getWaterLevel() <= 0.0f) {
            return 0.0; // 干燥群落强制无水
        }

        return getWaterLevel(worldX, worldY);
    }

    /** 获取区域尺寸字符串（调试用） */
    public String getSizeInfo() {
        return width + "×" + height;
    }
}
