package com.github.game.cdda.npc;

import com.github.game.cdda.Player;
import com.github.game.cdda.creature.Creature;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * NPC 管理器。
 * 管理所有 NPC 的生命周期：生成、回合处理、交互、渲染。
 *
 * <p>NPC 同时注册到 CreatureManager 的空间索引和回合系统中，
 * NpcManager 负责 NPC 特有的逻辑（交互、调试生成、渲染）。
 */
public class NpcManager {

    private static final Logger logger = LoggerFactory.getLogger(NpcManager.class);

    /** 所有 NPC 列表 */
    private final List<Npc> npcs = new ArrayList<>();

    /** CreatureManager 引用（用于空间索引和回合系统） */
    private final CreatureManager creatureManager;

    /** 地图管理器 */
    private final ChunkManager chunkManager;

    /** 回合管理器 */
    private final com.github.game.cdda.TurnManager turnManager;

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 玩家引用（用于交互判定） */
    private Player player;

    /** 当前正在交互的 NPC（可为 null） */
    private Npc interactingNpc;

    /**
     * 创建 NPC 管理器。
     *
     * @param creatureManager 生物管理器
     * @param chunkManager    地图管理器
     * @param turnManager     回合管理器
     */
    public NpcManager(CreatureManager creatureManager,
                      ChunkManager chunkManager,
                      com.github.game.cdda.TurnManager turnManager) {
        this.creatureManager = creatureManager;
        this.chunkManager = chunkManager;
        this.turnManager = turnManager;
    }

    /**
     * 设置玩家引用。
     */
    public void setPlayer(Player player) {
        this.player = player;
    }

    // ═══════════════════════════════════════════════
    // 添加/移除 NPC
    // ═══════════════════════════════════════════════

    /**
     * 添加 NPC 到世界。
     * 同时注册到 CreatureManager 的空间索引和回合系统。
     */
    public void addNpc(Npc npc) {
        if (npc == null) return;
        npcs.add(npc);
        // 注册到 CreatureManager 的空间索引和回合系统
        creatureManager.addCreature(npc);
        logger.debug("添加 NPC: {} at ({},{})", npc.getName(), npc.getTileX(), npc.getTileY());
    }

    /**
     * 从世界移除 NPC。
     */
    public void removeNpc(Npc npc) {
        npcs.remove(npc);
        creatureManager.removeCreature(npc);
        if (interactingNpc == npc) {
            interactingNpc = null;
        }
    }

    // ═══════════════════════════════════════════════
    // 调试生成
    // ═══════════════════════════════════════════════

    /**
     * 在指定位置生成一个调试 NPC。
     *
     * @param tileX 瓦片 X
     * @param tileY 瓦片 Y
     * @param type  NPC 类型
     * @return 生成的 NPC
     */
    public Npc spawnDebugNpc(int tileX, int tileY, NpcType type) {
        // 随机分配地域
        NpcRegion[] regions = NpcRegion.values();
        NpcRegion region = regions[random.nextInt(regions.length)];

        Npc npc = new Npc(region, type, tileX, tileY);

        // 尝试加载注册表中的模板
        String templateId = switch (type) {
            case FRIENDLY -> "villager";
            case NEUTRAL -> "wanderer";
            case HOSTILE -> "bandit";
            case FUNCTIONAL -> "guide";
        };
        NpcDefinition def = NpcRegistry.get(templateId);
        if (def != null) {
            npc.setDefinition(def);
        }

        addNpc(npc);
        GameLog.getInstance().log(
                String.format("调试生成: %s（%s，%s）出现在 (%d, %d)",
                        npc.getName(), region.name, type.name(), tileX, tileY));
        return npc;
    }

    /**
     * 在玩家附近随机生成多个调试 NPC。
     *
     * @param count 数量
     * @param maxDistance 最大距离（瓦片）
     */
    public void spawnDebugNpcsNearPlayer(int count, int maxDistance) {
        if (player == null) {
            logger.warn("玩家未设置，无法在附近生成 NPC");
            return;
        }

        int playerX = player.getTileX();
        int playerY = player.getTileY();
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            // 随机类型
            NpcType[] types = NpcType.values();
            NpcType type = types[random.nextInt(types.length)];

            // 随机位置
            int dx = random.nextInt(maxDistance * 2 + 1) - maxDistance;
            int dy = random.nextInt(maxDistance * 2 + 1) - maxDistance;
            int tileX = playerX + dx;
            int tileY = playerY + dy;

            // 检查瓦片是否可通行
            TileType tile = chunkManager.getTile(tileX, tileY);
            if (tile == null || !tile.isPassable()) {
                continue;
            }

            // 检查是否已有生物
            Creature existing = creatureManager.getCreatureAtTile(tileX, tileY);
            if (existing != null && existing.isAlive()) {
                continue;
            }

            spawnDebugNpc(tileX, tileY, type);
            spawned++;
        }

