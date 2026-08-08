package com.github.game.engine.core.render;

import java.awt.*;

/**
 * 渲染相关常量：默认字体、颜色等。
 */
public final class RenderContext {

    private RenderContext() {}

    public static final Font DEFAULT_FONT = new Font("Monospaced", Font.PLAIN, 14);
    public static final Color DEFAULT_BACKGROUND = Color.BLACK;
    public static final Color DEFAULT_TEXT_COLOR = Color.WHITE;
}
