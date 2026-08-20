package com.github.game.cdda.creature;

import com.github.game.cdda.world.chunk.ChunkCoords;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生物空间索引（chunk-based 空间哈希）。
 *
 * <p>将生物按所在区块组织，提供 O(1) 位置查询和 O(可见区块) 遍历，
 * 替代 CreatureManager 中原有的 O(N) 全列表扫描。
 *
 * <h3>使用方式：</h3>
 * <ul>
 *   <li>{@link #add(Creature)} — 添加生物到索引</li>
 *   <li>{@link #remove(Creature)} — 从索引移除</li>
 *   <li>{@link #move(Creature, int, int, int, int)} — 移动后更新位置</li>
 *   <li>{@link #getAtTile(int, int)} — 查询某瓦片上的生物</li>
 *   <li>{@link #getInRadius(int, int, int)} — 查询某半径内的生物</li>
 *   <li>{@link #getInChunk(int, int)} — 查询某区块内的生物</li>
 * </ul>
 *
 * <h3>线程安全：</h3>
 * <p>内部使用 ConcurrentHashMap 存储，单个方法调用线程安全。
 * 但组合操作（如 remove + add）需要外部同步。
 */
public class CreatureGrid {

    /** 区块大小（瓦片数） */
    private static final int CHUNK_SIZE = 32;

    /** 区块 → 生物列表 */
    private final ConcurrentHashMap<Long, List<Creature>> chunkMap = new ConcurrentHashMap<>();

    /**
     * 将瓦片坐标转为区块坐标。
     */
    private static int tileToChunk(int tileCoord) {
        return ChunkCoords.toChunkX(tileCoord);
    }

    /**
     * 添加生物到空间索引。
     */
    public void add(Creature creature) {
        int cx = tileToChunk(creature.getTileX());
        int cy = tileToChunk(creature.getTileY());
        long key = ChunkCoords.key(cx, cy);
        chunkMap.computeIfAbsent(key, k -> new ArrayList<>()).add(creature);
    }

    /**
     * 从空间索引中移除生物。
     */
    public void remove(Creature creature) {
        int cx = tileToChunk(creature.getTileX());
        int cy = tileToChunk(creature.getTileY());
        long key = ChunkCoords.key(cx, cy);
        List<Creature> list = chunkMap.get(key);
        if (list != null) {
            list.remove(creature);
            if (list.isEmpty()) {
                chunkMap.remove(key, list);
            }
        }
    }

    /**
     * 更新生物位置（移动后调用）。
     *
     * @param creature  生物实例
     * @param oldTileX  旧瓦片 X
     * @param oldTileY  旧瓦片 Y
     * @param newTileX  新瓦片 X
     * @param newTileY  新瓦片 Y
     */
    public void move(Creature creature, int oldTileX, int oldTileY,
                     int newTileX, int newTileY) {
        int oldCx = tileToChunk(oldTileX);
        int oldCy = tileToChunk(oldTileY);
        int newCx = tileToChunk(newTileX);
        int newCy = tileToChunk(newTileY);

        // 同一区块内移动，无需更新索引
        if (oldCx == newCx && oldCy == newCy) return;

        long oldKey = ChunkCoords.key(oldCx, oldCy);
        long newKey = ChunkCoords.key(newCx, newCy);

        List<Creature> oldList = chunkMap.get(oldKey);
        if (oldList != null) {
            oldList.remove(creature);
            if (oldList.isEmpty()) {
                chunkMap.remove(oldKey, oldList);
            }
        }

        chunkMap.computeIfAbsent(newKey, k -> new ArrayList<>()).add(creature);
    }

    /**
     * 查询指定瓦片上的第一个存活生物。
     *
     * @return 该位置的生物，无则返回 null
     */
    public Creature getAtTile(int tileX, int tileY) {
        int cx = tileToChunk(tileX);
        int cy = tileToChunk(tileY);
        long key = ChunkCoords.key(cx, cy);
        List<Creature> list = chunkMap.get(key);
        if (list == null) return null;

        for (Creature c : list) {
            if (c.isAlive() && c.getTileX() == tileX && c.getTileY() == tileY) {
                return c;
            }
        }
        return null;
    }

    /**
     * 查询指定瓦片上的所有存活生物。
     *
     * @param tileX 瓦片 X
     * @param tileY 瓦片 Y
     * @return 该位置的存活生物列表（可能为空，不含已死亡的生物）
     */
    public List<Creature> getAllAtTile(int tileX, int tileY) {
        int cx = tileToChunk(tileX);
        int cy = tileToChunk(tileY);
        long key = ChunkCoords.key(cx, cy);
        List<Creature> list = chunkMap.get(key);
        if (list == null) return List.of();

        List<Creature> result = new ArrayList<>();
        for (Creature c : list) {
            if (c.isAlive() && c.getTileX() == tileX && c.getTileY() == tileY) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * 查询指定半径（曼哈顿距离）内的所有存活生物。
     *
     * @param centerX     中心瓦片 X
     * @param centerY     中心瓦片 Y
     * @param maxDistance 最大曼哈顿距离
     * @return 生物列表（不保证排序）
     */
    public List<Creature> getInRadius(int centerX, int centerY, int maxDistance) {
        int cx = tileToChunk(centerX);
        int cy = tileToChunk(centerY);
        // 需要检查的区块范围（曼哈顿距离 → 区块数）
        int chunkRadius = (maxDistance + CHUNK_SIZE - 1) / CHUNK_SIZE;

        List<Creature> result = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dy = -chunkRadius; dy <= chunkRadius; dy++) {
                long key = ChunkCoords.key(cx + dx, cy + dy);
                List<Creature> list = chunkMap.get(key);
                if (list == null) continue;

                for (Creature c : list) {
                    if (!c.isAlive()) continue;
                    int dist = Math.abs(c.getTileX() - centerX)
                             + Math.abs(c.getTileY() - centerY);
                    if (dist <= maxDistance) {
                        result.add(c);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 查询指定区块内的所有存活生物。
     *
     * @param chunkX 区块 X
     * @param chunkY 区块 Y
     * @return 生物列表
     */
    public List<Creature> getInChunk(int chunkX, int chunkY) {
        long key = ChunkCoords.key(chunkX, chunkY);
        List<Creature> list = chunkMap.get(key);
        if (list == null) return List.of();
        List<Creature> result = new ArrayList<>(list.size());
        for (Creature c : list) {
            if (c.isAlive()) result.add(c);
        }
        return result;
    }

    /**
     * 统计指定生物附近（曼哈顿距离）的同种存活生物数量。
     * 仅检查生物所在的区块及相邻 8 个区块，而非全图扫描。
     *
     * @param center  中心生物
     * @param maxDist 最大曼哈顿距离
     * @return 同种数量（不含自身）
     */
    public int countSameSpeciesNearby(Animal center, int maxDist) {
        String speciesId = center.getDefinition().id;
        int cx = tileToChunk(center.getTileX());
        int cy = tileToChunk(center.getTileY());
        int chunkRadius = (maxDist + CHUNK_SIZE - 1) / CHUNK_SIZE;

        int count = 0;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dy = -chunkRadius; dy <= chunkRadius; dy++) {
                long key = ChunkCoords.key(cx + dx, cy + dy);
                List<Creature> list = chunkMap.get(key);
                if (list == null) continue;

                for (Creature c : list) {
                    if (c == center || !c.isAlive() || !(c instanceof Animal)) continue;
                    Animal other = (Animal) c;
                    if (!speciesId.equals(other.getDefinition().id)) continue;
                    int dist = Math.abs(c.getTileX() - center.getTileX())
                             + Math.abs(c.getTileY() - center.getTileY());
                    if (dist <= maxDist) count++;
                }
            }
        }
        return count;
    }

    /**
     * 获取所有存活生物（用于向后兼容的全列表遍历）。
     */
    public List<Creature> getAllAlive() {
        List<Creature> result = new ArrayList<>();
        for (List<Creature> list : chunkMap.values()) {
            for (Creature c : list) {
                if (c.isAlive()) result.add(c);
            }
        }
        return result;
    }

    /**
     * 获取总生物数（含死亡）。
     */
    public int totalCreatureCount() {
        int count = 0;
        for (List<Creature> list : chunkMap.values()) {
            count += list.size();
        }
        return count;
    }

    /**
     * 清空索引。
     */
    public void clear() {
        chunkMap.clear();
    }
}
