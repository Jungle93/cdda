package com.github.game.cdda.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生态日志测试。
 */
class EcologyLogTest {

    @BeforeEach
    void clearGameLog() throws Exception {
        // 反射清理 GameLog 的 entries
        Method m = GameLog.class.getDeclaredMethod("getInstance");
        GameLog log = (GameLog) m.invoke(null);
        Method clear = log.getClass().getDeclaredMethod("getAllEntries");
        List<String> entries = (List<String>) clear.invoke(log);
        // 通过反射清空 entries
        java.lang.reflect.Field f = GameLog.class.getDeclaredField("entries");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.LinkedList<String> list = (java.util.LinkedList<String>) f.get(log);
        list.clear();
    }

    @Test
    void logWithCategory() {
        EcologyLog.getInstance().log(EcologyLog.Category.PREDATION, "狼 捕食了 鹿");

        List<String> entries = GameLog.getInstance().getRecentEntries(10);
        assertFalse(entries.isEmpty());
        assertTrue(entries.get(0).contains("[捕食]"));
        assertTrue(entries.get(0).contains("狼"));
    }

    @Test
    void logDiscovery() {
        EcologyLog.getInstance().logDiscovery("狼", "森林");

        List<String> entries = GameLog.getInstance().getRecentEntries(10);
        assertFalse(entries.isEmpty());
        assertTrue(entries.get(0).contains("[物种发现]"));
        assertTrue(entries.get(0).contains("森林"));
        assertTrue(entries.get(0).contains("狼"));
    }

    @Test
    void logPredation() {
        EcologyLog.getInstance().logPredation("狼", "鹿", "(10,20)");

        List<String> entries = GameLog.getInstance().getRecentEntries(10);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).contains("[捕食]"));
        assertTrue(entries.get(0).contains("狼"));
        assertTrue(entries.get(0).contains("鹿"));
        assertTrue(entries.get(0).contains("(10,20)"));
    }

    @Test
    void logBreeding() {
        EcologyLog.getInstance().logBreeding("兔子", 3, "草原");

        List<String> entries = GameLog.getInstance().getRecentEntries(10);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).contains("[繁殖]"));
        assertTrue(entries.get(0).contains("兔子"));
        assertTrue(entries.get(0).contains("3"));
        assertTrue(entries.get(0).contains("草原"));
    }

    @Test
    void logMigration() {
        EcologyLog.getInstance().logMigration("狼", "北区");

        List<String> entries = GameLog.getInstance().getRecentEntries(10);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).contains("[迁徙]"));
        assertTrue(entries.get(0).contains("狼"));
    }

    @Test
    void logWeather() {
        EcologyLog.getInstance().logWeather("降雨");

        List<String> entries = GameLog.getInstance().getRecentEntries(10);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).contains("[天气]"));
        assertTrue(entries.get(0).contains("降雨"));
    }

    @Test
    void categoryDisplayNames() {
        assertEquals("物种发现", EcologyLog.Category.SPECIES_DISCOVERY.getDisplayName());
        assertEquals("捕食", EcologyLog.Category.PREDATION.getDisplayName());
        assertEquals("迁徙", EcologyLog.Category.MIGRATION.getDisplayName());
        assertEquals("繁殖", EcologyLog.Category.BREEDING.getDisplayName());
        assertEquals("死亡", EcologyLog.Category.DEATH.getDisplayName());
        assertEquals("天气", EcologyLog.Category.WEATHER.getDisplayName());
    }

    @Test
    void multipleEventsPreserveOrder() {
        EcologyLog.getInstance().logDiscovery("鹿", "森林");
        EcologyLog.getInstance().logPredation("狼", "鹿", "(5,5)");
        EcologyLog.getInstance().logWeather("降雨");

        List<String> entries = GameLog.getInstance().getRecentEntries(10);
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).contains("[物种发现]"));
        assertTrue(entries.get(1).contains("[捕食]"));
        assertTrue(entries.get(2).contains("[天气]"));
    }
}
