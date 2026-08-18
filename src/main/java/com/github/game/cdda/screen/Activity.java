package com.github.game.cdda.screen;

import com.github.game.engine.core.render.Renderer;

/**
 * 玩家活动接口（多回合长动作）。
 *
 * <p>设计借鉴 Cataclysm-DDA 的 activity_actor 模式：
 * 多回合动作不再用同步循环一次性跑完，而是拆分为多个"回合步骤"，
 * 每步占用一个完整的游戏回合（玩家移动点耗尽 → 生物行动 → 回合补满 → 下一步）。
 *
 * <p>活动生命周期：
 * <ol>
 *   <li>{@link #start()} — 开始活动（播放音效、初始化状态）</li>
 *   <li>{@link #update()} — 每回合推进一步（消耗移动点、推进时钟、触发生物行动）</li>
 *   <li>{@link #isComplete()} — 检查是否完成</li>
 *   <li>{@link #finish()} — 完成回调（移除植被、生成掉落物）</li>
 *   <li>{@link #cancel()} — 取消回调（ESC 触发）</li>
 * </ol>
 *
 * <p>活动期间：
 * <ul>
 *   <li>玩家不能移动或执行其他动作（{@link #blocksInput()} 返回 true）</li>
 *   <li>生物在每步之间正常行动</li>
 *   <li>世界正常更新（植物生长、代谢等）</li>
 * </ul>
 */
public interface Activity {

    /** 开始活动（播放音效、初始化状态）。由 GameScene 在分配活动时调用一次。 */
    void start();

    /**
     * 推进一步（每回合调用一次）。
     * 消耗移动点、推进时钟、触发生物行动。
     * 实现中应调用 {@code endOfPlayerRound()} 使生物行动 + 回合补满。
     */
    void update();

    /** @return true 如果活动结束（应调用 finish） */
    boolean isComplete();

    /** 完成回调。由 GameScene 在 isComplete() 返回 true 时调用一次。 */
    void finish();

    /** 取消回调。由 ESC 键触发。已消耗的回合不退。 */
    void cancel();

    /**
     * 活动是否阻止玩家输入（移动、使用物品等）。
     * 默认返回 true（活动进行期间玩家被锁定）。
     */
    default boolean blocksInput() { return true; }

    /**
     * 渲染活动相关的 UI（进度条、状态提示等）。
     * 由 GameScene 在每帧渲染时调用。默认不做任何渲染。
     */
    default void render(Renderer renderer, int tileW, int tileH) {}
}
