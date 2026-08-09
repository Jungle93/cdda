package com.github.game.cdda.screen.overlay;

import com.github.game.cdda.Constants;
import com.github.game.cdda.GameWorld;
import com.github.game.cdda.Player;
import com.github.game.cdda.creature.CreatureManager;
import com.github.game.cdda.item.ItemRegistry;
import com.github.game.cdda.item.ItemStack;
import com.github.game.cdda.log.GameLog;
import com.github.game.cdda.screen.menu.MenuScreen;
import com.github.game.engine.core.GameEngine;
import com.github.game.engine.core.render.Renderer;

import java.awt.*;

/**
 * 调试菜单。
 * 按反引号（`）打开，显示游戏状态信息和调试操作。
 *
 * <p>功能：
 * <ul>
 *   <li>显示实时游戏状态（位置、时间、温度、能量等）</li>
 *   <li>切换调试信息叠加层</li>
 *   <li>快捷操作：回复生命、生成物品、传送到原点等</li>
 * </ul>
 */
public class DebugMenuScreen extends MenuScreen {

    private static final String TITLE = "调试菜单";

    private final GameWorld world;
    private final Player player;

    /** 菜单项定义 */
    private static final String[] ACTIONS = {
            "切换调试叠加层",
            "回复生命值 (+50)",
            "补充能量 (+30%)",
            "补充水分 (+30%)",
            "生成面包到背包",
            "生成水瓶到背包",
            "生成石斧到背包",
            "传送到原点 (0,0)",
            "前进 1 小时",
            "切换 FPS 显示",
    };

    public DebugMenuScreen(GameEngine engine, GameWorld world) {
        super(engine);
        this.world = world;
        this.player = world.getPlayer();
    }

    @Override
    protected int getItemCount() {
        return ACTIONS.length;
    }

