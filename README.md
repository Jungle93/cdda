# CDDA

一款基于 Java 17 Swing 的轻量级 2D 字符游戏引擎，面向卡牌/策略/生存类游戏开发。

## ✨ 特性

- **回合制时间系统** — 时间仅在玩家行动时流逝，能量制调度支持多实体（NPC/敌人预留）
- **完整日历系统** — 年/月/日/时/分，四季变换，欧洲气候参考
- **三层环境温度** — 月均温 + 日内正弦波动 + 平滑随机漂移
- **人体代谢模拟** — 能量池（卡路里）、体温调节（缓冲机制）、基础代谢 + 环境代偿
- **口渴/水分系统** — 基础流失 + 温度倍率 + 动作倍率，与体温系统联动
- **无限区块地图** — Perlin 噪声地形生成，按需加载/卸载，支持 mod 扩展地形类型
- **区域系统** — 区块逻辑分组，预留大地图渲染接口

## 🏗️ 架构

项目分为**引擎层**和**游戏层**两个独立主包：

```
com.github.game.engine.core    ← 通用引擎（与具体游戏无关）
com.github.game.cdda           ← CDDA 游戏代码
```

### 引擎层 (`engine.core`)

| 模块 | 核心类 | 职责 |
|------|--------|------|
| 主循环 | `GameEngine`, `GamePanel` | 30 FPS Swing Timer, EDT 驱动 |
| 渲染 | `Renderer`, `Graphics2DRenderer` | 渲染抽象接口 + Swing 实现，可替换后端 |
| 屏幕 | `Screen`, `ScreenManager` | 状态模式，支持切换/压栈/弹栈 |
| 场景 | `Scene`, `Viewport` | 场景组合，支持分屏布局 |
| 输入 | `InputManager` | AWT 事件转发，Screen 接收干净坐标 |
| 资源 | `ResourceManager` | 图片加载缓存，支持 classpath/外部文件 |
| 时钟 | `GameClock` | 通用游戏时钟（totalSeconds + 时分秒） |

### 游戏层 (`cdda`)

| 模块 | 核心类 | 职责 |
|------|--------|------|
| 世界 | `GameWorld` | 逻辑层，创建并持有所有子系统 |
| 日历 | `GameCalendar`, `Month`, `Season` | 继承 GameClock，30天/月、12月/年、四季 |
| 回合 | `TurnManager` | 能量制行动调度（speed → 行动耗时） |
| 温度 | `TemperatureManager` | 环境温度三层模型 |
| 代谢 | `MetabolismManager` | 能量池、体温调节、饥饿 |
| 口渴 | `HydrationManager` | 水分收支、温度/动作倍率 |
| 地图 | `TileMap`, `TileType`, `Chunk`, `Region` | 区块地图 + Perlin 噪声地形 |
| 实体 | `Entity`, `Player` | 回合制实体基类 |
| 物品 | `ItemType`, `ItemStack`, `ItemRegistry` | 物品系统，支持 mod 扩展 |
| 屏幕 | `MainScreen`, `GameScene`, `HudScene` | 主屏幕 + 分屏场景 |

### 数据流

```
输入 → GamePanel → InputManager → Screen → GameScene
                                         ↓
                              GameWorld（所有游戏状态）
                                         ↓
                              TurnManager → 推进时间
                              MetabolismManager / HydrationManager → 更新状态
                                         ↓
                              GameScene → Renderer → 屏幕
```

## 🛠️ 技术栈

- **Java 17** — 语言版本
- **Swing** — GUI 框架（纯 CPU 渲染，字符模式）
- **SLF4J + slf4j-simple** — 日志
- **Maven** — 构建工具
- 窗口 600×400，横屏，可调字体大小

## 🚀 构建与运行

```bash
# 编译
mvn compile

# 打包
mvn package

# 运行
java -cp target/cdda-1.0-SNAPSHOT.jar com.github.game.cdda.Game
```

## 🎮 操作

| 按键 | 功能 |
|------|------|
| `W/A/S/D` 或 方向键 | 移动（每次一个瓦片） |
| `5` | 等待一回合 |
| `-` | 持续等待（10 回合） |
| `E` | 进入检查模式（查看相邻瓦片信息） |
| `V` | 切换日志面板扩展/紧凑 |
| `I` | 打开物品栏 |
| `ESC` | 游戏内菜单 |

## 📁 项目结构

```
cdda/
├── CLAUDE.md                    # AI 辅助开发指南
├── pom.xml                      # Maven 配置
├── src/main/java/com/darwin/game/
│   ├── engine/core/             # 通用引擎（13 文件）
│   │   ├── GameEngine.java      # 主循环
│   │   ├── GamePanel.java       # 渲染面板
│   │   ├── Camera.java          # 摄像机
│   │   ├── input/               # 输入管理
│   │   ├── render/              # 渲染抽象
│   │   ├── resource/            # 资源管理
│   │   ├── scene/               # 场景/视口
│   │   ├── screen/              # 屏幕状态机
│   │   └── time/                # 通用时钟
│   └── cdda/                    # 游戏代码（42 文件）
│       ├── Game.java            # 入口
│       ├── GameWorld.java       # 逻辑层
│       ├── GameCalendar.java    # 日历
│       ├── TurnManager.java     # 回合调度
│       ├── TemperatureManager.java  # 温度
│       ├── MetabolismManager.java   # 代谢
│       ├── HydrationManager.java    # 口渴
│       ├── screen/              # UI 屏幕
│       ├── world/               # 地图/区块
│       ├── item/                # 物品系统
│       ├── config/              # 配置
│       └── log/                 # 游戏日志
└── src/main/resources/
    └── simplelogger.properties
```

## 📐 设计理念

- **引擎与游戏分离** — `engine.core` 只包含通用基础设施，可复用于其他项目
- **高内聚低耦合** — 子系统独立，通过 GameWorld 注入，显示层不创建游戏状态
- **可扩展** — TileType 支持 mod 注册，Screen/Scene 支持自由组合，物品系统可扩展
- **回合制优先** — 所有时间推进由玩家行动驱动，为未来 NPC/敌人调度预留接口

## 📄 License

MIT
