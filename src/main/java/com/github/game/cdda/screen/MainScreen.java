package com.github.game.cdda.screen;

import com.github.game.cdda.config.ConfigManager;
import com.github.game.cdda.input.InputStateMachine;
import com.github.game.cdda.item.GroundItem;
import com.github.game.cdda.screen.overlay.*;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.Viewport;
import com.github.game.engine.core.screen.Screen;
import com.github.game.cdda.game.CharacterSettings;
import com.github.game.cdda.game.WorldSettings;
import com.github.game.cdda.screen.hud.CharacterInfoPanel;
import com.github.game.cdda.screen.hud.GameLogPanel;
import com.github.game.cdda.screen.hud.TimePanel;
import com.github.game.cdda.screen.scene.GameScene;
import com.github.game.cdda.screen.scene.HudScene;
import com.github.game.cdda.screen.scene.WorldMapScene;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Month;

import java.util.List;

/**
 * 主游戏屏幕。使用 Scene 系统实现分屏布局：
 * <ul>
 *   <li>{@link GameScene} — 左侧游戏区域（瓦片地图 + 玩家）</li>
 *   <li>{@link HudScene} — 右侧信息面板（角色状态 + 游戏日志）</li>
 * </ul>
 *
 * <p>MainScreen 负责：
 * <ul>
 *   <li>创建和组合场景</li>
 *   <li>通过 {@link InputStateMachine} 管理输入模式和按键路由</li>
 *   <li>实现 {@link InputStateMachine.OverlayCallback} 创建覆盖层 Screen</li>
 * </ul>
 *
 * @see InputStateMachine
 */
public class MainScreen extends Screen implements InputStateMachine.OverlayCallback {

    /** 游戏世界（逻辑层，持有所有子系统） */
    private GameWorld gameWorld;

    /** 游戏世界场景（显示层） */
    private GameScene gameScene;

    /** HUD 信息面板场景 */
    private HudScene hudScene;

    /** 世界地图场景（按 M 切换） */
    private WorldMapScene worldMapScene;

    /** 游戏日志面板（用于 V 键切换扩展/紧凑模式） */
    private GameLogPanel gameLogPanel;

    /** 角色信息面板（用于同步代谢数据） */
    private CharacterInfoPanel charPanel;

    /** 是否已初始化（popScreen 恢复时不再重复初始化） */
    private boolean initialized = false;

    /** 输入状态机（管理输入模式和按键分发） */
    private InputStateMachine inputStateMachine;

    /** 世界设置 */
    private final WorldSettings worldSettings;
    /** 角色设置 */
    private final CharacterSettings characterSettings;

    /** 使用默认设置创建 */
    public MainScreen(GameEngine engine) {
        this(engine, new WorldSettings(), new CharacterSettings());
    }

    /**
     * 使用指定设置创建。
     */
    public MainScreen(GameEngine engine, WorldSettings worldSettings,
                      CharacterSettings characterSettings) {
        super(engine);
        this.worldSettings = worldSettings;
        this.characterSettings = characterSettings;
    }

    @Override
    public void init() {
        // popScreen 恢复时不再重复初始化（保留玩家位置等状态）
        if (initialized) return;

        int fontSize = ConfigManager.getInstance().getFontSize();
        int infoPanelWidth = ConfigManager.getInstance().getInfoPanelWidth();

        // 使用实际窗口尺寸（跟随设置），而非固定常量
        int gameWidth = getWidth() - infoPanelWidth;
        int gameHeight = getHeight();

        // ── 创建游戏世界（逻辑层） ──
        gameWorld = new GameWorld(worldSettings, Month.MARCH, 8);

        // ── 游戏场景（显示层，左侧） ──
        gameScene = new GameScene(
                new Viewport(0, 0, gameWidth, gameHeight),
                gameWorld,
                fontSize
        );
        gameScene.init();

        // ── HUD 场景（右侧固定宽度） ──
        hudScene = new HudScene(
                new Viewport(gameWidth, 0, infoPanelWidth, gameHeight)
        );

        // 添加时间显示面板（从 GameWorld 获取时间数据）
        TimePanel timePanel = new TimePanel(gameWorld.getGameTime(), fontSize);
        hudScene.addPanel(timePanel);

        // 添加角色信息面板
        charPanel = new CharacterInfoPanel(fontSize);
        hudScene.addPanel(charPanel);

        // 添加游戏日志面板
        gameLogPanel = new GameLogPanel();
        hudScene.addPanel(gameLogPanel);

        // ── 世界地图场景（覆盖游戏区域，按 M 切换） ──
        worldMapScene = new WorldMapScene(
                new Viewport(0, 0, gameWidth, gameHeight),
                gameWorld.getWorldMap(),
                gameWorld.getPlayer(),
                gameWorld.getCreatureManager()
        );
        worldMapScene.init();

        // ── 输入状态机 ──
        inputStateMachine = new InputStateMachine(gameScene, worldMapScene, gameLogPanel, this);
        gameScene.setInputStateMachine(inputStateMachine);
        worldMapScene.setInputStateMachine(inputStateMachine);

        // 注册场景（渲染顺序：游戏场景 → HUD 场景 → 世界地图）
        addScene(gameScene);
        addScene(hudScene);
        addScene(worldMapScene);

        initialized = true;
    }

