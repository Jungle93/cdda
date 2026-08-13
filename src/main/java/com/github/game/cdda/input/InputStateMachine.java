package com.github.game.cdda.input;

import com.github.game.cdda.item.world.GroundItem;
import com.github.game.cdda.item.ItemAction;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.screen.hud.GameLogPanel;
import com.github.game.cdda.screen.scene.GameScene;
import com.github.game.cdda.screen.scene.WorldMapScene;

import java.awt.event.KeyEvent;
import java.util.List;

/**
 * 输入状态机——游戏输入模式的唯一真实来源。
 *
 * <p>职责：
 * <ul>
 *   <li>维护当前 {@link InputMode}（NORMAL / LOOK / WORLD_MAP）</li>
 *   <li>管理日志面板展开/折叠子状态（NORMAL 模式内部）</li>
 *   <li>提供显式的模式转换方法（带前置条件守卫）</li>
 *   <li>将按键事件按当前模式分发到对应处理器</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>模式转换方法是副作用的唯一定义点——调用者无需手动同步布尔标志</li>
 *   <li>按键分发按模式隔离，添加新模式只需增加一个 case 分支</li>
 *   <li>不持有游戏逻辑，具体处理委托给各 Scene</li>
 *   <li>覆盖层 Screen（菜单、物品栏等）的创建通过 {@link OverlayCallback} 回调解耦</li>
 * </ul>
 *
 * @see InputMode
 */
public class InputStateMachine {

    /**
     * 覆盖层 Screen 创建回调。
     *
     * <p>由 MainScreen 实现，将 Screen 创建逻辑保留在游戏层，
     * 状态机只需通知"要打开什么"，不关心具体如何创建。
     */
    public interface OverlayCallback {
        /** 推入游戏内菜单（ESC 触发） */
        void pushInGameMenu();
        /** 推入调试菜单（` 触发） */
        void pushDebugMenu();
        /** 推入物品栏界面（I 触发） */
        void pushInventoryScreen();
        /** 推入进食界面（E 触发） */
        void pushEatingScreen();
        /** 推入丢弃物品界面（D 触发） */
        void pushDropScreen();
        /** 推入拾取选择界面（G 触发，多个地面物品时） */
        void pushPickupScreen(List<GroundItem> items);
        /** 显示物品使用覆盖层（A 触发，半透明覆盖在游戏画面上） */
        void showItemUseOverlay();
        /** 推入 NPC 交互菜单（C 触发，传入光标选中的 NPC） */
        void pushNpcInteractionScreen(com.github.game.cdda.npc.Npc npc);
    }

    // ── 状态 ──

    /** 当前输入模式 */
    private InputMode currentMode = InputMode.NORMAL;

    /** 日志面板是否展开（仅 NORMAL 模式下有意义） */
    private boolean logExpanded = false;

    /** 方向选择模式下的待执行动作 */
    private ItemAction pendingDirectionAction;
    /** 方向选择模式下的工具物品 */
    private ItemStack pendingDirectionTool;

    // ── 依赖 ──

    private final GameScene gameScene;
    private final WorldMapScene worldMapScene;
    private final GameLogPanel logPanel;
    private final OverlayCallback overlayCallback;

    /**
     * 创建输入状态机。
     *
     * @param gameScene      游戏场景（NORMAL 模式移动、LOOK 模式观察）
     * @param worldMapScene  世界地图场景（WORLD_MAP 模式）
     * @param logPanel       日志面板（展开时拦截 UP/DOWN）
     * @param overlayCallback 覆盖层创建回调（由 MainScreen 实现）
     */
    public InputStateMachine(GameScene gameScene, WorldMapScene worldMapScene,
                             GameLogPanel logPanel, OverlayCallback overlayCallback) {
        this.gameScene = gameScene;
        this.worldMapScene = worldMapScene;
        this.logPanel = logPanel;
        this.overlayCallback = overlayCallback;
    }

    // ========================================
    // 模式查询（替代旧的分散布尔标志）
    // ========================================

