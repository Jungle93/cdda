package com.github.game.cdda.creature.energy;

import com.github.game.cdda.creature.Animal;
import com.github.game.cdda.world.chunk.ChunkCoords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能量流动管理器。
 *
 * <p>管理整个生态系统的能量流动：
 * <ul>
 *   <li>追踪每个区块的死亡记录和能量回流</li>
 *   <li>处理尸体分解（定时衰减）</li>
 *   <li>迁徙触发：能量累积达标 → 标记"食物丰富"区块</li>
 *   <li>能量回流：尸体分解 → 植被生长加速</li>
 * </ul>
 *
 * <p>能量传递规则：
 * <ul>
 *   <li>被吃：猎物能量的 20% 转移给捕食者</li>
 *   <li>自然死亡：能量 50% 回流到植被层，50% 损耗</li>
 *   <li>玩家杀死：掉落物品，能量不传递</li>
 * </ul>
 */
public class EnergyFlowManager {

    private static final Logger logger = LoggerFactory.getLogger(EnergyFlowManager.class);

    /** 能量传递效率（20%） */
    public static final double TRANSFER_RATE = 0.2;

    /** 迁徙触发阈值 */
    private static final int MIGRATION_THRESHOLD = 200;

    /** 迁徙概率上限 */
    private static final double MAX_MIGRATION_CHANCE = 0.15;

    /** 迁徙分母：能量 / 此值 = 概率 */
    private static final double MIGRATION_DIVISOR = 2000.0;

    /** 区块死亡记录 */
    private final Map<Long, DeathRecord> deathRecords = new ConcurrentHashMap<>();

    /** 植被生长加成（chunkKey → 额外密度 0~1） */
    private final Map<Long, Double> vegetationBoosts = new ConcurrentHashMap<>();

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 当前回合数 */
    private long currentRound;

    /** 区块最大动物数（APEX 领地限制） */
    private static final int MAX_APEX_PER_CHUNK = 2;

    // ── 死亡记录 ──────────────────────────────────

    /**
     * 记录自然死亡（老死/饿死）。
     * 不掉落物品，尸体分解，能量回流。
     */
    public void recordNaturalDeath(Animal animal) {
        long key = ChunkCoords.key(animal.getTileX(), animal.getTileY());
        DeathRecord record = deathRecords.computeIfAbsent(key,
                k -> new DeathRecord(ChunkCoords.toChunkX(animal.getTileX()), ChunkCoords.toChunkY(animal.getTileY())));

        record.addCorpse(animal.getBodyEnergy());

        // 检查是否触发迁徙
        checkMigration(key, record);

        com.github.game.cdda.log.EcologyLog.getInstance().log(
                com.github.game.cdda.log.EcologyLog.Category.DEATH,
                String.format("%s 在 (%d,%d) 自然死亡",
                        animal.getLocalizedName(), animal.getTileX(), animal.getTileY()));

        logger.debug("自然死亡: {} at ({},{}), bodyEnergy={}",
                animal.getDefinition().name, animal.getTileX(), animal.getTileY(),
                animal.getBodyEnergy());
    }

    /**
     * 记录捕食事件。
     * 能量直接转移给捕食者。
     *
     * @param prey     猎物
     * @param predator 捕食者
     */
    public void recordPredation(Animal prey, Animal predator) {
        // 基础能量获取 = 猎物的 20%（生态学十分之一定律的保守近似）
        int baseGain = (int) (prey.getBodyEnergy() * TRANSFER_RATE);
        // 物种特定的猎食增益（不同捕食者效率不同）
        int huntGain = predator.getEnergyConfig().getHuntGain();
        int gained = Math.max(baseGain, huntGain);
        int actualGain = Math.min(gained, predator.getMaxBodyEnergy() - predator.getBodyEnergy());

        if (actualGain > 0) {
            predator.addBodyEnergy(actualGain);
            com.github.game.cdda.log.EcologyLog.getInstance().logPredation(
                    predator.getLocalizedName(), prey.getLocalizedName(),
                    String.format("(%d,%d)", prey.getTileX(), prey.getTileY()));
            logger.debug("捕食: {} 吃掉 {}，获得 {} 能量（base={}%, huntGain={}）",
                    predator.getDefinition().name, prey.getDefinition().name, actualGain,
                    (int) (TRANSFER_RATE * 100), huntGain);
        } else {
            logger.trace("捕食: {} 吃掉 {}，但能量已满",
                    predator.getDefinition().name, prey.getDefinition().name);
        }
    }

