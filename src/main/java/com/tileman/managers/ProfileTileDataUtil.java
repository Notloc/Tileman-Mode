package com.tileman.managers;

import com.google.gson.reflect.TypeToken;
import com.tileman.GroupTileData;
import com.tileman.ProfileTileData;
import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.runelite.TilemanModeConfig;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;

import java.util.*;

public final class ProfileTileDataUtil {
    private ProfileTileDataUtil() {}

    private static final String LEGACY_REGION_PREFIX = "region_";
    private static final TypeToken<List<TilemanModeTile>> TILE_LIST_TYPE = new TypeToken<List<TilemanModeTile>>(){};

    public static void saveProfileTileData(TilemanProfile profile, ProfileTileData profileTileData, PersistenceManager persistenceManager) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        profileTileData.forEachRegion((regionId, regionTiles) -> {
            saveRegion(profile, regionId, regionTiles, persistenceManager);
        });
    }

    public static void saveRegion(long accountHash, int regionId, Set<TilemanModeTile> tiles, PersistenceManager persistenceManager) {
        TilemanProfile profile = new TilemanProfile(accountHash, "TEMP");
        saveRegion(profile, regionId, tiles, persistenceManager);
    }

    public static void saveRegion(TilemanProfile profile, int regionId, Set<TilemanModeTile> tiles, PersistenceManager persistenceManager) {
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

    public static ProfileTileData loadProfileTileData(TilemanProfile profile, PersistenceManager persistenceManager) {
        ProfileTileData profileTileData = new ProfileTileData();

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
            profileTileData.setRegion(regionId, tiles);
        }

        return profileTileData;
    }

    public static void deleteProfileTileData(TilemanProfile profile, ProfileTileData tileData, PersistenceManager persistenceManager) {
        tileData.forEachRegion((regionId, region) -> {
            persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, profile.getRegionKey(regionId));
        });
    }

    public static GroupTileData loadGroupTileData(GroupTilemanProfile groupProfile, PersistenceManager persistenceManager) {
        GroupTileData groupTileData = new GroupTileData();

        groupProfile.getGroupMemberAccountHashes().forEach(accountString -> {
            Long accountHash = Long.valueOf(accountString);
            TilemanProfile profile = TilemanProfileUtil.loadProfile(accountHash, persistenceManager);
            groupTileData.insertProfileTileData(accountHash, loadProfileTileData(profile, persistenceManager));
        });

        return groupTileData;
    }

    static void saveAllLegacyTilesToProfile(TilemanProfile profile, PersistenceManager persistenceManager) {
        Map<Integer, List<TilemanModeTile>> legacyTileData = new HashMap<>();
        String configGroup = TilemanModeConfig.CONFIG_GROUP;

        // Load legacy data
        List<String> regionIds = getAllLegacyRegionIds(persistenceManager, configGroup);
        for (String regionIdString : regionIds) {
            Integer regionId = Integer.valueOf(regionIdString);
            List<TilemanModeTile> tiles = persistenceManager.loadFromJsonOrDefault(configGroup, "region_" + regionIdString, TILE_LIST_TYPE, new ArrayList<>());

            if (!tiles.isEmpty() && regionId != null) {
                legacyTileData.put(regionId, tiles);
            }
        }

        // Convert
        ProfileTileData profileTileData = new ProfileTileData();
        legacyTileData.keySet().forEach(regionId -> {
            profileTileData.setRegion(regionId, legacyTileData.get(regionId));
        });

        ProfileTileDataUtil.saveProfileTileData(profile, profileTileData, persistenceManager);
    }

    public static void importAndSaveGroundMarkerTilesToProfile(TilemanProfile profile, PersistenceManager persistenceManager) {
        ProfileTileData profileTileData = loadProfileTileData(profile, persistenceManager);

        List<String> groundMarkerRegions = getAllLegacyRegionIds(persistenceManager, "groundMarker");
        for (String region : groundMarkerRegions) {
            int regionId = Integer.parseInt(region);

            List<TilemanModeTile> tiles = persistenceManager.loadFromJsonOrDefault("groundMarker", LEGACY_REGION_PREFIX + region, TILE_LIST_TYPE, new ArrayList<>());
            Set<TilemanModeTile> groundMarkerTiles = new HashSet<>(tiles);

            Set<TilemanModeTile> existingTiles = profileTileData.getRegion(regionId);
            if (existingTiles != null) {
                groundMarkerTiles.addAll(existingTiles);
            }

            saveRegion(profile, regionId, groundMarkerTiles, persistenceManager);
        }
    }

    private static List<String> getAllLegacyRegionIds(PersistenceManager persistenceManager, String configGroup) {
        List<String> keys = persistenceManager.findFullKeysByPrefix(configGroup + ".region");
        return removeKeyPrefixes(keys, configGroup,"region_");
    }

    private static List<String> removeKeyPrefixes(List<String> keys, String configGroup, String keyPrefix) {
        String fullPrefix = configGroup + "." + keyPrefix;
        List<String> trimmedRegions = new ArrayList<String>();
        for (String region : keys) {
            trimmedRegions.add(region.replace(fullPrefix, ""));
        }
        return trimmedRegions;
    }
}
