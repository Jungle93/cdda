# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CDDA is a lightweight Java 17 Swing-based 2D game engine for card/strategy games. Uses SLF4J for logging (with slf4j-simple binding). Window is 600×400 fixed-size, landscape orientation.

## Build & Run

```bash
mvn compile                    # Compile
mvn package                    # Package JAR
java -cp target/cdda-1.0-SNAPSHOT.jar com.github.game.cdda.CddaGame   # Run
```

No exec plugin or test framework is configured yet.

## Architecture

### Game Loop (GameEngine)
- `javax.swing.Timer` at ~30 FPS (~33ms), all logic on Swing EDT
- Each tick: `deltaTime` → `screen.update(deltaTime)` → `gamePanel.repaint()`
- `EngineConfig` 封装引擎配置（帧率、窗口尺寸、字体、资源路径），由游戏层注入
- 引擎层不依赖 JFrame 或游戏配置类，通过 `EngineConfig.OnChangeListener` 回调通知游戏层

### Screen System (state pattern)
- **Screen** — abstract base with lifecycle: `init()` → `update(dt)` → `render(renderer)` → `dispose()`
- **ScreenManager** — `switchScreen()` (replace), `pushScreen()`/`popScreen()` (stack for overlays)
- Input hooks: `onMousePressed/Clicked/Moved(x,y)`, `onKeyPressed/Released(keyCode)`, `onKeyTyped(charCode)`
- New game states = new Screen subclass in `com.github.game.cdda.screen`

### Rendering Pipeline
- `GamePanel.paintComponent()` → wraps `Graphics2D` in `Renderer` interface → delegates to `screen.render(renderer)`
- **Renderer** interface abstracts all drawing — currently implemented by `Graphics2DRenderer`, designed to be swappable for sprite/image backends
- **RenderContext** holds defaults: black bg, white text, monospaced 14pt font

### Input Flow
`AWT events` → `GamePanel` (listener) → `InputManager` (extracts x,y/keyCode) → `Screen` hook methods

### Resource Management
- **ResourceManager** — 图片加载与缓存，支持两种来源：
  - `classpath:path` — 程序内资源（JAR 中 `src/main/resources/` 下的文件）
  - `file:path` — 程序外资源（外部目录，支持绝对/相对路径）
  - 无前缀时自动检测（优先 classpath，回退到外部文件）
- 外部文件基准目录由系统配置 `resource.base` 决定
- 通过 `engine.getResourceManager()` 访问

### Audio System
- **AudioEngine** — 音频系统对外入口，由 `GameEngine` 创建，通过 `engine.getAudioEngine()` 访问
- **核心概念**：
  - **通道（Channel）** — 独立音量控制的混音通道：`BGM`（背景音乐）、`SFX`（音效）、`AMBIENT`（环境音）
  - **音源（Source）** — `ClipSource`（短音效，整段加载）和 `StreamSource`（长音乐，流式解码）
  - **缓存（Cache）** — 自动缓存已解码的 PCM 数据，避免重复解码
- **常用 API**：
  ```java
  AudioEngine audio = engine.getAudioEngine();
  audio.playBGM("music/background.mp3");              // 播放 BGM（默认：循环，音量 0.7，淡入 2s）
  audio.playBGM("music/boss.mp3", true, 0.8f, 3000);  // 自定义：循环，音量 0.8，淡入 3 秒
  audio.playSFX("audio/click.wav");                   // 播放一次性音效
  audio.playSFX("audio/hit.wav", false, 0.6f);        // 自定义音量
  audio.stopBGM(1500);                                // BGM 淡出 1.5 秒
  audio.crossFade("music/night.mp3", 3000, 0.7f);     // BGM 交叉淡入切换
  audio.setMasterVolume(0.5f);                        // 全局主音量
  audio.toggleMute();                                 // 静音切换
  audio.setChannelVolume("SFX", 0.8f);                // 单独调通道音量
  ```
- **资源路径**：使用相对路径（相对 `src/main/resources/`），如 `"music/background.mp3"`
- **支持格式**：MP3（JLayer 解码）、WAV（javax.sound 原生支持）
- **注意事项**：
  - BGM 使用流式解码，适合长音乐文件；SFX 一次性加载到内存，适合短音效
  - 同一时刻只能有一首 BGM；切换时旧 BGM 自动停止
  - `AudioEngine.update(deltaTime)` 由 GameEngine 自动调用，处理淡入淡出和音源更新

