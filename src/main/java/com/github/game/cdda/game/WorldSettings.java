package com.github.game.cdda.game;

/**
 * 世界设置数据类。
 * 保存世界生成所需的参数，与 UI 解耦。
 * 后续世界生成逻辑可直接从此对象读取配置。
 */
public class WorldSettings {

    /** 世界生成种子（默认随机） */
    private long seed;
    /** 地图宽度（瓦片数） */
    private int mapWidth;
    /** 地图高度（瓦片数） */
    private int mapHeight;
    /** 方向梯度（默认 ~2000 区块尺度） */
    private DirectionalGradients gradients = DirectionalGradients.DEFAULT;

    /** 创建默认世界设置 */
    public WorldSettings() {
        this.seed = System.currentTimeMillis();
        this.mapWidth = 64;
        this.mapHeight = 64;
    }

    /** 使用指定种子创建世界设置 */
    public WorldSettings(long seed) {
        this.seed = seed;
        this.mapWidth = 64;
        this.mapHeight = 64;
    }

    /** 从已有设置复制 */
    public WorldSettings(WorldSettings other) {
        this.seed = other.seed;
        this.mapWidth = other.mapWidth;
        this.mapHeight = other.mapHeight;
        this.gradients = other.gradients;
    }

    // ── 访问器 ──────────────────────────────────────

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public int getMapWidth() { return mapWidth; }
    public void setMapWidth(int mapWidth) { this.mapWidth = mapWidth; }

    public int getMapHeight() { return mapHeight; }
    public void setMapHeight(int mapHeight) { this.mapHeight = mapHeight; }

    public DirectionalGradients getGradients() { return gradients; }
    public void setGradients(DirectionalGradients gradients) { this.gradients = gradients; }
}