    /**
     * 记录玩家杀死。
     * 掉落物品，能量部分回流到植被（30%，低于自然死亡的 50%）。
     */
    public void recordPlayerKill(Animal animal) {
        // 30% 能量回流到植被（尸体部分被作为战利品带走）
        int backflow = (int) (animal.getBodyEnergy() * 0.3);
        if (backflow > 0) {
            long key = ChunkCoords.key(animal.getTileX(), animal.getTileY());
            DeathRecord record = deathRecords.computeIfAbsent(key,
                    k -> new DeathRecord(ChunkCoords.toChunkX(animal.getTileX()), ChunkCoords.toChunkY(animal.getTileY())));
            record.addCorpse(backflow);
            checkMigration(key, record);
            com.github.game.cdda.log.EcologyLog.getInstance().log(
                    com.github.game.cdda.log.EcologyLog.Category.DEATH,
                    String.format("%s 在 (%d,%d) 被玩家击杀",
                            animal.getLocalizedName(), animal.getTileX(), animal.getTileY()));
        }

        logger.debug("玩家杀死: {} at ({},{}), bodyEnergy={}, 回流={}",
                animal.getDefinition().name, animal.getTileX(), animal.getTileY(),
                animal.getBodyEnergy(), backflow);
    }

    // ── 尸体分解 ──────────────────────────────────

    /**
     * 处理尸体分解衰减。
     * 每 100 回合衰减一次未处理的尸体。
     */
    public void processDecay() {
        currentRound++;

        deathRecords.entrySet().removeIf(entry -> {
            DeathRecord record = entry.getValue();
            record.decay(currentRound);
            return record.isEmpty();
        });
    }

    // ── 迁徙触发 ──────────────────────────────────

    /**
     * 检查区块是否触发迁徙。
     *
     * @param chunkKey 区块键
     * @param record   死亡记录
     */
    private void checkMigration(long chunkKey, DeathRecord record) {
        if (record.getTotalEnergyReturned() >= MIGRATION_THRESHOLD) {
            if (!record.isFoodRich()) {
                record.markFoodRich();
                logger.info("区块 ({},{}) 标记为食物丰富（能量={}）",
                        record.chunkX, record.chunkY, record.getTotalEnergyReturned());
            }
        }
    }

    /**
     * 检查是否应迁徙来上层捕食者。
     *
     * @param chunkKey 区块键
     * @param trophicLevel 目标营养级
     * @return true 如果应迁徙
     */
    public boolean shouldSpawnPredator(long chunkKey, TrophicLevel trophicLevel) {
        DeathRecord record = deathRecords.get(chunkKey);
        if (record == null || !record.isFoodRich()) {
            return false;
        }

        // 计算迁徙概率
        double probability = Math.min(MAX_MIGRATION_CHANCE,
                record.getTotalEnergyReturned() / MIGRATION_DIVISOR);

        if (random.nextDouble() < probability) {
            // 迁徙成功，重置记录
            record.clearFoodRich();
            logger.info("迁徙触发: 区块 ({},{}) 迎来 {}（能量={})",
                    record.chunkX, record.chunkY, trophicLevel.getDisplayName(),
                    record.getTotalEnergyReturned());
            return true;
        }

        return false;
    }

    // ── 能量回流 → 植被加速 ──────────────────────────────────

    /**
     * 获取区块的植被生长加成（来自尸体分解）。
     *
     * @param chunkKey 区块键
     * @return 额外密度 0~0.3
     */
    public double getVegetationBoost(long chunkKey) {
        Double boost = vegetationBoosts.get(chunkKey);
        return boost != null ? boost : 0.0;
    }

    /**
     * 更新植被生长加成（由尸体分解驱动）。
     */
    public void updateVegetationBoosts() {
        for (Map.Entry<Long, DeathRecord> entry : deathRecords.entrySet()) {
            long key = entry.getKey();
            DeathRecord record = entry.getValue();

            double boost = Math.min(0.3, record.getTotalEnergyReturned() / 1000.0);
            if (boost > 0) {
                vegetationBoosts.put(key, boost);
            }
        }

        // 衰减植被加成（ConcurrentHashMap 不支持在 removeIf 中 setValue，分两步处理）
        java.util.List<Long> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<Long, Double> entry : vegetationBoosts.entrySet()) {
            double newBoost = entry.getValue() * 0.95;
            if (newBoost < 0.01) {
                toRemove.add(entry.getKey());
            } else {
                vegetationBoosts.put(entry.getKey(), newBoost);
            }
        }
        toRemove.forEach(vegetationBoosts::remove);
    }

    // ── 区块动物数量查询 ──────────────────────────────────

    /**
     * 获取区块 APEX 最大数量限制。
     */
    public int getMaxApexPerChunk() {
        return MAX_APEX_PER_CHUNK;
    }

    // ── 辅助方法 ──────────────────────────────────

    /** 获取当前回合数 */
    public long getCurrentRound() {
        return currentRound;
    }

    /** 设置当前回合数 */
    public void setCurrentRound(long round) {
        this.currentRound = round;
    }

    /** 获取区块死亡记录（调试用） */
    public DeathRecord getDeathRecord(long chunkKey) {
        return deathRecords.get(chunkKey);
    }

    /** 清理所有记录（用于测试） */
    public void clear() {
        deathRecords.clear();
        vegetationBoosts.clear();
    }
}
