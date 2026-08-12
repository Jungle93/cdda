# NPC 系统设计

> 本文档定义 NPC（非玩家角色）系统的架构、属性和行为规则。
> 所有 NPC 都是**人类**，差异来源于地域背景（生长环境），而非种族天赋。

---

## 一、架构设计

```
Entity (base game object)
  |
Creature (abstract: HP, attributes, perception, position, rendering)
  |
  +-- Player (input-driven, grid movement, combat, inventory)
  |
  +-- Animal (AI-driven, metabolism, reproduction, life stages, death causes)
  |
  +-- Npc (AI-driven, social, trade, combat, inventory)
       |
       +-- NpcAI (state machine: IDLE/WALK/TALK/TRADE/PATROL/FLEE/ATTACK/HUNT_PREY/SLEEP)
       +-- NpcDefinition (JSON-loaded template data)
       +-- NpcManager (lifecycle, async turns, spawning, interaction)
       +-- NpcInventory (NPC 背包，支持交易和掉落)
       +-- NpcRegistry (static registry of all NPC templates)
       +-- NpcNameGenerator (随机名字生成)
       +-- NpcSocial (关系/态度框架)
```

**设计原则**：
- Npc 与 Animal 平级，都继承 Creature
- NpcAI 独立于 AnimalAI，但共享 CreatureActionContext 基础设施
- 地域背景复用 human_kind.md 的定义
- NPC 暂时不与动物交互（不狩猎、不被动物攻击）

---

## 二、NPC 类型

通过 `NpcType` 枚举区分，影响 AI 行为初始倾向：

| 类型 | 行为倾向 | 示例 |
|------|---------|------|
| `FRIENDLY` | 友好，可对话/交易 | 商人、村民 |
| `NEUTRAL` | 中立，观察后决定态度 | 旅行者 |
| `HOSTILE` | 敌对，主动攻击玩家 | 土匪、敌对猎手 |
| `FUNCTIONAL` | 功能型，提供任务/信息 | 任务发布者、向导 |

---

## 三、地域背景

复用 `human_kind.md` 定义的地域背景，NPC 随机分配：

| 地域背景 | STR | AGI | CON | INT | PER | WIL | 显示字符 | 颜色 |
|---------|-----|-----|-----|-----|-----|-----|---------|------|
| 普通人 | 10 | 10 | 10 | 10 | 10 | 10 | `n` | 灰色(180,180,180) |
| 北方高地人 | 12 | 8 | 14 | 10 | 8 | 12 | `H` | 深蓝(100,120,180) |
| 南方河谷人 | 10 | 12 | 8 | 12 | 10 | 8 | `S` | 浅绿(120,180,120) |
| 东部林居人 | 8 | 14 | 8 | 8 | 14 | 10 | `E` | 深绿(80,160,80) |
| 西部草原人 | 14 | 10 | 12 | 8 | 12 | 10 | `W` | 棕黄(180,160,100) |
| 山地矿工 | 14 | 6 | 16 | 10 | 8 | 12 | `M` | 深灰(120,120,120) |

NPC 显示字符 = 地域字符，颜色 = 基础颜色 × 类型修正：
- FRIENDLY: 基础色（正常显示）
- NEUTRAL: 基础色
- HOSTILE: 基础色 + 红色偏移 (r+40, 保持 g,b)
- FUNCTIONAL: 基础色 + 金色偏移 (r+30, g+30, b-20)

---

## 四、NPC 属性

### 基础属性（一级属性）

继承 Creature 的 6 属性体系（STR/AGI/CON/INT/PER/WIL）：

```
初始值 = 地域基线值 + 随机波动(±2)
```

### 二级属性

| 属性 | 公式 |
|------|------|
| 生命值上限 | `50 + CON × 10` |
| 近战伤害 | `25 + STR × 1.5` |
| 命中率 | `80% + PER × 1% + AGI × 0.5%` |
| 闪避率 | `AGI × 1.5%`（上限 40%） |
| 暴击率 | `5% + PER × 0.5%` |

### 战斗属性

- **近战攻击** — 与 Player.meleeAttack 公式一致
- **追击** — 向玩家方向移动（与 AnimalAI 的 HUNT 类似，但目标是玩家而非猎物）
- **逃跑** — 生命值 < 30% 时尝试远离玩家

---

## 五、NpcAI 状态机

### 状态枚举

