package com.tileman.managers;

import com.google.gson.reflect.TypeToken;
import java.util.List;

public abstract class PersistenceManager {
    public abstract <T> void saveToJson(String prefixKey, String dataKey, T data);
    public abstract  <T> T loadFromJsonOrDefault(String prefixKey, String dataKey, Class<T> clazz, T defaultVal);
    public abstract <T> T loadFromJsonOrDefault(String prefixKey, String dataKey, TypeToken<T> typeToken, T defaultVal);
    public abstract <T> T loadOrDefault(String prefixKey, String dataKey, Class<T> clazz, T defaultVal);

    public abstract void delete(String prefixKey, String dataKey);

    public abstract List<String> findFullKeysByPrefix(String prefix);
}
