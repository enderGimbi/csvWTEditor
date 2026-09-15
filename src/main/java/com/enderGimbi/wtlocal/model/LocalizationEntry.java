package com.enderGimbi.wtlocal.model;

import java.util.HashMap;
import java.util.Map;

public class LocalizationEntry {

    private final String key;
    private final Map<String,String> translations;

    public LocalizationEntry(String key) {
        this.key = key;
        this.translations = new HashMap<>();
    }

    public String getKey() {
        return key;
    }

    public Map<String, String> getTranslations() {
        return translations;
    }

    public String getTranslations(String langCode) {
        return translations.getOrDefault(langCode,"");
    }

    public void setTranslation(String langCode, String text) {
        translations.put(langCode,text);
    }
}
