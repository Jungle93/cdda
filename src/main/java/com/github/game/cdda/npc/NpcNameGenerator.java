package com.github.game.cdda.npc;

import java.util.Random;

/**
 * NPC 随机名字生成器。
 * 根据地域背景生成符合风格的名字。
 */
public final class NpcNameGenerator {

    private NpcNameGenerator() {}

    private static final Random random = new Random();

    // ── 各地域名字前缀 ──────────────────────────────

    private static final String[] COMMON_NAMES = {
        "张三", "李四", "王五", "赵六", "孙七", "周八", "吴九", "郑十",
        "小明", "小红", "阿福", "阿贵", "大壮", "铁柱", "春生", "秋实"
    };

    private static final String[] NORTHERN_NAMES = {
        "巴特尔", "乌云", "其其格", "图雅", "斯琴", "朝鲁", "萨仁",
        "布和", "乌云其其格", "阿拉腾", "宝音", "呼和", "查干"
    };

    private static final String[] SOUTHERN_NAMES = {
        "水生", "柳娘", "阿莲", "渔歌", "荷香", "江平", "林溪",
        "雨来", "苗苗", "竹青", "梅香", "桃枝", "桂香"
    };

    private static final String[] EASTERN_NAMES = {
        "林深", "叶青", "松风", "竹隐", "花语", "藤蔓", "枝繁",
        "森罗", "树生", "木华", "柳暗", "花明", "叶落"
    };

    private static final String[] WESTERN_NAMES = {
        "苍狼", "白鹿", "飞鹰", "烈马", "狂风", "黄沙", "石坚",
        "草长", "天狼", "破云", "追风", "断岳", "牧歌"
    };

    private static final String[] MINER_NAMES = {
        "铁锤", "石头", "矿山", "石匠", "铜锁", "铁胆", "金不换",
        "石敢当", "铁牛", "铜锤", "钢骨", "岩松", "矿头"
    };

    /**
     * 为指定地域生成随机名字。
     *
     * @param region 地域背景
     * @return 随机名字
     */
    public static String generateName(NpcRegion region) {
        String[] pool = switch (region) {
            case COMMON -> COMMON_NAMES;
            case NORTHERN_HIGHLAND -> NORTHERN_NAMES;
            case SOUTHERN_RIVER -> SOUTHERN_NAMES;
            case EASTERN_FOREST -> EASTERN_NAMES;
            case WESTERN_PRAIRIE -> WESTERN_NAMES;
            case MOUNTAIN_MINER -> MINER_NAMES;
        };
        return pool[random.nextInt(pool.length)];
    }
}
