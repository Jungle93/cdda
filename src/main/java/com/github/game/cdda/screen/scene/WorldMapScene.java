package com.github.game.cdda.screen.scene;

import com.github.game.cdda.Player;
import com.github.game.cdda.creature.Creature;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.world.biome.BiomeType;
import com.github.game.cdda.world.biome.WorldMap;
import com.github.game.cdda.world.chunk.Chunk;
import com.github.game.cdda.input.InputStateMachine;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.Scene;
import com.github.game.engine.core.scene.Viewport;
import com.github.game.cdda.log.GameLog;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * 世界地图场景（大地图）。
 * 显示全局生物群落分布概览，按 M 键切换。
 *
 * <p>大地图上每个色块对应一个 {@link Chunk}（64×64 瓦片）的生物群落。
 * 由 {@link WorldMap} 提供数据，直接渲染群落颜色，无需加载区块。
 *
 * <p>特性：
 * <ul>
 *   <li>每个色块 = 1 个区块的生物群落颜色</li>
 *   <li>支持多级缩放（4/6/8/12/16 像素/区块）</li>
 *   <li>方向键/WASD 平移视角</li>
 *   <li>玩家显示为黄色 '@' 标记</li>
 *   <li>附近生物显示为红色点</li>
 *   <li>即时渲染——无需加载区块，性能极高</li>
 * </ul>
 *
 * <p>控制：
 * <ul>
 *   <li>方向键/WASD — 平移地图</li>
 *   <li>+/- — 缩放</li>
 *   <li>Home — 回到玩家位置</li>
 *   <li>ESC/M/Enter — 关闭地图</li>
 * </ul>
 */
public class WorldMapScene extends Scene {

    /** 缩放级别定义（像素/区块 cell） */
    private static final int[] ZOOM_LEVELS = {6, 8, 12, 16, 24};
    private static final int DEFAULT_ZOOM_INDEX = 2; // 12px

    /** 每次平移的区块数 */
    private static final int PAN_STEP_CHUNKS = 3;

    // ── 游戏数据引用 ──
    private final WorldMap worldMap;
    private final Player player;
    private final CreatureManager creatureManager;

    // ── 地图状态 ──

    /** 当前缩放级别索引 */
    private int zoomIndex = DEFAULT_ZOOM_INDEX;

    /** 平移偏移量（以区块为单位），相对于玩家所在区块 */
    private int panOffsetChunksX = 0;
    private int panOffsetChunksY = 0;

    /** 输入状态机引用（由 MainScreen 注入，用于查询模式和关闭地图） */
    private InputStateMachine inputStateMachine;

    /**
     * 创建世界地图场景。
     *
     * @param viewport        视口区域（与游戏区域相同大小）
     * @param worldMap        世界地图（生物群落数据源）
     * @param player          玩家实体
     * @param creatureManager 生物管理器（显示生物位置）
     */
    public WorldMapScene(Viewport viewport, WorldMap worldMap,
                         Player player, CreatureManager creatureManager) {
        super(viewport);
        this.worldMap = worldMap;
        this.player = player;
        this.creatureManager = creatureManager;
    }

    @Override
    public void init() {
        this.zoomIndex = DEFAULT_ZOOM_INDEX;
    }

    // ── 生命周期回调（由输入状态机调用） ──────────────────────────────────

    /** 设置输入状态机引用（由 MainScreen 在创建后调用） */
    public void setInputStateMachine(InputStateMachine inputStateMachine) {
        this.inputStateMachine = inputStateMachine;
    }

    /**
     * 打开大地图（生命周期回调）。
     * 重置平移到玩家所在区块。
     */
    public void onOpen() {
        panOffsetChunksX = 0;
        panOffsetChunksY = 0;
        zoomIndex = DEFAULT_ZOOM_INDEX;
        GameLog.getInstance().log("打开大地图 — 方向键平移，+/- 缩放，Home 居中，M/ESC 关闭");
    }

    /**
     * 关闭大地图（生命周期回调）。
     */
    public void onClose() {
        GameLog.getInstance().log("关闭大地图");
    }

    // ── 坐标转换 ──────────────────────────────────

    /** 当前缩放像素/区块 cell */
    private int cellSize() {
        return ZOOM_LEVELS[zoomIndex];
    }

    /** 玩家所在区块坐标 */
    private int playerChunkX() {
        return Math.floorDiv(player.getTileX(), Chunk.SIZE);
    }

    private int playerChunkY() {
        return Math.floorDiv(player.getTileY(), Chunk.SIZE);
    }

