# CDDA 游戏全面评估报告

> 评估时间：2026-08-21
> 评估范围：物品、战斗、代谢、世界生成、植被、HUD、合成、存档、音频、设置
> 方法：16 个子智能体并行代码审查 + 人工核实

---

## 修复记录

### 已修复 ✅

| # | 问题 | 修复内容 | 文件 |
|---|------|---------|------|
| 1 | 交易中栏无滚动 | 添加 `offeredScroll` 偏移量 | `TradeScreen.java` |
| 2 | 代谢/脱水/体温无死亡判定 | 集成 `DeathCause`（新增 DEHYDRATION/TEMPERATURE）到代谢系统，体温异常/极度脱水扣 HP | `DeathCause.java`, `MetabolismManager.java`, `HydrationManager.java`, `GameScene.java` |
| 3 | NPC 初始可能为零 | 三级保障生成机制 | `NpcManager.java` |
| 5 | 植被系统无玩家交互接口 | 已存在完整实现：ChopTreeAction / GatherPlantAction / PlantSeedAction / HarvestCropAction | `ChopTreeAction.java`, `GatherPlantAction.java`, `PlantSeedAction.java`, `HarvestCropAction.java` |
| 6 | 帮助缺少交易说明 | 新增第 4 页「NPC 与交易」 | `HelpOverlay.java` |
| 8 | 数量调整效率低 | PgUp/PgDn(±5)、Home/End(±10) | `TradeScreen.java` |
| 11 | 中栏 Enter 语义不清 | 改为完全移除（←保留减1） | `TradeScreen.java` |
| 14 | 左栏可用标记不清 | 部分可用=橙色、不可用=灰色 | `TradeScreen.java` |

### 设计意图（不修复）

| # | 问题 | 设计理由 |
|---|------|---------|
| 4 | 交易价值不透明 | **故意为之** — 以物换物核心玩法，通过 NPC 态度猜测价值，保持"猜"的趣味性 |

### 待修复

| # | 优先级 | 问题 | 建议 |
|---|--------|------|------|
| 9 | 🟡 | 合成无进度反馈 | 拆分为多回合 Activity |
| 10 | 🟡 | 配方全部可见无解锁 | 添加配方解锁条件 |
| 12 | 🟡 | HP/SPD 无颜色预警 | 添加阈值变色+进度条 |
| 13 | 🟡 | 植被定义字段未使用 | 优先使用物种级定义 |
| 15 | 🟡 | TurnManager 无先手排序 | 引入 initiative 排序 |
| 16 | 🟡 | 日志面板多项问题 | LINE_HEIGHT/颜色/滚轮 |
| 17 | 🟡 | 调试菜单无访问限制 | 开发模式标志保护 |
| 19 | 🟢 | 植被跨区块传播 | 检查相邻 chunk 边界 |
| 20 | 🟢 | 植被加成未消费 | PlantGrowthSystem 读取 boost |
| 21 | 🟢 | 枯萎瓦片永久残留 | 添加退化机制 |
| 22 | 🟢 | 区块加载/卸载形状不匹配 | 统一阈值形状 |
| 23 | 🟢 | 合成分类硬编码 | 动态收集 category 值 |
| 24 | 🟢 | 合成无搜索功能 | / 键触发搜索 |
| 25 | 🟢 | 设置功能单薄 | 添加音量/单位等选项 |
| 26 | ⚪ | 日历简化无周系统 | 可接受，暂缓 |
| 27 | ⚪ | AMBIENT 通道未暴露 | 添加便捷方法 |
| 28 | ⚪ | 区域系统未生效 | 接入世界生成 |
| 29 | ⚪ | 生物群落边界生硬 | 添加过渡带 |
| 30 | ⚪ | 区块生成单线程 | 预加载半径大时考虑并行 |

---

## 按模块评估

### 🟢 良好

