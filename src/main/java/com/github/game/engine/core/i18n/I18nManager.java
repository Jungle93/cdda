package com.github.game.engine.core.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 国际化（i18n）管理器。
 * <p>
 * 引擎级单例，负责加载和管理多语言翻译资源。
 * 翻译文件存储在 classpath:/i18n/{locale}/ 目录下，采用 JSON 格式。
 * <p>
 * 使用方式：
 * <pre>
 * I18nManager i18n = engine.getI18nManager();
 * String name = i18n.t("item.bread.name");        // 当前语言
 * String nameEn = i18n.t("item.bread.name", "en"); // 指定语言
 * </pre>
 * <p>
 * 翻译键约定格式：{@code {domain}.{key}.{field}}
 * <ul>
 *   <li>{@code item.{name}.name} — 物品显示名</li>
 *   <li>{@code item.{name}.description} — 物品描述</li>
 *   <li>{@code tile.{name}.name} — 地块显示名</li>
 *   <li>{@code tile.{name}.description} — 地块描述</li>
 *   <li>{@code creature.{id}.name} — 生物显示名</li>
 *   <li>{@code ui.{screen}.{element}} — UI 文本</li>
 *   <li>{@code system.{key}} — 系统通用文本</li>
 * </ul>
 *
 * @see #setLocale(String)
 * @see #t(String)
 * @see #getAvailableLocales()
 */
public class I18nManager {

    private static final Logger logger = LoggerFactory.getLogger(I18nManager.class);

    private static final String I18N_BASE_PATH = "i18n/";
    private static final String SUPPORTED_LOCALES_FILE = I18N_BASE_PATH + "supported_locales.json";

    private final Gson gson = new Gson();

    /** 当前语言代码 */
    private String currentLocale;
    /** 可用语言列表 */
    private List<LocaleInfo> availableLocales;
    /** 翻译缓存：locale → domain → key → value */
    private final Map<String, Map<String, String>> localeCache = new LinkedHashMap<>();
    /** 默认回退语言 */
    private String fallbackLocale = "en";

    /**
     * 语言信息（从 supported_locales.json 反序列化）。
     */
    public static class LocaleInfo {
        public String code;
        public String displayName;
    }

    public I18nManager() {
        loadSupportedLocales();
    }

    /**
     * 加载支持的语言列表。
     */
    private void loadSupportedLocales() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SUPPORTED_LOCALES_FILE)) {
            if (is == null) {
                logger.warn("未找到支持的语言配置文件: {}", SUPPORTED_LOCALES_FILE);
                availableLocales = Collections.emptyList();
                currentLocale = fallbackLocale;
                return;
            }
            Type listType = new TypeToken<List<LocaleInfo>>() {}.getType();
            availableLocales = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), listType);
            // 默认使用第一个语言
            if (!availableLocales.isEmpty()) {
                currentLocale = availableLocales.get(0).code;
            } else {
                currentLocale = fallbackLocale;
            }
        } catch (IOException e) {
            logger.warn("加载支持的语言配置失败: {}", e.getMessage());
            availableLocales = Collections.emptyList();
            currentLocale = fallbackLocale;
        }
    }

    /**
     * 加载指定语言的翻译文件。
     * 扫描 classpath:/i18n/{locale}/ 目录下所有 JSON 文件并合并。
     */
    private Map<String, String> loadLocale(String locale) {
        if (localeCache.containsKey(locale)) {
            return localeCache.get(locale);
        }

        Map<String, String> translations = new LinkedHashMap<>();
        String dirPath = I18N_BASE_PATH + locale + "/";

        try {
            // 尝试加载已知的翻译文件
            String[] files = { "items.json", "tiles.json", "creatures.json", "ui.json", "system.json" };
            for (String fileName : files) {
                String fullPath = dirPath + fileName;
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(fullPath)) {
                    if (is != null) {
                        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                        Map<String, String> fileData = gson.fromJson(
                                new InputStreamReader(is, StandardCharsets.UTF_8), mapType);
                        if (fileData != null) {
                            translations.putAll(fileData);
                            logger.debug("加载翻译文件: {} ({} 条)", fullPath, fileData.size());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("加载语言 {} 的翻译失败: {}", locale, e.getMessage());
        }

        localeCache.put(locale, Collections.unmodifiableMap(translations));
        logger.info("语言 {} 加载完成，共 {} 条翻译", locale, translations.size());
        return translations;
    }

    /**
     * 翻译查找（使用当前语言）。
     *
     * @param key 翻译键
     * @return 翻译文本，未找到时返回键本身
     */
    public String t(String key) {
        return t(key, currentLocale);
    }

    /**
     * 翻译查找（使用指定语言）。
     *
     * @param key 翻译键
     * @param locale 语言代码
     * @return 翻译文本，未找到时返回键本身
     */
    public String t(String key, String locale) {
        Map<String, String> map = loadLocale(locale);
        String value = map.get(key);
        if (value != null) {
            return value;
        }
        // 回退到默认语言
        if (!fallbackLocale.equals(locale)) {
            Map<String, String> fallback = loadLocale(fallbackLocale);
            value = fallback.get(key);
            if (value != null) {
                return value;
            }
        }
        logger.debug("翻译键未找到: {} (语言: {})", key, locale);
        return key; // 未找到时返回键本身
    }

    /**
     * 翻译查找，带默认值。
     *
     * @param key 翻译键
     * @param defaultValue 未找到时的默认值
     * @return 翻译文本或默认值
     */
    public String tWithDefault(String key, String defaultValue) {
        String value = t(key);
        return key.equals(value) ? defaultValue : value;
    }

    /**
     * 设置当前语言。
     *
     * @param locale 语言代码（必须在支持列表中）
     * @throws IllegalArgumentException 如果语言不支持
     */
    public void setLocale(String locale) {
        if (!isLocaleSupported(locale)) {
            throw new IllegalArgumentException("不支持的语言: " + locale + "，可用: " + getAvailableLocaleCodes());
        }
        this.currentLocale = locale;
        logger.info("切换语言至: {}", locale);
    }

    /**
     * 获取当前语言。
     */
    public String getLocale() {
        return currentLocale;
    }

    /**
     * 检查指定语言是否支持。
     */
    public boolean isLocaleSupported(String locale) {
        return availableLocales.stream().anyMatch(l -> l.code.equals(locale));
    }

    /**
     * 获取可用语言列表。
     */
    public List<LocaleInfo> getAvailableLocales() {
        return Collections.unmodifiableList(availableLocales);
    }

    /**
     * 获取所有可用语言代码列表。
     */
    public List<String> getAvailableLocaleCodes() {
        return Collections.unmodifiableList(
                availableLocales.stream().map(l -> l.code).toList()
        );
    }

    /**
     * 获取语言的显示名称。
     */
    public String getLocaleDisplayName(String locale) {
        return availableLocales.stream()
                .filter(l -> l.code.equals(locale))
                .findFirst()
                .map(l -> l.displayName)
                .orElse(locale);
    }
}
