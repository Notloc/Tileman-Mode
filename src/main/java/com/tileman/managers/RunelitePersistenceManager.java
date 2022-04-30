package com.tileman.managers;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.config.ConfigManager;

import java.util.List;

public class RunelitePersistenceManager extends PersistenceManager {
    private ConfigManager configManager;
    private Gson GSON;

    public RunelitePersistenceManager(ConfigManager configManager) {
        this.configManager = configManager;
        GSON = new Gson();
    }

    @Override
    public <T> void saveToJson(String prefixKey, String dataKey, T data) {
        String json = GSON.toJson(data);
        configManager.setConfiguration(prefixKey, dataKey, json);
    }

    @Override
    public <T> T loadOrDefault(String prefixKey, String dataKey, Class<T> clazz, T defaultVal) {
        try {
            Object val = configManager.getConfiguration(prefixKey, dataKey, clazz);
            if (val != null && clazz.isAssignableFrom(val.getClass())) {
                return (T)val;
            }
        } catch (ClassCastException e) {}
        return defaultVal;
    }

    @Override
    public <T> T loadFromJsonOrDefault(String prefixKey, String dataKey, Class<T> clazz, T defaultVal) {
        String json = loadOrDefault(prefixKey, dataKey, String.class, "");
        if (Strings.isNullOrEmpty(json)) {
            return defaultVal;
        }

        try {
            return GSON.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            return defaultVal;
        }
    }

    @Override
    public <T> T loadFromJsonOrDefault(String prefixKey, String dataKey, TypeToken<T> typeToken, T defaultVal) {
        String json = configManager.getConfiguration(prefixKey, dataKey);
        if (Strings.isNullOrEmpty(json)) {
            return defaultVal;
        }

        try {
            return GSON.fromJson(json, typeToken.getType());
        } catch (JsonSyntaxException e) {
            return defaultVal;
        }
    }

    @Override
    public void delete(String prefixKey, String dataKey) {
        configManager.unsetConfiguration(prefixKey, dataKey);
    }

    @Override
    public List<String> findFullKeysByPrefix(String prefix) {
        return configManager.getConfigurationKeys(prefix);
    }
}
