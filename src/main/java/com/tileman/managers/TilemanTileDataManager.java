package com.tileman.managers;

import com.google.gson.reflect.TypeToken;
import com.tileman.runelite.TilemanModeConfig;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;

import java.util.*;

public class TilemanTileDataManager {

    private static final String LEGACY_REGION_PREFIX = "region_";
    private static final TypeToken<List<TilemanModeTile>> TILE_LIST_TYPE = new TypeToken<List<TilemanModeTile>>(){};

    public static void saveAllTiles(TilemanProfile profile, Map<Integer, List<TilemanModeTile>> tileData, PersistenceManager persistenceManager) {
        for (Integer region : tileData.keySet()) {
            saveTiles(profile, region, tileData.get(region), persistenceManager);
        }
    }

    public static void saveTiles(TilemanProfile profile, int regionId, Collection<TilemanModeTile> tiles, PersistenceManager persistenceManager) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }

        String regionKey = profile.getRegionKey(regionId);
        if (tiles == null || tiles.isEmpty()) {
            persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, regionKey);
            return;
        }

        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, regionKey, tiles);
    }

    public static Map<Integer, List<TilemanModeTile>> loadAllTiles(TilemanProfile profile, PersistenceManager persistenceManager) {
        Map<Integer, List<TilemanModeTile>> tilesByRegion = new HashMap<>();

        List<String> regionStrings = persistenceManager.findFullKeysByPrefix(TilemanModeConfig.CONFIG_GROUP + "." + profile.getRegionPrefix());
        regionStrings = removeKeyPrefixes(regionStrings, TilemanModeConfig.CONFIG_GROUP, profile.getRegionPrefix());

        List<Integer> regions = new ArrayList<>();
        for (String regionString : regionStrings) {
            Integer region = Integer.valueOf(regionString);
            if (region != null) {
                regions.add(region);
            }
        }

        for (int regionId : regions) {
            String regionKey = profile.getRegionKey(regionId);
            List<TilemanModeTile> tiles = persistenceManager.loadFromJsonOrDefault(TilemanModeConfig.CONFIG_GROUP, regionKey, TILE_LIST_TYPE, new ArrayList<>());
            tilesByRegion.put(regionId, tiles);
        }

        return tilesByRegion;
    }

    private static List<String> removeKeyPrefixes(List<String> keys, String configGroup, String keyPrefix) {
        String fullPrefix = configGroup + "." + keyPrefix;
        List<String> trimmedRegions = new ArrayList<String>();
        for (String region : keys) {
            trimmedRegions.add(region.replace(fullPrefix, ""));
        }
        return trimmedRegions;
    }

    // LEGACY STUFF

    public static Map<Integer, List<TilemanModeTile>> loadAllLegacyTilesFromConfig(PersistenceManager persistenceManager) {
        Map<Integer, List<TilemanModeTile>> tileData = new HashMap<>();
        String configGroup = TilemanModeConfig.CONFIG_GROUP;

        List<String> regionIds = getAllLegacyRegionIds(persistenceManager, configGroup);
        for (String regionIdString : regionIds) {
            Integer regionId = Integer.valueOf(regionIdString);
            List<TilemanModeTile> tiles = persistenceManager.loadFromJsonOrDefault(configGroup, "region_" + regionIdString, TILE_LIST_TYPE, new ArrayList<>());

            if (!tiles.isEmpty() && regionId != null) {
                tileData.put(regionId, tiles);
            }
        }

        return tileData;
    }

    public static void importAndSaveGroundMarkerTilesToProfile(TilemanProfile profile, PersistenceManager persistenceManager) {
        Map<Integer, List<TilemanModeTile>> profileTilesByRegion = loadAllTiles(profile, persistenceManager);

        List<String> groundMarkerRegions = getAllLegacyRegionIds(persistenceManager, "groundMarker");
        for (String region : groundMarkerRegions) {
            int regionId = Integer.parseInt(region);

            List<TilemanModeTile> tiles = persistenceManager.loadFromJsonOrDefault("groundMarker", LEGACY_REGION_PREFIX + region, TILE_LIST_TYPE, new ArrayList<>());
            Set<TilemanModeTile> groundMarkerTiles = new HashSet<>(tiles);
            groundMarkerTiles.addAll(profileTilesByRegion.getOrDefault(regionId, Collections.emptyList()));

            saveTiles(profile, regionId, groundMarkerTiles, persistenceManager);
        }
    }

    private static List<String> getAllLegacyRegionIds(PersistenceManager persistenceManager, String configGroup) {
        List<String> keys = persistenceManager.findFullKeysByPrefix(configGroup + ".region");
        return removeKeyPrefixes(keys, configGroup,"region_");
    }
}
