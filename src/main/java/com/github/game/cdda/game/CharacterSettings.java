package com.github.game.cdda.game;

/**
 * 角色设置数据类。
 * 保存角色创建时玩家指定的个人信息。
 */
public class CharacterSettings {

    /** 角色名称 */
    private String name;
    /** 角色性别 */
    private String gender;

    /** 可用性别选项 */
    public static final String[] GENDERS = {"男", "女"};
    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 20;

    /** 创建默认角色设置 */
    public CharacterSettings() {
        this.name = "旅行者";
        this.gender = GENDERS[0];
    }

    /** 从已有设置复制 */
    public CharacterSettings(CharacterSettings other) {
        this.name = other.name;
        this.gender = other.gender;
    }

    // ── 访问器 ──────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) {
        if (name != null && name.length() <= NAME_MAX_LENGTH) {
            this.name = name;
        }
    }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    /** 切换到下一个性别选项 */
    public void cycleGender(int direction) {
        int idx = 0;
        for (int i = 0; i < GENDERS.length; i++) {
            if (GENDERS[i].equals(gender)) { idx = i; break; }
        }
        idx = (idx + direction + GENDERS.length) % GENDERS.length;
        gender = GENDERS[idx];
    }
}
