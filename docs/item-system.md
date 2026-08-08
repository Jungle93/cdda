# 物品系统设计文档

## 架构概览

```
com.github.game.item/
├── ConsumableType.java    # 可消耗类型枚举（位标志）
├── DisplayUnit.java       # 显示单位枚举（含转换）
├── ItemType.java          # 物品类型（不可变模板 + Builder）
├── ItemStack.java         # 物品堆叠实例（运行时对象）
└── ItemRegistry.java      # 注册表（TileType 模式）
```

**核心区分**：
- **ItemType** — 不可变的物品"蓝图"，定义固有属性（名称、重量、体积等）。全局唯一，通过注册表管理。
- **ItemStack** — 运行时的物品"实例"，持有 ItemType 引用 + 数量。可序列化到存档。

## 定义新物品

### 内置物品

在 `ItemRegistry` 的静态初始化块中添加：

```java
public static final ItemType MY_ITEM = registerBuiltin(
    new ItemType.Builder(100, "my_item")
        .description("一个自定义物品")
        .weight(200)          // 克
        .volume(100)          // 毫升
        .maxStackSize(10)
        .consumable(ConsumableType.FOOD)
        .nutrition(200, 40, 50)  // 热量kcal, 饱腹度, 水分mL
        .build()
);
```

### Mod 物品

Mod 在初始化时调用：

```java
ItemType customSword = new ItemType.Builder(1000, "mod_sword")
    .description("Mod 添加的宝剑")
    .weight(1500).volume(200)
    .unique()  // 不可堆叠
    .buildAndRegister();
```

## 属性说明

| 属性 | 类型 | 说明 |
|------|------|------|
| `id` | int | 稳定数字 ID，用于序列化 |
| `name` | String | 稳定字符串标识，用于配置文件/mod 查找 |
| `description` | String | 物品描述文本 |
| `weightGrams` | double | 单件重量（克），内部基准单位 |
| `volumeMl` | double | 单件体积（毫升），内部基准单位 |
| `maxStackSize` | int | 最大堆叠数（默认 1） |
| `unique` | boolean | 唯一物品（强制 maxStackSize=1） |
| `consumableTypes` | Set | 可消耗标签（多标签位） |
| `calories` | double | 热量（千卡 kcal） |
| `satiety` | double | 饱腹度（0-100 相对值） |
| `waterContent` | double | 含水量（毫升） |

## 消耗标签系统

`ConsumableType` 使用位标志设计，一个物品可同时拥有多种标签：

```java
// 同时是食物和药品（药膳）
.consumable(ConsumableType.FOOD, ConsumableType.MEDICINE)
```

位掩码序列化（用于存档）：

```java
int mask = ConsumableType.toMask(itemType.getConsumableTypes());
// 反序列化
Set<ConsumableType> types = ConsumableType.fromMask(mask);
```

## 单位系统

内部存储一律使用公制基准（克/毫升）。显示时按玩家偏好转换：

```java
DisplayUnit massUnit = gameConfig.getMassUnit();    // GRAM / KILOGRAM / ...
DisplayUnit volUnit = gameConfig.getVolumeUnit();   // MILLILITER / LITER / ...

String weightText = stack.formatTotalWeight(massUnit);  // "1.50 kg"
String volumeText = stack.formatTotalVolume(volUnit);   // "500.00 mL"
```

配置持久化到 `game.properties`：
```properties
display.unit.mass=GRAM
display.unit.volume=MILLILITER
```

## 堆叠系统

```java
ItemStack bread = new ItemStack(ItemRegistry.BREAD, 3);  // 3个面包

// 尝试合并
ItemStack moreBread = new ItemStack(ItemRegistry.BREAD, 5);
int remainder = bread.merge(moreBread);  // 合并后剩余（如果超限）

// 检查能否合并
if (bread.canMerge(moreBread)) {
    // 同类型 + 非唯一 + 合并后不超限
}
```

唯一物品（`unique()`）不可堆叠。

## 序列化格式

存档时仅序列化 `ItemStack`：

```json
{
  "typeId": 2,
  "count": 5
}
```

加载时：
```java
ItemType type = ItemRegistry.getById(typeId);
ItemStack stack = new ItemStack(type, count);
```

## ID 分配约定

| 范围 | 用途 |
|------|------|
| 0–999 | 内置物品（Base game） |
| 1000–9999 | Mod 物品 |

`registerMod()` 不强制检查范围，由 mod 作者自行遵守。

## 内置物品列表

| ID | name | 类型 | 重量 | 体积 | 堆叠 | 消耗标签 | 热量 | 饱腹 | 水分 |
|----|------|------|------|------|------|----------|------|------|------|
| 0 | water_bottle | 饮品 | 550g | 500mL | 4 | WATER | 0 | 0 | 500 |
| 1 | dirty_water | 饮品 | 550g | 500mL | 4 | WATER | 0 | 0 | 500 |
| 2 | bread | 食物 | 300g | 400mL | 5 | FOOD | 350 | 60 | 30 |
| 3 | canned_food | 食物 | 400g | 350mL | 8 | FOOD | 500 | 80 | 50 |
| 4 | bandage | 药物 | 50g | 30mL | 10 | MEDICINE | - | - | - |
| 5 | painkiller | 药物 | 20g | 10mL | 20 | MEDICINE | - | - | - |
| 6 | herbal_tea | 复合 | 250g | 200mL | 3 | WATER+MED | 10 | 5 | 200 |
| 7 | rusty_knife | 工具 | 150g | 50mL | 1(unique) | - | - | - | - |

## 扩展点

未来可按需添加（不破坏现有代码）：

| 功能 | 实现方式 |
|------|----------|
| 分类（武器/防具/材料等） | ItemType 加 `category` 字段 |
| 装备槽 | 新枚举 `EquipSlot` + ItemType 字段 |
| 耐久度 | ItemType 加 `maxDurability`，ItemStack 加 `durability` |
| 稀有度 | 新枚举 `Rarity` + ItemType 字段 |
| 合成配方 | 新包 `com.github.game.craft` |
| JSON 加载 Mod | `ModLoader` 读取 JSON → 调用 Builder |
