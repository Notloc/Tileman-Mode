package com.tileman;

import com.google.gson.Gson;
import com.tileman.shared.DataManager;
import net.runelite.client.config.ConfigManager;

// Encapsulates saving and loading via the Runelite configManager
public class RuneliteDataManager extends DataManager {
    private ConfigManager configManager;
    private Gson GSON;

    public RuneliteDataManager(ConfigManager configManager) {
        this.configManager = configManager;
        GSON = new Gson();
    }

    @Override
    public <T> void saveJson(String prefixKey, String dataKey, T data) {
        String json = GSON.toJson(data);
        configManager.setConfiguration(prefixKey, dataKey, json);
    }

    @Override
    public <T> T loadJsonOrDefault(String prefixKey, String dataKey, Class<T> clazz, T defaultVal) {
        try {
            Object val = configManager.getConfiguration(prefixKey, dataKey, clazz);
            if (val != null && clazz.isAssignableFrom(val.getClass())) {
                return (T)val;
            }
        } catch (ClassCastException e) {}
        return defaultVal;
    }
}
