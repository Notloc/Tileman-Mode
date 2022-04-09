package com.tileman;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class TilemanProfileManager {

    private static final String LEGACY_REGION_PREFIX = "region_";
    private static final Gson GSON = new Gson();

    private ConfigManager configManager;
    private Client client;

    @Getter
    private TilemanGameRules gameRules = TilemanGameRules.DEFAULT_RULES;
    private TilemanProfile activeProfile = TilemanProfile.NONE;

    @Getter(AccessLevel.PACKAGE)
    private Map<Integer, List<TilemanModeTile>> tilesByRegion = new HashMap<>();

    public List<Consumer<TilemanProfile>> onProfileChangedEvent = new ArrayList<>();

    public  TilemanProfileManager(TilemanModePlugin plugin, Client client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
        plugin.onGameStateChangedEvent.add(gs -> onGameStateChanged(gs));
    }

    private void onGameStateChanged(GameState gameState) {
        if (gameState == GameState.LOGGED_IN) {
            TilemanProfile profile = getProfileForAccount(client.getAccountHash());
            setActiveProfile(profile);
        } else {
            setActiveProfile(TilemanProfile.NONE);
        }
    }

    public void setGameMode(TilemanGameMode mode) {
        gameRules.setGameMode(mode);
        saveGameRules(activeProfile, gameRules);
    }

    public void setCustomGameMode(boolean state) {
        gameRules.setEnableCustomGameMode(state);
        saveGameRules(activeProfile, gameRules);
    }

    public void setAllowTileDeficit(boolean state) {
        gameRules.setAllowTileDeficit(state);
        saveGameRules(activeProfile, gameRules);
    }

    public void setIncludeTotalLevel(boolean state) {
        gameRules.setIncludeTotalLevel(state);
        saveGameRules(activeProfile, gameRules);
    }

    public void setExcludeExp(boolean state) {
        gameRules.setExcludeExp(state);
        saveGameRules(activeProfile, gameRules);
    }

    public void setTileOffset(int offset) {
        gameRules.setTilesOffset(offset);
        saveGameRules(activeProfile, gameRules);
    }

    public void setExpPerTile(int exp) {
        gameRules.setExpPerTile(exp);
        saveGameRules(activeProfile, gameRules);
    }

    private TilemanGameRules loadGameRules(TilemanProfile profile) {
        String rulesKey = profile.getGameRulesKey();
        return getJsonFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, rulesKey, TilemanGameRules.class, TilemanGameRules.DEFAULT_RULES);
    }

    private void saveGameRules(TilemanProfile profile, TilemanGameRules rules) {
        if (profile == TilemanProfile.NONE) {
            return;
        }
        String rulesKey = profile.getGameRulesKey();
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, rulesKey, GSON.toJson(rules));
    }

    private void saveProfile(TilemanProfile profile) {
        if (profile == TilemanProfile.NONE) {
            return;
        }
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, TilemanProfile.getProfileKey(profile.getAccountHash()), GSON.toJson(profile));
    }

    private TilemanProfile getProfileForAccount(long accountHash) {
        String key = TilemanProfile.getProfileKey(String.valueOf(accountHash));
        return getJsonFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, key, TilemanProfile.class, TilemanProfile.NONE);
    }

    TilemanProfile createProfile(String name) {
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            return TilemanProfile.NONE;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, name);
        saveProfile(profile);
        return profile;
    }

    TilemanProfile createProfileWithLegacyData(String name) {
        TilemanProfile profile = createProfile(name);
        saveAllTiles(profile, loadAllLegacyTilesFromConfig(configManager));
        saveGameRules(profile, loadGameRulesFromLegacySaveDataOrUseDefaults());
        return profile;
    }

    TilemanProfile createProfileWithGroundMarkerData(String name) {
        TilemanProfile profile = createProfile(name);
        importGroundMarkerTilesToProfile(profile);
        return profile;
    }

    public TilemanProfile getActiveProfile() {
        return activeProfile;
    }

    public boolean hasActiveProfile() {
        return activeProfile != TilemanProfile.NONE;
    }

    void setActiveProfile(TilemanProfile profile) {
        this.activeProfile = profile;
        this.gameRules = loadGameRules(profile);

        tilesByRegion.clear();
        tilesByRegion = loadAllTiles(profile, configManager);

        onProfileChangedEvent.forEach(l -> l.accept(profile));
    }

    private void saveAllTiles(TilemanProfile profile, Map<Integer, List<TilemanModeTile>> tileData) {
        for (Integer region : tileData.keySet()) {
            saveTiles(profile, region, tileData.get(region));
        }
    }

    public void saveTiles(TilemanProfile profile, int regionId, Collection<TilemanModeTile> tiles) {
        if (profile == TilemanProfile.NONE) {
            return;
        }
        String regionKey = profile.getRegionKey(regionId);

        if (tiles == null || tiles.isEmpty()) {
            configManager.unsetConfiguration(TilemanModeConfig.CONFIG_GROUP, regionKey);
            return;
        }

        String json = GSON.toJson(tiles);
        configManager.setConfiguration(TilemanModeConfig.CONFIG_GROUP, regionKey, json);
    }

    public static Map<Integer, List<TilemanModeTile>> loadAllTiles(TilemanProfile profile, ConfigManager configManager) {
        Map<Integer, List<TilemanModeTile>> tilesByRegion = new HashMap<>();

        List<String> regionStrings = configManager.getConfigurationKeys(TilemanModeConfig.CONFIG_GROUP + "." + profile.getRegionPrefix());
        regionStrings = removeKeyPrefixes(regionStrings, TilemanModeConfig.CONFIG_GROUP, profile.getRegionPrefix());

        List<Integer> regions = new ArrayList<>();
        for (String regionString : regionStrings) {
            Integer region = Integer.valueOf(regionString);
            if (region != null) {
                regions.add(region);
            }
        }

        for (int regionId : regions) {
            List<TilemanModeTile> points = loadTilesByRegion(configManager, profile, regionId);
            tilesByRegion.put(regionId, points);
        }

        return tilesByRegion;
    }

    private static List<TilemanModeTile> loadTilesByRegion(ConfigManager configManager, TilemanProfile profile, int regionId) {
        String regionKey = profile.getRegionKey(regionId);
        return loadTilesFromConfig(configManager, TilemanModeConfig.CONFIG_GROUP, regionKey);
    }

    private static List<String> removeKeyPrefixes(List<String> keys, String configGroup, String keyPrefix) {
        String fullPrefix = configGroup + "." + keyPrefix;
        List<String> trimmedRegions = new ArrayList<String>();
        for (String region : keys) {
            trimmedRegions.add(region.replace(fullPrefix, ""));
        }
        return trimmedRegions;
    }

    // CONFIG STUFF

    private static List<TilemanModeTile> loadTilesFromConfig(ConfigManager configManager, String configGroup, String key) {
        String json = configManager.getConfiguration(configGroup, key);
        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }

        return GSON.fromJson(json, new TypeToken<List<TilemanModeTile>>(){}.getType());
    }

    <T> T getJsonFromConfigOrDefault(String configGroup, String key, Class<T> clazz, T defaultVal) {
        String json = getFromConfigOrDefault(configGroup, key, String.class, "");
        if (Strings.isNullOrEmpty(json)) {
            return defaultVal;
        }

        try {
            return GSON.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            return defaultVal;
        }
    }

    <T> T getFromConfigOrDefault(String configGroup, String key, Class<T> clazz, T defaultVal) {
        try {
            Object val = configManager.getConfiguration(configGroup, key, clazz);
            if (val != null && clazz.isAssignableFrom(val.getClass())) {
                return (T)val;
            }
        } catch (ClassCastException e) {}
        return defaultVal;
    }







    // LEGACY STUFF

    public boolean hasLegacySaveData() {
        List<String> oldKeys = configManager.getConfigurationKeys(TilemanModeConfig.CONFIG_GROUP+".region");
        return !oldKeys.isEmpty();
    }

    private TilemanGameRules loadGameRulesFromLegacySaveDataOrUseDefaults() {
        TilemanGameRules defaults = TilemanGameRules.DEFAULT_RULES;
        TilemanGameRules rules = new TilemanGameRules();
        rules.setGameMode(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "gameMode", TilemanGameMode.class, defaults.getGameMode()));
        rules.setAllowTileDeficit(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "allowTileDeficit", boolean.class, defaults.isAllowTileDeficit()));
        rules.setEnableCustomGameMode(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "enableCustomGameMode", boolean.class, defaults.isEnableCustomGameMode()));
        rules.setTilesOffset(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "tilesOffset", int.class, defaults.getTilesOffset()));
        rules.setIncludeTotalLevel(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "includeTotalLevels", boolean.class, defaults.isIncludeTotalLevel()));
        rules.setExcludeExp(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "excludeExp", boolean.class, defaults.isExcludeExp()));
        rules.setExpPerTile(getFromConfigOrDefault(TilemanModeConfig.CONFIG_GROUP, "expPerTile", int.class, defaults.getExpPerTile()));
        return rules;
    }

    private static Map<Integer, List<TilemanModeTile>> loadAllLegacyTilesFromConfig(ConfigManager configManager) {
        Map<Integer, List<TilemanModeTile>> tileData = new HashMap<>();
        String configGroup = TilemanModeConfig.CONFIG_GROUP;

        List<String> regionIds = getAllLegacyRegionIds(configManager, configGroup);
        for (String regionIdString : regionIds) {
            Integer regionId = Integer.valueOf(regionIdString);
            List<TilemanModeTile> tiles = loadTilesFromConfig(configManager, configGroup, "region_" + regionIdString);

            if (!tiles.isEmpty() && regionId != null) {
                tileData.put(regionId, tiles);
            }
        }

        return tileData;
    }

    private static List<String> getAllLegacyRegionIds(ConfigManager configManager, String configGroup) {
        List<String> keys = configManager.getConfigurationKeys(configGroup + ".region");
        return removeKeyPrefixes(keys, configGroup,"region_");
    }

    private void importGroundMarkerTilesToProfile(TilemanProfile profile) {
        Map<Integer, List<TilemanModeTile>> profileTilesByRegion = loadAllTiles(profile, configManager);

        List<String> groundMarkerRegions = getAllLegacyRegionIds(configManager, "groundMarker");
        for (String region : groundMarkerRegions) {
            int regionId = Integer.parseInt(region);

            Set<TilemanModeTile> groundMarkerTiles = new HashSet<>(loadTilesFromConfig(configManager, "groundMarker", LEGACY_REGION_PREFIX + region));
            groundMarkerTiles.addAll(profileTilesByRegion.getOrDefault(regionId, Collections.emptyList()));

            saveTiles(profile, regionId, groundMarkerTiles);
        }
    }
}