| 状态 | 说明 | 转换条件 |
|------|------|---------|
| `IDLE` | 原地休息 | 持续 3-8 回合 → WALK/PATROL/SLEEP |
| `WALK` | 随机游荡 | 持续 2-5 回合 → IDLE |
| `PATROL` | 固定路线巡逻 | 到达巡逻点 → 下一巡逻点，完成后 → IDLE |
| `TALK` | 与玩家对话 | 玩家关闭对话 → IDLE |
| `TRADE` | 与玩家交易 | 玩家关闭交易 → IDLE |
| `ATTACK` | 攻击玩家 | 玩家死亡/NPC死亡/远离 → IDLE |
| `FLEE` | 远离玩家 | 距离 ≥ 10 或安全 → IDLE |
| `HUNT_PREY` | 追击玩家（敌对型） | 玩家超出感知 → IDLE |
| `SLEEP` | 休息恢复 | 持续 5-10 回合 → IDLE |

### 状态转换规则

```
IDLE ──玩家进入感知──> FLEE（仅 FRIENDLY/NEUTRAL 且玩家声望低时）
    ──玩家进入感知+敌对──> ATTACK
    ──持续回合到──> WALK / PATROL / SLEEP

WALK ──玩家进入感知+敌对──> HUNT_PREY
     ──持续回合到──> IDLE

ATTACK ──玩家死亡/NPC死亡──> IDLE
       ──玩家跑远──> IDLE

FLEE ──距离≥10──> IDLE

HUNT_PREY ──追上玩家──> ATTACK
          ──玩家跑远──> IDLE

SLEEP ──持续回合到──> IDLE
```

---

## 六、NPC 背包（NpcInventory）

- 基于重量的容量限制：`STR × CARRY_PER_STRENGTH`
- 商人 NPC：携带可交易商品
- 敌对 NPC：携带武器/防具（被击杀后掉落）
- 掉落逻辑：NPC 死亡时，所有物品散落到地面

---

## 七、NPC 社交框架（NpcSocial）

### 对玩家的态度

| 字段 | 说明 |
|------|------|
| `attitude` | 友好(0~100)：初始 50（中立），根据声望和交互变化 |
| `lastInteraction` | 最后一次交互时间 |
| `knownThreats` | 已知的威胁（如玩家攻击过该 NPC） |

### NPC 之间关系（预留）

| 字段 | 说明 |
|------|------|
| `relationships` | Map<NpcId, Relationship>，记录对其他 NPC 的态度 |

### 阵营（预留）

| 字段 | 说明 |
|------|------|
| `faction` | 阵营标识，同一阵营 NPC 共享敌友关系 |

---

## 八、NPC 交互菜单

玩家靠近 NPC 并按 **C 键**，打开多级菜单：

```
┌─ 与 [NPC名字] 交互 ──────────┐
│  1. 对话                      │
│  2. 交易                      │
│  3. 观察（获取信息）          │
│  4. 离开                      │
└───────────────────────────────┘
```

- **对话** — 根据 NPC 类型显示不同对话内容
- **交易** — 打开商人界面（展示 NPC 背包）
- **观察** — 显示 NPC 信息（名字、类型、地域背景、态度）

---

## 九、NPC 生成

### 当前阶段：调试生成

通过控制台命令或硬编码位置生成 NPC，用于测试。

### 后续阶段：营地生成

NPC 跟随村庄/营地系统生成，根据营地的地域背景分配 NPC 类型和数量。

### 生成时属性计算

```
for each attribute in [STR, AGI, CON, INT, PER, WIL]:
    base = regionBase[attribute]
    variance = random(-2, +2)
    final = base + variance
```

---

## 十、NPC 死亡

- **永久死亡** — NPC 死亡后从世界中移除
- **掉落物品** — NpcInventory 中所有物品散落到死亡位置
- **日志记录** — 记录击杀信息

---

## 十一、预留扩展

| 功能 | 状态 | 说明 |
|------|------|------|
| 作息/日程 | 预留字段 | NpcSchedule 记录每日行为规律 |
| 对话树 | 未实现 | 后续可扩展多分支对话 |
| 阵营系统 | 预留字段 | NpcFaction 定义阵营关系 |
| NPC 之间关系 | 预留字段 | Map<NpcId, Relationship> |
| 驯养动物 | 未实现 | 后续可添加宠物系统 |
| 远程攻击 | 未实现 | 弓箭等武器 |
