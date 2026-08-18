package com.github.game.cdda.game;

/**
 * 方向梯度配置。控制世界地图在不同方向上的生态趋势。
 *
 * <p>每个参数表示"每区块"的变化率，正方向为坐标轴正向：
 * <ul>
 *   <li><b>+Y（北）</b>：{@code elevationPerChunkY} 海拔升高，{@code tempPerChunkY} 温度变化</li>
 *   <li><b>-Y（南）</b>：反向，海拔降低、温度升高</li>
 *   <li><b>+X（东）</b>：{@code moisturePerChunkX} 湿度升高</li>
 *   <li><b>-X（西）</b>：{@code moisturePerChunkX} 反向，湿度降低</li>
 * </ul>
 *
 * <p>默认梯度设置为 ~0.0001/chunk 量级，约 2000 区块（64000 瓦片）
 * 距离才会出现明显的群落变化。200 区块以内偏移 &lt; 0.03，基本无感。
 *
 * <p>植被密度梯度（{@code forestDensityPerChunkY/X}）使森林在某个方向上变密。
 * 默认北方森林更密（+0.03/chunk），20 区块后密度偏移 ≈ 0.6（使 FOREST 接近 DENSE_FOREST）。
 */
public class DirectionalGradients {

    /** 默认梯度：~2000 区块（~64000 瓦片）才出现明显群落变化；北方森林更密 */
    public static final DirectionalGradients DEFAULT = new DirectionalGradients(
            0.00015f,   // elevationPerChunkY
            -0.000075f, // tempPerChunkY
            0.0001f,    // moisturePerChunkX
            -0.000025f, // tempPerChunkX
            0.03f,      // forestDensityPerChunkY（北方森林更密）
            0.01f       // forestDensityPerChunkX（东方轻微增密）
    );

    /**
     * 无梯度（各向同性）。用于测试或不想有方向差异的世界。
     */
    public static final DirectionalGradients NONE = new DirectionalGradients(
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
    );

    /**
     * 每向北一个区块（+Y），海拔噪声增加的量。
     * 默认 0.00015，2000 区块 ≈ +0.3（从平原到丘陵/山地）
     */
    public final float elevationPerChunkY;

    /**
     * 每向北一个区块（+Y），温度噪声的变化量。
     * 默认 -0.000075，2000 区块 ≈ -0.15（更冷）
     */
    public final float tempPerChunkY;

    /**
     * 每向东一个区块（+X），湿度噪声增加的量。
     * 默认 0.0001，2000 区块 ≈ +0.2（从干燥到湿润）
     */
    public final float moisturePerChunkX;

    /**
     * 每向东一个区块（+X），温度噪声的变化量。
     * 默认 -0.000025（海洋性气候轻微降温），2000 区块 ≈ -0.05
     */
    public final float tempPerChunkX;

    /**
     * 每向北一个区块（+Y），植被密度增加的量。
     * 默认 0.03，20 区块 ≈ +0.6（使 FOREST 接近 DENSE_FOREST 级别）。
     * 用于 {@code Chunk.placeVegetation()} 修正 biome 的 treeDensity/grassDensity。
     */
    public final float forestDensityPerChunkY;

    /**
     * 每向东一个区块（+X），植被密度增加的量。
     * 默认 0.01（轻微），20 区块 ≈ +0.2。
     */
    public final float forestDensityPerChunkX;

    /**
     * 创建方向梯度配置（向后兼容 4 参数版本，无植被密度梯度）。
     */
    public DirectionalGradients(float elevationPerChunkY, float tempPerChunkY,
                                float moisturePerChunkX, float tempPerChunkX) {
        this(elevationPerChunkY, tempPerChunkY, moisturePerChunkX, tempPerChunkX,
                0.0f, 0.0f);
    }

    /**
     * 创建方向梯度配置（完整版，含植被密度梯度）。
     *
     * @param elevationPerChunkY     每向北一个区块，海拔增加量
     * @param tempPerChunkY          每向北一个区块，温度变化量
     * @param moisturePerChunkX      每向东一个区块，湿度增加量
     * @param tempPerChunkX          每向东一个区块，温度变化量
     * @param forestDensityPerChunkY 每向北一个区块，植被密度增加量
     * @param forestDensityPerChunkX 每向东一个区块，植被密度增加量
     */
    public DirectionalGradients(float elevationPerChunkY, float tempPerChunkY,
                                float moisturePerChunkX, float tempPerChunkX,
                                float forestDensityPerChunkY, float forestDensityPerChunkX) {
        this.elevationPerChunkY = elevationPerChunkY;
        this.tempPerChunkY = tempPerChunkY;
        this.moisturePerChunkX = moisturePerChunkX;
        this.tempPerChunkX = tempPerChunkX;
        this.forestDensityPerChunkY = forestDensityPerChunkY;
        this.forestDensityPerChunkX = forestDensityPerChunkX;
    }

    /**
     * 获取指定区块坐标的海拔偏移量。
     *
     * @param chunkY 区块 Y 坐标
     * @return 海拔偏移（约 -1 ~ 1 范围，与 Perlin 噪声值同量级）
     */
    public double getElevationOffset(int chunkY) {
        return (double) elevationPerChunkY * chunkY;
    }

    /**
     * 获取指定区块坐标的温度偏移量。
     *
     * @param chunkX 区块 X 坐标
     * @param chunkY 区块 Y 坐标
     * @return 温度偏移
     */
    public double getTemperatureOffset(int chunkX, int chunkY) {
        return (double) tempPerChunkY * chunkY + (double) tempPerChunkX * chunkX;
    }

    /**
     * 获取指定区块坐标的湿度偏移量。
     *
     * @param chunkX 区块 X 坐标
     * @return 湿度偏移
     */
    public double getMoistureOffset(int chunkX) {
        return (double) moisturePerChunkX * chunkX;
    }

    /**
     * 获取指定区块坐标的植被密度偏移量。
     *
     * <p>用于 {@code Chunk.placeVegetation()} 修正 biome 的 treeDensity/grassDensity。
     * 北方（+Y）森林更密，东方（+X）轻微增密。
     *
     * @param chunkX 区块 X 坐标
     * @param chunkY 区块 Y 坐标
     * @return 植被密度偏移（约 0 ~ 0.5 范围，叠加到 biome.treeDensity 上）
     */
    public double getForestDensityOffset(int chunkX, int chunkY) {
        return (double) forestDensityPerChunkY * chunkY
                + (double) forestDensityPerChunkX * chunkX;
    }

    @Override
    public String toString() {
        return String.format(
                "Gradients{elevY=%.5f, tempY=%.5f, moistX=%.5f, tempX=%.5f, forestY=%.4f, forestX=%.4f}",
                elevationPerChunkY, tempPerChunkY, moisturePerChunkX, tempPerChunkX,
                forestDensityPerChunkY, forestDensityPerChunkX);
    }
}
