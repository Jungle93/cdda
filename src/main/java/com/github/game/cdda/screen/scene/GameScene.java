package com.github.game.cdda.screen.scene;

import com.github.game.cdda.Constants;
import com.github.game.cdda.Player;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.item.GroundItem;
import com.github.game.cdda.item.GroundItemManager;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.input.InputStateMachine;
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

        // 生成初始生物
        world.spawnInitialCreatures();

        // 记录开局日志
        GameLog.getInstance().log("游戏开始。方向键移动/攻击，5等待，L观察，M大地图，E进食，G拾取，D丢弃，I背包，`调试，ESC菜单");
        GameLog.getInstance().log(String.format("周围生成了 %d 个生物", creatureManager.getCreatureCount()));

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
    }

    @Override
    public void render(Renderer renderer) {
        if (!initialized) return;

        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();

        // 渲染瓦片地图
        tileMap.render(renderer, camera);

        // 渲染地面物品
        renderGroundItems(renderer, tileW, tileH);

        // 渲染生物（在玩家之下）
        creatureManager.renderCreatures(renderer, camera, tileW, tileH);

        // 渲染玩家
        player.render(renderer, camera, tileW, tileH);

        // 渲染调试信息（场景局部坐标，左上角）
        renderDebugInfo(renderer);

        // 渲染观察模式光标高亮（在玩家之上）
        renderLookCursorHighlight(renderer, tileW, tileH);

        // 渲染观察模式状态栏（场景局部坐标，底部）
        renderLookStatusBar(renderer, tileW, tileH);

        // 渲染方向选择提示
        renderDirectionSelectHint(renderer);
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
            sb.append(String.format(" [%s]", biome.getName()));
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

    // ── 输入处理 ──────────────────────────────────

    @Override
    public void onKeyPressed(int keyCode) {
        if (!initialized) return;

        // ── 等待动作（时间流逝但不做其他事） ──
        if (handleWait(keyCode)) return;

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
            player.meleeAttack(target);
            turnManager.addAction(player, Constants.ATTACK_BASE_TIME);
            metabolismManager.addActionCost(Constants.MOVE_CALORIE_COST);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_COMBAT);
            hydrationManager.update();
            // 处理生物回合
            processCreatureTurns();
            turnManager.processRound();
            return;
        }

        // 无生物 → 正常移动
        // 回合制：玩家行动后推进时间
        if (player.move(dx, dy)) {
            turnManager.addAction(player, Constants.MOVE_BASE_TIME);
            metabolismManager.addActionCost(Constants.MOVE_CALORIE_COST);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_WALK);
            hydrationManager.update();
            // 处理生物回合
            processCreatureTurns();
            turnManager.processRound();
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
     * @param keyCode 按键码
     * @return true 如果按键被消耗（是等待键），false 否则
     */
    public boolean handleWait(int keyCode) {
        if (keyCode == KeyEvent.VK_5) {
            turnManager.addAction(player, Constants.WAIT_BASE_TIME);
            processCreatureTurns();
            turnManager.processRound();
            metabolismManager.addActionCost(0);
            metabolismManager.update();
            hydrationManager.addAction(Constants.ADD_THIRST_IDLE);
            hydrationManager.update();
            GameLog.getInstance().log("等待了一回合...");
            return true;
        }
        if (keyCode == KeyEvent.VK_MINUS || keyCode == KeyEvent.VK_SUBTRACT) {
            for (int i = 0; i < 10; i++) {
                turnManager.addAction(player, Constants.WAIT_BASE_TIME);
                processCreatureTurns();
                metabolismManager.update();
                hydrationManager.addAction(Constants.ADD_THIRST_IDLE);
                hydrationManager.update();
            }
            turnManager.processRound();
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

        // 视口范围内可移动的瓦片数
        int maxDx = viewport.getWidth() / tileW;
        int maxDy = viewport.getHeight() / tileH;

        lookCursorDx = Math.max(-maxDx, Math.min(maxDx, lookCursorDx + dx));
        lookCursorDy = Math.max(-maxDy, Math.min(maxDy, lookCursorDy + dy));

        // 光标移动后重置生物循环
        creatureCycleIndex = -1;
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
     * 渲染观察模式光标高亮。
     * 在目标瓦片上绘制青色边框 + 半透明叠加，重绘目标字符为高亮色。
     */
    private void renderLookCursorHighlight(Renderer renderer, int tileW, int tileH) {
        if (inputStateMachine == null || !inputStateMachine.isInLookMode()) return;

        int targetTileX = player.getTileX() + lookCursorDx;
        int targetTileY = player.getTileY() + lookCursorDy;

        int pixelX = targetTileX * tileW;
        int pixelY = targetTileY * tileH;
        int viewX = camera.toViewX(pixelX);
        int viewY = camera.toViewY(pixelY);

        // 边界检查：只在视口内绘制
        if (viewX < -tileW || viewX >= viewport.getWidth()
                || viewY < -tileH || viewY >= viewport.getHeight()) {
            return;
        }

        // 1. 绘制半透明蓝色叠加层
        renderer.setColor(new Color(50, 100, 200, 80));
        renderer.fillRect(viewX, viewY, tileW, tileH);

        // 2. 绘制青色边框
        renderer.setColor(Color.CYAN);
        renderer.drawRect(viewX, viewY, tileW, tileH);

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
     * 渲染观察模式状态栏（游戏区域底部）。
     * 显示光标指向的瓦片信息和生物信息。
     */
    private void renderLookStatusBar(Renderer renderer, int tileW, int tileH) {
        if (inputStateMachine == null || !inputStateMachine.isInLookMode()) return;

        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();
        int barHeight = 40;
        int barY = vpH - barHeight;

        // 背景
        renderer.setColor(new Color(0, 0, 0, 200));
        renderer.fillRect(0, barY, vpW, barHeight);

        int targetTileX = player.getTileX() + lookCursorDx;
        int targetTileY = player.getTileY() + lookCursorDy;
        int distance = Math.abs(lookCursorDx) + Math.abs(lookCursorDy);

        renderer.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // 第一行：坐标 + 地形 + 距离
        TileType tile = chunkManager.getTile(targetTileX, targetTileY);
        String coordStr = String.format("[%d,%d] 距离:%d", targetTileX, targetTileY, distance);
        if (tile != null) {
            String tileStr = String.format("  %s(%c) %s",
                    tile.getName(), tile.getChar(),
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
            coordStr += String.format("  %s", vegDef.name);
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
                renderer.drawText(String.format("%s (%s)  %s",
                        vegDef.name, vegDef.type.getDisplayName(), vegDef.id), 4, barY + 30);
            } else if (tile != null) {
                renderer.setColor(Color.GRAY);
                renderer.drawText("地形：" + tile.getName(), 4, barY + 30);
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

    // ── 地面物品渲染 ──────────────────────────────────

    /**
     * 渲染所有地面物品。
     * 物品显示为 '~' 字符（黄色），在生物和玩家之下渲染。
     */
    private void renderGroundItems(Renderer renderer, int tileW, int tileH) {
        if (groundItemManager == null) return;

        int ascent = renderer.getFontMetrics().getAscent();
        for (GroundItem gi : groundItemManager.getAllGroundItems()) {
            int worldX = gi.getTileX() * tileW;
            int worldY = gi.getTileY() * tileH;
            int viewX = camera.toViewX(worldX);
            int viewY = camera.toViewY(worldY);

            // 边界检查
            if (viewX < -tileW || viewX >= viewport.getWidth()
                    || viewY < -tileH || viewY >= viewport.getHeight()) {
                continue;
            }

            renderer.setColor(Color.YELLOW);
            renderer.drawText(String.valueOf(Constants.GROUND_ITEM_CHAR), viewX, viewY + ascent);
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
                    stack.getType().getName()));
            return;
        }

        if (player.getInventory().addItem(stack)) {
            groundItemManager.removeGroundItem(groundItem);
            GameLog.getInstance().log(String.format("拾取了 %s x%d",
                    stack.getType().getName(), stack.getCount()));
        }
    }

    // ── 访问器 ──────────────────────────────────

    /** 获取游戏世界（逻辑层） */
    public GameWorld getWorld() { return world; }
    public Camera getGameCamera() { return camera; }
    public boolean isInitialized() { return initialized; }

    // ── 生物回合处理 ──────────────────────────────────

    /**
     * 处理所有生物的回合。
     * 创建行动上下文并委托给 CreatureManager。
     */
    private void processCreatureTurns() {
        int tileW = tileMap.getTileWidth();
        int tileH = tileMap.getTileHeight();
        CreatureActionContext context = new CreatureActionContext(player, chunkManager, tileW, tileH);
        context.setCreatureManager(creatureManager);
        creatureManager.processCreatureTurns(context);
    }
}
