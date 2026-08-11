package com.github.game.cdda.creature.energy;

/**
 * 区块死亡记录。
 *
 * <p>追踪每个区块的自然死亡和饥饿死亡事件，用于：
 * <ul>
 *   <li>尸体分解 → 能量回流到植被层</li>
 *   <li>迁徙触发 → 食物丰富的区块吸引上层捕食者</li>
 * </ul>
 */
public class DeathRecord {

    /** 区块 X */
    public final int chunkX;

    /** 区块 Y */
    public final int chunkY;

    /** 分解回流的总能量 */
    private int totalEnergyReturned;

    /** 当前未分解尸体数 */
    private int corpseCount;

    /** 最后衰减回合 */
    private long lastDecayRound;

    /** 此区块是否有足够食物吸引迁徙的标记 */
    private boolean foodRich;

    /**
     * 创建死亡记录。
     */
    public DeathRecord(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.lastDecayRound = 0;
    }

    /** 添加一具尸体 */
    public void addCorpse(int bodyEnergy) {
        corpseCount++;
        // 分解能量 = bodyEnergy * 0.5（一半回归自然，一半损耗）
        totalEnergyReturned += bodyEnergy / 2;
    }

    /** 移除一具尸体（被食腐动物吃掉） */
    public void removeCorpse() {
        if (corpseCount > 0) {
            corpseCount--;
        }
    }

    /** 衰减处理 */
    public void decay(long currentRound) {
        // 每 100 回合衰减一次
        if (currentRound - lastDecayRound >= 100) {
            totalEnergyReturned = (int) (totalEnergyReturned * 0.9);
            lastDecayRound = currentRound;
        }
    }

    /** 标记为食物丰富 */
    public void markFoodRich() {
        this.foodRich = true;
    }

    /** 清除食物丰富标记（迁徙后重置） */
    public void clearFoodRich() {
        this.foodRich = false;
        totalEnergyReturned = 0;
        corpseCount = 0;
    }

    // ── 访问器 ──────────────────────────────────

    public int getTotalEnergyReturned() { return totalEnergyReturned; }
    public int getCorpseCount() { return corpseCount; }
    public boolean isFoodRich() { return foodRich; }

    /** 是否为空记录（无尸体、无能量） */
    public boolean isEmpty() {
        return corpseCount == 0 && totalEnergyReturned == 0 && !foodRich;
    }
}
