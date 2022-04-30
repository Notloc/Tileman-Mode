package com.tileman.multiplayer;

import com.tileman.runelite.TilemanModeConfig;
import com.tileman.managers.PersistenceManager;
import com.tileman.TilemanProfile;

public class GroupTilemanProfileManager {

    private PersistenceManager persistenceManager;
    private GroupTilemanProfile groupProfile;
    private GroupTileData groupTileData;

    public GroupTilemanProfileManager(PersistenceManager persistenceManager, GroupTilemanProfile groupProfile) {
        this.persistenceManager = persistenceManager;
        this.groupProfile = groupProfile;
    }

    private void save(GroupTilemanProfile groupProfile) {
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, groupProfile.getGroupTilemanProfileKey(), groupProfile);
    }


    public boolean isMember(long accountHash) {
        return groupProfile.isMember(accountHash);
    }

    public boolean addMember(TilemanProfile profile) {
        boolean added = groupProfile.addMember(profile);
        if (added) {
            save(groupProfile);
        }
        return added;
    }
}
