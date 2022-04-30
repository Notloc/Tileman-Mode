package com.tileman.multiplayer.shared;

import com.tileman.TilemanModeConfig;
import com.tileman.shared.DataManager;
import com.tileman.shared.TilemanProfile;

public class GroupTilemanProfileManager {

    private DataManager dataManager;
    private GroupTilemanProfile groupProfile;

    public GroupTilemanProfileManager(DataManager dataManager, GroupTilemanProfile groupProfile) {
        this.dataManager = dataManager;
        this.groupProfile = groupProfile;
    }

    private void save(GroupTilemanProfile groupProfile) {
        dataManager.saveJson(TilemanModeConfig.CONFIG_GROUP, groupProfile.getGroupTilemanProfileKey(), groupProfile);
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
