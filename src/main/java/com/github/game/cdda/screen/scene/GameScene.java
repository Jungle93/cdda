package com.github.game.cdda.screen.scene;

import com.github.game.cdda.Constants;
import com.github.game.cdda.Player;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.Scene;
import com.github.game.engine.core.scene.Viewport;
import com.github.game.cdda.TurnManager;
import com.github.game.cdda.MetabolismManager;
import com.github.game.cdda.HydrationManager;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.world.TileMap;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.awt.*;
import java.awt.event.KeyEvent;

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
public class GameScene extends Scene {

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

    /** 是否处于检查模式 */
    private boolean inExamineMode = false;

    /** 瓦片尺寸是否已初始化 */
    private boolean initialized = false;

    // ── FPS 计算 ──────────────────────────────────
    private long fpsFrameCount = 0;
    private long fpsLastTime = System.currentTimeMillis();
    private int currentFps = 0;

    /**
     * 创建游戏世界场景。
     *
     * @param viewport 屏幕视口区域（游戏区域）
     * @param world    游戏世界（逻辑层，包含所有子系统）
     * @param fontSize 字体大小（pt），用于瓦片渲染
     */
    public GameScene(Viewport viewport, GameWorld world, int fontSize) {
        super(viewport);
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

        // 仅创建渲染层组件
        tileMap = new TileMap(chunkManager, fontSize);
    }

    /**
     * 首次渲染时初始化 Camera 和 Player 的渲染属性。
     * 需要 Renderer 的 FontMetrics 来测量瓦片像素尺寸。
     */
    public void ensureInitialized(Renderer renderer) {
        if (initialized) return;

        tileMap.initTileSize(renderer);

        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();

        // 初始化玩家的像素尺寸和世界查询接口
        world.initPlayerForRendering(tileW, tileH);

        // 创建摄像机 — 视口尺寸 = Scene viewport 尺寸
        camera = new Camera(viewport.getWidth(), viewport.getHeight());
        setCamera(camera);

        // 立即加载玩家周围的区块
        chunkManager.updateChunks(player.getWorldX(), player.getWorldY(), tileW, tileH);

        // 将玩家注册到回合系统
        world.registerPlayerToTurnSystem();

        // 记录开局日志
        GameLog.getInstance().log("游戏开始。WASD移动，5等待，-持续等待，E检查，ESC菜单");

        initialized = true;
    }

    @Override
    public void update(long deltaTime) {
        if (!initialized) return;

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
    }

