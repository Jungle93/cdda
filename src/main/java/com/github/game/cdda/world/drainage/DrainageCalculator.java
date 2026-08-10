package com.github.game.cdda.world.drainage;

import com.github.game.cdda.world.biome.WorldMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * 排水算法计算器。基于矮人要塞风格的排水模拟。
 *
 * <p>算法流程：
 * <ol>
 *   <li><b>高程采样</b> — 从 WorldMap 对区域内每个瓦片采样高程</li>
 *   <li><b>洼地填充</b> — Priority-Flood 算法，将洼地填平到溢出水位</li>
 *   <li><b>排水方向</b> — 每个瓦片向坡度最大的邻居排水（考虑对角线距离）</li>
 *   <li><b>流量累加</b> — 拓扑排序，从上游往下游累加流量</li>
 * </ol>
 *
 * <p>计算完成后，Chunk 通过 {@link DrainageMap#getWaterLevel(int, int)} 查询水域。
 */
public class DrainageCalculator {

    private static final Logger logger = LoggerFactory.getLogger(DrainageCalculator.class);

    /** 高程噪声采样频率（比地形细节噪声低频，但比生物群落噪声高频） */
    private static final double DRAIN_ELEVATION_FREQ = 0.006;
    /** 高程 fBm 参数 */
    private static final int DRAIN_OCTAVES = 3;
    private static final double DRAIN_PERSISTENCE = 0.5;
    private static final double DRAIN_LACUNARITY = 2.0;
    /** 微量扰动（打破高程平局的确定性哈希） */
    private static final double JITTER_SCALE = 0.001;

    private final WorldMap worldMap;

    public DrainageCalculator(WorldMap worldMap) {
        this.worldMap = worldMap;
    }

    /**
     * 计算指定区域的排水图。
     *
     * @param minX 区域最小 X（世界瓦片坐标）
     * @param minY 区域最小 Y（世界瓦片坐标）
     * @param maxX 区域最大 X（世界瓦片坐标）
     * @param maxY 区域最大 Y（世界瓦片坐标）
     * @return 填充完成的排水图
     */
    public DrainageMap compute(int minX, int minY, int maxX, int maxY) {
        DrainageMap map = new DrainageMap(minX, minY, maxX, maxY);
        long startTime = System.currentTimeMillis();

        // ── 第 1 步：高程采样 ──
        sampleElevation(map);

        // ── 第 2 步：洼地填充（Priority-Flood） ──
        priorityFlood(map);

        // ── 第 2.5 步：干燥群落后处理 ──
        // 洼地填充可能为干燥群落制造湖泊，流量累加可能制造河流。
        // 对 waterLevel==0 的群落，清除洼地积水和流量，确保不生成水域。
        suppressWaterForDryBiomes(map);

        // ── 第 3 步：排水方向（坡度最大方向） ──
        computeFlowDirection(map);

        // ── 第 4 步：流量累加（拓扑排序） ──
        computeFlowAccumulation(map);

        // ── 第 4.5 步：再次清除干燥群落的流量 ──
        // 流量累加可能重新为干燥群落分配流量，再次清除。
        suppressFlowForDryBiomes(map);

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("排水计算完成 — 区域 {}，耗时 {}ms", map.getSizeInfo(), elapsed);
        return map;
    }

    /**
     * 减少干燥群落（waterLevel==0）的洼地积水。
     * 洼地填充可能为干燥群落制造湖泊，减少填充量而非完全清除，
     * 保留少量小水洼，但消除大面积湖泊。
     */
    private void suppressWaterForDryBiomes(DrainageMap map) {
        for (int row = 0; row < map.height; row++) {
            for (int col = 0; col < map.width; col++) {
                int worldX = map.minX + col;
                int worldY = map.minY + row;
                var biome = worldMap.getBiomeAt(worldX, worldY);
                if (biome.getWaterLevel() <= 0.0f) {
                    // 减少填充量至原来的 20%（保留少量小水洼）
                    double elev = map.getElevation(worldX, worldY);
                    double fill = map.getFillElevation(worldX, worldY);
                    double lakeDepth = fill - elev;
                    if (lakeDepth > 0.01) {
                        double reducedDepth = lakeDepth * 0.2;
                        map.setFillElevation(worldX, worldY, elev + reducedDepth);
                    }
                }
            }
        }
    }

    /**
     * 减少干燥群落（waterLevel==0）的流量累加。
     * 流量累加可能为干燥群落分配过多流量，大幅减少而非完全清零，
     * 保留少量小河流/水洼，但消除大面积水域。
     */
    private void suppressFlowForDryBiomes(DrainageMap map) {
        for (int row = 0; row < map.height; row++) {
            for (int col = 0; col < map.width; col++) {
                int worldX = map.minX + col;
                int worldY = map.minY + row;
                var biome = worldMap.getBiomeAt(worldX, worldY);
                if (biome.getWaterLevel() <= 0.0f) {
                    // 减少流量至原来的 10%（保留少量小河流）
                    int current = map.getFlowAccum(worldX, worldY);
                    if (current > 1) {
                        int reduced = current / 10;
                        map.addFlowAccum(worldX, worldY, -(current - reduced));
                    }
                }
            }
        }
    }

    // ── 第 1 步：高程采样 ──────────────────

    /**
     * 对区域内每个瓦片采样高程。
     * 使用 WorldMap 的高程噪声，加上群落基础海拔偏移和微量扰动打破平局。
     *
     * <p>群落基础海拔偏移使：
     * <ul>
     *   <li>山地/丘陵 → 海拔高，水往低处流，成为河流源头</li>
     *   <li>平原 → 中等海拔，少量洼地积水（小水洼）</li>
     *   <li>沼泽 → 低海拔，易积水</li>
     *   <li>海洋 → 很低海拔，始终是水</li>
     * </ul>
     *
     * <p>对于非水生群落（水陆倾向=0），将高程抬升到海平面以上，
     * 确保大地图显示为平原/森林/山地等的位置不会意外生成水域，
     * 保持大地图与小地图视觉一致。
     */
    private void sampleElevation(DrainageMap map) {
        // 海平面（低于此值视为水域）
        double seaLevel = -0.20;
        // 非水生群落的安全海拔（确保不生成水）
        double dryBiomeMinElev = seaLevel + 0.15;

        for (int row = 0; row < map.height; row++) {
            for (int col = 0; col < map.width; col++) {
                int worldX = map.minX + col;
                int worldY = map.minY + row;
                double elev = worldMap.sampleElevationNoise(worldX, worldY,
                        DRAIN_ELEVATION_FREQ, DRAIN_OCTAVES,
                        DRAIN_PERSISTENCE, DRAIN_LACUNARITY);

                // 群落基础海拔偏移（大地图决定）
                var biome = worldMap.getBiomeAt(worldX, worldY);
                float biomeElevation = biome.getBaseElevation();
                float waterLevel = biome.getWaterLevel();
                elev += biomeElevation;

                // 非水生群落：抬升到海平面以上，防止意外生成水域
                // 水生群落（沼泽/海洋）：保持原始高程，允许积水
                if (waterLevel <= 0.0f && elev < dryBiomeMinElev) {
                    elev = dryBiomeMinElev;
                }

                // 微量确定性扰动（避免完全平坦区域的水流方向不确定）
                double jitter = deterministicJitter(worldX, worldY) * JITTER_SCALE;
                map.setElevation(worldX, worldY, elev + jitter);
            }
        }
    }

    /** 确定性伪随机扰动 [0, 1) */
    private static double deterministicJitter(int x, int y) {
        long h = (long) x * 374761393L + (long) y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL;
    }

    // ── 第 2 步：洼地填充（Priority-Flood） ──────────────────

    /**
     * Priority-Flood 洼地填充算法。
     *
     * <p>核心思想：从边界最低点开始，像洪水一样向内扩展。
     * 遇到比当前水位低的洼地就填平到水位，确保每滴水最终都能流出区域。
     *
     * <p>复杂度：O(n log n)，n = 瓦片数。
     */
    private void priorityFlood(DrainageMap map) {
        // 优先队列：按填充后高程排序（最低的先处理）
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> map.getFillElevation(a[0], a[1])));
        boolean[][] processed = new boolean[map.height][map.width];

        // 将所有边界瓦片加入优先队列
        for (int row = 0; row < map.height; row++) {
            for (int col = 0; col < map.width; col++) {
                if (row == 0 || row == map.height - 1 || col == 0 || col == map.width - 1) {
                    int worldX = map.minX + col;
                    int worldY = map.minY + row;
                    pq.offer(new int[]{worldY, worldX});
                    processed[row][col] = true;
                }
            }
        }

        // 8 方向邻居
        int[] dRow = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dCol = {-1, 0, 1, -1, 1, -1, 0, 1};

        while (!pq.isEmpty()) {
            int[] cell = pq.poll();
            int curRow = cell[0] - map.minY;
            int curCol = cell[1] - map.minX;
            double curLevel = map.getFillElevation(cell[1], cell[0]);

            for (int d = 0; d < 8; d++) {
                int nr = curRow + dRow[d];
                int nc = curCol + dCol[d];
                if (nr < 0 || nr >= map.height || nc < 0 || nc >= map.width) continue;
                if (processed[nr][nc]) continue;
                processed[nr][nc] = true;

                int worldX = map.minX + nc;
                int worldY = map.minY + nr;
                double neighborElev = map.getElevation(worldX, worldY);

                // 如果邻居比当前水位低 → 填平到水位（洼地积水）
                if (neighborElev < curLevel) {
                    // 需要更新 fillElevation
                    updateFillElevation(map, worldX, worldY, curLevel);
                }

                pq.offer(new int[]{map.minY + nr, worldX});
            }
        }
    }

    /**
     * 递归更新 fillElevation（洼地填平需要连带更新相邻洼地）。
     * 使用迭代而非递归，避免栈溢出。
     */
    private void updateFillElevation(DrainageMap map, int worldX, int worldY, double fillLevel) {
        // 用栈迭代处理（类似洪水填充）
        java.util.Deque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{worldY, worldX});

        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            int row = cell[0] - map.minY;
            int col = cell[1] - map.minX;

            if (row < 0 || row >= map.height || col < 0 || col >= map.width) continue;
            double fill = map.getFillElevation(cell[1], cell[0]);
            if (fill >= fillLevel) continue; // 已经填到或超过目标水位

            // 更新填充高程
            map.setFillElevation(cell[1], cell[0], fillLevel);

            // 连带检查 4 方向邻居（洼地通常连通）
            int[] dRow = {-1, 0, 0, 1};
            int[] dCol = {0, -1, 1, 0};
            for (int d = 0; d < 4; d++) {
                int nr = row + dRow[d];
                int nc = col + dCol[d];
                if (nr < 0 || nr >= map.height || nc < 0 || nc >= map.width) continue;
                double nElev = map.getElevation(map.minX + nc, map.minY + nr);
                double nFill = map.getFillElevation(map.minX + nc, map.minY + nr);
                if (nElev < fillLevel && nFill < fillLevel) {
                    stack.push(new int[]{map.minY + nr, map.minX + nc});
                }
            }
        }
    }

    // ── 第 3 步：排水方向 ──────────────────

    /**
     * 计算每个瓦片的排水方向。
     * 选择坡度最大的邻居方向（坡度 = 高程差 / 距离）。
     * 对角线距离 ≈ √2，水平/垂直距离 = 1。
     */
    private void computeFlowDirection(DrainageMap map) {
        for (int row = 0; row < map.height; row++) {
            for (int col = 0; col < map.width; col++) {
                int worldX = map.minX + col;
                int worldY = map.minY + row;
                double elev = map.getElevation(worldX, worldY);

                DrainageMap.Direction bestDir = DrainageMap.Direction.NONE;
                double bestSlope = 0;

                int[] dRow = {-1, -1, -1, 0, 0, 1, 1, 1};
                int[] dCol = {-1, 0, 1, -1, 1, -1, 0, 1};

                for (int d = 0; d < 8; d++) {
                    int nr = row + dRow[d];
                    int nc = col + dCol[d];
                    if (nr < 0 || nr >= map.height || nc < 0 || nc >= map.width) continue;

                    int nWorldX = map.minX + nc;
                    int nWorldY = map.minY + nr;
                    double nElev = map.getElevation(nWorldX, nWorldY);

                    double elevDiff = elev - nElev;
                    if (elevDiff <= 0) continue; // 邻居不低于自己

                    double dist = (dRow[d] != 0 && dCol[d] != 0) ? 1.41421356 : 1.0;
                    double slope = elevDiff / dist;

                    if (slope > bestSlope) {
                        bestSlope = slope;
                        bestDir = DrainageMap.Direction.values()[d];
                    }
                }

                map.setFlowDir(worldX, worldY, bestDir);
            }
        }
    }

    // ── 第 4 步：流量累加 ──────────────────

    /**
     * 拓扑排序累加流量。
     * 入度为 0 的瓦片是源头（没有水汇过来），先处理。
     * 每个瓦片的流量 = 1（自身降水）+ 上游汇水。
     */
    private void computeFlowAccumulation(DrainageMap map) {
        int totalTiles = map.width * map.height;

        // 计算入度（有多少邻居流向自己）
        int[] inDegree = new int[totalTiles];

        for (int row = 0; row < map.height; row++) {
            for (int col = 0; col < map.width; col++) {
                int worldX = map.minX + col;
                int worldY = map.minY + row;
                DrainageMap.Direction dir = map.getFlowDir(worldX, worldY);
                if (dir == DrainageMap.Direction.NONE) continue;

                int nRow = row + dir.dRow;
                int nCol = col + dir.dCol;
                if (nRow < 0 || nRow >= map.height || nCol < 0 || nCol >= map.width) continue;

                int nIdx = nRow * map.width + nCol;
                inDegree[nIdx]++;
            }
        }

        // 拓扑排序：入度为 0 的瓦片先入队
        java.util.Queue<int[]> queue = new java.util.LinkedList<>();
        for (int row = 0; row < map.height; row++) {
            for (int col = 0; col < map.width; col++) {
                int idx = row * map.width + col;
                if (inDegree[idx] == 0) {
                    queue.offer(new int[]{map.minY + row, map.minX + col});
                }
            }
        }

        // 按拓扑序累加流量
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int row = cell[0] - map.minY;
            int col = cell[1] - map.minX;
            int worldX = cell[1];
            int worldY = cell[0];

            // 自身降水 = 1，加上下游传过来的（已经在入队时累加了）
            map.addFlowAccum(worldX, worldY, 1);

            // 流向下游
            DrainageMap.Direction dir = map.getFlowDir(worldX, worldY);
            if (dir == DrainageMap.Direction.NONE) continue;

            int nRow = row + dir.dRow;
            int nCol = col + dir.dCol;
            if (nRow < 0 || nRow >= map.height || nCol < 0 || nCol >= map.width) continue;

            int nWorldX = map.minX + nCol;
            int nWorldY = map.minY + nRow;
            int nIdx = nRow * map.width + nCol;

            // 将当前流量传递给下游
            map.addFlowAccum(nWorldX, nWorldY, map.getFlowAccum(worldX, worldY));

            // 下游入度减 1，为 0 时入队
            inDegree[nIdx]--;
            if (inDegree[nIdx] == 0) {
                queue.offer(new int[]{nWorldY, nWorldX});
            }
        }
    }
}
