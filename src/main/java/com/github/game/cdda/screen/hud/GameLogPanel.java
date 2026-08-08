package com.github.game.cdda.screen.hud;

import com.github.game.engine.core.render.Renderer;
import com.github.game.cdda.log.GameLog;

import java.awt.*;
import java.util.List;

/**
 * 游戏日志面板（HUD 组件）。
 * 记录游戏内交互信息：检查结果、拾取/丢弃物品、事件通知等。
 * <p>
 * 两种显示模式：
 * <ul>
 *   <li>紧凑模式：固定显示最新 {@value #COMPACT_LINES} 条，高度小</li>
 *   <li>扩展模式：显示 {@value #EXPANDED_LINES} 条，支持 UP/DOWN 滚动浏览全部历史</li>
 * </ul>
 * 通过 {@link #toggleExpanded()} 切换模式，V 键触发。
 */
public class GameLogPanel implements StatusPanel {

    /** 紧凑模式显示行数 */
    private static final int COMPACT_LINES = 3;
    /** 扩展模式显示行数 */
    private static final int EXPANDED_LINES = 12;
    /** 每行高度 */
    private static final int LINE_HEIGHT = 14;
    /** 内边距 */
    private static final int PADDING = 4;

    /** 面板背景色（与 HudScene 一致） */
    private static final Color BG_COLOR = new Color(20, 20, 30);
    /** 标题颜色 */
    private static final Color TITLE_COLOR = new Color(160, 160, 180);
    /** 日志文本颜色 */
    private static final Color TEXT_COLOR = new Color(200, 200, 200);
    /** 提示文本颜色 */
    private static final Color HINT_COLOR = new Color(100, 100, 120);

    private final GameLog gameLog;

    /** 是否处于扩展模式 */
    private boolean expanded = false;
    /** 滚动偏移量（0 = 最新内容） */
    private int scrollOffset = 0;

    public GameLogPanel() {
        this.gameLog = GameLog.getInstance();
    }

    /**
     * 切换紧凑/扩展模式。
     * 进入扩展模式时重置滚动到最新。
     */
    public void toggleExpanded() {
        expanded = !expanded;
        if (expanded) {
            scrollOffset = 0;
        }
    }

    /**
     * 向上滚动（查看更旧的日志）。
     * 仅在扩展模式下有效。
     */
    public void scrollUp() {
        if (!expanded) return;
        int total = gameLog.size();
        int maxOffset = Math.max(0, total - EXPANDED_LINES);
        scrollOffset = Math.min(scrollOffset + 1, maxOffset);
    }

    /**
     * 向下滚动（查看更新的日志）。
     * 仅在扩展模式下有效。
     */
    public void scrollDown() {
        if (!expanded) return;
        scrollOffset = Math.max(scrollOffset - 1, 0);
    }

    /** 是否处于扩展模式 */
    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public int getHeight() {
        if (expanded) {
            // 标题 + 日志行 + 提示 + 上下内边距
            return (1 + EXPANDED_LINES + 1) * LINE_HEIGHT + PADDING * 2;
        } else {
            return COMPACT_LINES * LINE_HEIGHT + PADDING * 2;
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // 日志面板始终启用，忽略此设置
    }

    @Override
    public void render(Renderer renderer, int x, int y, int width, int height) {
        // 背景
        renderer.setColor(BG_COLOR);
        renderer.fillRect(x, y, width, height);

        int fontSize = LINE_HEIGHT - 2;
        int textX = x + PADDING;

        if (expanded) {
            renderExpanded(renderer, x, y, width, textX, fontSize);
        } else {
            renderCompact(renderer, textX, y, fontSize);
        }
    }

    /**
     * 紧凑模式：显示最新 {@value #COMPACT_LINES} 条日志。
     */
    private void renderCompact(Renderer renderer, int textX, int y, int fontSize) {
        List<String> entries = gameLog.getRecentEntries(COMPACT_LINES);

        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));
        renderer.setColor(TEXT_COLOR);

        for (int i = 0; i < entries.size(); i++) {
            int textY = y + PADDING + i * LINE_HEIGHT + LINE_HEIGHT - 2;
            renderer.drawText(entries.get(i), textX, textY);
        }
    }

    /**
     * 扩展模式：标题 + 可滚动日志 + 底部提示。
     */
    private void renderExpanded(Renderer renderer, int x, int y, int width,
                                 int textX, int fontSize) {
        int currentY = y + PADDING;

        // 标题
        renderer.setFont(new Font("Monospaced", Font.BOLD, fontSize));
        renderer.setColor(TITLE_COLOR);
        renderer.drawText("── 日志 ──", textX, currentY + LINE_HEIGHT - 2);
        currentY += LINE_HEIGHT;

        // 计算显示范围：从末尾往前取 EXPANDED_LINES + scrollOffset 条
        int total = gameLog.size();
        int takeCount = Math.min(total, EXPANDED_LINES + scrollOffset);
        List<String> entries = gameLog.getRecentEntries(takeCount);

        // 去掉 scrollOffset 条最新的（它们被滚出了视口上方）
        int visibleCount = Math.max(0, entries.size() - scrollOffset);

        renderer.setFont(new Font("Monospaced", Font.PLAIN, fontSize));

        for (int i = 0; i < visibleCount && i < EXPANDED_LINES; i++) {
            // 最旧的条目透明度最低，最新的完全可见
            int age = visibleCount - 1 - i;
            int alpha = Math.max(80, 255 - age * 15);
            renderer.setColor(new Color(200, 200, 200, alpha));

            int textY = currentY + i * LINE_HEIGHT + LINE_HEIGHT - 2;
            String text = entries.get(i);

            // 截断过长文本以适应面板宽度
            int maxWidth = width - PADDING * 2;
            while (renderer.getTextWidth(text) > maxWidth && text.length() > 1) {
                text = text.substring(0, text.length() - 2) + "…";
            }
            renderer.drawText(text, textX, textY);
        }

        // 底部提示
        int hintY = currentY + EXPANDED_LINES * LINE_HEIGHT + LINE_HEIGHT - 2;
        renderer.setColor(HINT_COLOR);
        renderer.drawText("↑↓ 滚动  V 收起", textX, hintY);
    }
}