    @Override
    public void render(Renderer renderer) {
        if (!initialized) return;

        // 渲染瓦片地图
        tileMap.render(renderer, camera);

        // 渲染玩家
        player.render(renderer, camera);

        // 渲染调试信息（场景局部坐标，左上角）
        renderDebugInfo(renderer);

        // 渲染检查模式覆盖层（场景局部坐标，底部）
        renderExamineOverlay(renderer);
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

        // 第一行：位置 + 摄像机 + 区块 + FPS
        StringBuilder sb = new StringBuilder();
        if (Constants.DEBUG_SHOW_TILE_POS && tileW > 0 && tileH > 0) {
            int ptx = Math.floorDiv(player.getWorldX(), tileW);
            int pty = Math.floorDiv(player.getWorldY(), tileH);
            sb.append(String.format("瓦片:(%d,%d)", ptx, pty));
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

    // ── 输入处理 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        if (!initialized) return;

        // 检查模式：拦截所有按键，不传递给移动逻辑
        if (inExamineMode) {
            handleExamineInput(keyCode);
            return;
        }

        // ── 等待动作（时间流逝但不做其他事） ──
        if (keyCode == KeyEvent.VK_5) {
            turnManager.addAction(player, Constants.WAIT_BASE_TIME);
            turnManager.processRound();
            metabolismManager.addActionCost(0);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_IDLE);
            hydrationManager.update();
            GameLog.getInstance().log("等待了一回合...");
            return;
        }
        if (keyCode == KeyEvent.VK_MINUS || keyCode == KeyEvent.VK_SUBTRACT) {
            for (int i = 0; i < 10; i++) {
                turnManager.addAction(player, Constants.WAIT_BASE_TIME);
                metabolismManager.update();
                hydrationManager.addAction(Constants.ADD_THIRST_IDLE);
                hydrationManager.update();
            }
            turnManager.processRound();
            GameLog.getInstance().log("持续等待了10回合...");
            return;
        }

        // ── 网格式移动：每次按键移动恰好一个瓦片 ──
        int dx = 0, dy = 0;
        switch (keyCode) {
            case KeyEvent.VK_W: case KeyEvent.VK_UP:    dy = -1; break;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN:  dy =  1; break;
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  dx = -1; break;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: dx =  1; break;
            default: return;
        }

        // 回合制：玩家行动后推进时间
        if (player.move(dx, dy)) {
            turnManager.addAction(player, Constants.MOVE_BASE_TIME);
            metabolismManager.addActionCost(Constants.MOVE_CALORIE_COST);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_WALK);
            hydrationManager.update();
            turnManager.processRound();
        }
    }

    @Override
    public void onKeyReleased(int keyCode) {
        // 网格式移动无需处理按键释放
    }

    // ── 检查模式 ──────────────────────────────────

    /** 进入检查模式 */
    public void enterExamineMode() {
        inExamineMode = true;
        GameLog.getInstance().log("按下方向键检查相邻位置，ESC 退出");
    }

    /** 退出检查模式 */
    private void exitExamineMode() {
        inExamineMode = false;
        GameLog.getInstance().log("退出检查模式");
    }

    /** 检查模式下的按键处理 */
    private void handleExamineInput(int keyCode) {
        int dx = 0, dy = 0;
        switch (keyCode) {
            case KeyEvent.VK_UP:    case KeyEvent.VK_W: dy = -1; break;
            case KeyEvent.VK_DOWN:  case KeyEvent.VK_S: dy =  1; break;
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: dx = -1; break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: dx =  1; break;
            case KeyEvent.VK_ESCAPE:
                exitExamineMode();
                return;
            default:
                return;
        }
        examineDirection(dx, dy);
    }

    /**
     * 检查指定方向相邻瓦片，结果写入游戏日志。
     * 支持 8 方向（含对角线）。
     */
    private void examineDirection(int dx, int dy) {
        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();
        if (tileW == 0 || tileH == 0) return;

        int playerTileX = Math.floorDiv(player.getWorldX(), tileW);
        int playerTileY = Math.floorDiv(player.getWorldY(), tileH);

        int targetTileX = playerTileX + dx;
        int targetTileY = playerTileY + dy;

        TileType tile = chunkManager.getTile(targetTileX, targetTileY);
        if (tile != null) {
            String dirName = getDirectionName(dx, dy);
            String passStr = tile.isPassable() ? "可通过" : "不可通过";
            GameLog.getInstance().log(String.format(
                    "检查 %s[%d,%d]: %s(%c) %s",
                    dirName, targetTileX, targetTileY,
                    tile.getName(), tile.getChar(), passStr));
        } else {
            String dirName = getDirectionName(dx, dy);
            GameLog.getInstance().log(String.format(
                    "检查 %s[%d,%d]: 未知区域",
                    dirName, targetTileX, targetTileY));
        }
    }

    /** 获取方向中文名称 */
    private String getDirectionName(int dx, int dy) {
        if (dx == 0 && dy == -1) return "北";
        if (dx == 0 && dy == 1)  return "南";
        if (dx == 1 && dy == 0)  return "东";
        if (dx == -1 && dy == 0) return "西";
        if (dx == 1 && dy == -1) return "东北";
        if (dx == -1 && dy == -1) return "西北";
        if (dx == 1 && dy == 1)  return "东南";
        if (dx == -1 && dy == 1) return "西南";
        return "";
    }

    /**
     * 渲染检查模式覆盖层（游戏区域底部提示条）。
     */
    private void renderExamineOverlay(Renderer renderer) {
        if (!inExamineMode) return;

        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();

        renderer.setColor(new Color(0, 0, 0, 160));
        int barHeight = 24;
        int barY = vpH - barHeight;
        renderer.fillRect(0, barY, vpW, barHeight);

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
        renderer.setColor(Color.YELLOW);
        String hint = "检查模式：方向键/WASD 检查  ESC 退出";
        int textX = (vpW - renderer.getTextWidth(hint)) / 2;
        renderer.drawText(hint, textX, barY + 16);
    }

    // ── 访问器 ──────────────────────────────────

    /** 获取游戏世界（逻辑层） */
    public GameWorld getWorld() { return world; }
    public Camera getGameCamera() { return camera; }
    public boolean isInitialized() { return initialized; }
    public boolean isInExamineMode() { return inExamineMode; }
}
