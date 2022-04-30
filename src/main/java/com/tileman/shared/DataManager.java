package com.tileman.shared;

// Hides how data is actually saved/loaded.
// Will help us more easily pull out the GroupTilemanProfileManager into a stand-alone app
public abstract class DataManager {
    public abstract <T> void saveJson(String prefixKey, String dataKey, T data);
    public abstract  <T> T loadJsonOrDefault(String prefixKey, String dataKey, Class<T> clazz, T defaultVal);
}
