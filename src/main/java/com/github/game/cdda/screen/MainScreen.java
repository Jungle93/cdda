package com.github.game.cdda.screen;

import com.github.game.cdda.config.ConfigManager;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.Viewport;
import com.github.game.engine.core.screen.Screen;
import com.github.game.cdda.game.CharacterSettings;
import com.github.game.cdda.game.WorldSettings;
import com.github.game.cdda.screen.hud.CharacterInfoPanel;
import com.github.game.cdda.screen.hud.GameLogPanel;
import com.github.game.cdda.screen.hud.TimePanel;
import com.github.game.cdda.screen.overlay.InGameMenuScreen;
import com.github.game.cdda.screen.overlay.InventoryScreen;
import com.github.game.cdda.screen.scene.GameScene;
import com.github.game.cdda.screen.scene.HudScene;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Month;

import java.awt.event.KeyEvent;

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
 *   <li>全局按键路由（ESC/V/I/E 等快捷键）</li>
 * </ul>
 *
 * <p>按键路由优先级（从高到低）：
 * <ol>
 *   <li>检查模式 → 直接转发给 GameScene</li>
 *   <li>ESC → 游戏内菜单</li>
 *   <li>V → 切换日志面板扩展/紧凑</li>
 *   <li>I → 物品栏</li>
 *   <li>E → 进入检查模式</li>
 *   <li>UP/DOWN（日志扩展时）→ 滚动日志</li>
 *   <li>其他 → 广播给所有场景（WASD 移动等）</li>
 * </ol>
 */
public class MainScreen extends Screen {

    /** 游戏世界（逻辑层，持有所有子系统） */
    private GameWorld gameWorld;

    /** 游戏世界场景（显示层） */
    private GameScene gameScene;

    /** HUD 信息面板场景 */
    private HudScene hudScene;

    /** 游戏日志面板（用于 V 键切换扩展/紧凑模式） */
    private GameLogPanel gameLogPanel;

    /** 角色信息面板（用于同步代谢数据） */
    private CharacterInfoPanel charPanel;

    /** 是否已初始化（popScreen 恢复时不再重复初始化） */
    private boolean initialized = false;

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

        // 注册场景（渲染顺序：游戏场景 → HUD 场景）
        addScene(gameScene);
        addScene(hudScene);

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

    // ── 全局按键路由 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        // 1. 检查模式：直接转发给 GameScene，不处理全局快捷键
        if (gameScene.isInExamineMode()) {
            gameScene.onKeyPressed(keyCode);
            return;
        }

        switch (keyCode) {
            // 2. ESC → 游戏内菜单
            case KeyEvent.VK_ESCAPE:
                engine.getScreenManager().pushScreen(new InGameMenuScreen(engine));
                return;

            // 3. V → 切换日志面板扩展/紧凑模式
            case KeyEvent.VK_V:
                gameLogPanel.toggleExpanded();
                return;

            // 4. I → 物品栏
            case KeyEvent.VK_I:
                engine.getScreenManager().pushScreen(new InventoryScreen(engine));
                return;

            // 5. E → 进入检查模式
            case KeyEvent.VK_E:
                gameScene.enterExamineMode();
                return;

            // 6. UP/DOWN 在日志扩展模式下 → 滚动日志
            case KeyEvent.VK_UP:
                if (gameLogPanel.isExpanded()) {
                    gameLogPanel.scrollUp();
                    return;
                }
                break;
            case KeyEvent.VK_DOWN:
                if (gameLogPanel.isExpanded()) {
                    gameLogPanel.scrollDown();
                    return;
                }
                break;

            default:
                break;
        }

        // 7. 其他按键 → 广播给所有场景（WASD 移动等）
        super.onKeyPressed(keyCode);
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
