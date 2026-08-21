package com.github.game.cdda.screen.scene;

import com.github.game.cdda.Constants;
import com.github.game.cdda.config.ConfigManager;
import com.github.game.cdda.creature.Creature;
import com.github.game.cdda.creature.Player;
import com.github.game.engine.core.EngineServices;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.item.action.ChopTreeAction;
import com.github.game.cdda.item.world.GroundItem;
import com.github.game.cdda.item.world.GroundItemManager;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.input.InputStateMachine;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.Scene;
import com.github.game.engine.core.scene.Viewport;
import com.github.game.engine.core.sprite.SpriteManager;
import com.github.game.cdda.game.TurnManager;
import com.github.game.cdda.game.MetabolismManager;
import com.github.game.cdda.game.HydrationManager;
import com.github.game.cdda.log.GameLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.world.TileMap;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.cdda.world.vegetation.VegetationDefinition;
import com.github.game.cdda.world.vegetation.VegetationRegistry;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏世界场景（显示层）。负责世界渲染、摄像机跟随和输入处理。
 *
 * <p>职责（仅显示层）：
 * <ul>
 *   <li>TileMap 渲染</li>
 *   <li>Camera 管理（跟随玩家）</li>
 *   <li>输入处理（WASD 移动、等待、检查模式等）</li>
 *   <li>调试信息叠加</li>
 *   <li>FPS 计算</li>
 * </ul>
 *
 * <p>不创建任何游戏子系统——所有游戏状态通过 {@link GameWorld} 访问。
 *
 * <p>需要延迟初始化：Camera 的创建依赖 Renderer 的 FontMetrics，
 * 通过 {@link #ensureInitialized(Renderer)} 在首帧渲染时完成。
 */
public class GameScene extends Scene implements TileMap.TileLayerRenderer {

    private static final Logger logger = LoggerFactory.getLogger(GameScene.class);

    private final GameWorld world;
    private final int fontSize;

    // ── 渲染层组件 ──────────────────────────────────
    private TileMap tileMap;
    private Camera camera;

    // ── 便捷引用（来自 GameWorld） ──────────────────────────────────
    private ChunkManager chunkManager;
    private Player player;
    private TurnManager turnManager;
    private MetabolismManager metabolismManager;
    private HydrationManager hydrationManager;
    private CreatureManager creatureManager;
    private GroundItemManager groundItemManager;

    // ── 观察模式（Look） ──────────────────────────────────

    /** 输入状态机引用（由 MainScreen 注入，用于查询当前模式） */
    private InputStateMachine inputStateMachine;

    /** 观察光标相对玩家瓦片的偏移 */
    private int lookCursorDx = 0, lookCursorDy = 0;

    /** 可见生物列表（Tab 循环用，按距离排序） */
    private List<com.github.game.cdda.creature.Creature> visibleCreatureList = new ArrayList<>();

    /** 当前循环到的生物索引 */
    private int creatureCycleIndex = -1;

    /** 瓦片尺寸是否已初始化 */
    private volatile boolean initialized = false;

    // ── FPS 计算 ──────────────────────────────────
    private long fpsFrameCount = 0;
    private long fpsLastTime = System.currentTimeMillis();
    private int currentFps = 0;

    // ── 行走音效看门狗 ──────────────────────────────────

    /** 行走音效动作名（用于 playActionSound/stopActionSound 绑定） */
    private static final String WALK_ACTION = "walk";

    /**
     * 看门狗续期阈值（毫秒）。
     * 玩家超过此时间未移动，自动停止行走音效。
     * 需大于 OS 按键重复间隔（通常 ~250ms），避免正常连走时被误停。
     */
    private static final long WALK_WATCHDOG_MS = 500;

    /** 上次成功移动的时间戳（毫秒），用于看门狗续期 */
    private long lastMoveTimeMs = 0;

    // ── 静态实例引用（供 ChopTreeAction 等外部类访问） ──────────────────────────────────

    /** 当前活跃的 GameScene 实例（单例场景，与 GameLog 模式一致） */
    private static GameScene activeInstance;

    /** 获取当前活跃的 GameScene 实例 */
    public static GameScene getActiveInstance() { return activeInstance; }

    // ── 玩家活动系统 ──────────────────────────────────

    /** 砍伐音效动作名 */
    private static final String CHOP_ACTION = "chop";

    /**
     * 当前活跃的玩家活动（多回合长动作）。
     * 非 null 时阻止玩家其他输入（仅 ESC 可取消）。
     * 活动每回合前进一步，期间生物正常行动。
     */
    private com.github.game.cdda.screen.Activity activeActivity;

    /**
     * 创建游戏世界场景。
     *
     * @param viewport 屏幕视口区域（游戏区域）
     * @param world    游戏世界（逻辑层，包含所有子系统）
     * @param fontSize 字体大小（pt），用于瓦片渲染
     */
    public GameScene(Viewport viewport, GameWorld world, int fontSize) {
        super(viewport);
        activeInstance = this;
        this.world = world;
        this.fontSize = fontSize;
    }

    @Override
    public void init() {
        // 获取便捷引用
        chunkManager = world.getChunkManager();
        player = world.getPlayer();
        turnManager = world.getTurnManager();
        metabolismManager = world.getMetabolismManager();
        hydrationManager = world.getHydrationManager();
        creatureManager = world.getCreatureManager();
        groundItemManager = world.getGroundItemManager();

        // 仅创建渲染层组件
        tileMap = new TileMap(chunkManager, fontSize);
    }

    /**
     * 首次渲染时初始化 Camera 和 Player 的渲染属性。
     * 需要 Renderer 的 FontMetrics 来测量瓦片像素尺寸。
     */
    public void ensureInitialized(Renderer renderer) {
        if (initialized) return;

        // 根据图形包决定瓦片尺寸
        if (SpriteManager.hasActivePack()) {
            int tileSize = SpriteManager.getTileSize();
            tileMap.initTileSize(tileSize, tileSize);
        } else {
            tileMap.initTileSize(renderer);
        }

        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();

        // 初始化玩家的像素尺寸和世界查询接口
        world.initPlayerForRendering(tileW, tileH);

        // 创建摄像机 — 视口尺寸 = Scene viewport 尺寸
        camera = new Camera(viewport.getWidth(), viewport.getHeight());
        setCamera(camera);

        // 从配置加载相机缩放级别
        int zoomLevel = ConfigManager.getInstance().getCameraZoomLevel();
        camera.setZoomLevel(zoomLevel);

        // 立即加载玩家周围的区块
        chunkManager.updateChunks(player.getWorldX(), player.getWorldY(), tileW, tileH);

        // 将玩家注册到回合系统
        world.registerPlayerToTurnSystem();

        // 生成初始生物
        world.spawnInitialCreatures();

        // 记录开局日志
        GameLog.getInstance().log("游戏开始。方向键移动/攻击，5等待，L观察，C对话，M大地图，E进食，G拾取，D丢弃，I背包，`调试，ESC菜单");
        GameLog.getInstance().log(String.format("周围生成了 %d 个生物", creatureManager.getCreatureCount()));

        // 新手引导提示
        GameLog.getInstance().log("—— 新手提示 ——");
        GameLog.getInstance().log("按 [?] 或 F1 查看完整帮助");
        GameLog.getInstance().log("按 [I] 打开背包查看初始装备");
        GameLog.getInstance().log("按 [F2] 打开合成界面");
        GameLog.getInstance().log("按 [C] 与附近 NPC 对话");
        GameLog.getInstance().log("按 [L] 观察周围环境");

        initialized = true;
    }

    @Override
    public void update(long deltaTime) {
        if (!initialized) return;

        // 世界地图打开时，暂停游戏逻辑更新（摄像机不跟随、区块不加载）
        if (inputStateMachine != null && inputStateMachine.isWorldMapOpen()) return;

        // FPS 计算
        fpsFrameCount++;
        long now = System.currentTimeMillis();
        long elapsed = now - fpsLastTime;
        if (elapsed >= 1000) {
            currentFps = (int) (fpsFrameCount * 1000 / elapsed);
            fpsFrameCount = 0;
            fpsLastTime = now;
        }

        // 1) 根据玩家位置更新区块加载
        chunkManager.updateChunks(
                player.getWorldX(), player.getWorldY(),
                player.getPixelWidth(), player.getPixelHeight()
        );

        // 2) 摄像机跟随玩家
        camera.follow(
                player.getWorldX(), player.getWorldY(),
                player.getPixelWidth(), player.getPixelHeight()
        );

        // 3) 应用植物生长后台计算结果（每帧调用，批量执行变更）
        world.applyPendingPlantGrowthMutations();

        // 4) 应用生物回合后台计算结果（出生、死亡、迁徙）
        creatureManager.applyPendingCreatureMutations();

        // 5) 行走音效看门狗：玩家长时间未移动时自动停止
        if (lastMoveTimeMs > 0) {
            long idle = System.currentTimeMillis() - lastMoveTimeMs;
            if (idle > WALK_WATCHDOG_MS) {
                var audio = EngineServices.audio;
                if (audio != null && audio.isActionSoundPlaying(WALK_ACTION)) {
                    audio.stopActionSound(WALK_ACTION);
                }
                lastMoveTimeMs = 0; // 重置，避免重复检查
            }
        }

        // 6) 玩家活动状态更新（完成检测）
        updateActiveActivity();
    }

    /**
     * 活动状态更新（每帧调用）。
     * 检查当前活动是否完成，完成则调用 finish() 并清除。
     *
     * <p>FallingActivity 是例外 — 它基于实时，直接在 update() 中检查完成。
     *
     * <p>其他活动（如砍伐）每帧自动推进：玩家被锁定无法动作，
     * 所以需要在这里自动触发回合处理（生物行动 + 补满移动点 + 活动前进一步）。
     */
    private void updateActiveActivity() {
        if (activeActivity == null) return;

        // FallingActivity 是实时动画，每帧检查是否完成
        if (activeActivity instanceof FallingActivity) {
            if (activeActivity.isComplete()) {
                activeActivity.finish();
                activeActivity = null;
                // 活动结束后恢复玩家移动点，允许玩家行动
                player.addMoves(player.getSpeed());
            }
            return;
        }

        // 其他活动（如砍伐）：玩家被锁定，自动推进回合
        // 1. 如果玩家有剩余移动点，先耗尽（确保能触发回合处理）
        if (turnManager.hasMoves(player)) {
            player.spendMoves(player.getMoves());
        }
        // 2. 触发生物行动 + 补满移动点
        requestCreatureTurns();
        turnManager.processRound();
        // 3. 活动前进一步（内部会消耗移动点、推进时钟、检查完成）
        if (!activeActivity.isComplete()) {
            activeActivity.update();
        }

        // 砍伐完成后转换为倒地动画（在 update 之后检查，避免同一帧内渲染两个进度条）
        if (activeActivity instanceof ChopActivity chopActivity
                && chopActivity.shouldTransitionToFalling()) {
            activeActivity = new FallingActivity(
                    chopActivity.getTileX(), chopActivity.getTileY(),
                    chopActivity.getSpeciesId(), chopActivity.getOriginalTile());
        }
    }

    @Override
    public void dispose() {
        // 场景销毁时停止行走和砍伐音效，避免残留
        var audio = EngineServices.audio;
        if (audio != null) {
            audio.stopActionSound(WALK_ACTION);
            audio.stopActionSound(CHOP_ACTION);
        }
        lastMoveTimeMs = 0;
        if (activeActivity != null) {
            activeActivity.cancel();
            activeActivity = null;
        }
    }

    @Override
    public void render(Renderer renderer) {
        if (!initialized) return;

        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();

        // 按 tile 图层循环渲染（地形 → 物品 → 生物 → 覆盖层）
        // this 实现 TileLayerRenderer，提供物品和生物图层回调
        tileMap.render(renderer, camera, this);

        // 渲染陷阱（在生物层之上、玩家高亮之下）
        renderTraps(renderer, tileW, tileH);

        // 渲染玩家高亮光环（在玩家角色之下，帮助快速定位）
        renderPlayerHighlight(renderer, tileW, tileH);

        // 渲染玩家（在生物层之上）
        player.render(renderer, camera, tileW, tileH);

        // 渲染活动相关的 UI（砍伐进度条、倒地动画等）
        if (activeActivity != null) {
            activeActivity.render(renderer, tileW, tileH);
        }

        // 渲染调试信息（场景局部坐标，左上角）
        renderDebugInfo(renderer);

        // 渲染观察模式光标高亮（在玩家之上）
        renderLookCursorHighlight(renderer, tileW, tileH);

        // 渲染观察模式状态栏（场景局部坐标，底部）
        renderLookStatusBar(renderer, tileW, tileH);

        // 渲染方向选择提示
        renderDirectionSelectHint(renderer);

        // 渲染昼夜色调叠加（覆盖整个游戏区域，营造昼夜氛围）
        DayNightOverlay.render(renderer, world.getGameTime(),
                viewport.getWidth(), viewport.getHeight());
    }

    /**
     * 渲染玩家高亮光环。
     * 在玩家脚下绘制半透明白色圆形光晕，帮助玩家在复杂地形中快速定位。
     * 使用 fillOval + drawOval 组合，alpha 值较低以不遮挡地形。
     */
    private void renderPlayerHighlight(Renderer renderer, int tileW, int tileH) {
        double zoom = camera.getZoom();
        int scaledW = (int) (tileW * zoom);
        int scaledH = (int) (tileH * zoom);

        // 光环尺寸（1.3 倍瓦片）
        int glowSize = (int) (scaledW * 1.3);
        int viewX = camera.toViewX(player.getWorldX());
        int viewY = camera.toViewY(player.getWorldY());
        int glowX = viewX - (glowSize - scaledW) / 2;
        int glowY = viewY - (glowSize - scaledH) / 2;

        // 微弱脉冲动画（alpha 60-100）
        long time = System.currentTimeMillis();
        int alpha = 80 + (int) (20 * Math.sin(time / 500.0));

        // 半透明白色填充圆
        renderer.setColor(new Color(255, 255, 255, alpha));
        renderer.fillOval(glowX, glowY, glowSize, glowSize);

        // 白色边框（更明显）
        renderer.setColor(new Color(255, 255, 255, Math.min(255, alpha + 80)));
        renderer.drawOval(glowX, glowY, glowSize, glowSize);
    }

    /**
     * 渲染陷阱层。
     * 在生物层之上、玩家高亮之下绘制。
     * - ARMED: 半透明白色 '∧' 标记
     * - TRIGGERED（有捕获）: 红色 '⚑' 标记
     * - TRIGGERED（空触发）: 橙色 '×' 标记
     */
    private void renderTraps(Renderer renderer, int tileW, int tileH) {
        var trapManager = world.getTrapManager();
        if (trapManager == null) return;

        double zoom = camera.getZoom();
        int scaledW = (int) (tileW * zoom);
        int scaledH = (int) (tileH * zoom);

        // 计算可见范围
        int viewStartX = camera.getX();
        int viewStartY = camera.getY();
        int viewWidth = camera.getZoomedViewportWidth();
        int viewHeight = camera.getZoomedViewportHeight();

        int startTileX = viewStartX / tileW;
        int startTileY = viewStartY / tileH;
        int endTileX = (viewStartX + viewWidth) / tileW;
        int endTileY = (viewStartY + viewHeight) / tileH;

        for (var trap : trapManager.getAllTraps()) {
            int tx = trap.getTileX();
            int ty = trap.getTileY();
            // 只渲染可见范围内的陷阱
            if (tx < startTileX || tx > endTileX || ty < startTileY || ty > endTileY) continue;

            int viewX = camera.toViewX(tx * tileW);
            int viewY = camera.toViewY(ty * tileH);

            if (trap.getState() == com.github.game.cdda.trap.PlacedTrap.State.ARMED) {
                // ARMED: 半透明白色 '∧'
                renderer.setColor(new Color(255, 255, 255, 150));
                int fontSize = Math.max(8, (int) (scaledW * 0.7));
                renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
                renderer.drawText("∧", viewX, viewY + scaledH - 2);
            } else if (trap.hasCapture()) {
                // TRIGGERED + 捕获: 红色 '⚑'
                renderer.setColor(new Color(255, 60, 60, 200));
                int fontSize = Math.max(8, (int) (scaledW * 0.7));
                renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
                renderer.drawText("⚑", viewX, viewY + scaledH - 2);
            } else {
                // TRIGGERED 空触发: 橙色 '×'
                renderer.setColor(new Color(255, 165, 0, 180));
                int fontSize = Math.max(8, (int) (scaledW * 0.7));
                renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
                renderer.drawText("×", viewX, viewY + scaledH - 2);
            }
        }
    }

    /**
     * 渲染调试信息（游戏区域左上角）。
     * 各项显示由 Constants.DEBUG_SHOW_* 开关控制。
     */
    private void renderDebugInfo(Renderer renderer) {
        if (!Constants.SHOW_DEBUG_INFO) return;

        renderer.setColor(Color.YELLOW);
        int debugFontSize = Math.max(10, fontSize - 2);
        renderer.setFont(new Font("Monospaced", Font.PLAIN, debugFontSize));

        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();

        // 第一行：位置 + 生物群落 + 摄像机 + 区块 + FPS
        StringBuilder sb = new StringBuilder();
        if (Constants.DEBUG_SHOW_TILE_POS && tileW > 0 && tileH > 0) {
            int ptx = Math.floorDiv(player.getWorldX(), tileW);
            int pty = Math.floorDiv(player.getWorldY(), tileH);
            sb.append(String.format("瓦片:(%d,%d)", ptx, pty));
            // 显示当前生物群落
            com.github.game.cdda.world.biome.BiomeType biome =
                    world.getWorldMap().getBiomeAt(ptx, pty);
            sb.append(String.format(" [%s]", biome.getLocalizedName()));
        }
        if (Constants.DEBUG_SHOW_CAMERA) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.format("摄像机:(%d,%d)", camera.getX(), camera.getY()));
        }
        if (Constants.DEBUG_SHOW_CHUNK_COUNT) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.format("区块:%d", chunkManager.getLoadedChunkCount()));
        }
        if (Constants.DEBUG_SHOW_FPS) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.format("FPS:%d", currentFps));
        }

        if (sb.length() > 0) {
            // 用群落的颜色显示（如果包含群落信息）
            int ptx = Math.floorDiv(player.getWorldX(), tileW);
            int pty = Math.floorDiv(player.getWorldY(), tileH);
            com.github.game.cdda.world.biome.BiomeType biome =
                    world.getWorldMap().getBiomeAt(ptx, pty);
            renderer.setColor(biome.getColor().brighter());
            renderer.drawText(sb.toString(), 4, debugFontSize + 2);
        }

        // 第二行：季节 + 环境温度
        if (Constants.DEBUG_SHOW_TEMPERATURE) {
            renderer.setColor(world.getGameTime().getSeason().getColor());
            String tempStr = String.format("%s  环境:%.1f°C %s",
                    world.getGameTime().getSeason().getFullName(),
                    world.getTemperatureManager().getTemperature(),
                    world.getTemperatureManager().getTemperatureDescriptor());
            renderer.drawText(tempStr, 4, (debugFontSize + 2) * 2);
        }

        // 第三行：代谢信息（体温 + 饥饿）
        renderer.setColor(metabolismManager.hasCriticalTemperature()
                ? new Color(255, 80, 80) : Color.CYAN);
        String metabStr = String.format("体温:%.1f°C %s  能量:%d%%",
                metabolismManager.getBodyTemperature(),
                metabolismManager.getBodyTempDescriptor(),
                metabolismManager.getHungerPercent());
        int metabY = Constants.DEBUG_SHOW_TEMPERATURE
                ? (debugFontSize + 2) * 3
                : (debugFontSize + 2) * 2;
        renderer.drawText(metabStr, 4, metabY);

        // 第四行：口渴信息
        renderer.setColor(hydrationManager.getThirstColor());
        String thirstStr = String.format("水分:%d%% %s",
                hydrationManager.getWaterPercent(),
                hydrationManager.getThirstDescriptor());
        renderer.drawText(thirstStr, 4, metabY + (debugFontSize + 2));
    }

    // ── 玩家活动系统方法 ──────────────────────────────────

    /**
     * 是否正在进行多回合活动（阻止玩家输入）。
     * 活动期间玩家不能移动或执行其他动作，仅 ESC 可取消。
     */
    public boolean isChopping() {
        return activeActivity != null && activeActivity.blocksInput();
    }

    /**
     * 开始砍伐。由 ChopTreeAction 调用。
     * 创建 ChopActivity 并分配给玩家。活动每回合前进一步，
     * 期间生物正常行动、世界正常更新。
     *
     * @param tileX     目标瓦片 X
     * @param tileY     目标瓦片 Y
     * @param speciesId 植被物种 ID（用于掉落计算，可为 null）
     * @param tile      原始瓦片类型（TREE 或 BUSH）
     */
    public void startChopping(int tileX, int tileY, String speciesId, TileType tile) {
        activeActivity = new ChopActivity(tileX, tileY, speciesId, tile);
        // 耗尽玩家移动点，确保 endOfPlayerRound() 能触发活动推进
        player.spendMoves(player.getMoves());
        activeActivity.start();
    }

    /** 取消当前活动（ESC 触发）。已消耗的回合不退。 */
    private void cancelChopping() {
        if (activeActivity != null) {
            activeActivity.cancel();
            activeActivity = null;
        }
    }

    /**
     * 回合结束处理。
     * 当玩家移动点耗尽时调用：触发生物行动 → 补满移动点 → 推进活动 → 植物生长。
     *
     * <p>活动每回合前进一步（CDDA 模式），使生物在每步之间正常行动。
     * 取代了旧的"所有动作在同步循环中一次性完成"的设计。
     */
    private void endOfPlayerRound() {
        if (!turnManager.hasMoves(player)) {
            requestCreatureTurns();
            turnManager.processRound();

            // 活动前进一步（在移动点补满之后，为下一步消耗准备）
            if (activeActivity != null && !activeActivity.isComplete()) {
                activeActivity.update();
            }
        }
        world.updatePlantGrowth();

        // 代谢/脱水伤害（在回合结束后统一结算）
        applyMetabolismDamage();
    }

    /**
     * 根据代谢和水分状态对玩家造成伤害。
     *
     * <p>伤害逻辑：
     * <ul>
     *   <li>能量耗尽且体温异常 → 扣 HP（饥饿/失温/中暑）</li>
     *   <li>极度脱水 → 扣 HP（脱水）</li>
     *   <li>HP 归零 → 触发对应死亡原因</li>
     * </ul>
     */
    private void applyMetabolismDamage() {
        if (!player.isAlive()) return;

        int tempDamage = metabolismManager.calcTemperatureDamage();
        if (tempDamage > 0) {
            player.takeDamage(tempDamage);
            String reason = metabolismManager.getTemperatureDeathReason();
            GameLog.getInstance().log(String.format("%s（-%d HP）", reason, tempDamage));

            if (!player.isAlive()) {
                handlePlayerDeath(com.github.game.cdda.creature.energy.DeathCause.TEMPERATURE,
                        "你因" + reason + "而倒下");
                return;
            }
        }

        int waterDamage = hydrationManager.calcDehydrationDamage();
        if (waterDamage > 0) {
            player.takeDamage(waterDamage);
            String desc = hydrationManager.getThirstDescriptor();
            GameLog.getInstance().log(String.format("%s（-%d HP）", desc, waterDamage));

            if (!player.isAlive()) {
                handlePlayerDeath(com.github.game.cdda.creature.energy.DeathCause.DEHYDRATION,
                        "你因极度脱水而倒下");
            }
        }
    }

    /**
     * 处理玩家死亡。
     *
     * @param cause 死亡原因
     * @param message 死亡消息
     */
    private void handlePlayerDeath(com.github.game.cdda.creature.energy.DeathCause cause,
                                   String message) {
        GameLog.getInstance().log(message);
        // TODO: 显示游戏结束画面
        logger.info("玩家死亡 - 原因: {}, {}", cause, message);
    }

    // ── 活动内部类 ──────────────────────────────────

    /**
     * 砍树活动。多回合活动，每回合前进一步。
     * 完成后转为 FallingActivity（实时倒地动画）。
     *
     * <p>设计借鉴 Cataclysm-DDA 的 chop_tree_activity_actor 模式。
     */
    private class ChopActivity implements com.github.game.cdda.screen.Activity {
        private final int tileX, tileY;
        private final String speciesId;
        private final TileType originalTile;
        private final long roundsTotal;
        private long progress = 0;

        ChopActivity(int tileX, int tileY, String speciesId, TileType originalTile) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.speciesId = speciesId;
            this.originalTile = originalTile;
            this.roundsTotal = originalTile == TileType.TREE
                    ? Constants.CHOP_ROUNDS_TREE : Constants.CHOP_ROUNDS_BUSH;
        }

        @Override
        public void start() {
            var audio = EngineServices.audio;
            if (audio != null) {
                audio.stopActionSound(WALK_ACTION);
                audio.playActionSound(CHOP_ACTION, "audio/sfx/felling.mp3", 0.7f);
            }
            lastMoveTimeMs = 0;
            String vegName = originalTile == TileType.TREE ? "树" : "灌木";
            GameLog.getInstance().log(String.format("开始砍伐%s（需要 %d 回合）...", vegName, roundsTotal));
        }

        @Override
        public void update() {
            // 推进游戏时钟
            turnManager.addAction(player, Constants.CHOP_BASE_TIME);
            // 代谢消耗
            metabolismManager.addActionCost(Constants.MOVE_CALORIE_COST);
            metabolismManager.update();
            // 水分消耗
            hydrationManager.addAction(Constants.ADD_THIRST_COMBAT);
            hydrationManager.update();
            // 回合处理（生物行动 + 移动点补满）已在 updateActiveActivity() 中完成
            // 植物生长在 GameScene.update() 中每帧处理
            world.updatePlantGrowth();

            progress++;
            if (progress >= roundsTotal) {
                // 砍伐完成 — 播放倒地音效
                // 实际的 FallingActivity 创建由 updateActiveActivity() 处理，
                // 避免在同一帧内同时渲染 ChopActivity 和 FallingActivity 的进度条
                var audio = EngineServices.audio;
                if (audio != null) {
                    audio.stopActionSound(CHOP_ACTION);
                    audio.playSFX("audio/sfx/tree_fallen.mp3", false, 0.8f);
                }
                String vegName = originalTile == TileType.TREE ? "树" : "灌木";
                GameLog.getInstance().log(String.format("经过 %d 回合的砍伐，%s终于倒下了...", roundsTotal, vegName));
            }
        }

        /**
         * 砍伐完成后是否需要转换为倒地动画。
         * 由 updateActiveActivity() 检查并执行转换。
         */
        public boolean shouldTransitionToFalling() {
            return progress >= roundsTotal;
        }

        /** 获取目标瓦片 X 坐标（用于创建 FallingActivity） */
        public int getTileX() { return tileX; }
        /** 获取目标瓦片 Y 坐标（用于创建 FallingActivity） */
        public int getTileY() { return tileY; }
        /** 获取植被物种 ID（用于创建 FallingActivity） */
        public String getSpeciesId() { return speciesId; }
        /** 获取原始瓦片类型（用于创建 FallingActivity） */
        public TileType getOriginalTile() { return originalTile; }

        @Override
        public boolean isComplete() {
            return progress >= roundsTotal;
        }

        @Override
        public void finish() {
            // 由 FallingActivity 接手 — 不应直接调用
        }

        @Override
        public void cancel() {
            var audio = EngineServices.audio;
            if (audio != null) {
                audio.stopActionSound(CHOP_ACTION);
            }
            GameLog.getInstance().log("砍伐被取消了");
        }

        @Override
        public void render(Renderer renderer, int tileW, int tileH) {
            // 计算目标瓦片的屏幕坐标
            int viewX = tileX * tileW - (int) camera.getX();
            int viewY = tileY * tileH - (int) camera.getY();

            // 进度条尺寸
            int barWidth = tileW;
            int barHeight = 4;
            int barX = viewX;
            int barY = viewY - barHeight - 6;

            // 进度比例（砍伐进度）
            float progressRatio = (roundsTotal > 0)
                    ? Math.min(1.0f, (float) progress / roundsTotal)
                    : 0f;

            // 背景（深灰）
            renderer.setColor(new Color(50, 50, 50));
            renderer.fillRect(barX, barY, barWidth, barHeight);

            // 前景（黄色 — 表示砍伐中）
            renderer.setColor(new Color(255, 215, 0));
            renderer.fillRect(barX, barY, (int) (barWidth * progressRatio), barHeight);

            // 文字提示
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 10));
            renderer.setColor(Color.WHITE);
            String text = String.format("砍伐 %d/%d", progress, roundsTotal);
            int textX = barX + (barWidth - renderer.getTextWidth(text)) / 2;
            renderer.drawText(text, textX, barY - 2);
        }
    }

    /**
     * 树木倒地活动。实时动画（等待倒地音效播放完毕后完成）。
     * 完成后移除植被、生成掉落物。
     */
    private class FallingActivity implements com.github.game.cdda.screen.Activity {
        private final int tileX, tileY;
        private final String speciesId;
        private final TileType originalTile;
        private final long startMs;

        FallingActivity(int tileX, int tileY, String speciesId, TileType originalTile) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.speciesId = speciesId;
            this.originalTile = originalTile;
            this.startMs = System.currentTimeMillis();
        }

        @Override
        public void start() {
            // 音效已在 ChopActivity 完成时播放
        }

        @Override
        public void update() {
            // 实时活动 — 由 GameScene.update() 每帧检查完成
        }

        @Override
        public boolean isComplete() {
            return System.currentTimeMillis() - startMs >= Constants.FALL_SOUND_DURATION_MS;
        }

        @Override
        public void finish() {
            // 恢复地面层瓦片
            TileType groundTile = chunkManager.getGroundTile(tileX, tileY);
            chunkManager.setTile(tileX, tileY,
                    groundTile != null ? groundTile : TileType.GRASS);
            chunkManager.clearVegetation(tileX, tileY);

            // 生成掉落物
            int dropCount = ChopTreeAction.generateDrops(speciesId, world, tileX, tileY);

            // 日志
            String vegName = originalTile == TileType.TREE ? "树" : "灌木";
            if (dropCount > 0) {
                GameLog.getInstance().log(String.format("你砍倒了一棵%s，获得了 %d 件物品", vegName, dropCount));
            } else {
                GameLog.getInstance().log(String.format("你砍倒了一棵%s", vegName));
            }
        }

        @Override
        public void cancel() {
            // 倒地阶段不可取消
        }

        @Override
        public boolean blocksInput() {
            return true; // 倒地动画期间玩家仍被锁定
        }

        @Override
        public void render(Renderer renderer, int tileW, int tileH) {
            // 计算目标瓦片的屏幕坐标
            int viewX = tileX * tileW - (int) camera.getX();
            int viewY = tileY * tileH - (int) camera.getY();

            // 进度条尺寸
            int barWidth = tileW;
            int barHeight = 4;
            int barX = viewX;
            int barY = viewY - barHeight - 6;

            // 进度比例（倒地音效播放进度）
            long elapsed = System.currentTimeMillis() - startMs;
            float progress = Math.min(1.0f, (float) elapsed / Constants.FALL_SOUND_DURATION_MS);

            // 背景（深灰）
            renderer.setColor(new Color(50, 50, 50));
            renderer.fillRect(barX, barY, barWidth, barHeight);

            // 前景（橙色 — 表示倒下中）
            renderer.setColor(new Color(255, 165, 0));
            renderer.fillRect(barX, barY, (int) (barWidth * progress), barHeight);

            // 文字提示
            renderer.setFont(new Font("Monospaced", Font.PLAIN, 10));
            renderer.setColor(Color.WHITE);
            String text = "倒下...";
            int textX = barX + (barWidth - renderer.getTextWidth(text)) / 2;
            renderer.drawText(text, textX, barY - 2);
        }
    }

    // ── 输入处理 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        if (!initialized) return;

        // ── 砍伐期间拦截所有输入（ESC 可取消） ──
        if (isChopping()) {
            if (keyCode == KeyEvent.VK_ESCAPE) {
                cancelChopping();
            }
            return;
        }

        // ── 等待动作（时间流逝但不做其他事） ──
        if (handleWait(keyCode)) return;

        // ── 移动点检查：无移动点时无法行动 ──
        if (!turnManager.hasMoves(player)) {
            GameLog.getInstance().log("你太累了，先休息一下吧...");
            return;
        }

        // ── 网格式移动：每次按键移动恰好一个瓦片（仅方向键） ──
        // 移动即攻击：先检查目标位置是否有生物
        int dx = 0, dy = 0;
        switch (keyCode) {
            case KeyEvent.VK_UP:    dy = -1; break;
            case KeyEvent.VK_DOWN:  dy =  1; break;
            case KeyEvent.VK_LEFT:  dx = -1; break;
            case KeyEvent.VK_RIGHT: dx =  1; break;
            default: return;
        }

        // 目标瓦片坐标
        int targetTileX = player.getTileX() + dx;
        int targetTileY = player.getTileY() + dy;

        // 检查目标位置是否有生物 → 近战攻击
        com.github.game.cdda.creature.Creature target =
                creatureManager.getCreatureAtTile(targetTileX, targetTileY);

        if (target != null) {
            // 近战攻击（消耗 ATTACK_BASE_TIME）
            // 攻击时停止行走音效
            var attackAudio = EngineServices.audio;
            if (attackAudio != null) {
                attackAudio.stopActionSound(WALK_ACTION);
            }
            lastMoveTimeMs = 0;
            player.meleeAttack(target);
            turnManager.addAction(player, Constants.ATTACK_BASE_TIME);
            metabolismManager.addActionCost(Constants.MOVE_CALORIE_COST);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_COMBAT);
            hydrationManager.update();
            // 回合结束处理（移动点耗尽时触发：生物行动 + 补满 + 推进活动）
            endOfPlayerRound();
            return;
        }

        // 无生物 → 正常移动
        // 回合制：玩家行动后推进时间
        if (player.move(dx, dy)) {
            // 播放行走音效（循环，由看门狗在 update 中自动停止）
            var audio = EngineServices.audio;
            if (audio != null) {
                audio.playActionSound(WALK_ACTION, "audio/sfx/walk.mp3", 0.6f);
            }
            // 续期看门狗：记录本次移动时间
            lastMoveTimeMs = System.currentTimeMillis();
            turnManager.addAction(player, Constants.MOVE_BASE_TIME);
            metabolismManager.addActionCost(Constants.MOVE_CALORIE_COST);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_WALK);
            hydrationManager.update();
            // 回合结束处理（移动点耗尽时触发：生物行动 + 补满 + 推进活动）
            endOfPlayerRound();
        }
    }

    @Override
    public void onKeyReleased(int keyCode) {
        // 网格式移动无需处理按键释放
    }

    /**
     * 处理等待动作按键（5 = 等待一回合，- = 等待十回合）。
     * 由输入状态机在 NORMAL 模式下调用。
     *
     * <p>等待 = 主动耗尽所有移动点 → 触发生物回合 → 补满。
     *
     * @param keyCode 按键码
     * @return true 如果按键被消耗（是等待键），false 否则
     */
    public boolean handleWait(int keyCode) {
        if (keyCode == KeyEvent.VK_5) {
            // 无移动点时无法等待
            if (!turnManager.hasMoves(player)) {
                GameLog.getInstance().log("你太累了，先休息一下吧...");
                return true;
            }
            // 等待时停止行走音效
            var waitAudio = EngineServices.audio;
            if (waitAudio != null) {
                waitAudio.stopActionSound(WALK_ACTION);
            }
            lastMoveTimeMs = 0;
            // 消耗等待时间（推进时钟）
            turnManager.addAction(player, Constants.WAIT_BASE_TIME);
            metabolismManager.addActionCost(0);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_IDLE);
            hydrationManager.update();
            // 主动耗尽剩余移动点 → 回合结束处理
            player.spendMoves(player.getMoves());
            endOfPlayerRound();
            GameLog.getInstance().log("等待了一回合...");
            return true;
        }
        if (keyCode == KeyEvent.VK_MINUS || keyCode == KeyEvent.VK_SUBTRACT) {
            // 无移动点时无法等待
            if (!turnManager.hasMoves(player)) {
                GameLog.getInstance().log("你太累了，先休息一下吧...");
                return true;
            }
            // 等待时停止行走音效
            var waitAudio = EngineServices.audio;
            if (waitAudio != null) {
                waitAudio.stopActionSound(WALK_ACTION);
            }
            lastMoveTimeMs = 0;
            // 持续等待 10 轮：每轮消耗 WAIT_BASE_TIME 时钟 + 耗尽移动点 + 回合处理
            for (int i = 0; i < 10; i++) {
                turnManager.addAction(player, Constants.WAIT_BASE_TIME);
                metabolismManager.update();
                hydrationManager.addAction(Constants.ADD_THIRST_IDLE);
                hydrationManager.update();
                // 主动耗尽剩余移动点 → 回合结束处理
                player.spendMoves(player.getMoves());
                endOfPlayerRound();
            }
            GameLog.getInstance().log("持续等待了10回合...");
            return true;
        }
        return false;
    }

    // ── 观察模式（Look）生命周期回调 ──────────────────────────────────

    /** 设置输入状态机引用（由 MainScreen 在创建后调用） */
    public void setInputStateMachine(InputStateMachine inputStateMachine) {
        this.inputStateMachine = inputStateMachine;
    }

    /** 进入观察模式（由输入状态机调用） */
    public void onEnterLookMode() {
        lookCursorDx = 0;
        lookCursorDy = 0;
        creatureCycleIndex = -1;
        refreshVisibleCreatures();
        GameLog.getInstance().log("观察模式：方向键/WASD 移动光标，Tab 切换生物，ESC 退出");
    }

    /** 退出观察模式（由输入状态机调用） */
    public void onExitLookMode() {
        visibleCreatureList.clear();
        creatureCycleIndex = -1;
        GameLog.getInstance().log("退出观察模式");
    }

    // ── NPC 选择模式生命周期回调 ──────────────────────────────────

    /** 可见 NPC 列表（NPC 选择模式用，按距离排序） */
    private List<com.github.game.cdda.npc.Npc> visibleNpcList = new ArrayList<>();

    /** 当前循环到的 NPC 索引（NPC 选择模式） */
    private int npcCycleIndex = -1;

    /** 进入 NPC 选择模式（由输入状态机调用） */
    public void onEnterNpcSelectMode() {
        lookCursorDx = 0;
        lookCursorDy = 0;
        npcCycleIndex = -1;
        refreshVisibleNpcs();
        if (visibleNpcList.isEmpty()) {
            GameLog.getInstance().log("视野内没有 NPC");
        } else {
            GameLog.getInstance().log(String.format(
                    "选择 NPC：方向键/WASD 移动光标，Tab 切换（%d 个 NPC），Enter 确认，ESC 取消",
                    visibleNpcList.size()));
        }
    }

    /** 退出 NPC 选择模式（由输入状态机调用） */
    public void onExitNpcSelectMode() {
        visibleNpcList.clear();
        npcCycleIndex = -1;
    }

    /** 刷新可见 NPC 列表（视口范围内且存活的 NPC） */
    private void refreshVisibleNpcs() {
        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();
        if (tileW == 0 || tileH == 0) {
            visibleNpcList = new ArrayList<>();
            return;
        }

        // 基于摄像机实际可见范围（与 moveLookCursor 一致）
        int zoomedVW = camera.getZoomedViewportWidth();
        int zoomedVH = camera.getZoomedViewportHeight();
        int camStartCol = Math.floorDiv(camera.getX(), tileW);
        int camStartRow = Math.floorDiv(camera.getY(), tileH);
        int camEndCol = Math.floorDiv(camera.getX() + zoomedVW, tileW);
        int camEndRow = Math.floorDiv(camera.getY() + zoomedVH, tileH);

        int playerTileX = player.getTileX();
        int playerTileY = player.getTileY();

        visibleNpcList = new ArrayList<>();
        for (com.github.game.cdda.npc.Npc npc : world.getNpcManager().getAllNpcs()) {
            if (!npc.isAlive()) continue;
            int npcTileX = npc.getTileX();
            int npcTileY = npc.getTileY();
            // 摄像机可见范围内
            if (npcTileX >= camStartCol && npcTileX <= camEndCol
                    && npcTileY >= camStartRow && npcTileY <= camEndRow) {
                visibleNpcList.add(npc);
            }
        }

        // 按距离排序（曼哈顿距离）
        visibleNpcList.sort((a, b) -> {
            int da = Math.abs(a.getTileX() - player.getTileX())
                   + Math.abs(a.getTileY() - player.getTileY());
            int db = Math.abs(b.getTileX() - player.getTileX())
                   + Math.abs(b.getTileY() - player.getTileY());
            return Integer.compare(da, db);
        });
    }

    /** NPC 选择模式下的按键处理（由输入状态机在 NPC_SELECT 模式下调用） */
    public void handleNpcSelectInput(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
                inputStateMachine.exitNpcSelectMode();
                GameLog.getInstance().log("取消选择 NPC");
                return;
            case KeyEvent.VK_ENTER:
                confirmNpcAtCursor();
                return;
            case KeyEvent.VK_TAB:
                cycleNpcs();
                return;
            case KeyEvent.VK_UP:    case KeyEvent.VK_W: moveLookCursor(0, -1); break;
            case KeyEvent.VK_DOWN:  case KeyEvent.VK_S: moveLookCursor(0, 1);  break;
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: moveLookCursor(-1, 0); break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: moveLookCursor(1, 0);  break;
            default: break;
        }
    }

    /** 确认光标位置的 NPC，打开交互菜单 */
    private void confirmNpcAtCursor() {
        com.github.game.cdda.npc.Npc npc = getNpcAtCursor();
        if (npc != null && npc.isAlive()) {
            // 由状态机打开交互菜单并退出 NPC 选择模式
            inputStateMachine.confirmNpcSelection(npc);
        } else {
            GameLog.getInstance().log("这里没有 NPC");
        }
    }

    /** 获取光标所在瓦片的 NPC（可能为 null） */
    private com.github.game.cdda.npc.Npc getNpcAtCursor() {
        int targetTileX = player.getTileX() + lookCursorDx;
        int targetTileY = player.getTileY() + lookCursorDy;
        com.github.game.cdda.creature.Creature c =
                creatureManager.getCreatureAtTile(targetTileX, targetTileY);
        if (c instanceof com.github.game.cdda.npc.Npc npc && npc.isAlive()) {
            return npc;
        }
        return null;
    }

    /** Tab 键在可见 NPC 之间循环切换（NPC 选择模式） */
    private void cycleNpcs() {
        if (visibleNpcList.isEmpty()) {
            GameLog.getInstance().log("视野内没有 NPC");
            return;
        }
        npcCycleIndex = (npcCycleIndex + 1) % visibleNpcList.size();
        com.github.game.cdda.npc.Npc target = visibleNpcList.get(npcCycleIndex);

        // 将光标跳转到目标 NPC 位置
        lookCursorDx = target.getTileX() - player.getTileX();
        lookCursorDy = target.getTileY() - player.getTileY();

        GameLog.getInstance().log(String.format("选中：%s（%s，距离 %d）",
                target.getName(), target.getTypeDisplayName(),
                Math.abs(lookCursorDx) + Math.abs(lookCursorDy)));
    }

    /** 刷新可见生物列表（以玩家感知范围为半径） */
    private void refreshVisibleCreatures() {
        int maxRange = Math.max(player.getVisionRange(), player.getHearingRange());
        visibleCreatureList = creatureManager.getVisibleCreatures(
                player.getTileX(), player.getTileY(), maxRange);
    }

    /** 观察模式下的按键处理（由输入状态机在 LOOK 模式下调用） */
    public void handleLookInput(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
                inputStateMachine.exitLookMode();
                return;
            case KeyEvent.VK_TAB:
                cycleCreatures();
                return;
            case KeyEvent.VK_UP:    case KeyEvent.VK_W: moveLookCursor(0, -1); break;
            case KeyEvent.VK_DOWN:  case KeyEvent.VK_S: moveLookCursor(0, 1);  break;
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: moveLookCursor(-1, 0); break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: moveLookCursor(1, 0);  break;
            default: break;
        }
    }

    /** 移动观察光标（不受 1 格限制，可在整个视口范围内移动） */
    private void moveLookCursor(int dx, int dy) {
        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();
        if (tileW == 0 || tileH == 0) return;

        // 基于摄像机实际可见范围计算光标移动边界（而非视口像素尺寸）
        // 这确保光标范围与实际渲染的瓦片范围一致，避免偏移
        int zoomedVW = camera.getZoomedViewportWidth();
        int zoomedVH = camera.getZoomedViewportHeight();
        int camStartCol = Math.floorDiv(camera.getX(), tileW);
        int camStartRow = Math.floorDiv(camera.getY(), tileH);
        int camEndCol = Math.floorDiv(camera.getX() + zoomedVW, tileW);
        int camEndRow = Math.floorDiv(camera.getY() + zoomedVH, tileH);

        int playerTileX = player.getTileX();
        int playerTileY = player.getTileY();

        // 光标偏移范围 = 可见格子范围相对于玩家位置的偏移
        int minDx = camStartCol - playerTileX;
        int maxDx = camEndCol - playerTileX;
        int minDy = camStartRow - playerTileY;
        int maxDy = camEndRow - playerTileY;

        lookCursorDx = Math.max(minDx, Math.min(maxDx, lookCursorDx + dx));
        lookCursorDy = Math.max(minDy, Math.min(maxDy, lookCursorDy + dy));

        // 光标移动后重置生物循环
        creatureCycleIndex = -1;
        npcCycleIndex = -1;
    }

    /**
     * Tab 键在可见生物之间循环切换。
     * 每次按 Tab，光标跳转到下一个生物的位置。
     */
    private void cycleCreatures() {
        if (visibleCreatureList.isEmpty()) {
            GameLog.getInstance().log("视野内没有生物");
            return;
        }
        creatureCycleIndex = (creatureCycleIndex + 1) % visibleCreatureList.size();
        com.github.game.cdda.creature.Creature target = visibleCreatureList.get(creatureCycleIndex);

        // 将光标跳转到目标生物位置
        lookCursorDx = target.getTileX() - player.getTileX();
        lookCursorDy = target.getTileY() - player.getTileY();

        GameLog.getInstance().log(String.format("观察到：%s（距离 %d，HP %d/%d）",
                target.getDisplayChar() + " " + getCreatureDisplayName(target),
                Math.abs(lookCursorDx) + Math.abs(lookCursorDy),
                target.getHp(), target.getMaxHp()));
    }

    /**
     * 获取生物的完整显示名称（含生命阶段）。
     * 对于 Animal，返回当前阶段的名称（如 "幼兔"）；其他情况返回通用名称。
     */
    private String getCreatureDisplayName(com.github.game.cdda.creature.Creature creature) {
        if (creature instanceof com.github.game.cdda.creature.Animal) {
            return ((com.github.game.cdda.creature.Animal) creature).getStageName();
        }
        // 其他类型（未来扩展：NPC、怪物等）
        return "未知生物";
    }

    /**
     * 渲染观察模式 / NPC 选择模式的光标高亮。
     * 在目标瓦片上绘制半透明叠加 + 边框，重绘目标字符为高亮色。
     * <ul>
     *   <li>观察模式：青色边框 + 蓝色叠加</li>
     *   <li>NPC 选择模式：绿色边框 + 绿色叠加</li>
     * </ul>
     */
    private void renderLookCursorHighlight(Renderer renderer, int tileW, int tileH) {
        if (inputStateMachine == null) return;
        boolean lookMode = inputStateMachine.isInLookMode();
        boolean npcSelect = inputStateMachine.isInNpcSelectMode();
        if (!lookMode && !npcSelect) return;

        int targetTileX = player.getTileX() + lookCursorDx;
        int targetTileY = player.getTileY() + lookCursorDy;

        int pixelX = targetTileX * tileW;
        int pixelY = targetTileY * tileH;
        int viewX = camera.toViewX(pixelX);
        int viewY = camera.toViewY(pixelY);

        // 缩放后的绘制尺寸
        double zoom = camera.getZoom();
        int scaledW = (int) (tileW * zoom);
        int scaledH = (int) (tileH * zoom);

        // 边界检查：只在视口内绘制
        if (viewX < -scaledW || viewX >= viewport.getWidth()
                || viewY < -scaledH || viewY >= viewport.getHeight()) {
            return;
        }

        // 颜色按模式区分
        Color overlayColor = npcSelect ? new Color(50, 180, 80, 80) : new Color(50, 100, 200, 80);
        Color borderColor = npcSelect ? new Color(80, 255, 120) : Color.CYAN;

        // 1. 绘制半透明叠加层
        renderer.setColor(overlayColor);
        renderer.fillRect(viewX, viewY, scaledW, scaledH);

        // 2. 绘制边框
        renderer.setColor(borderColor);
        renderer.drawRect(viewX, viewY, scaledW, scaledH);

        // 3. 高亮重绘该瓦片上的内容（生物或玩家）
        int ascent = renderer.getFontMetrics().getAscent();

        // 检查是否有生物
        com.github.game.cdda.creature.Creature creature = creatureManager.getCreatureAtTile(targetTileX, targetTileY);
        if (creature != null) {
            renderer.setColor(Color.YELLOW);
            renderer.drawText(String.valueOf(creature.getDisplayChar()), viewX, viewY + ascent);
        }

        // 检查是否是玩家位置（玩家在最上层，覆盖生物高亮）
        if (lookCursorDx == 0 && lookCursorDy == 0) {
            renderer.setColor(Color.YELLOW);
            renderer.drawText(String.valueOf(player.getDisplayChar()), viewX, viewY + ascent);
        }
    }

    /**
     * 渲染观察模式 / NPC 选择模式的底部状态栏。
     */
    private void renderLookStatusBar(Renderer renderer, int tileW, int tileH) {
        if (inputStateMachine == null) return;
        boolean lookMode = inputStateMachine.isInLookMode();
        boolean npcSelect = inputStateMachine.isInNpcSelectMode();
        if (!lookMode && !npcSelect) return;

        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();
        int barHeight = 40;
        int barY = vpH - barHeight;

        // 背景（NPC 选择模式用绿色调，观察模式用蓝色调）
        Color bgColor = npcSelect ? new Color(0, 20, 0, 200) : new Color(0, 0, 0, 200);
        renderer.setColor(bgColor);
        renderer.fillRect(0, barY, vpW, barHeight);

        int targetTileX = player.getTileX() + lookCursorDx;
        int targetTileY = player.getTileY() + lookCursorDy;
        int distance = Math.abs(lookCursorDx) + Math.abs(lookCursorDy);

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));

        if (npcSelect) {
            // ── NPC 选择模式状态栏 ──
            renderNpcSelectStatusBar(renderer, barY, vpW, targetTileX, targetTileY, distance);
            return;
        }

        // ── 观察模式状态栏（原有逻辑） ──

        // 第一行：坐标 + 地形 + 距离
        TileType tile = chunkManager.getTile(targetTileX, targetTileY);
        String coordStr = String.format("[%d,%d] 距离:%d", targetTileX, targetTileY, distance);
        if (tile != null) {
            String tileStr = String.format("  %s(%c) %s",
                    tile.getLocalizedName(), tile.getChar(),
                    tile.isPassable() ? "可通过" : "不可通过");
            coordStr += tileStr;
        } else {
            coordStr += "  未知区域";
        }

        // 附加植被物种信息（如：橡树、桦树）
        String vegSpeciesId = chunkManager.getVegetation(targetTileX, targetTileY);
        VegetationDefinition vegDef = (vegSpeciesId != null)
                ? VegetationRegistry.getById(vegSpeciesId) : null;
        if (vegDef != null) {
            coordStr += String.format("  %s", vegDef.getLocalizedName());
        }

        // 附加地面物品提示
        java.util.List<GroundItem> groundItems = groundItemManager.getItemsAt(targetTileX, targetTileY);
        if (!groundItems.isEmpty()) {
            coordStr += String.format("  [%c物品x%d]", Constants.GROUND_ITEM_CHAR, groundItems.size());
        }

        renderer.setColor(Color.WHITE);
        renderer.drawText(coordStr, 4, barY + 14);

        // 第二行：生物信息
        com.github.game.cdda.creature.Creature creature = creatureManager.getCreatureAtTile(targetTileX, targetTileY);
        if (creature != null) {
            int hpPercent = creature.getMaxHp() > 0
                    ? (creature.getHp() * 100 / creature.getMaxHp()) : 0;
            Color hpColor = hpPercent > 60 ? Color.GREEN
                    : hpPercent > 30 ? Color.YELLOW : Color.RED;

            // 生物描述
            String bioStr = String.format("%s %s  HP:",
                    creature.getDisplayChar(), getCreatureDisplayName(creature));
            renderer.setColor(Color.CYAN);
            renderer.drawText(bioStr, 4, barY + 30);

            // Debug 模式：显示 AI 状态和能量值
            if (Constants.SHOW_DEBUG_INFO && creature instanceof com.github.game.cdda.creature.Animal animal) {
                String debugInfo = String.format(" | AI:%s 能量:%d 疲劳:%d",
                        animal.getAIState(), animal.getBodyEnergy(), animal.getFatigue());
                renderer.setColor(new Color(200, 200, 100));
                int bioW = renderer.getTextWidth(bioStr);
                renderer.drawText(debugInfo, 4 + bioW, barY + 30);
            }

            int bioStrWidth = renderer.getTextWidth(bioStr);
            int hpBarWidth = 80;
            int hpBarX = 4 + bioStrWidth + 4;
            int hpBarY = barY + 20;

            // HP 条背景
            renderer.setColor(Color.DARK_GRAY);
            renderer.fillRect(hpBarX, hpBarY, hpBarWidth, 12);
            // HP 条填充
            renderer.setColor(hpColor);
            renderer.fillRect(hpBarX, hpBarY, (int) (hpBarWidth * hpPercent / 100.0), 12);
            // HP 条边框
            renderer.setColor(Color.GRAY);
            renderer.drawRect(hpBarX, hpBarY, hpBarWidth, 12);

            // HP 数字
            String hpStr = String.format("%d/%d", creature.getHp(), creature.getMaxHp());
            renderer.setColor(Color.WHITE);
            renderer.drawText(hpStr, hpBarX + hpBarWidth + 4, barY + 30);

            // 循环提示
            if (!visibleCreatureList.isEmpty()) {
                String cycleHint = String.format("  Tab 切换 (%d/%d)",
                        creatureCycleIndex + 1, visibleCreatureList.size());
                renderer.setColor(Color.GRAY);
                renderer.drawText(cycleHint, hpBarX + hpBarWidth + 4 + renderer.getTextWidth(hpStr), barY + 30);
            }
        } else {
            // 无生物时显示植被或地形信息
            if (vegDef != null) {
                renderer.setColor(new Color(100, 200, 100));
                renderer.drawText(String.format("%s (%s)",
                        vegDef.getLocalizedName(), vegDef.type.getDisplayName()), 4, barY + 30);
            } else if (tile != null) {
                renderer.setColor(Color.GRAY);
                renderer.drawText("地形：" + tile.getLocalizedName(), 4, barY + 30);
            }
        }

        // 底部提示行（右对齐）
        String hint = "方向键/WASD 移动光标 | Tab 切换生物 | ESC 退出";
        renderer.setColor(new Color(180, 180, 180));
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 10));
        int hintY = barY + barHeight - 4;
        int hintX = vpW - renderer.getTextWidth(hint) - 4;
        renderer.drawText(hint, hintX, hintY);
    }

    /**
     * 渲染 NPC 选择模式的底部状态栏。
     * 显示光标处 NPC 信息或"此处无 NPC"提示。
     */
    private void renderNpcSelectStatusBar(Renderer renderer, int barY, int vpW,
                                          int targetTileX, int targetTileY, int distance) {
        int barHeight = 40;
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));

        com.github.game.cdda.creature.Creature creature =
                creatureManager.getCreatureAtTile(targetTileX, targetTileY);

        if (creature instanceof com.github.game.cdda.npc.Npc npc && npc.isAlive()) {
            // 第一行：NPC 名称 + 类型 + 距离
            String npcInfo = String.format("[%d,%d] 距离:%d  %s（%s）",
                    targetTileX, targetTileY, distance,
                    npc.getName(), npc.getTypeDisplayName());
            renderer.setColor(new Color(180, 255, 180));
            renderer.drawText(npcInfo, 4, barY + 14);

            // 第二行：地域 + 态度 + 循环提示
            String detail = String.format("地域: %s  |  态度: %s",
                    npc.getRegion().name, npc.getAttitudeDescription());
            renderer.setColor(new Color(140, 220, 140));
            renderer.drawText(detail, 4, barY + 30);

            if (!visibleNpcList.isEmpty()) {
                String cycleHint = String.format("  Tab 切换 (%d/%d)",
                        npcCycleIndex + 1, visibleNpcList.size());
                renderer.setColor(Color.GRAY);
                int detailWidth = renderer.getTextWidth(detail);
                renderer.drawText(cycleHint, 4 + detailWidth + 4, barY + 30);
            }
        } else {
            // 光标不在 NPC 上
            renderer.setColor(Color.GRAY);
            renderer.drawText(String.format("[%d,%d] 距离:%d  此处无 NPC",
                    targetTileX, targetTileY, distance), 4, barY + 14);

            // 显示视野内 NPC 数量
            if (visibleNpcList.isEmpty()) {
                renderer.setColor(new Color(200, 100, 100));
                renderer.drawText("视野内没有可交互的 NPC", 4, barY + 30);
            } else {
                renderer.setColor(new Color(180, 180, 180));
                renderer.drawText(String.format("视野内共 %d 个 NPC（Tab 快速切换）",
                        visibleNpcList.size()), 4, barY + 30);
            }
        }

        // 底部提示行（右对齐）
        String hint = "方向键/WASD 移动光标 | Tab 切换 NPC | Enter 确认 | ESC 取消";
        renderer.setColor(new Color(180, 220, 180));
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 10));
        int hintY = barY + barHeight - 4;
        int hintX = vpW - renderer.getTextWidth(hint) - 4;
        renderer.drawText(hint, hintX, hintY);
    }

    /**
     * 渲染方向选择提示（底部状态栏）。
     * 当输入状态机处于方向选择模式时，显示动作名称和方向键提示。
     */
    private void renderDirectionSelectHint(Renderer renderer) {
        if (inputStateMachine == null || !inputStateMachine.isDirectionSelecting()) return;

        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();
        int barHeight = 28;
        int barY = vpH - barHeight;

        // 背景
        renderer.setColor(new Color(60, 40, 0, 200));
        renderer.fillRect(0, barY, vpW, barHeight);

        // 提示文字
        renderer.setFont(new Font("Monospaced", Font.BOLD, 13));
        renderer.setColor(Color.YELLOW);
        String hint = String.format("选择方向: ↑↓←→ 执行 %s | Esc 取消",
                inputStateMachine.getDirectionActionName());
        renderer.drawText(hint, 4, barY + 18);
    }

    // ── TileLayerRenderer 实现（逐 tile 图层回调） ──────────────────────────────────

    /**
     * 绘制指定瓦片上的地面物品（图层2）。
     * 通过 GroundItemManager 的空间索引 O(1) 查询该位置的物品。
     */
    @Override
    public void drawGroundItems(Renderer renderer, Camera camera,
                                int tileCol, int tileRow,
                                int scaledTileW, int scaledTileH) {
        if (groundItemManager == null) return;

        List<GroundItem> items = groundItemManager.getItemsAt(tileCol, tileRow);
        if (items.isEmpty()) return;

        int ascent = renderer.getFontMetrics().getAscent();
        int viewX = camera.toViewX(tileCol * tileMap.getTileWidth());
        int viewY = camera.toViewY(tileRow * tileMap.getTileHeight());

        renderer.setColor(Color.YELLOW);
        for (GroundItem gi : items) {
            renderer.drawText(String.valueOf(Constants.GROUND_ITEM_CHAR), viewX, viewY + ascent);
        }
    }

    /**
     * 绘制指定瓦片上的生物（图层3）。
     * 通过 CreatureManager 的空间查询获取该位置的存活生物。
     * 排除玩家（玩家由 render() 单独绘制在生物层之上）。
     */
    @Override
    public void drawCreatures(Renderer renderer, Camera camera,
                              int tileCol, int tileRow,
                              int scaledTileW, int scaledTileH) {
        List<Creature> creatures = creatureManager.getAliveCreaturesAt(tileCol, tileRow);
        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();
        for (Creature creature : creatures) {
            if (creature == player) continue; // 玩家单独渲染
            creature.render(renderer, camera, tileW, tileH);
        }
    }

    // ── 拾取操作 ──────────────────────────────────

    /**
     * 处理拾取操作（G 键）。
     * 查询玩家脚下的地面物品列表。
     * 如果只有一个物品，直接尝试拾取；否则返回列表供 UI 显示。
     *
     * @return 脚下物品列表（可能为空）；单个物品时已自动拾取，返回空列表
     */
    public java.util.List<GroundItem> handlePickup() {
        if (!initialized || groundItemManager == null) {
            return java.util.Collections.emptyList();
        }

        java.util.List<GroundItem> items = groundItemManager.getItemsAt(
                player.getTileX(), player.getTileY());

        if (items.isEmpty()) {
            GameLog.getInstance().log("这里没有物品");
            return items;
        }

        if (items.size() == 1) {
            // 单个物品：直接尝试拾取
            tryPickupItem(items.get(0));
            return java.util.Collections.emptyList();
        }

        // 多个物品：返回列表，由 MainScreen 打开拾取 UI
        return items;
    }

    /**
     * 尝试拾取单个地面物品到玩家背包。
     *
     * @param groundItem 地面物品
     */
    private void tryPickupItem(GroundItem groundItem) {
        ItemStack stack = groundItem.getItemStack();
        if (!player.getInventory().canCarry(stack)) {
            GameLog.getInstance().log(String.format("%s 太重了，无法携带",
                    stack.getType().getDisplayName()));
            return;
        }

        if (player.getInventory().addItem(stack)) {
            groundItemManager.removeGroundItem(groundItem);
            GameLog.getInstance().log(String.format("拾取了 %s x%d",
                    stack.getType().getDisplayName(), stack.getCount()));
        }
    }

    // ── 访问器 ──────────────────────────────────

    /** 获取游戏世界（逻辑层） */
    public GameWorld getWorld() { return world; }
    public Camera getGameCamera() { return camera; }
    public boolean isInitialized() { return initialized; }

    // ── 生物回合处理 ──────────────────────────────────

    /**
     * 请求处理所有生物的回合（异步，立即返回）。
     * 创建行动上下文并提交到 CreatureManager 的后台线程。
     * 实际变更通过 {@link CreatureManager#applyPendingCreatureMutations()} 在每帧应用。
     */
    private void requestCreatureTurns() {
        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();
        CreatureActionContext context = new CreatureActionContext(player, chunkManager, tileW, tileH);
        context.setCreatureManager(creatureManager);
        context.setCurrentGameSeconds(world.getGameTime().getTotalSeconds());
        creatureManager.requestTurnProcessing(context);
    }
}