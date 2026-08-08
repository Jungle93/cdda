package com.github.game.engine.core.screen;

import com.github.game.engine.core.GameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Screen 切换管理器，支持直接切换和栈式 push/pop。
 */
public class ScreenManager {

    private static final Logger logger = LoggerFactory.getLogger(ScreenManager.class);

    private final GameEngine engine;
    private Screen currentScreen;
    private final Deque<Screen> screenStack = new ArrayDeque<>();

    public ScreenManager(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * 直接切换到新 Screen。旧 Screen 被 dispose，新 Screen 被 init。
     */
    public void switchScreen(Screen newScreen) {
        String oldName = currentScreen != null ? currentScreen.getClass().getSimpleName() : "null";
        if (currentScreen != null) {
            currentScreen.dispose();
        }
        currentScreen = newScreen;
        currentScreen.init();
        logger.info("切换屏幕: {} -> {}", oldName, newScreen.getClass().getSimpleName());
    }

    /**
     * 将当前 Screen 压入栈中，切换到新 Screen。
     * 适用于暂停菜单、确认对话框等叠加场景。
     */
    public void pushScreen(Screen newScreen) {
        String oldName = currentScreen != null ? currentScreen.getClass().getSimpleName() : "null";
        if (currentScreen != null) {
            screenStack.push(currentScreen);
        }
        currentScreen = newScreen;
        currentScreen.init();
        logger.info("压入屏幕: {} (栈深度: {})", newScreen.getClass().getSimpleName(), screenStack.size());
    }

    /**
     * 弹出栈顶 Screen 并恢复。当前 Screen 被 dispose。
     */
    public void popScreen() {
        String poppedName = currentScreen != null ? currentScreen.getClass().getSimpleName() : "null";
        if (currentScreen != null) {
            currentScreen.dispose();
        }
        if (!screenStack.isEmpty()) {
            currentScreen = screenStack.pop();
            currentScreen.init();
            logger.info("弹出屏幕: {} -> {} (栈深度: {})", poppedName, currentScreen.getClass().getSimpleName(), screenStack.size());
        } else {
            currentScreen = null;
            logger.info("弹出屏幕: {} -> null (栈已空)", poppedName);
        }
    }

    public Screen getCurrentScreen() {
        return currentScreen;
    }
}