| 模块 | 状态 | 说明 |
|------|------|------|
| 代谢与死亡系统 | ✅ 已修复 | 新增 DEHYDRATION/TEMPERATURE 死亡原因；体温异常（能量<5%且体温<34°C或>39°C）扣 HP；极度脱水（<10%）扣 HP；HP 归零触发对应 DeathCause |
| 植被交互 | ✅ 已实现 | ChopTreeAction(砍树) / GatherPlantAction(采集) / PlantSeedAction(播种) / HarvestCropAction(收获) 全部对接 VegetationDefinition.drops |
| 存档系统 | ✅ 已实现 | SaveManager 支持多槽位存档/读档，InGameMenuScreen 有保存入口 |
| 音频系统 | ✅ 基本完善 | BGM/SFX/AMBIENT 三通道，淡入淡出、交叉淡入、缓存机制齐全 |
| 时间系统 | ✅ 基本完善 | GameCalendar 四季变换、TurnManager 回合调度、TemperatureManager 环境温度 |
| 区块管理 | ✅ 基本完善 | ChunkManager 按需加载/卸载、预加载、卸载远处区块 |
| 输入状态机 | ✅ 优秀 | InputStateMachine 统一管理多种输入模式，模式转换清晰 |

### 🟡 需要改进

#### 代谢与死亡系统（✅ 已修复）

**问题 #2: 代谢/脱水/体温无死亡判定**

- `DeathCause` 新增 `DEHYDRATION`（脱水）和 `TEMPERATURE`（体温异常）两种死因
- `MetabolismManager.calcTemperatureDamage()`: 能量<5%且体温<34°C或>39°C时扣 1 HP/回合；能量<2%且体温<32°C或>41°C时扣 3 HP/回合
- `HydrationManager.calcDehydrationDamage()`: 水分<10%扣 1 HP/回合；<3%扣 3 HP/回合
- `GameScene.applyMetabolismDamage()`: 在 `endOfPlayerRound()` 中统一结算，HP 归零触发对应 DeathCause 并记录日志
- 游戏结束画面（TODO）待实现

#### 植被系统（✅ 已完整实现）

**问题 #5: 植被系统缺少玩家交互接口**

已确认 4 种植被交互动作全部实现：
- `ChopTreeAction`（chopping 标签）— 砍伐相邻 TREE/BUSH，多回合活动，使用 VegetationDefinition.drops 生成掉落物
- `GatherPlantAction`（foraging 标签）— 采集相邻 TALL_GRASS/REEDS/FLOWER/DEAD_GRASS，即时完成，使用 VegetationDefinition.drops
- `PlantSeedAction`（sowing 标签）— 在 FARMLAND 上播种，通过 ItemType.producesCrop 映射到植被物种 ID
- `HarvestCropAction`（harvesting 标签）— 收获成熟（MATURE 阶段）作物，使用 VegetationDefinition.drops，有收割工具时额外掉落种子

全部 20 种植被定义均有 drops 配置，掉落物品系统已联通。

**问题 #13: 植被定义字段已定义但未使用**（🟡 中）

- `VegetationDefinition` 中的 `germinateFertility`/`seedlingFertility`/`growingFertility`/`matureFertility`/`dailyFertilityCost` 等字段已定义
- 但 `VegetationState.isFertilitySufficient()` 和 `getDailyFertilityCost()` 统一从 `PlantGrowthConstants` 按植物类型查找
- JSON 配置和 Constants 两套数据源可能不一致

**建议**：优先使用 `VegetationDefinition` 中的物种级定义，Constants 仅作为默认值兜底

#### HUD 面板

**问题 #12: CharacterInfoPanel 无视觉预警**（🟡 中）

- HP 颜色固定（始终白色），< 25% 时无红色预警
- SPD（速度）无颜色预警，< 50% 时无黄色提示
- 所有状态仅以文本显示，无进度条可视化

**建议**：HP < 25% 时文字变红/闪烁；SPD < 50% 时黄色预警；添加进度条可视化组件

**问题 #16: GameLogPanel 多项问题**（🟡 中）

- `LINE_HEIGHT` 固定为 14，不随 `fontSize` 动态计算
- 日志无级别颜色区分（WARN/ERROR/INFO 同色）
- 紧凑模式无背景填充
- `setEnabled()` 空操作
- 无鼠标滚轮支持

**建议**：LINE_HEIGHT 基于 fontSize 计算；GameLog 条目增加级别颜色；紧凑模式加背景填充；添加滚轮支持

**其他 HUD 问题**：
- `TimePanel` 中 `temperatureManager` 为 null 时无 fallback 显示
- `StatusPanel` 接口缺少 `getWidth()`/`onResize()` 方法、无 `update(dt)` 生命周期

#### 合成系统

**问题 #9: 合成无进度反馈**（🟡 高）

