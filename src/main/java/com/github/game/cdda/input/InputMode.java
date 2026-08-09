package com.github.game.cdda.input;

/**
 * 游戏输入模式枚举。
 *
 * <p>定义所有互斥的输入状态。{@link InputStateMachine} 保证同一时刻只有一个模式生效，
 * 消除分散在各处的布尔标志。
 *
 * <p>注意：模态覆盖层菜单（游戏内菜单、物品栏、进食等）通过 ScreenManager
 * 的 push/pop 管理，覆盖层激活时主屏幕不接收按键，因此不在此枚举中定义。
 *
 * @see InputStateMachine
 */
public enum InputMode {

    /** 正常游戏模式（默认）。WASD/方向键移动，各功能键触发对应操作。 */
    NORMAL,

    /** 观察模式——方向键移动观察光标，Tab 切换生物，ESC 退出。 */
    LOOK,

    /** 世界地图——方向键平移，+/- 缩放，ESC/M/Enter 关闭。 */
    WORLD_MAP,

    /** 方向选择——等待方向键输入（如砍树选择方向），ESC 取消。 */
    DIRECTION_SELECT
}
