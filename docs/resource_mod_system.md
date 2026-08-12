# 资源系统与 Mod 支持重构设计

## 现状分析

### 当前目录结构

```
src/main/resources/
├── creatures/          # 10 个生物定义 JSON（硬编码逐个加载）
├── items/              # 70+ 个物品 JSON 混在一起（硬编码逐个加载）
├── recipes/            # 9 个合成配方 JSON（硬编码逐个加载）
├── vegetation/         # 16 个植被定义 JSON（硬编码逐个加载）
├── music/              # BGM 音频
├── sounds/             # SFX 音频
├── config/i18n/        # 国际化文件
└── simplelogger.properties
```

### 加载机制现状

| Registry | 来源 | 方式 | Mod 支持 |
|----------|------|------|----------|
| CreatureRegistry | classpath | 硬编码路径逐个加载 | ❌ |
| ItemRegistry | classpath | 硬编码路径逐个加载 | ⚠️ `registerMod()` 仅代码注册 |
| RecipeRegistry | classpath | 硬编码路径逐个加载 | ❌ |
| VegetationRegistry | classpath | 硬编码路径逐个加载 | ❌ |
| NpcRegistry | 代码构造 | `loadDefaults()` 硬编码 | ❌ |
| ResourceManager | classpath + file | 前缀检测（仅图片） | 部分 `file:` |

### 核心问题

1. **不支持 Mod 扩展** — 所有 Registry 硬编码路径，Mod 作者无法通过放 JSON 文件添加内容
2. **目录扁平无分类** — `items/` 下 70+ 文件混杂（食物、武器、材料、尸体、木材…）
3. **游戏数据与技术配置混放** — `simplelogger.properties` 和游戏数据同级
4. **i18n 层级冗余** — `config/i18n/` 多了一层 `config`
5. **NpcRegistry 无 JSON 支持** — NPC 定义纯代码构造，不符合数据驱动原则

---

## 目标目录结构

```
src/main/resources/
├── data/core/                        # 内置核心游戏数据（打包进 JAR）
│   ├── items/
│   │   ├── food/                     # 食物类
│   │   │   ├── water_bottle.json
│   │   │   ├── dirty_water.json
│   │   │   ├── bread.json
│   │   │   ├── canned_food.json
│   │   │   └── herbal_tea.json
│   │   ├── weapon/                   # 武器类
│   │   │   ├── rusty_knife.json
│   │   │   └── stone_axe.json
│   │   ├── medicine/                 # 药品类
│   │   │   ├── bandage.json
│   │   │   └── painkiller.json
│   │   ├── material/                 # 材料类
│   │   │   ├── wood/                 # 木材
│   │   │   ├── fiber/                # 纤维/植物材料
│   │   │   └── bone/                 # 骨头/角/牙
│   │   ├── hide/                     # 毛皮类
│   │   ├── meat/                     # 肉类
│   │   └── corpse/                   # 尸体类
│   ├── creatures/
│   │   ├── animal/                   # 动物
│   │   └── npc/                      # NPC 模板（从代码迁移到 JSON）
│   ├── recipes/                      # 合成配方
│   │   ├── woodworking.json          # 木工配方
│   │   └── basic.json                # 基础配方
│   ├── vegetation/                   # 植被定义
│   │   ├── trees.json                # 树木
│   │   ├── shrubs.json               # 灌木
│   │   ├── groundcover.json          # 草/苔藓
│   │   └── aquatic.json              # 水生植物
│   └── npc/                          # NPC 模板定义
│       └── templates.json
├── audio/                            # 音频资源
│   ├── music/
│   └── sfx/
├── gfx/                              # 图片/精灵图资源
├── i18n/                             # 国际化（去掉 config 层）
│   ├── supported_locales.json
│   ├── en/
│   └── zh/
└── log.properties                    # 技术配置
```

### Mods 外部目录（不打包进 JAR）

```
mods/                                 # 游戏运行目录（JAR 外）
└── <mod-id>/
    ├── mod.json                      # Mod 元信息
    └── data/                         # 与 core 同样的子目录结构
        ├── items/
        ├── creatures/
        ├── recipes/
        └── ...
```

---

## Mod 系统设计

### mod.json 格式

