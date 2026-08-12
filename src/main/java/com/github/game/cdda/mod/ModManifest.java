package com.github.game.cdda.mod;

/**
 * Mod 元信息（对应 mod.json）。
 */
public class ModManifest {

    /** 唯一标识（小写字母+数字+连字符） */
    public String id;

    /** 显示名称 */
    public String name;

    /** 语义版本号 */
    public String version;

    /** 描述 */
    public String description = "";

    /** 作者 */
    public String author = "";

    /** 依赖的其他 Mod ID 列表 */
    public java.util.List<String> dependencies = java.util.List.of();

    /** 加载顺序（数字越大越晚加载） */
    public int loadOrder = 0;

    @Override
    public String toString() {
        return String.format("ModManifest{id='%s', name='%s', version='%s', loadOrder=%d}",
                id, name, version, loadOrder);
    }
}
