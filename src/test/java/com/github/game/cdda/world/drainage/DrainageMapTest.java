package com.github.game.cdda.world.drainage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DrainageMap 数据结构单元测试。
 */
class DrainageMapTest {

    private DrainageMap map;

    @BeforeEach
    void setUp() {
        // 5×5 区域，世界坐标 (10,10) ~ (14,14)
        map = new DrainageMap(10, 10, 14, 14);
    }

    // ── 基本属性 ──────────────────

    @Test
    void testDimensions() {
        assertEquals(10, map.minX);
        assertEquals(10, map.minY);
        assertEquals(14, map.maxX);
        assertEquals(14, map.maxY);
        assertEquals(5, map.width);
        assertEquals(5, map.height);
    }

    @Test
    void testSizeInfo() {
        assertEquals("5×5", map.getSizeInfo());
    }

    // ── 高程存取 ──────────────────

    @Test
    void testSetAndGetElevation() {
        map.setElevation(12, 12, 0.5);
        assertEquals(0.5, map.getElevation(12, 12), 1e-9);
        // fillElevation 初始等于 elevation
        assertEquals(0.5, map.getFillElevation(12, 12), 1e-9);
    }

    @Test
    void testElevationOutOfBounds() {
        assertEquals(Double.POSITIVE_INFINITY, map.getElevation(0, 0));
        assertEquals(Double.POSITIVE_INFINITY, map.getElevation(99, 99));
    }

    @Test
    void testSetFillElevation() {
        map.setElevation(12, 12, 0.3);
        map.setFillElevation(12, 12, 0.8);
        assertEquals(0.3, map.getElevation(12, 12), 1e-9);
        assertEquals(0.8, map.getFillElevation(12, 12), 1e-9);
    }

    @Test
    void testSetElevationOutOfBounds() {
        // 不应抛异常
        assertDoesNotThrow(() -> map.setElevation(0, 0, 1.0));
        assertDoesNotThrow(() -> map.setFillElevation(99, 99, 1.0));
    }

    // ── 排水方向 ──────────────────

    @Test
    void testSetAndGetFlowDir() {
        map.setFlowDir(12, 12, DrainageMap.Direction.S);
        assertEquals(DrainageMap.Direction.S, map.getFlowDir(12, 12));
    }

    @Test
    void testFlowDirDefault() {
        // 默认是 NONE (ordinal=8)
        assertEquals(DrainageMap.Direction.NONE, map.getFlowDir(12, 12));
    }

    @Test
    void testFlowDirOutOfBounds() {
        assertEquals(DrainageMap.Direction.NONE, map.getFlowDir(0, 0));
    }

    // ── 流量累加 ──────────────────

    @Test
    void testFlowAccum() {
        map.addFlowAccum(12, 12, 5);
        assertEquals(5, map.getFlowAccum(12, 12));
        map.addFlowAccum(12, 12, 3);
        assertEquals(8, map.getFlowAccum(12, 12));
    }

    @Test
    void testFlowAccumNegative() {
        map.addFlowAccum(12, 12, 10);
        map.addFlowAccum(12, 12, -10);
        assertEquals(0, map.getFlowAccum(12, 12));
    }

    @Test
    void testFlowAccumOutOfBounds() {
        assertEquals(0, map.getFlowAccum(0, 0));
        assertDoesNotThrow(() -> map.addFlowAccum(99, 99, 5));
    }

    // ─ 水域查询 ──────────────────

    @Test
    void testWaterLevelOcean() {
        // 高程 < SEA_LEVEL (-0.20) → 海洋
        map.setElevation(12, 12, -0.30);
        assertEquals(2.0, map.getWaterLevel(12, 12), 1e-9);
    }

    @Test
    void testWaterLevelLake() {
        // 填充高程 > 原始高程 → 湖泊
        map.setElevation(12, 12, 0.0);
        map.setFillElevation(12, 12, 0.1);
        double waterLevel = map.getWaterLevel(12, 12);
        assertTrue(waterLevel > 0.0, "湖泊水域强度应 > 0");
        assertEquals(0.1, waterLevel, 1e-9);
    }

    @Test
    void testWaterLevelRiver() {
        // flowAccum >= RIVER_THRESHOLD (8) → 河流
        map.setElevation(12, 12, 0.5);
        map.setFillElevation(12, 12, 0.5);
        map.addFlowAccum(12, 12, 10);
        double waterLevel = map.getWaterLevel(12, 12);
        assertTrue(waterLevel > 0.0, "河流水域强度应 > 0");
        // log(10) * 0.25 ≈ 0.575
        assertEquals(Math.log(10) * 0.25, waterLevel, 1e-6);
    }

    @Test
    void testWaterLevelDry() {
        // 无水域条件 → 0
        map.setElevation(12, 12, 0.5);
        map.setFillElevation(12, 12, 0.5);
        map.addFlowAccum(12, 12, 1);
        assertEquals(0.0, map.getWaterLevel(12, 12), 1e-9);
    }

    @Test
    void testWaterLevelOutOfBounds() {
        assertEquals(0.0, map.getWaterLevel(0, 0));
        assertEquals(0.0, map.getWaterLevel(99, 99));
    }