        if (spawned > 0) {
            GameLog.getInstance().log(
                    String.format("调试生成: %d 个 NPC 出现在你附近", spawned));
        }
    }

    // ═══════════════════════════════════════════════
    // 回合处理
    // ═══════════════════════════════════════════════

    /**
     * 请求处理 NPC 回合。
     * NPC 的回合处理由 CreatureManager 统一调度（因为 NPC 也是 Creature），
     * 此方法保留用于未来独立调度。
     */
    public void requestTurnProcessing(CreatureActionContext context) {
        // NPC 回合已由 CreatureManager 统一处理（Npc extends Creature）
        // 此处仅更新交互状态
        if (interactingNpc != null && !interactingNpc.isAlive()) {
            interactingNpc = null;
        }
    }

    // ═══════════════════════════════════════════════
    // 交互
    // ═══════════════════════════════════════════════

    /**
     * 查找玩家面前最近的 NPC（1 瓦片范围内）。
     *
     * @return 最近的 NPC，无则返回 null
     */
    public Npc findNearestNpcToPlayer() {
        if (player == null) return null;

        int playerX = player.getTileX();
        int playerY = player.getTileY();

        // 检查玩家相邻的 8 个瓦片 + 自身位置
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Creature c = creatureManager.getCreatureAtTile(
                        playerX + dx, playerY + dy);
                if (c != null && c.isAlive() && c instanceof Npc npc) {
                    return npc;
                }
            }
        }

        // 如果相邻没有，扩大范围到视野内
        List<Creature> visible = creatureManager.getVisibleCreatures(
                playerX, playerY, player.getVisionRange());
        for (Creature c : visible) {
            if (c instanceof Npc npc) {
                return npc;
            }
        }

        return null;
    }

    /**
     * 开始与指定 NPC 交互。
     */
    public void startInteraction(Npc npc) {
        if (npc == null || !npc.isAlive()) return;
        interactingNpc = npc;
        npc.getSocial().recordInteraction(
                player != null ? getCurrentGameSeconds() : 0);
    }

    /**
     * 结束当前交互。
     */
    public void endInteraction() {
        if (interactingNpc != null) {
            interactingNpc.endInteraction();
            interactingNpc = null;
        }
    }

    /**
     * 是否正在与 NPC 交互。
     */
    public boolean isInteracting() {
        return interactingNpc != null;
    }

    /**
     * 获取当前交互的 NPC。
     */
    public Npc getInteractingNpc() {
        return interactingNpc;
    }

    /**
     * 获取当前游戏时间（秒）。
     */
    private long getCurrentGameSeconds() {
        if (turnManager != null && turnManager.getGameTime() != null) {
            return turnManager.getGameTime().getTotalSeconds();
        }
        return 0;
    }

    // ═══════════════════════════════════════════════
    // 渲染
    // ═══════════════════════════════════════════════

    /**
     * 渲染所有存活的 NPC。
     * 复用 CreatureManager 的空间索引渲染（NPC 作为 Creature 渲染）。
     */
    public void renderNpcs(Renderer renderer, Camera camera, int tileWidth, int tileHeight) {
        // NPC 已通过 CreatureManager.addCreature 注册，
        // CreatureManager.renderCreatures 会统一渲染
        // 此处保留作为独立渲染入口
        for (Npc npc : npcs) {
            if (npc.isAlive()) {
                npc.render(renderer, camera, tileWidth, tileHeight);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    /**
     * 获取所有 NPC 列表。
     */
    public List<Npc> getAllNpcs() {
        return Collections.unmodifiableList(npcs);
    }

    /**
     * 获取存活 NPC 数量。
     */
    public int getAliveCount() {
        int count = 0;
        for (Npc npc : npcs) {
            if (npc.isAlive()) count++;
        }
        return count;
    }

    /**
     * 清理死亡的 NPC。
     *
     * @return 清理数量
     */
    public int cleanupDeadNpcs() {
        int removed = 0;
        Iterator<Npc> it = npcs.iterator();
        while (it.hasNext()) {
            Npc npc = it.next();
            if (!npc.isAlive()) {
                creatureManager.removeCreature(npc);
                it.remove();
                removed++;
            }
        }
        return removed;
    }
}