    @Override
    public void render(Renderer renderer) {
        // 首次渲染时初始化 GameScene（需要 FontMetrics）
        if (!gameScene.isInitialized()) {
            gameScene.ensureInitialized(renderer);
        }

        // 同步代谢数据到角色信息面板（从 GameWorld 获取）
        if (charPanel != null) {
            charPanel.setHunger(gameWorld.getMetabolismManager().getHungerPercent(), 100);
            charPanel.setTemperatureLevel(gameWorld.getMetabolismManager().getBodyTempLevel());
            charPanel.setThirst(gameWorld.getHydrationManager().getWaterPercent(), 100);
            charPanel.setThirstColor(gameWorld.getHydrationManager().getThirstColor());
            charPanel.setSpeed(gameWorld.getPlayer().getSpeed());
        }

        // 基类遍历场景：pushClip → scene.render → popClip
        super.render(renderer);
    }

    // ── 按键路由（委托给输入状态机） ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        inputStateMachine.onKeyPressed(keyCode);
    }

    // ── OverlayCallback 实现（覆盖层 Screen 创建） ──────────────────────────────────

    @Override
    public void pushInGameMenu() {
        engine.getScreenManager().pushScreen(new InGameMenuScreen(engine));
    }

    @Override
    public void pushDebugMenu() {
        engine.getScreenManager().pushScreen(new DebugMenuScreen(engine, gameWorld));
    }

    @Override
    public void pushInventoryScreen() {
        engine.getScreenManager().pushScreen(
                new InventoryScreen(engine, gameWorld.getPlayer(),
                        gameWorld.getGroundItemManager()));
    }

    @Override
    public void pushEatingScreen() {
        engine.getScreenManager().pushScreen(
                new EatingScreen(engine, gameWorld));
    }

    @Override
    public void pushDropScreen() {
        engine.getScreenManager().pushScreen(
                new DropScreen(engine, gameWorld.getPlayer(),
                        gameWorld.getGroundItemManager()));
    }

    @Override
    public void pushPickupScreen(List<GroundItem> items) {
        engine.getScreenManager().pushScreen(
                new PickupScreen(engine, gameWorld.getPlayer(),
                        gameWorld.getGroundItemManager(), items));
    }

    @Override
    public void pushItemUseScreen() {
        engine.getScreenManager().pushScreen(
                new ItemUseScreen(engine, gameWorld.getPlayer(), gameWorld));
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    // ── 窗口 resize 处理 ──────────────────────────────────

    /**
     * 窗口尺寸变更时，重新计算游戏区域和 HUD 区域的 Viewport，
     * 并更新 Camera 的视口尺寸。
     */
    @Override
    public void onWindowResized(int width, int height) {
        if (gameScene == null || hudScene == null) return; // 尚未初始化

        int infoPanelWidth = ConfigManager.getInstance().getInfoPanelWidth();
        int gameWidth = width - infoPanelWidth;
        int gameHeight = height;

        // 更新游戏场景 viewport
        gameScene.getViewport().setSize(gameWidth, gameHeight);

        // 更新 HUD 场景 viewport（位置 + 尺寸）
        hudScene.getViewport().setPosition(gameWidth, 0);
        hudScene.getViewport().setSize(infoPanelWidth, gameHeight);

        // 更新摄像机视口尺寸
        if (gameScene.getCamera() != null) {
            gameScene.getCamera().setViewportSize(gameWidth, gameHeight);
        }

        // 更新世界地图 viewport
        if (worldMapScene != null) {
            worldMapScene.getViewport().setSize(gameWidth, gameHeight);
        }
    }
}