## Package Layout

项目分为两大主包：**引擎层** (`engine`) 和 **游戏层** (`cdda`)。

### 引擎层 — `com.github.game.engine.core`（通用，与具体游戏无关）

```
com.github.game.engine.core         — GameEngine, GamePanel, EngineConfig, GameApplication, Camera (loop, render, input, viewport)
com.github.game.engine.core.screen  — Screen (abstract, 场景容器), ScreenManager
com.github.game.engine.core.scene   — Scene (抽象基类), Viewport (屏幕区域)
com.github.game.engine.core.render  — Renderer (interface, pushClip/popClip), Graphics2DRenderer, RenderContext
com.github.game.engine.core.input   — InputManager (event forwarding)
com.github.game.engine.core.resource — ResourceManager (image loading + caching)
com.github.game.engine.core.audio    — AudioEngine (对外入口), AudioManager (通道/缓存/淡入淡出), AudioSource/ClipSource/StreamSource (音源), AudioChannel (混音通道), AudioCache (缓存), FadeManager, AudioScheduler
com.github.game.engine.core.time    — GameClock (通用游戏时钟，只有 totalSeconds + 时分秒)
```

### 游戏层 — `com.github.game.cdda`（CDDA 游戏特定代码）

```
com.github.game.cdda                — CddaGame (extends GameApplication, 入口), Constants, Player (extends Creature), Entity, GameWorld, GameCalendar
com.github.game.cdda                — Month, Season (日历枚举)
com.github.game.cdda                — TurnManager (回合调度), TemperatureManager (环境温度)
com.github.game.cdda                — MetabolismManager (能量/体温), HydrationManager (口渴/水分)
com.github.game.cdda.screen         — MainScreen (主游戏屏幕，Scene 系统 + 全局按键路由)
com.github.game.cdda.screen.menu    — MenuScreen (抽象基类), MainMenuScreen, SettingsScreen, GameSetupScreen
com.github.game.cdda.screen.scene   — GameScene (游戏世界场景，含检查模式), HudScene (HUD 面板场景)
com.github.game.cdda.screen.hud     — StatusPanel (接口), CharacterInfoPanel, GameLogPanel, TimePanel
com.github.game.cdda.screen.overlay — InGameMenuScreen (游戏内菜单), InventoryScreen (物品栏占位)
com.github.game.cdda.log            — GameLog (游戏日志单例)
com.github.game.cdda.item           — ItemType, ItemStack, ItemRegistry（物品系统，支持 mod 扩展）
com.github.game.cdda.game           — WorldSettings, CharacterSettings (游戏数据模型)
com.github.game.cdda.config         — ConfigManager, GameConfig (配置管理)
com.github.game.cdda.creature       — Creature (抽象基类), Animal, CreatureManager, CreatureActionContext
com.github.game.cdda.creature.ai    — AIState (状态枚举), AnimalAI (动物 AI 状态机)
com.github.game.cdda.creature.config — CreatureDefinition (JSON 数据结构), CreatureRegistry (注册表)
com.github.game.cdda.world          — TileMap, TileType（地图渲染层 + 地形类型注册表）
com.github.game.cdda.world.noise    — PerlinNoise (Perlin 噪声生成器)
com.github.game.cdda.world.chunk    — Chunk, ChunkManager (区块管理)
com.github.game.cdda.world.region   — Region, RegionManager (区域管理)
```

### GameClock vs GameCalendar

- **GameClock**（引擎层）— 纯时钟，跟踪 totalSeconds，提供 getHour/Minute/Second。任何游戏通用。
- **GameCalendar**（游戏层）— 继承 GameClock，添加 CDDA 日历规则（30天/月、12月/年、季节）。

## Conventions

- All Javadoc and code comments are in Chinese (Mandarin)
- Screens receive clean coordinates/key codes, never raw AWT event objects
- No threading beyond Swing EDT — avoid introducing background threads for game logic
- Demo/concrete screens go in `com.github.game.cdda.screen`, engine internals in `com.github.game.engine.core`

## 设计要求
- 代码要考虑可扩展性
- 游戏引擎那块代码尽可能参考优秀的游戏引擎设计理念
- 通用游戏引擎代码、游戏部分代码要区分
- 代码要尽量高内聚、低耦合
- 算法类的代码关键位置要注释
- 如果计算量大尽量考虑并行计算