    /** @return 当前输入模式 */
    public InputMode getCurrentMode() { return currentMode; }

    /** @return 当前是否处于观察模式（替代 GameScene.inLookMode） */
    public boolean isInLookMode() { return currentMode == InputMode.LOOK; }

    /** @return 当前是否处于 NPC 选择模式 */
    public boolean isInNpcSelectMode() { return currentMode == InputMode.NPC_SELECT; }

    /** @return 当前是否打开世界地图（替代 WorldMapScene.open） */
    public boolean isWorldMapOpen() { return currentMode == InputMode.WORLD_MAP; }

    /** @return 日志面板是否展开（替代 GameLogPanel.expanded） */
    public boolean isLogExpanded() { return logExpanded; }

    // ========================================
    // 模式转换方法（显式、带前置条件守卫）
    // ========================================

    /** 进入观察模式（仅 NORMAL 模式下可进入） */
    public void enterLookMode() {
        if (currentMode != InputMode.NORMAL) return;
        currentMode = InputMode.LOOK;
        gameScene.onEnterLookMode();
    }

    /** 退出观察模式（仅 LOOK 模式下可退出） */
    public void exitLookMode() {
        if (currentMode != InputMode.LOOK) return;
        currentMode = InputMode.NORMAL;
        gameScene.onExitLookMode();
    }

    /** 打开世界地图（仅 NORMAL 模式下可打开） */
    public void openWorldMap() {
        if (currentMode != InputMode.NORMAL) return;
        currentMode = InputMode.WORLD_MAP;
        worldMapScene.onOpen();
    }

    /** 关闭世界地图（仅 WORLD_MAP 模式下可关闭） */
    public void closeWorldMap() {
        if (currentMode != InputMode.WORLD_MAP) return;
        currentMode = InputMode.NORMAL;
        worldMapScene.onClose();
    }

    /** 进入 NPC 选择模式（仅 NORMAL 模式下可进入） */
    public void enterNpcSelectMode() {
        if (currentMode != InputMode.NORMAL) return;
        currentMode = InputMode.NPC_SELECT;
        gameScene.onEnterNpcSelectMode();
    }

    /** 退出 NPC 选择模式（仅 NPC_SELECT 模式下可退出） */
    public void exitNpcSelectMode() {
        if (currentMode != InputMode.NPC_SELECT) return;
        currentMode = InputMode.NORMAL;
        gameScene.onExitNpcSelectMode();
    }

    /**
     * NPC 选择确认：打开交互菜单并退出选择模式。
     * 由 GameScene 在用户按 Enter 确认选中 NPC 时调用。
     *
     * @param npc 选中的 NPC
     */
    public void confirmNpcSelection(com.github.game.cdda.npc.Npc npc) {
        currentMode = InputMode.NORMAL;
        gameScene.onExitNpcSelectMode();
        overlayCallback.pushNpcInteractionScreen(npc);
    }

    /** 切换日志面板展开/折叠 */
    public void toggleLogPanel() {
        if (logExpanded) {
            logExpanded = false;
            logPanel.setExpanded(false);
        } else {
            logExpanded = true;
            logPanel.setExpanded(true);
        }
    }

    /**
     * 进入方向选择模式。
     * 关闭覆盖层后在主游戏界面等待方向键输入，按方向键执行动作，ESC 取消。
     *
     * @param action 待执行的方向动作
     * @param tool   使用的工具物品
     */
    public void startDirectionSelection(ItemAction action, ItemStack tool) {
        if (currentMode != InputMode.NORMAL) return;
        currentMode = InputMode.DIRECTION_SELECT;
        pendingDirectionAction = action;
        pendingDirectionTool = tool;
    }

    /** @return 是否处于方向选择模式 */
    public boolean isDirectionSelecting() { return currentMode == InputMode.DIRECTION_SELECT; }

    /** @return 方向选择模式下的动作名称（用于 HUD 提示） */
    public String getDirectionActionName() {
        return pendingDirectionAction != null ? pendingDirectionAction.getName() : "";
    }

    // ========================================
    // 按键分发
    // ========================================

