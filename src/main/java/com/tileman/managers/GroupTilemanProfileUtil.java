package com.tileman.managers;

import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.runelite.TilemanModeConfig;
import com.tileman.TilemanProfile;

public class GroupTilemanProfileUtil {


    public static void saveGroupProfile(GroupTilemanProfile groupProfile, PersistenceManager persistenceManager) {
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, groupProfile.getGroupTilemanProfileKey(), groupProfile);
    }

    public static GroupTilemanProfile loadGroupProfile(TilemanProfile profile, PersistenceManager persistenceManager) {
        return persistenceManager.loadFromJsonOrDefault(TilemanModeConfig.CONFIG_GROUP, profile.getGroupTilemanProfileKey(), GroupTilemanProfile.class, GroupTilemanProfile.NONE);
    }
}
