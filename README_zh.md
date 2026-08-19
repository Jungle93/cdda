# CDDA - 像素生存游戏

[English](README.md)

一款基于 Java 17 Swing 的 2D 像素生存游戏，采用回合制玩法。游戏具备完整的生态系统、代谢模拟、无限地图生成和 MOD 支持。

## ✨ 特性

- **回合制时间系统** — 时间仅在玩家行动时流逝，能量制调度支持多实体
- **完整日历系统** — 年/月/日/时/分，四季变换（30 天/月、12 月/年）
- **三层环境温度** — 月均温 + 日内正弦波动 + 平滑随机漂移
- **人体代谢模拟** — 能量池（卡路里）、体温调节（缓冲机制）、基础代谢 + 环境代偿
- **口渴/水分系统** — 基础流失 + 温度倍率 + 动作倍率，与体温系统联动
- **无限区块地图** — Perlin 噪声地形生成，按需加载/卸载
- **生态系统** — 10+ 动物物种，AI 状态机，繁殖系统
- **物品系统** — 50+ 物品，合成系统，装备系统
- **NPC 系统** — 社交 AI，交易系统
- **MOD 支持** — JSON 数据驱动，热加载 MOD
- **存档系统** — JSON 格式，多槽位保存
- **昼夜系统** — 视觉色调随时间变化

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
| 渲染 | `Renderer`, `Graphics2DRenderer` | 渲染抽象接口 + Swing 实现 |
| 屏幕 | `Screen`, `ScreenManager` | 状态模式，支持切换/压栈/弹栈 |
| 场景 | `Scene`, `Viewport` | 场景组合，支持分屏布局 |
| 输入 | `InputManager` | AWT 事件转发 |
| 资源 | `ResourceManager` | 图片加载缓存 |
| 音频 | `AudioEngine` | 多通道音频（BGM/SFX/AMBIENT） |
| 时钟 | `GameClock` | 通用游戏时钟 |
| 数据 | `DataScanner` | JSON 文件扫描器 |

### 游戏层 (`cdda`)

| 模块 | 核心类 | 职责 |
|------|--------|------|
| 世界 | `GameWorld` | 逻辑层，创建并持有所有子系统 |
| 日历 | `GameCalendar`, `Month`, `Season` | 30 天/月、12 月/年、四季 |
| 回合 | `TurnManager` | 能量制行动调度 |
| 温度 | `TemperatureManager` | 环境温度三层模型 |
| 代谢 | `MetabolismManager` | 能量池、体温调节、饥饿 |
| 口渴 | `HydrationManager` | 水分收支 |
| 地图 | `TileMap`, `TileType`, `Chunk` | 区块地图 + Perlin 噪声 |
| 实体 | `Entity`, `Player`, `Animal` | 回合制实体 |
| 物品 | `ItemType`, `ItemStack`, `ItemRegistry` | 物品系统 |
| 生物 | `CreatureRegistry`, `AnimalAI` | 10+ 动物 AI |
| NPC | `Npc`, `NpcManager` | NPC 系统 |
| 屏幕 | `MainScreen`, `GameScene`, `HudScene` | UI 屏幕 |
| MOD | `ModLoader`, `ModManifest` | MOD 加载框架 |
| 存档 | `SaveManager` | 存档系统 |
| 合成 | `RecipeRegistry` | 合成系统 |
| 日志 | `GameLog` | 游戏日志 |

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
- **Swing** — GUI 框架（纯 CPU 渲染）
- **SLF4J + slf4j-simple** — 日志
- **Gson** — JSON 解析
- **MP3SPI + JLayer** — MP3 音频解码
- **Maven** — 构建工具

窗口 600×400，横屏，可调字体大小

## 🚀 构建与运行

```bash
# 编译
mvn compile

# 打包
mvn package

# 运行
java -cp target/cdda-1.0-SNAPSHOT.jar com.github.game.cdda.CddaGame

# 运行测试
mvn test
```

## 🎮 操作

