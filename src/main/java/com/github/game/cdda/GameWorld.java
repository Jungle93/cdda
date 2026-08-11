package com.github.game.cdda;

import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.creature.config.CreatureRegistry;
import com.github.game.cdda.creature.energy.EnergyFlowManager;
import com.github.game.cdda.game.WorldSettings;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemRegistry;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.chunk.ChunkManager;
/**
 * 游戏世界（逻辑层）。创建并持有所有游戏子系统，是游戏状态的唯一权威来源。
 *
 * <p>职责：
 * <ul>
 *   <li>创建和管理所有游戏子系统（时间、回合、温度、代谢、口渴）</li>
 *   <li>管理地图数据（ChunkManager）</li>
 *   <li>管理玩家实体（Player）</li>
 *   <li>提供子系统访问接口</li>
 * </ul>
 *
 * <p>与显示层（GameScene）完全解耦：
 * <ul>
 *   <li>不关心渲染、输入、摄像机</li>
 *   <li>由 MainScreen 创建，注入给 GameScene 和 HUD 面板</li>
 * </ul>
 *
 * <h3>初始化分两阶段：</h3>
 * <ol>
 *   <li>构造函数 — 创建所有子系统，搜索出生点，创建 Player（tile 坐标）</li>
 *   <li>{@link #initPlayerForRendering(int, int)} — 设置像素尺寸和世界查询接口（需 Renderer 的 FontMetrics）</li>
 * </ol>
 */
public class GameWorld {

    // ── 游戏子系统 ──────────────────────────────────
    private final WorldMap worldMap;
    private final ChunkManager chunkManager;
    private final GameCalendar gameTime;
    private final TurnManager turnManager;
    private final TemperatureManager temperatureManager;
    private final MetabolismManager metabolismManager;
    private final HydrationManager hydrationManager;
    private final CreatureManager creatureManager;
    private final GroundItemManager groundItemManager;
    private final EnergyFlowManager energyFlowManager;

    // ── 玩家 ──────────────────────────────────
    private final Player player;

    /**
     * 创建游戏世界，初始化所有子系统。
     *
     * @param settings   世界设置（种子等）
     * @param startMonth 起始月份
     * @param startHour  起始小时（0-23）
     */
    public GameWorld(WorldSettings settings, Month startMonth, int startHour) {
        // 0) 世界地图（大地图 — 生物群落分布）
        worldMap = new WorldMap(settings.getSeed());

        // 1) 地图数据（小地图 — 由世界地图驱动区块生成）
        chunkManager = new ChunkManager(settings.getSeed(), Constants.DEFAULT_PRELOAD_RADIUS, worldMap);

        // 2) 时间系统
        gameTime = new GameCalendar(startMonth, startHour);
        turnManager = new TurnManager(gameTime);

        // 3) 温度系统
        temperatureManager = new TemperatureManager(gameTime);

        // 4) 代谢系统
        metabolismManager = new MetabolismManager(gameTime, temperatureManager);

        // 5) 口渴系统
        hydrationManager = new HydrationManager(gameTime, temperatureManager);

        // 6) 生物系统
        ItemRegistry.loadAll();
        CreatureRegistry.loadAll();
        com.github.game.cdda.world.vegetation.VegetationRegistry.loadAll();
        com.github.game.cdda.crafting.RecipeRegistry.loadAll();
        creatureManager = new CreatureManager(chunkManager, turnManager);

        // 7) 地面物品系统
        groundItemManager = new GroundItemManager();
        creatureManager.setGroundItemManager(groundItemManager);

        // 7.5) 能量流动系统
        energyFlowManager = new EnergyFlowManager();
        creatureManager.setEnergyFlowManager(energyFlowManager);

        // 8) 连接区块管理器与生物管理器（新区块加载时触发新生物生成）
        chunkManager.setCreatureManager(creatureManager);

        // 8) 玩家（在可通行的出生点创建）
        int[] spawn = findPassableSpawn();
        player = new Player(spawn[0], spawn[1]);
    }

    // ── 延迟初始化 ──────────────────────────────────

    /**
     * 为玩家设置渲染所需的像素尺寸和世界查询接口。
     * 须在首次渲染时调用（需要 Renderer 的 FontMetrics 来确定瓦片像素大小）。
     *
     * @param tileWidth  瓦片像素宽度
     * @param tileHeight 瓦片像素高度
     */
    public void initPlayerForRendering(int tileWidth, int tileHeight) {
        player.initDimensions(tileWidth, tileHeight);
        player.initWorld(chunkManager, tileWidth, tileHeight);
    }

    /**
     * 将玩家注册到回合系统，设置初始能量。
     * 须在 initPlayerForRendering 之后调用。
     */
    public void registerPlayerToTurnSystem() {
        turnManager.addEntity(player);
        player.addEnergy(TurnManager.ENERGY_PER_ACTION);
    }

    /**
     * 更新现实气泡中心位置。
     * 在玩家跨越区块边界时调用，使气泡外的动物静止、气泡内的恢复活跃。
     */
    public void updateRealityBubble() {
        creatureManager.updateBubble(player.getTileX(), player.getTileY());
    }

    /**
     * 生成初始生物。
     * 在玩家周围 3x3 区块范围内生成动物。
     * 须在 initPlayerForRendering 之后调用。
     */
    public void spawnInitialCreatures() {
        int tileWidth = player.getPixelWidth();
        int tileHeight = player.getPixelHeight();
        if (tileWidth > 0 && tileHeight > 0) {
            creatureManager.spawnInitialCreatures(player.getTileX(), player.getTileY(), 1);
        }
    }

    // ── 出生点搜索 ──────────────────────────────────

    /**
     * 从原点 (0,0) 开始螺旋搜索可通行的瓦片，返回其瓦片坐标。
     * 搜索顺序：原点 → 右 → 下 → 左 → 上（螺旋扩大），最多搜索 20 圈。
     *
     * @return 瓦片坐标 [tileX, tileY]
     */
    private int[] findPassableSpawn() {
        int maxRadius = 20;
        for (int r = 0; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    // 只检查最外圈（内圈已检查过）
                    if (r > 0 && Math.abs(dx) < r && Math.abs(dy) < r) continue;

                    TileType tile = chunkManager.getTile(dx, dy);
                    if (tile != null && tile.isPassable()) {
                        GameLog.getInstance().log(
                                String.format("出生点: 瓦片(%d,%d) %s", dx, dy, tile.getName()));
                        return new int[]{dx, dy};
                    }
                }
            }
        }
        // 找不到可通行瓦片，回退到原点
        return new int[]{0, 0};
    }

    // ── 访问器 ──────────────────────────────────

    public WorldMap getWorldMap() { return worldMap; }
    public ChunkManager getChunkManager() { return chunkManager; }
    public GameCalendar getGameTime() { return gameTime; }
    public TurnManager getTurnManager() { return turnManager; }
    public TemperatureManager getTemperatureManager() { return temperatureManager; }
    public MetabolismManager getMetabolismManager() { return metabolismManager; }
    public HydrationManager getHydrationManager() { return hydrationManager; }
    public CreatureManager getCreatureManager() { return creatureManager; }
    public GroundItemManager getGroundItemManager() { return groundItemManager; }
    public EnergyFlowManager getEnergyFlowManager() { return energyFlowManager; }
    public Player getPlayer() { return player; }

    /** 清理资源（游戏退出时调用） */
    public void dispose() {
        chunkManager.shutdown();
    }
}
