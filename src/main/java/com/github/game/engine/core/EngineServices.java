package com.github.game.engine.core;

import com.github.game.engine.core.audio.AudioEngine;
import com.github.game.engine.core.i18n.I18nManager;
import com.github.game.engine.core.input.InputManager;
import com.github.game.engine.core.resource.ResourceManager;
import com.github.game.engine.core.screen.ScreenManager;

/**
 * 引擎服务静态门面。
 * <p>
 * 单实例游戏中统一访问所有引擎子系统的静态入口，
 * 参考 LibGDX {@code Gdx} 的设计：公开静态字段，一次初始化，随处访问。
 * <p>
 * 任何游戏层代码（Screen、Scene、World、HUD…）都可以直接访问这些静态字段，
 * 无需持有 Engine/World 的引用穿透传递。
 * <p>
 * 使用示例：
 * <pre>
 * // 音频
 * EngineServices.audio.playBGM("audio/music/bg.mp3");
 * EngineServices.audio.playSFX("audio/sfx/walk.wav");
 *
 * // 国际化
 * String name = EngineServices.i18n.t("item.bread.name");
 * EngineServices.i18n.setLocale("zh");
 *
 * // 资源
 * BufferedImage img = EngineServices.resources.loadImage("sprites/tree.png");
 *
 * // 屏幕
 * EngineServices.screens.pushScreen(new GameSetupScreen(engine));
 *
 * // 引擎本身（需要未封装 API 时）
 * JFrame frame = EngineServices.engine.getFrame();
 * </pre>
 *
 * @see #init(GameEngine)
 */
public final class EngineServices {

    /** 游戏引擎本身 */
    public static GameEngine engine;

    /** 音频引擎 */
    public static AudioEngine audio;

    /** 国际化管理器 */
    public static I18nManager i18n;

    /** 资源管理器（图片加载与缓存） */
    public static ResourceManager resources;

    /** 屏幕管理器 */
    public static ScreenManager screens;

    /** 输入管理器 */
    public static InputManager input;

    private EngineServices() {}

    /**
     * 初始化所有服务。
     * 从 GameEngine 提取各子系统，赋值到静态字段。
     * 必须在游戏启动早期调用（由游戏入口的 init() 负责）。
     *
     * @param engine 游戏引擎实例
     */
    public static void init(GameEngine engine) {
        EngineServices.engine = engine;
        EngineServices.audio = engine.getAudioEngine();
        EngineServices.i18n = engine.getI18nManager();
        EngineServices.resources = engine.getResourceManager();
        EngineServices.screens = engine.getScreenManager();
        EngineServices.input = engine.getInputManager();
    }
}