| 按键 | 功能 |
|------|------|
| `W/A/S/D` 或 方向键 | 移动（每次一个瓦片） |
| `5` | 等待一回合 |
| `-` | 持续等待（10 回合） |
| `G` | 拾取物品 |
| `D` | 丢弃物品 |
| `E` | 进食 |
| `A` | 使用物品 |
| `I` | 打开物品栏 |
| `F2` | 合成 |
| `F1` | 帮助 |
| `L` | 观察模式 |
| `C` | 对话/NPC 交互 |
| `M` | 大地图 |
| `V` | 切换日志面板扩展/紧凑 |
| `↑↓` | 日志滚动 |
| `]` | 放大视角 |
| `[` | 缩小视角 |
| 鼠标滚轮 | 缩放 |
| `ESC` | 游戏内菜单 |
| `` ` `` | 调试菜单 |

##  项目结构

```
cdda/
├── README.md                        # 英文文档
── README_zh.md                     # 本文件（中文）
├── CLAUDE.md                        # AI 辅助开发指南
├── pom.xml                          # Maven 配置
├── src/main/java/com/github/game/
│   ├── engine/core/                 # 通用引擎
│   │   ├── GameEngine.java          # 主循环
│   │   ├── GamePanel.java           # 渲染面板
│   │   ├── Camera.java              # 摄像机
│   │   ├── input/                   # 输入管理
│   │   ├── render/                  # 渲染抽象
│   │   ├── resource/                # 资源管理
│   │   ├── scene/                   # 场景/视口
│   │   ├── screen/                  # 屏幕状态机
│   │   ├── time/                    # 通用时钟
│   │   ├── audio/                   # 音频系统
│   │   └── data/                    # 数据扫描器
│   └── cdda/                        # 游戏代码
│       ├── CddaGame.java            # 入口
│       ├── GameWorld.java           # 逻辑层
│       ├── creature/                # 生物系统
│       ├── item/                    # 物品系统
│       ├── world/                   # 地图/区块
│       ├── screen/                  # UI 屏幕
│       ├── mod/                     # MOD 加载器
│       ├── save/                    # 存档系统
│       ├── npc/                     # NPC 系统
│       ├── crafting/                # 合成系统
│       ├── game/                    # 游戏管理器
│       ├── config/                  # 配置
│       └── log/                     # 游戏日志
└── src/main/resources/
    ├── data/core/                   # 基础游戏数据
    │   ├── creatures/animal/        # 10+ 动物定义
    │   ├── items/                   # 50+ 物品定义
    │   ├── recipes/                 # 24+ 合成配方
    │   └── vegetation/              # 16+ 植被定义
    ├── audio/                       # 音效
    └── music/                       # 背景音乐
```

##  MOD 开发

### MOD 目录结构

```
mods/
  my_mod/
    mod.json              ← Mod 清单（必需）
    data/
      creatures/           ← 生物定义（可选）
        animal/
          my_creature.json
      items/               ← 物品定义（可选）
        food/
          my_food.json
      recipes/             ← 合成配方（可选）
        my_recipe.json
      vegetation/          ← 植被定义（可选）
        my_plant.json
```

### mod.json 格式

```json
{
  "id": "my_mod",
  "name": "我的 Mod",
  "version": "1.0.0",
  "description": "Mod 描述",
  "author": "作者名",
  "dependencies": ["other_mod_id"],
  "loadOrder": 100
}
```

### 生物定义示例

```json
{
  "id": "my_dragon",
  "name": "龙",
  "hp": 500,
  "speed": 150,
  "trophicLevel": "APEX_PREDATOR",
  "stats": { "strength": 50, "agility": 30, "endurance": 40 },
  "perception": { "vision": 30, "hearing": 20 },
  "displayChar": "D",
  "displayColor": [255, 50, 50]
}
```

### 物品定义示例

```json
{
  "id": 1001,
  "name": "my_mod:magic_sword",
  "displayName": "魔法剑",
  "description": "一把附魔的剑",
  "weightGrams": 1500,
  "volumeMl": 800,
  "maxStackSize": 1
}
```

### 配方定义示例

```json
{
  "id": "my_mod:make_magic_sword",
  "name": "制作魔法剑",
  "category": "weapon",
  "inputItemId": "iron_ingot",
  "inputCount": 5,
  "toolRequired": "forging",
  "processingTime": 200,
  "outputs": [{ "itemId": "my_mod:magic_sword", "count": 1 }]
}
```

##  设计理念

- **引擎与游戏分离** — `engine.core` 只包含通用基础设施，可复用于其他项目
- **高内聚低耦合** — 子系统独立，通过 GameWorld 注入，显示层不创建游戏状态
- **可扩展** — MOD 支持，JSON 数据驱动
- **回合制优先** — 所有时间推进由玩家行动驱动

## 📄 License

MIT
