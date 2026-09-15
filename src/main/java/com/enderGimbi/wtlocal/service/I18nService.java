package com.enderGimbi.wtlocal.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class I18nService {

    private static final String PREF_KEY_LANG = "user_ui_language";
    private final Preferences prefs = Preferences.userNodeForPackage(I18nService.class);
    private final Map<String, String> translations = new HashMap<>();
    private String currentLanguage;

    public I18nService() {
        String savedLang = prefs.get(PREF_KEY_LANG, "en");
        loadLanguage(savedLang);
    }

    public void loadLanguage(String langCode) {
        this.currentLanguage = langCode;
        prefs.put(PREF_KEY_LANG, langCode);
        translations.clear();

        String resourcePath = "/i18n/" + langCode + ".json";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                String jsonText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                parseJsonToMap(jsonText);
            }
        } catch (Exception e) {
            System.err.println("Failed to load UI translation: " + e.getMessage());
        }
    }

    public String get(String key) {
        return translations.getOrDefault(key, key);
    }

    public String get(String key, Object... args) {
        String template = get(key);
        for (int i = 0; i < args.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    // Надежный парсер пар "ключ": "значение"
    private void parseJsonToMap(String json) {
        // Регулярное выражение ищет структуры вида "key": "value"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n");
            translations.put(key, value);
        }
    }

    private String clean(String str) {
        str = str.trim();
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        }
        return str.replace("\\\"", "\"");
    }
}