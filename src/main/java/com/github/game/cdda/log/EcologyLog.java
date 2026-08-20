package com.github.game.cdda.log;

/**
 * 生态日志（单例）。
 *
 * <p>记录生态系统相关事件，按类别分类：
 * <ul>
 *   <li>SPECIES_DISCOVERY — 物种发现</li>
 *   <li>PREDATION — 捕食事件</li>
 *   <li>MIGRATION — 迁徙事件</li>
 *   <li>BREEDING — 繁殖事件</li>
 *   <li>DEATH — 死亡事件</li>
 *   <li>WEATHER — 天气变化</li>
 * </ul>
 *
 * <p>同时写入分类记录和全局 GameLog，确保生态事件在普通日志中可见。
 */
public class EcologyLog {

    /** 单例实例 */
    private static final EcologyLog INSTANCE = new EcologyLog();

    /** 生态事件类别 */
    public enum Category {
        SPECIES_DISCOVERY("物种发现"),
        PREDATION("捕食"),
        MIGRATION("迁徙"),
        BREEDING("繁殖"),
        DEATH("死亡"),
        WEATHER("天气");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private EcologyLog() {}

    /**
     * 获取全局唯一实例。
     */
    public static EcologyLog getInstance() {
        return INSTANCE;
    }

    /**
     * 记录一条生态事件。
     *
     * @param category 事件类别
     * @param message  事件描述
     */
    public void log(Category category, String message) {
        String entry = String.format("[%s] %s", category.getDisplayName(), message);
        GameLog.getInstance().log(entry);
    }

    /**
     * 记录物种发现事件（便捷方法）。
     *
     * @param speciesName 物种名称
     * @param location    位置描述
     */
    public void logDiscovery(String speciesName, String location) {
        log(Category.SPECIES_DISCOVERY,
                String.format("在 %s 发现了 %s", location, speciesName));
    }

    /**
     * 记录捕食事件（便捷方法）。
     *
     * @param predator 捕食者名称
     * @param prey     猎物名称
     * @param location 位置描述
     */
    public void logPredation(String predator, String prey, String location) {
        log(Category.PREDATION,
                String.format("%s 在 %s 捕食了 %s", predator, location, prey));
    }

    /**
     * 记录繁殖事件（便捷方法）。
     *
     * @param species  物种名称
     * @param count    繁殖数量
     * @param location 位置描述
     */
    public void logBreeding(String species, int count, String location) {
        log(Category.BREEDING,
                String.format("%s 在 %s 繁殖了 %d 只", species, location, count));
    }

    /**
     * 记录迁徙事件（便捷方法）。
     *
     * @param species  物种名称
     * @param location 位置描述
     */
    public void logMigration(String species, String location) {
        log(Category.MIGRATION,
                String.format("%s 迁徙到了 %s", species, location));
    }

    /**
     * 记录天气变化事件（便捷方法）。
     *
     * @param weather 天气描述
     */
    public void logWeather(String weather) {
        log(Category.WEATHER, String.format("天气变为: %s", weather));
    }
}
