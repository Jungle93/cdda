# 贴图包（Sprite Pack）规范

## 概述

游戏使用分层贴图系统，地面（Ground）和植被/装饰（Overlay）是两个独立的图层。贴图作者在制作素材时**不需要考虑底层地形**——只需确保 overlay 类素材使用透明背景即可。

## 渲染层级（从底到顶）

```
┌─────────────────────┐
│  第4层: UI / HUD     │  ← 状态面板、日志、菜单等
│  第3层: Creature     │  ← 玩家、动物
│  第2层: Overlay      │  ← 树、灌木、花（透明底 PNG）
│  第1层: Ground       │  ← 草地、泥土、水、石头（不透明）
└─────────────────────┘
```

渲染由 `TileMap.render()` 的两遍绘制实现：
- **Pass 1**：绘制地面层（对于 overlay 瓦片，先画它下方的 `groundTile`）
- **Pass 2**：绘制 overlay 层（植被叠于地面之上）

## 设计原则

### 植被不算地形

这是业界标准做法，被 Dwarf Fortress、RimWorld、CDDA 原版、Factorio 等游戏广泛采用：

- **地形（Terrain/Ground）**：草地、泥土、沙地、水面、石头等——构成世界的"地板"
- **植被（Vegetation/Overlay）**：树、灌木、花、高草等——放置在地形之上的"装饰物"

理由：

1. **避免组合爆炸** — 如果树要考虑地形，20 种地形 × 10 种树 = 200 张贴图。分层只需 20 + 10 = 30 张
2. **独立替换** — Mod 作者可以只换地形包或只换植被包
3. **逻辑独立** — 砍树后地面不变，着火只烧植被不烧地形

### 地面与植被的贴图要求

| 属性 | 地面层（Ground） | 植被层（Overlay） |
|------|-----------------|------------------|
| 背景 | **不透明**（RGB，无 alpha） | **透明**（RGBA，alpha 通道） |
| 尺寸 | 32×32 像素 | 32×32 像素 |
| 内容 | 铺满整个瓦片 | 仅绘制物体本身，周围留透明 |
| 缩放 | 最近邻插值（nearest-neighbor） | 最近邻插值（nearest-neighbor） |

## 资源目录结构

### Classpath 路径（打包进 JAR）

```
src/main/resources/gfx/sprites/
├── tile/                    ← 地形贴图（含地面和植被）
│   ├── tile_grass.png       ← 地面层，不透明
│   ├── tile_dirt.png        ← 地面层，不透明
│   ├── tile_sand.png        ← 地面层，不透明
│   ├── tile_water.png       ← 地面层，不透明
│   ├── tile_stone.png       ← 地面层，不透明
│   ├── tile_mud.png         ← 地面层，不透明
│   ├── tile_floor.png       ← 地面层，不透明
│   ├── tile_wall.png        ← 地面层，不透明
│   ├── tile_fence.png       ← 地面层，不透明
│   ├── tile_door.png        ← 地面层，不透明
│   ├── tile_tree.png        ← 植被层，透明底
│   ├── tile_bush.png        ← 植被层，透明底
│   ├── tile_flower.png      ← 植被层，透明底
│   ├── tile_tall_grass.png  ← 植被层，透明底
│   ├── tile_reeds.png       ← 植被层，透明底
│   ├── tile_withered_tree.png   ← 植被层，透明底
│   ├── tile_withered_bush.png   ← 植被层，透明底
│   └── tile_dead_grass.png      ← 植被层，透明底
├── creature/                ← 生物贴图
│   ├── creature_wolf.png
│   ├── creature_fox.png
│   ├── creature_player.png
│   └── ...
└── items/                   ← 物品贴图（尚未完全实现）
    └── item_axe.png
```

### 文件系统回退路径（开发模式）

```
sprites/                     ← 项目根目录下的开发资源
├── tile/
│   ├── tile_grass.png
│   ├── tile_tree.png
│   └── ...
├── creature/
│   ├── creature_wolf.png
│   └── ...
└── items/
    └── ...
```

### 加载优先级