    /**
     * 区块坐标 → 屏幕像素 X。
     * 以玩家所在区块为中心，加上平移偏移。
     */
    private int chunkToScreenX(int chunkX) {
        int cs = cellSize();
        int centerScreenX = viewport.getWidth() / 2 + panOffsetChunksX * cs;
        return centerScreenX + (chunkX - playerChunkX()) * cs;
    }

    /**
     * 区块坐标 → 屏幕像素 Y。
     */
    private int chunkToScreenY(int chunkY) {
        int cs = cellSize();
        int centerScreenY = viewport.getHeight() / 2 + panOffsetChunksY * cs;
        return centerScreenY + (chunkY - playerChunkY()) * cs;
    }

    // ── 渲染 ──────────────────────────────────

    @Override
    public void render(Renderer renderer) {
        if (inputStateMachine == null || !inputStateMachine.isWorldMapOpen()) return;

        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();
        int cs = cellSize();

        // 1. 深色背景（未探索区域）
        renderer.setColor(new Color(20, 20, 30));
        renderer.fillRect(0, 0, vpW, vpH);

        // 2. 渲染生物群落色块
        int halfCellsW = (vpW / 2 / cs) + 2;
        int halfCellsH = (vpH / 2 / cs) + 2;
        int pChunkX = playerChunkX();
        int pChunkY = playerChunkY();

        for (int dy = -halfCellsH; dy <= halfCellsH; dy++) {
            for (int dx = -halfCellsW; dx <= halfCellsW; dx++) {
                int chunkX = pChunkX + dx;
                int chunkY = pChunkY + dy;

                BiomeType biome = worldMap.getBiomeAtChunk(chunkX, chunkY);
                int screenX = chunkToScreenX(chunkX);
                int screenY = chunkToScreenY(chunkY);

                // 生物群落颜色
                renderer.setColor(biome.getColor());
                renderer.fillRect(screenX, screenY, cs, cs);

                // 区块边界线（缩放较大时显示，增强网格感）
                if (cs >= 8) {
                    renderer.setColor(new Color(0, 0, 0, 40));
                    renderer.drawRect(screenX, screenY, cs, cs);
                }

                // 群落字符标记（高缩放时显示）
                if (cs >= 12) {
                    renderer.setColor(darken(biome.getColor(), 0.5));
                    renderer.setFont(new Font("Monospaced", Font.PLAIN, Math.min(cs - 2, 12)));
                    String ch = String.valueOf(biome.getMapChar());
                    int tw = renderer.getTextWidth(ch);
                    int ascent = renderer.getFontMetrics().getAscent();
                    renderer.drawText(ch, screenX + (cs - tw) / 2, screenY + (cs + ascent) / 2 - 1);
                }
            }
        }

        // 3. 渲染玩家标记（黄色 '@'）
        renderPlayerMarker(renderer);

        // 4. 渲染附近生物（红色点）
        renderCreatures(renderer);

        // 5. 底部状态栏
        renderStatusBar(renderer, vpW, vpH);

        // 6. 操作提示（左上角）
        renderHints(renderer);
    }

    /**
     * 渲染玩家所在区块的标记。
     */
    private void renderPlayerMarker(Renderer renderer) {
        int cs = cellSize();
        int px = chunkToScreenX(playerChunkX());
        int py = chunkToScreenY(playerChunkY());

        int markerSize = Math.max(cs + 4, 10);
        int markerX = px + (cs - markerSize) / 2;
        int markerY = py + (cs - markerSize) / 2;

        // 黄色背景
        renderer.setColor(Color.YELLOW);
        renderer.fillRect(markerX, markerY, markerSize, markerSize);
        // 深色边框
        renderer.setColor(Color.ORANGE);
        renderer.drawRect(markerX, markerY, markerSize, markerSize);
        // '@' 字符
        renderer.setColor(Color.BLACK);
        int fontSize = Math.max(markerSize - 2, 8);
        renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
        int textW = renderer.getTextWidth("@");
        int textH = renderer.getFontMetrics().getAscent();
        renderer.drawText("@", markerX + (markerSize - textW) / 2,
                markerY + (markerSize + textH) / 2 - 1);
    }

    /**
     * 渲染附近生物标记。
     */
    private void renderCreatures(Renderer renderer) {
        if (creatureManager == null) return;

        int cs = cellSize();
        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();
        // 将视口像素范围转换为区块范围
        int halfCellsW = (vpW / 2 / cs) + 2;
        int halfCellsH = (vpH / 2 / cs) + 2;
        int maxRangeTiles = Math.max(halfCellsW, halfCellsH) * Chunk.SIZE;

        List<Creature> creatures = creatureManager.getVisibleCreatures(
                player.getTileX(), player.getTileY(), maxRangeTiles);

        for (Creature creature : creatures) {
            int cChunkX = Math.floorDiv(creature.getTileX(), Chunk.SIZE);
            int cChunkY = Math.floorDiv(creature.getTileY(), Chunk.SIZE);
            int screenX = chunkToScreenX(cChunkX) + cs / 2;
            int screenY = chunkToScreenY(cChunkY) + cs / 2;

            int dotSize = Math.max(cs / 3, 2);
            renderer.setColor(Color.RED);
            renderer.fillRect(screenX - dotSize / 2, screenY - dotSize / 2, dotSize, dotSize);
        }
    }