    /**
     * 处理按键事件。根据当前模式分发到对应处理器。
     *
     * @param keyCode 按键码（KeyEvent.VK_XXX）
     */
    public void onKeyPressed(int keyCode) {
        switch (currentMode) {
            case NORMAL:
                handleNormalModeKey(keyCode);
                break;
            case LOOK:
                gameScene.handleLookInput(keyCode);
                break;
            case NPC_SELECT:
                gameScene.handleNpcSelectInput(keyCode);
                break;
            case WORLD_MAP:
                worldMapScene.onKeyPressed(keyCode);
                break;
            case DIRECTION_SELECT:
                handleDirectionSelectKey(keyCode);
                break;
        }
    }

    /** 方向选择模式：方向键执行动作，ESC 取消 */
    private void handleDirectionSelectKey(int keyCode) {
        int dx = 0, dy = 0;
        switch (keyCode) {
            case KeyEvent.VK_UP:    dy = -1; break;
            case KeyEvent.VK_DOWN:  dy =  1; break;
            case KeyEvent.VK_LEFT:  dx = -1; break;
            case KeyEvent.VK_RIGHT: dx =  1; break;
            case KeyEvent.VK_ESCAPE:
                // 取消方向选择，回到正常模式
                currentMode = InputMode.NORMAL;
                pendingDirectionAction = null;
                pendingDirectionTool = null;
                return;
            default:
                return;
        }

        // 执行带方向的动作
        var player = gameScene.getWorld().getPlayer();
        var world = gameScene.getWorld();
        pendingDirectionAction.executeDirection(player, world, pendingDirectionTool, dx, dy);

        // 执行完毕，回到正常模式
        currentMode = InputMode.NORMAL;
        pendingDirectionAction = null;
        pendingDirectionTool = null;
    }

    // ── 各模式私有处理器 ──

    /**
     * 正常模式按键处理。
     *
     * <p>处理顺序：
     * <ol>
     *   <li>日志面板导航键拦截（UP/DOWN 展开时）</li>
     *   <li>全局功能键（V/I/E/L/M/D/G 等）</li>
     *   <li>等待动作（5/-）</li>
     *   <li>其余委托给 GameScene（WASD/方向键移动/攻击）</li>
     * </ol>
     */
    private void handleNormalModeKey(int keyCode) {
        // 日志面板展开时拦截 UP/DOWN 滚动
        if (logExpanded) {
            if (keyCode == KeyEvent.VK_UP) {
                logPanel.scrollUp();
                return;
            }
            if (keyCode == KeyEvent.VK_DOWN) {
                logPanel.scrollDown();
                return;
            }
        }

        switch (keyCode) {
            // ── 模态覆盖层 ──
            case KeyEvent.VK_ESCAPE:
                overlayCallback.pushInGameMenu();
                return;
            case KeyEvent.VK_BACK_QUOTE:
                overlayCallback.pushDebugMenu();
                return;
            case KeyEvent.VK_I:
                overlayCallback.pushInventoryScreen();
                return;
            case KeyEvent.VK_E:
                overlayCallback.pushEatingScreen();
                return;
            case KeyEvent.VK_D:
                overlayCallback.pushDropScreen();
                return;
            case KeyEvent.VK_A:
                overlayCallback.showItemUseOverlay();
                return;
            case KeyEvent.VK_C:
                enterNpcSelectMode();
                return;

            // ── 模式切换 ──
            case KeyEvent.VK_V:
                toggleLogPanel();
                return;
            case KeyEvent.VK_L:
                enterLookMode();
                return;
            case KeyEvent.VK_M:
                openWorldMap();
                return;

            // ── 游戏动作 ──
            case KeyEvent.VK_G:
                List<GroundItem> pickupItems = gameScene.handlePickup();
                if (pickupItems.size() > 1) {
                    overlayCallback.pushPickupScreen(pickupItems);
                }
                return;

            // ── 移动/攻击/等待 → 委托 GameScene ──
            default:
                gameScene.onKeyPressed(keyCode);
                return;
        }
    }
}
