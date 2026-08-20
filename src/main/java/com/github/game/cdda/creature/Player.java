package com.github.game.cdda.creature;

import com.github.game.cdda.Constants;

import com.github.game.cdda.creature.Creature;
import com.github.game.cdda.creature.CreatureActionContext;
import com.github.game.cdda.item.model.ItemStack;
import com.github.game.cdda.item.model.ItemType;
import com.github.game.cdda.item.world.PlayerInventory;
import com.github.game.cdda.log.GameLog;
import com.github.game.engine.core.Camera;
import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.sprite.Sprite;
import com.github.game.engine.core.sprite.SpriteManager;
import com.github.game.cdda.world.TileType;
import com.github.game.cdda.world.chunk.ChunkManager;

import java.awt.*;
import java.util.Random;

/**
 * 玩家角色。继承自 {@link Creature}，管理世界坐标位置、移动输入和字符渲染。
 *
 * <p>采用网格式移动：每次按键移动恰好一个瓦片的距离，
 * 位置始终对齐到瓦片边界，保证与地图字符完美对齐。
 *
 * <p>支持碰撞检测：移动前检查目标瓦片通行性。
 * 移动是回合制行动——每次成功移动消耗游戏时间。
 */
public class Player extends Creature {

    /** 世界坐标 X（像素，左上角，始终为 tileWidth 的整数倍） */
    private int worldX;
    /** 世界坐标 Y（像素，左上角，始终为 tileHeight 的整数倍） */
    private int worldY;

    /** 玩家像素宽度（= 一个字符宽度，由外部设置） */
    private int pixelWidth;
    /** 玩家像素高度（= 一个字符高度，由外部设置） */
    private int pixelHeight;

    /** 世界查询接口（碰撞检测用） */
    private ChunkManager chunkManager;
    /** 瓦片像素尺寸（碰撞检测用） */
    private int tileWidth;
    private int tileHeight;

    /** 随机数生成器（伤害浮动用） */
    private final Random random = new Random();

    /** 玩家背包 */
    private final PlayerInventory inventory;

    public Player(int startX, int startY) {
        this.worldX = startX;
        this.worldY = startY;
        // 默认速度 100 = 正常步行（1.2 m/s）
        this.speed = Constants.ENTITY_DEFAULT_SPEED;

        // 玩家基础属性
        this.strength = 10;
        this.agility = 10;
        this.endurance = 10;

        // 玩家感知
        this.visionRange = 10;
        this.hearingRange = 8;

        // 玩家生命
        this.maxHp = 100;
        this.hp = 100;

        // 玩家渲染
        this.displayChar = '@';
        this.displayColor = Color.WHITE;

        // 背包
        this.inventory = new PlayerInventory(this);
    }

    /**
     * 设置玩家像素尺寸。在瓦片尺寸确定后调用一次。
     */
    public void initDimensions(int tileWidth, int tileHeight) {
        this.pixelWidth = tileWidth;
        this.pixelHeight = tileHeight;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        // 对齐到瓦片边界
        this.worldX = Math.floorDiv(worldX, tileWidth) * tileWidth;
        this.worldY = Math.floorDiv(worldY, tileHeight) * tileHeight;
        // 更新瓦片坐标
        this.tileX = this.worldX / tileWidth;
        this.tileY = this.worldY / tileHeight;
    }