- `executeCraft()` 一次性完成所有产出，`processingTime` 较大的配方（如 `CRAFT_BASE_TIME=100` 回合）无倒计时或制作中状态
- 与砍树活动（`ChopActivity` 有进度条）的交互范式不一致

**建议**：将制作拆分为多回合 Activity，渲染进度条，与砍树活动保持一致

**问题 #10: 配方全部可见无解锁**（🟡 高）

- `CraftingScreen` 直接 `RecipeRegistry.getAll()` 全量获取，没有任何可见性过滤
- 玩家从一开始就知道所有配方，缺少渐进式探索感

**建议**：在 `ProcessingRecipe` 增加 `unlockedBy`/`unlockCondition` 字段，过滤未解锁配方

**其他合成问题**：
- 分类硬编码为静态常量数组（#23）
- 无搜索/过滤功能（#24）

#### 回合系统

**问题 #15: TurnManager 无先手排序**（🟡 中）

- `processRound()` 简单遍历 entities 列表，没有 initiative 系统
- 多个生物的行动顺序取决于列表插入顺序而非速度
- 高速实体不一定先行动

**建议**：引入 initiative 排序或速度优先队列

#### 调试系统

**问题 #17: 调试菜单无访问限制**（🟡 高）

- `DebugMenuScreen` 可直接回复生命(+50)、补充能量(+30%)、补充水分(+30%)、传送等
- 没有开发模式标志或密码保护
- 发布版本可能泄露

**建议**：添加开发模式标志（如 `System.getProperty("debug.mode")`），发布时可通过构建配置禁用

#### 设置系统

**问题 #25: InGameSettingsScreen 功能单薄**（🟡 中）

- 仅提供摄像机缩放一项设置
- 缺少音量调节、字幕开关、快捷键自定义、显示单位切换等

**建议**：添加音量滑块、单位切换（ConfigManager 中已定义 `KEY_MASS_UNIT`/`KEY_VOLUME_UNIT` 但无 UI 入口）

### 🟢 小问题

| # | 问题 | 建议 |
|---|------|------|
| 19 | 植被跨区块传播未实现 | 处理边界瓦片时检查相邻 chunk |
| 20 | 植被加成值未被消费 | PlantGrowthSystem 读取 `getVegetationBoost()` |
| 21 | 枯萎瓦片永久残留 | 添加随时间退化为 GRASS 的机制 |
| 22 | 区块加载/卸载形状不匹配 | 统一使用相同形状阈值 |
| 26 | 日历简化无周系统 | 可接受，暂缓 |
| 27 | AMBIENT 通道未暴露 | 添加 `playAmbient()`/`stopAmbient()` |
| 28 | 区域系统未生效 | 接入世界生成或作为命名区域 |
| 29 | 生物群落边界生硬 | 添加过渡带混合 |
| 30 | 区块生成单线程 | 预加载半径大时考虑并行 |

---

## 游戏优势

1. **架构优秀**：引擎层与游戏层分离良好，Scene 系统层次分明，InputStateMachine 设计清晰
2. **代谢系统完整**：热量/体温/水分/口渴联动设计合理，四季温度变化有深度
3. **世界生成丰富**：Perlin 噪声地形、区块管理、植被系统、生物群落
4. **音频系统成熟**：三通道混音、淡入淡出、交叉淡入、缓存机制
5. **NPC 系统丰富**：AI 状态机、巡逻、社交态度、背包、交易
6. **物品框架完善**：注册表、动作系统、消耗品、合成配方
7. **国际化基础**：i18n 系统已建立，关键 UI 文本已翻译
8. **存档系统可用**：SaveManager 支持多槽位存档/读档

## 修复优先级建议

### 第一轮（核心生存循环）✅ 已完成
1. ~~**代谢死亡判定**（#2）~~ — 已实现：体温异常/极度脱水扣 HP，HP 归零触发 DeathCause
2. ~~**植被交互接口**（#5）~~ — 已确认完整实现：砍树/采集/播种/收获 4 种动作

### 第二轮（体验提升）
3. **合成进度反馈**（#9）— 与砍树活动保持一致
4. **配方解锁系统**（#10）— 增加探索动力
5. **HP/SPD 预警**（#12）— 生存信息可视化
6. **调试菜单保护**（#17）— 发布前必做

### 第三轮（完善细节）
7. TurnManager 先手排序（#15）
8. GameLogPanel 改进（#16）
9. 植被定义字段使用（#13）
10. 设置系统扩充（#25）