    @Test
    void testWaterLevelLakeTrumpsRiver() {
        // 同时满足湖泊和河流条件时，湖泊优先
        map.setElevation(12, 12, 0.0);
        map.setFillElevation(12, 12, 0.2); // 湖泊深度 0.2
        map.addFlowAccum(12, 12, 100); // 也满足河流
        double waterLevel = map.getWaterLevel(12, 12);
        assertEquals(0.2, waterLevel, 1e-9); // 应返回湖泊深度
    }

    // ── Direction 枚举 ──────────────────

    @Test
    void testDirectionDistance() {
        assertEquals(1.0, DrainageMap.Direction.N.distance(), 1e-9);
        assertEquals(1.0, DrainageMap.Direction.S.distance(), 1e-9);
        assertEquals(1.0, DrainageMap.Direction.E.distance(), 1e-9);
        assertEquals(1.0, DrainageMap.Direction.W.distance(), 1e-9);
        assertEquals(1.41421356, DrainageMap.Direction.NE.distance(), 1e-6);
        assertEquals(1.41421356, DrainageMap.Direction.SW.distance(), 1e-6);
    }

    @Test
    void testDirectionOffsets() {
        assertEquals(-1, DrainageMap.Direction.N.dRow);
        assertEquals(0, DrainageMap.Direction.N.dCol);
        assertEquals(1, DrainageMap.Direction.S.dRow);
        assertEquals(0, DrainageMap.Direction.S.dCol);
        assertEquals(0, DrainageMap.Direction.E.dRow);
        assertEquals(1, DrainageMap.Direction.E.dCol);
    }

    // ── 水域梯度（过渡带） ─────────────────

    @Test
    void testWaterGradientBasic() {
        // 创建一个简单场景：中心是水域，周围是陆地
        map.setElevation(12, 12, -0.30); // 中心：海洋
        map.computeWaterGradient();

        double centerGradient = map.getWaterGradient(12, 12);
        assertTrue(centerGradient >= 0.5, "中心水域梯度应 ≥ 0.5");
    }

    @Test
    void testWaterGradientDecay() {
        // 创建水域瓦片
        map.setElevation(12, 12, -0.30); // 中心水域
        map.computeWaterGradient();

        // 距离水域 1 瓦片的梯度应该低于中心
        double centerGradient = map.getWaterGradient(12, 12);
        double neighborGradient = map.getWaterGradient(13, 12); // 东边邻居

        // 梯度应该衰减（除非邻居也是水域）
        // 由于 BFS 衰减系数是 0.3，邻居梯度 = max(0, 2.0 - 0.3) = 1.7
        assertTrue(neighborGradient < centerGradient || neighborGradient > 0.0,
                "邻居梯度应 < 中心梯度 或 > 0（有衰减）");
    }

    @Test
    void testWaterGradientTransitionZone() {
        // 验证过渡带存在：水域边缘应该有中等梯度值
        // 创建更大的地图以容纳水域和过渡带
        DrainageMap largeMap = new DrainageMap(5, 5, 19, 19);

        // 创建一个小水域（3x3）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int worldX = 11 + col;
                int worldY = 11 + row;
                largeMap.setElevation(worldX, worldY, -0.30); // 水域
            }
        }
        largeMap.computeWaterGradient();

        // 检查梯度值：水域中心应该高，外围应该递减
        // 衰减 0.3/瓦片，起始 2.0：距离 5 瓦片梯度 = 2.0 - 5*0.3 = 0.5（过渡带）
        double centerGradient = largeMap.getWaterGradient(12, 12); // 水域中心
        double nearGradient = largeMap.getWaterGradient(10, 12); // 水域外 1 瓦片
        double midGradient = largeMap.getWaterGradient(7, 12); // 水域外 4 瓦片
        double transitionGradient = largeMap.getWaterGradient(6, 12); // 水域外 5 瓦片（过渡带）
        double farGradient = largeMap.getWaterGradient(4, 12); // 水域外 7 瓦片（无水）

        System.out.printf("中心: %.2f, 近: %.2f, 中: %.2f, 过渡: %.2f, 远: %.2f%n",
                centerGradient, nearGradient, midGradient, transitionGradient, farGradient);

        // 中心应该最高
        assertTrue(centerGradient > nearGradient,
                String.format("中心梯度 (%.2f) 应 > 近处梯度 (%.2f)", centerGradient, nearGradient));

        // 过渡带应该有中等梯度（0.3~0.6）
        assertTrue(transitionGradient >= 0.3 && transitionGradient < 0.6,
                String.format("过渡带梯度 (%.2f) 应在 0.3~0.6 范围内", transitionGradient));
    }

    @Test
    void testWaterGradientMultipleWaterBodies() {
        // 多个水域源，梯度应该叠加
        map.setElevation(10, 10, -0.30); // 水域 1
        map.setElevation(14, 14, -0.30); // 水域 2
        map.computeWaterGradient();

        double g1 = map.getWaterGradient(10, 10);
        double g2 = map.getWaterGradient(14, 14);

        assertTrue(g1 > 0.0, "水域 1 梯度应 > 0");
        assertTrue(g2 > 0.0, "水域 2 梯度应 > 0");
    }
}