1. **classpath**：`/gfx/sprites/<category>/<prefix>_<name>.png` — 打包在 JAR 内
2. **filesystem**：`sprites/<category>/<prefix>_<name>.png` — 项目根目录（开发用）
3. **程序化回退**：`PixelArt` 生成简易像素图（最终兜底）

## Sprite ID 命名规则

| 类别 | ID 格式 | 示例 |
|------|---------|------|
| 地形（含地面和植被） | `tile.<name>` | `tile.grass`, `tile.tree`, `tile.withered_bush` |
| 生物 | `creature.<id>` | `creature.wolf`, `creature.fox` |
| 玩家 | `player` | `player`（特殊，无前缀） |
| 物品 | `item.<id>` | `item.axe`（规划中） |

## 文件命名规则

| 类别 | 文件名格式 | 前缀 |
|------|-----------|------|
| 地形 | `tile_<name>.png` | `tile_` |
| 生物 | `creature_<name>.png` | `creature_` |
| 物品 | `item_<id>.png` | `item_` |

## 分类对照表

### 地面层瓦片（`overlay = false`，不透明贴图）

| TileType | 名称 | Sprite ID | 说明 |
|----------|------|-----------|------|
| `GRASS` | 草地 | `tile.grass` | 默认地面 |
| `DIRT` | 泥地 | `tile.dirt` | 裸露土地 |
| `SAND` | 沙地 | `tile.sand` | 水边/沙漠 |
| `WATER` | 水面 | `tile.water` | 河流/湖泊 |
| `STONE` | 石地 | `tile.stone` | 岩石地面 |
| `MUD` | 泥沼 | `tile.mud` | 湿滑地面 |
| `FLOOR` | 地板 | `tile.floor` | 建筑内部 |
| `WALL` | 墙壁 | `tile.wall` | 建筑围墙 |
| `FENCE` | 栅栏 | `tile.fence` | 围栏 |
| `DOOR` | 门 | `tile.door` | 出入口 |

### 植被层瓦片（`overlay = true`，透明底贴图）

| TileType | 名称 | Sprite ID | 说明 |
|----------|------|-----------|------|
| `TREE` | 树木 | `tile.tree` | 阔叶树 |
| `BUSH` | 灌木 | `tile.bush` | 低矮灌木 |
| `FLOWER` | 花 | `tile.flower` | 野花 |
| `TALL_GRASS` | 高草 | `tile.tall_grass` | 高草丛 |
| `REEDS` | 芦苇 | `tile.reeds` | 水边芦苇 |
| `WITHERED_TREE` | 枯树 | `tile.withered_tree` | 冬季/枯萎 |
| `WITHERED_BUSH` | 枯灌木 | `tile.withered_bush` | 冬季/枯萎 |
| `DEAD_GRASS` | 枯草 | `tile.dead_grass` | 冬季/枯萎 |
| `ROCK` | 岩石 | `tile.rock` | 地面石块 |

## 制作贴图注意事项

1. **地面贴图**：画满 32×32，不需要透明背景，可以无缝拼接
2. **植被贴图**：只画物体本身（树冠、灌木丛等），四周留透明。会被画在任何地面之上
3. **像素风格**：所有贴图使用最近邻插值缩放，保持像素画风格。避免抗锯齿和模糊
4. **季节变体**：当前 `WITHERED_*` 系列是冬季/枯萎状态的独立瓦片类型，不是动态切换。如果将来需要动态季节切换，可能需要扩展机制
5. **不需要考虑底层地形**：同一棵树的贴图放在草地、泥地、沙地上效果相同（因为地面会先画好）

## 代码入口

| 类 | 职责 |
|----|------|
| `SpriteManager` | 全局单例，管理当前激活的贴图包 |
| `SpritePack` | 贴图包接口 |
| `BuiltinSpritePack` | 内置贴图包实现，聚合三类 SpriteData |
| `TileSpriteData` | 地形贴图加载与程序化生成 |
| `CreatureSpriteData` | 生物贴图加载与程序化生成 |
| `ItemSpriteData` | 物品贴图（规划中） |
| `Sprite` | 贴图数据载体（id + BufferedImage + 尺寸） |