    @Override
    protected void renderMenu(Renderer renderer) {
        // 半透明深色背景
        renderer.setColor(new Color(0, 0, 20, 230));
        renderer.fillRect(0, 0, getWidth(), getHeight());

        int width = getWidth();
        int height = getHeight();
        int fontSize = 13;
        int lineHeight = 18;

        // ── 标题 ──
        renderer.setFont(new Font("Monospaced", Font.BOLD, 20));
        renderer.setColor(new Color(0, 255, 128));
        String title = "[ " + TITLE + " ]";
        renderer.drawText(title, (width - renderer.getTextWidth(title)) / 2, 28);

        // ── 状态信息区 ──
        int y = 55;
        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));

        // 分隔线
        renderer.setColor(new Color(0, 180, 80));
        renderer.drawText("── 游戏状态 ──────────────────────────", 10, y);
        y += lineHeight + 2;

        // 位置
        renderer.setColor(Color.CYAN);
        int tileW = 14, tileH = 20;  // 默认值，仅用于显示
        renderer.drawText(String.format("玩家位置: 瓦片(%d, %d)  像素(%d, %d)",
                player.getTileX(), player.getTileY(),
                player.getWorldX(), player.getWorldY()), 10, y);
        y += lineHeight;

        // 时间
        renderer.setColor(Color.YELLOW);
        renderer.drawText(String.format("时间: %s  %s",
                world.getGameTime().formatDateTime(),
                world.getGameTime().getSeason().getFullName()), 10, y);
        y += lineHeight;

        // 温度
        renderer.setColor(world.getTemperatureManager().getTemperatureColor());
        renderer.drawText(String.format("环境温度: %.1f°C %s",
                world.getTemperatureManager().getTemperature(),
                world.getTemperatureManager().getTemperatureDescriptor()), 10, y);
        y += lineHeight;

        // 代谢
        renderer.setColor(Color.ORANGE);
        renderer.drawText(String.format("能量: %d%%  体温: %.1f°C %s",
                world.getMetabolismManager().getHungerPercent(),
                world.getMetabolismManager().getBodyTemperature(),
                world.getMetabolismManager().getBodyTempDescriptor()), 10, y);
        y += lineHeight;

        // 水分
        renderer.setColor(world.getHydrationManager().getThirstColor());
        renderer.drawText(String.format("水分: %d%% %s",
                world.getHydrationManager().getWaterPercent(),
                world.getHydrationManager().getThirstDescriptor()), 10, y);
        y += lineHeight;

        // 生物 / 区块 / 地面物品
        renderer.setColor(Color.GREEN);
        renderer.drawText(String.format("生物: %d  区块: %d  地面物品: %d  背包: %d种",
                world.getCreatureManager().getCreatureCount(),
                world.getChunkManager().getLoadedChunkCount(),
                world.getGroundItemManager().getCount(),
                player.getInventory().getItemCount()), 10, y);
        y += lineHeight;

        // 背包重量
        renderer.setColor(Color.LIGHT_GRAY);
        renderer.drawText(String.format("背包重量: %dg / %dg",
                (int) player.getInventory().getTotalWeight(),
                player.getInventory().getCarryCapacity()), 10, y);
        y += lineHeight + 4;

        // ── 分隔线 ──
        renderer.setColor(new Color(0, 180, 80));
        renderer.drawText("── 调试操作 ──────────────────────────", 10, y);
        y += lineHeight + 2;

        // ── 操作列表 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        int maxVisible = (height - y - 30) / lineHeight;
        int scrollOffset = 0;
        if (selectedIndex >= maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }

        for (int i = 0; i < ACTIONS.length; i++) {
            int visibleIndex = i - scrollOffset;
            if (visibleIndex < 0 || visibleIndex >= maxVisible) continue;

            boolean sel = (i == selectedIndex);
            String prefix = sel ? "▶ " : "  ";
            String line = prefix + ACTIONS[i];

            // 某些选项显示当前状态
            String suffix = "";
            if (i == 0) suffix = Constants.SHOW_DEBUG_INFO ? " [开]" : " [关]";
            if (i == 9) suffix = Constants.DEBUG_SHOW_FPS ? " [开]" : " [关]";

            renderer.setColor(sel ? Color.YELLOW : new Color(180, 220, 180));
            renderer.drawText(line + suffix, 10, y + visibleIndex * lineHeight);
        }

        // ── 底部提示 ──
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        renderer.setColor(Color.GRAY);
        String hint = "↑↓ 选择 | Enter 执行 | Esc 关闭";
        renderer.drawText(hint, (width - renderer.getTextWidth(hint)) / 2, height - 12);
    }

    @Override
    protected void onSelect(int index) {
        switch (index) {
            case 0: // 切换调试叠加层
                Constants.SHOW_DEBUG_INFO = !Constants.SHOW_DEBUG_INFO;
                log("调试叠加层: " + (Constants.SHOW_DEBUG_INFO ? "开" : "关"));
                break;

            case 1: // 回复生命
                player.heal(50);
                log("回复了 50 点生命 (HP: " + player.getHp() + "/" + player.getMaxHp() + ")");
                break;

            case 2: // 补充能量
                double energyAdd = player.getInventory().getCarryCapacity() * 0.3;
                // 使用 MetabolismManager 补充能量
                world.getMetabolismManager().addCalories(
                        Constants.CALORIE_MAX_POOL * 0.3);
                log("补充了 30% 能量");
                break;

            case 3: // 补充水分
                world.getHydrationManager().addWater(
                        Constants.WATER_MAX * 0.3);
                log("补充了 30% 水分");
                break;

            case 4: // 生成面包
                addItemToInventory(2, 3);  // bread id=2
                break;

            case 5: // 生成水瓶
                addItemToInventory(0, 2);  // water_bottle id=0
                break;

            case 6: // 生成石斧
                addItemToInventory(8, 1);  // stone_axe id=8
                break;

            case 7: // 传送到原点
                // 移动玩家到 (0,0) 瓦片
                player.move(-player.getTileX(), -player.getTileY());
                log("已传送到原点 (0,0)");
                break;

            case 8: // 前进 1 小时
                world.getGameTime().advance(3600);  // 3600 游戏秒 = 1 小时
                log("时间前进 1 小时");
                break;

            case 9: // 切换 FPS 显示
                Constants.DEBUG_SHOW_FPS = !Constants.DEBUG_SHOW_FPS;
                log("FPS 显示: " + (Constants.DEBUG_SHOW_FPS ? "开" : "关"));
                break;

            default:
                break;
        }
    }

    @Override
    protected void onCancel() {
        engine.getScreenManager().popScreen();
    }

    /** 添加物品到玩家背包 */
    private void addItemToInventory(int itemId, int count) {
        var type = ItemRegistry.getById(itemId);
        if (type == null) {
            log("物品 ID " + itemId + " 不存在");
            return;
        }
        ItemStack stack = new ItemStack(type, count);
        if (player.getInventory().addItem(stack)) {
            log("生成了 " + type.getName() + " x" + count);
        } else {
            log("背包超重，无法添加 " + type.getName());
        }
    }

    /** 记录日志 */
    private void log(String msg) {
        GameLog.getInstance().log("[调试] " + msg);
    }
}