    /**
     * 渲染底部状态栏。
     */
    private void renderStatusBar(Renderer renderer, int vpW, int vpH) {
        int barHeight = 20;
        int barY = vpH - barHeight;

        renderer.setColor(new Color(0, 0, 0, 180));
        renderer.fillRect(0, barY, vpW, barHeight);

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // 玩家区块坐标 + 视野中心区块坐标
        int centerChunkX = playerChunkX() - panOffsetChunksX;
        int centerChunkY = playerChunkY() - panOffsetChunksY;
        BiomeType centerBiome = worldMap.getBiomeAtChunk(centerChunkX, centerChunkY);

        renderer.setColor(Color.WHITE);
        String posInfo = String.format("区块:(%d,%d)  视野:(%d,%d)  %s  缩放:%dpx/区块",
                playerChunkX(), playerChunkY(),
                centerChunkX, centerChunkY,
                centerBiome.getName(), cellSize());
        renderer.drawText(posInfo, 4, barY + 14);
    }

    /**
     * 渲染操作提示（左上角）。
     */
    private void renderHints(Renderer renderer) {
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 10));

        String[] hints = {
                "方向键/WASD: 平移",
                "+/-: 缩放",
                "Home: 回到玩家",
                "M/ESC/Enter: 关闭"
        };

        int hintX = 4;
        int hintY = 12;
        int lineHeight = 13;

        int panelW = 140;
        int panelH = hints.length * lineHeight + 6;
        renderer.setColor(new Color(0, 0, 0, 150));
        renderer.fillRect(hintX - 2, hintY - 11, panelW, panelH);

        renderer.setColor(new Color(200, 200, 200));
        for (String hint : hints) {
            renderer.drawText(hint, hintX, hintY);
            hintY += lineHeight;
        }
    }

    // ── 输入处理 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        switch (keyCode) {
            // 缩放
            case KeyEvent.VK_ADD:
            case KeyEvent.VK_EQUALS:
                zoomIn();
                return;
            case KeyEvent.VK_SUBTRACT:
            case KeyEvent.VK_MINUS:
                zoomOut();
                return;

            // 关闭地图
            case KeyEvent.VK_ESCAPE:
            case KeyEvent.VK_M:
            case KeyEvent.VK_ENTER:
                inputStateMachine.closeWorldMap();
                return;

            // 回到玩家位置
            case KeyEvent.VK_HOME:
                panOffsetChunksX = 0;
                panOffsetChunksY = 0;
                return;

            // 平移（以区块为单位）
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                panOffsetChunksY -= PAN_STEP_CHUNKS;
                return;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                panOffsetChunksY += PAN_STEP_CHUNKS;
                return;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                panOffsetChunksX -= PAN_STEP_CHUNKS;
                return;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                panOffsetChunksX += PAN_STEP_CHUNKS;
                return;

            default:
                break;
        }
    }

    /**
     * 放大（增加像素/区块 cell）。
     * 调整平移偏移以保持视野中心的区块不变。
     */
    private void zoomIn() {
        if (zoomIndex < ZOOM_LEVELS.length - 1) {
            int oldCs = cellSize();
            zoomIndex++;
            int newCs = cellSize();
            // 保持视野中心区块不变
            panOffsetChunksX = panOffsetChunksX * oldCs / newCs;
            panOffsetChunksY = panOffsetChunksY * oldCs / newCs;
        }
    }

    /**
     * 缩小（减少像素/区块 cell）。
     */
    private void zoomOut() {
        if (zoomIndex > 0) {
            int oldCs = cellSize();
            zoomIndex--;
            int newCs = cellSize();
            panOffsetChunksX = panOffsetChunksX * oldCs / newCs;
            panOffsetChunksY = panOffsetChunksY * oldCs / newCs;
        }
    }

    // ── 工具方法 ──────────────────────────────────

    /**
     * 将颜色变暗指定比例。
     */
    private static Color darken(Color c, double factor) {
        return new Color(
                (int) (c.getRed() * factor),
                (int) (c.getGreen() * factor),
                (int) (c.getBlue() * factor));
    }
}
