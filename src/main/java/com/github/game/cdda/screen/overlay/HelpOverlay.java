package com.github.game.cdda.screen.overlay;

import com.github.game.engine.core.render.Renderer;
import com.github.game.engine.core.scene.GameOverlay;
import com.github.game.engine.core.scene.Viewport;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;

/**
 * 帮助覆盖层。
 * 显示游戏控制说明和机制介绍，分 3 页展示。
 *
 * <p>操作：
 * <ul>
 *   <li>←/→ 或 空格：翻页</li>
 *   <li>ESC 或 F1：关闭</li>
 * </ul>
 */
public class HelpOverlay extends GameOverlay {

    /** 面板内边距 */
    private static final int PADDING = 20;

    /** 行高 */
    private static final int LINE_HEIGHT = 22;

    /** 字体大小 */
    private static final int FONT_SIZE = 13;

    /** 是否已显示过帮助（单次游戏生命周期内） */
    private static boolean hasShownAutoHelp = false;

    /** 当前页码（0-2） */
    private int currentPage = 0;

    /** 页面内容：4 页，每页多行 */
    private static final String[][] PAGES = {
        {
            "══════ 基本控制 ═════",
            "",
            "移动/攻击 .............. 方向键 / WASD",
            "等待一回合 ................. 5",
            "等待十回合 ................. -",
            "",
            "拾取物品 ................. G",
            "丢弃物品 ................. D",
            "进食 ..................... E",
            "使用物品 ................. A",
            "背包 ..................... I",
            "合成 ..................... F2",
            "",
            "观察模式 ................. L",
            "对话 ..................... C",
            "大地图 ................... M",
        },
        {
            "══════ 高级功能 ══════",
            "",
            "放大视角 ................. ]",
            "缩小视角 ................. [",
            "鼠标滚轮 ................. 缩放",
            "",
            "游戏菜单 ................. ESC",
            "调试菜单 ................. `",
            "帮助 ..................... F1",
            "",
            "日志展开/折叠 ............ V",
            "日志滚动 ................. ↑↓",
            "",
            "观察模式中 Tab 切换生物",
            "NPC 选择中 Tab 切换 NPC",
        },
        {
            "══════ 游戏机制 ═════",
            "",
            "• 回合制：玩家行动后生物才会行动",
            "• 移动点耗尽后需等待恢复",
            "",
            "• 饥饿/体温/水分影响身体状态",
            "• 四季变换影响环境温度",
            "• 动植物自然生长繁殖",
            "",
            "• 砍树需要多回合（30回合）",
            "• 砍灌木需要10回合",
            "",
            "• 方向键移动时遇到生物会自动攻击",
            "• 武器伤害加成从背包自动计算",
        },
        {
            "══════ NPC 与交易 ═══",
            "",
            "• 靠近 NPC 后按 C 键进入交互",
            "• 选择'交易'后用 ↑↓ 浏览物品",
            "• Enter 添加想要的物品到清单",
            "• D 键移除已选物品",
            "• F 键确认并进入交易界面",
            "",
            "交易界面操作：",
            "• Tab/Q/E — 切换背包/提供物面板",
            "• ↑↓ — 选择物品",
            "• Enter — 添加/移除物品",
            "• ←→ — 调整交易数量",
            "• F — 确认交易（ESC 取消）",
            "",
            "• NPC 态度影响对话和交易意愿",
            "• 敌对 NPC 拒绝交易",
        }
    };

    /** 页码提示 */
    private static final String[] PAGE_TITLES = {
        "第 1/4 页 — 基本控制",
        "第 2/4 页 — 高级功能",
        "第 3/4 页 — 游戏机制",
        "第 4/4 页 — NPC 与交易"
    };

    public HelpOverlay(Viewport viewport) {
        super(viewport);
    }

    /**
     * 首次进入游戏时自动显示帮助（单次生命周期内仅一次）。
     *
     * @param create 创建回调，返回 HelpOverlay 实例
     * @param show   显示回调
     */
    public static void autoShowIfFirstTime(java.util.function.Function<Viewport, HelpOverlay> create,
                                           java.util.function.Consumer<HelpOverlay> show) {
        if (!hasShownAutoHelp) {
            hasShownAutoHelp = true;
            show.accept(create.apply(null));
        }
    }

    @Override
    public void onKeyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
            case KeyEvent.VK_F1:
                dismiss();
                return;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_SPACE:
                // 左右翻页
                if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_SPACE) {
                    currentPage = (currentPage + 1) % PAGES.length;
                } else {
                    currentPage = (currentPage - 1 + PAGES.length) % PAGES.length;
                }
                return;
            default:
                break;
        }
    }

    @Override
    public void render(Renderer renderer) {
        // 半透明背景（让玩家能看到背后的游戏画面）
        renderOverlayBackground(renderer);

        int vpW = viewport.getWidth();
        int vpH = viewport.getHeight();

        // 计算面板尺寸（居中显示）
        int panelWidth = Math.min(500, vpW - 40);
        int panelHeight = Math.min(340, vpH - 40);
        int panelX = (vpW - panelWidth) / 2;
        int panelY = (vpH - panelHeight) / 2;

        // 绘制面板背景
        renderPanel(renderer, panelX, panelY, panelWidth, panelHeight);

        // 设置字体
        renderer.setFont(new Font("Monospaced", Font.PLAIN, FONT_SIZE));

        int contentX = panelX + PADDING;
        int contentY = panelY + PADDING + LINE_HEIGHT;

        // 绘制页标题
        renderer.setColor(Color.YELLOW);
        drawCentered(renderer, PAGE_TITLES[currentPage], vpW, panelY + PADDING + 14);

        // 绘制页面内容
        renderer.setColor(Color.WHITE);
        for (String line : PAGES[currentPage]) {
            if (line.startsWith("══")) {
                // 标题行用黄色
                renderer.setColor(new Color(255, 200, 100));
                drawCentered(renderer, line, vpW, contentY);
                renderer.setColor(Color.WHITE);
            } else if (line.isEmpty()) {
                // 空行跳过
            } else {
                renderer.drawText(line, contentX, contentY);
            }
            contentY += LINE_HEIGHT;
        }

        // 绘制底部提示
        renderer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        renderer.setColor(new Color(150, 150, 150));
        String hint = "← → 翻页  |  ESC 关闭";
        drawCentered(renderer, hint, vpW, panelY + panelHeight - 12);
    }
}