```json
{
  "id": "my-mod",
  "name": "我的 Mod",
  "version": "1.0.0",
  "description": "添加新物品和生物",
  "author": "jungle",
  "dependencies": [],
  "loadOrder": 100
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | ✅ | 唯一标识（小写字母+数字+连字符） |
| `name` | ✅ | 显示名称 |
| `version` | ✅ | 语义版本号 |
| `description` | ❌ | 描述 |
| `author` | ❌ | 作者 |
| `dependencies` | ❌ | 依赖的其他 Mod ID 列表 |
| `loadOrder` | ❌ | 加载顺序（数字越大越晚加载，越晚加载的优先级越高） |

### 加载顺序

```
1. data/core/        — 内置核心数据（优先级最低）
2. mods/mod-A/       — Mod A（按 loadOrder 排序）
3. mods/mod-B/       — Mod B（优先级高于 A）
...
```

### 覆盖规则

- **同名 ID 覆盖**：后加载的同 ID 定义覆盖先加载的（Mod 优先级 > Core）
- **不要求全量覆盖**：Mod 只需放自己新增/修改的文件，不存在的文件不会被加载
- **日志记录**：每次覆盖都输出 warn 级别日志，便于排查冲突

---

## 技术实现

### 1. DataScanner — 通用目录扫描器

```java
// 引擎层：com.github.game.engine.core.data
public class DataScanner {

    /**
     * 扫描目录（classpath 或文件系统），返回所有 JSON 文件路径。
     *
     * @param basePath 扫描根路径（"data/core/items" 或 "/home/user/game/mods/x/data"）
     * @param source   来源类型（CLASSPATH / FILE）
     * @return JSON 文件路径列表（有序）
     */
    public static List<String> scanJsonFiles(String basePath, DataSource source);

    /**
     * 从 classpath 打开 JSON 文件的输入流。
     */
    public static InputStream openClasspathStream(String path);

    /**
     * 从文件系统打开 JSON 文件的输入流。
     */
    public static InputStream openFileStream(Path basePath, String relativePath);
}
```

### 2. ModManifest — Mod 元信息

```java
// 游戏层：com.github.game.cdda.mod
public class ModManifest {
    public String id;
    public String name;
    public String version;
    public String description;
    public String author;
    public List<String> dependencies = List.of();
    public int loadOrder = 0;
}
```

### 3. ModLoader — Mod 加载器

```java
// 游戏层：com.github.game.cdda.mod
public class ModLoader {

    /** 发现并加载所有 Mod */
    public static List<LoadedMod> discoverAndLoad(Path modsDir);

    /** 按依赖和 loadOrder 排序 Mod 列表 */
    private static List<LoadedMod> resolveLoadOrder(List<LoadedMod> mods);
}

public class LoadedMod {
    public ModManifest manifest;
    public Path modDir;  // mods/<mod-id>/ 目录
}
```

### 4. Registry 改造 — 统一扫描加载

改造前（ItemRegistry）：
```java
// 硬编码 70+ 行
loadFromClasspath("items/water_bottle.json");
loadFromClasspath("items/bread.json");
// ...
```

改造后：
```java
public static synchronized void loadAll() {
    if (loaded) return;

    // 1. 扫描 core 数据（classpath 递归）
    DataScanner.scanJsonFiles("data/core/items", CLASSPATH)
        .forEach(ItemRegistry::loadFromClasspath);

    // 2. 扫描 mod 数据（文件系统递归）
    if (Files.exists(MODS_DIR)) {
        LoadedMod mod = ModLoader.discoverAndLoad(MODS_DIR);
        for (LoadedMod m : mod) {
            DataScanner.scanJsonFiles(m.modDir.resolve("data/items"), FILE)
                .forEach(path -> loadFromFile(m.modDir, path));
        }
    }

    loaded = true;
}
```

---

## 迁移计划

### Phase 1: 基础设施（不破坏现有功能）
1. 创建 `DataScanner` 工具类
2. 创建 `ModManifest`、`LoadedMod`、`ModLoader`
3. 创建 `i18n/` 新目录，移动文件，更新加载路径
4. 移动 `simplelogger.properties` → `log.properties`

### Phase 2: 目录重组 + Registry 扫描化
1. 重组 `items/` → `data/core/items/` 子分类
2. 重组 `creatures/` → `data/core/creatures/animal/`
3. 重组 `vegetation/` → `data/core/vegetation/` 子分类
4. 重组 `recipes/` → `data/core/recipes/` 合并
5. 改造 4 个 Registry 改为 `DataScanner` 扫描加载
6. 验证：编译通过 + 游戏可运行

### Phase 3: Mod 系统
1. NPC 定义迁移到 JSON（`data/core/npc/templates.json`）
2. NpcRegistry 改为从 JSON 加载
3. `mods/` 目录外部热加载
4. Mod 冲突检测 + 覆盖日志

---

## 兼容性

- **向后兼容**：旧路径 `items/xxx.json`、`creatures/xxx.json` 等在 Phase 2 中会被迁移，不再保留
- **JAR 打包**：`data/core/` 下的文件仍通过 Maven resources 打包进 JAR
- **mods/ 目录**：不打包进 JAR，运行时从游戏运行目录扫描