    /**
     * 设置世界查询接口（碰撞检测用）。
     */
    public void initWorld(ChunkManager chunkManager, int tileWidth, int tileHeight) {
        this.chunkManager = chunkManager;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    // ── 移动 ──────────────────────────────────

    /**
     * 尝试向指定方向移动一个瓦片。
     * 移动前检查目标瓦片是否可通过。
     *
     * @param dx 水平方向（-1=左, 0=不动, 1=右）
     * @param dy 垂直方向（-1=上, 0=不动, 1=下）
     * @return 是否成功移动
     */
    public boolean move(int dx, int dy) {
        if (dx == 0 && dy == 0) return false;
        if (chunkManager == null || tileWidth == 0 || tileHeight == 0) return false;

        int newX = worldX + dx * tileWidth;
        int newY = worldY + dy * tileHeight;

        if (!canMoveTo(newX, newY)) {
            return false;
        }

        worldX = newX;
        worldY = newY;
        // 更新瓦片坐标
        tileX = worldX / tileWidth;
        tileY = worldY / tileHeight;
        return true;
    }

    /**
     * 检查目标位置是否可通过。
     * 检查目标瓦片覆盖的四个角的瓦片通行性。
     */
    private boolean canMoveTo(int x, int y) {
        int left   = x;
        int top    = y;
        int right  = x + pixelWidth - 1;
        int bottom = y + pixelHeight - 1;

        int[][] corners = {
            {left,  top},
            {right, top},
            {left,  bottom},
            {right, bottom}
        };

        for (int[] corner : corners) {
            int tX = Math.floorDiv(corner[0], tileWidth);
            int tY = Math.floorDiv(corner[1], tileHeight);
            TileType tile = chunkManager.getTile(tX, tY);
            if (tile != null && !tile.isPassable()) {
                return false;
            }
        }
        return true;
    }

    // ── 战斗 ──────────────────────────────────

    /**
     * 近战攻击目标生物。
     * 伤害公式：基础伤害 = strength / 2 + 武器加成，浮动 ±20%。
     * 武器加成：检测背包中的狩猎武器（"hunting" 标签），取最高加成。
     *
     * @param target 目标生物
     * @return 实际造成的伤害值
     */
    public int meleeAttack(Creature target) {
        if (target == null || !target.isAlive()) return 0;

        // 基础伤害 = 力量的一半
        int baseDamage = strength / 2;
        // 至少造成 1 点伤害
        baseDamage = Math.max(1, baseDamage);

        // 武器加成：扫描背包中最高伤害加成的狩猎武器
        int weaponBonus = getBestWeaponBonus();
        baseDamage += weaponBonus;

        // ±20% 随机浮动
        double variance = 0.8 + random.nextDouble() * 0.4; // 0.8 ~ 1.2
        int finalDamage = (int) Math.round(baseDamage * variance);
        finalDamage = Math.max(1, finalDamage);

        // 应用伤害：如果是 Animal 且本次伤害会致死，调用 killByPlayer()
        // 确保死亡原因标记为 PLAYER_KILL → 触发掉落
        if (target instanceof com.github.game.cdda.creature.Animal animal
                && animal.getHp() <= finalDamage) {
            animal.killByPlayer();
        } else {
            target.takeDamage(finalDamage);
        }

        // 记录日志
        String targetName = getCreatureDisplayName(target);
        String weaponStr = weaponBonus > 0 ? "（武器+" + weaponBonus + "）" : "";
        if (target.isAlive()) {
            GameLog.getInstance().log(String.format("攻击了 %s，造成 %d 点伤害%s（剩余 %d/%d）",
                    targetName, finalDamage, weaponStr, target.getHp(), target.getMaxHp()));
        } else {
            GameLog.getInstance().log(String.format("击杀了 %s！（造成 %d 点伤害%s）",
                    targetName, finalDamage, weaponStr));
        }

        return finalDamage;
    }

    /**
     * 获取背包中最佳狩猎武器的伤害加成。
     * 扫描背包所有物品，查找带有 "hunting" 标签的物品，
     * 根据物品名称返回对应的伤害加成值。
     *
     * @return 最高武器加成（0 表示无武器）
     */
    private int getBestWeaponBonus() {
        if (inventory == null) return 0;
        int best = 0;
        for (ItemStack stack : inventory.getItems()) {
            if (stack == null || stack.isEmpty()) continue;
            ItemType type = stack.getType();
            // 检查 "hunting" 标签
            if (!type.hasTag("hunting")) continue;
            int bonus = getWeaponBonusForItem(type.getName());
            if (bonus > best) {
                best = bonus;
            }
        }
        return best;
    }

    /**
     * 根据物品名称获取武器伤害加成。
     * 使用常量配置，方便后续扩展和数据驱动。
     */
    private static int getWeaponBonusForItem(String itemName) {
        return switch (itemName) {
            case "stone_spear"    -> Constants.WEAPON_BONUS_STONE_SPEAR;
            case "wooden_spear"   -> Constants.WEAPON_BONUS_WOODEN_SPEAR;
            case "stone_axe"      -> Constants.WEAPON_BONUS_AXE;
            case "rusty_knife"    -> Constants.WEAPON_BONUS_KNIFE;
            default               -> 0;
        };
    }

    /**
     * 获取生物的显示名称（用于日志）。
     * 如果是 Animal，使用其当前生命阶段的名称。
     */
    private String getCreatureDisplayName(Creature creature) {
        if (creature instanceof com.github.game.cdda.creature.Animal) {
            return ((com.github.game.cdda.creature.Animal) creature).getStageName();
        }
        return "未知生物";
    }

    // ── 回合行动（玩家由输入驱动，空实现） ──────────────────────────────────

    @Override
    public void takeTurn(CreatureActionContext context) {
        // 玩家由输入驱动，不需要 AI 回合
    }

    // ── 渲染 ──────────────────────────────────

    /**
     * 渲染玩家。支持精灵模式和 ASCII 字符模式。
     * 通过 Camera 将世界坐标转换为视图局部坐标。
     */
    @Override
    public void render(Renderer renderer, Camera camera, int tileWidth, int tileHeight) {
        int viewX = camera.toViewX(worldX);
        int viewY = camera.toViewY(worldY);

        // 待机动画：随机微幅晃动（晃动幅度也随缩放）
        double zoom = camera.getZoom();
        viewX += (int) (getRenderOffsetX() * zoom);
        viewY += (int) (getRenderOffsetY() * zoom);

        // 缩放后的绘制尺寸
        int scaledW = (int) (tileWidth * zoom);
        int scaledH = (int) (tileHeight * zoom);

        // 优先使用精灵渲染（支持可变尺寸）
        if (SpriteManager.hasActivePack()) {
            Sprite sprite = SpriteManager.getSprite("player");
            if (sprite != null) {
                int drawW = (int) (scaledW * sprite.getTileWidth());
                int drawH = (int) (scaledH * sprite.getTileHeight());
                int offsetX = (int) (sprite.getAnchorX() * drawW);
                int offsetY = (int) (sprite.getAnchorY() * drawH);
                renderer.drawImage(sprite.getImage(), viewX - offsetX, viewY - offsetY, drawW, drawH);
                return;
            }
        }

        // 回退到字符渲染
        renderer.setColor(displayColor);
        int baselineY = viewY + renderer.getFontMetrics().getAscent();
        renderer.drawText(String.valueOf(displayChar), viewX, baselineY);
    }

    /**
     * 渲染玩家（兼容旧接口）。
     */
    public void render(Renderer renderer, Camera camera) {
        render(renderer, camera, tileWidth, tileHeight);
    }

    // ── 访问器 ──────────────────────────────────

    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }

    /**
     * 设置玩家世界坐标（像素坐标），并同步更新瓦片坐标。
     * 用于存档恢复位置。
     */
    public void setWorldPosition(int worldX, int worldY) {
        this.worldX = Math.floorDiv(worldX, tileWidth) * tileWidth;
        this.worldY = Math.floorDiv(worldY, tileHeight) * tileHeight;
        this.tileX = this.worldX / tileWidth;
        this.tileY = this.worldY / tileHeight;
    }

    public int getPixelWidth() { return pixelWidth; }
    public int getPixelHeight() { return pixelHeight; }

    /** 获取玩家背包 */
    public PlayerInventory getInventory() { return inventory; }

    @Override
    public int getTileX() {
        return tileX;
    }

    @Override
    public int getTileY() {
        return tileY;
    }
}
