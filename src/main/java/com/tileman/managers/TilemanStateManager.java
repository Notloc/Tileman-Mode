package com.tileman.managers;

import com.tileman.ProfileTileData;
import com.tileman.GroupTileData;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;
import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.multiplayer.TilemanMultiplayerService;
import com.tileman.multiplayer.TilemanPacket;
import com.tileman.runelite.TilemanModeConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class TilemanStateManager {

    private final PersistenceManager persistenceManager;
    @Getter private final TilemanGameRulesManager gameRulesManager;

    @Getter private TilemanProfile activeProfile = TilemanProfile.NONE;
    @Getter private GroupTilemanProfile activeGroupProfile = GroupTilemanProfile.NONE;

    public List<BiConsumer<TilemanProfile, GroupTilemanProfile>> onProfileChangedEvent = new ArrayList<>();

    public  TilemanStateManager(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.gameRulesManager = new TilemanGameRulesManager(this, persistenceManager);
    }

    public void onLoggedIn(long accountHash) {
        TilemanProfile profile = TilemanProfileUtil.loadProfile(accountHash, persistenceManager);
        setActiveProfile(profile);
    }

    public void onLoggedOut(){
        setActiveProfile(TilemanProfile.NONE);
    }

    public boolean hasActiveProfile() {
        return !activeProfile.equals(TilemanProfile.NONE);
    }

    void setActiveProfile(TilemanProfile profile) {
        this.activeProfile = profile;
        if (activeProfile.isGroupTileman()) {
            GroupTilemanProfile groupProfile = GroupTilemanProfileUtil.loadGroupProfile(activeProfile, persistenceManager);
            setActiveGroupProfile(activeProfile, groupProfile);
            return;
        }

        this.activeGroupProfile = GroupTilemanProfile.NONE;
        this.activeProfile.setTileData(ProfileTileDataUtil.loadProfileTileData(activeProfile, persistenceManager));
        this.gameRulesManager.setActiveProfile(profile);
        onProfileChangedEvent.forEach(listener -> listener.accept(profile, activeGroupProfile));
    }

    void setActiveGroupProfile(TilemanProfile activeProfile, GroupTilemanProfile groupProfile) {
        this.activeGroupProfile = groupProfile;
        this.activeGroupProfile.setGroupTileData(ProfileTileDataUtil.loadGroupTileData(groupProfile, persistenceManager));
        this.gameRulesManager.setActiveGroupProfile(activeProfile, groupProfile);
        onProfileChangedEvent.forEach(listener -> listener.accept(activeProfile, activeGroupProfile));
    }

    public void updateTileMark(int regionId, TilemanModeTile tile, boolean isMarked) {
        if (activeProfile.equals(TilemanProfile.NONE)) {
            return;
        }

        ProfileTileData tileData = getActiveProfileTileData();
        if (isMarked) {
            tileData.addTile(regionId, tile);
        } else {
            tileData.removeTile(regionId, tile);
        }

        TilemanMultiplayerService.sendMultiplayerTileUpdate(tile, isMarked);
        ProfileTileDataUtil.saveRegion(activeProfile, regionId, tileData.getRegion(regionId), persistenceManager);
    }

    public ProfileTileData getActiveProfileTileData() {
        return activeProfile.isGroupTileman() ?
                activeGroupProfile.getGroupTileData().getProfileTileData(activeProfile.getAccountHashLong()) :
                activeProfile.getTileData();
    }

    public ProfileTileData getProfileDataForProfile(TilemanProfile profile) {
        return getProfileDataForProfile(profile.getAccountHashLong());
    }

    public ProfileTileData getProfileDataForProfile(long accountHash) {
        if (accountHash == -1) {
            return null;
        }
        if (activeProfile.isGroupTileman()) {
            return activeGroupProfile.getGroupTileData().getProfileTileData(accountHash);
        } else if (accountHash == activeProfile.getAccountHashLong()) {
            return activeProfile.getTileData();
        }
        return null;
    }

    public Collection<ProfileTileData> getAllActiveProfileTileData() {
        if (activeProfile.isGroupTileman()) {
            return activeGroupProfile.getGroupTileData().getAllProfileTileData();
        } else {
            List<ProfileTileData> list = new ArrayList<>();
            list.add(activeProfile.getTileData());
            return list;
        }
    }

    public void assignGroupProfile(TilemanProfile profile, GroupTilemanProfile groupProfile) {
        if (profile == TilemanProfile.NONE) {
            return;
        }

        profile.joinMultiplayerGroup(groupProfile);
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, profile.getProfileKey(), profile);
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, groupProfile.getGroupTilemanProfileKey(), groupProfile);

        if (activeProfile.equals(profile)) {
            // Load the group tiles. This automatically imports your tiles from single player
            activeGroupProfile = groupProfile;
            activeGroupProfile.setGroupTileData(ProfileTileDataUtil.loadGroupTileData(groupProfile, persistenceManager));
            activeProfile.setTileData(null); // Allow GC of loaded single player data
        }
    }

    public int countUnlockedTiles() {
        if (activeProfile.equals(TilemanProfile.NONE)) {
            return 0;
        }

        if (activeProfile.isGroupTileman()) {
            return activeGroupProfile.getGroupTileData().countTiles();
        } else {
            return activeProfile.getTileData().countTiles();
        }
    }
}
