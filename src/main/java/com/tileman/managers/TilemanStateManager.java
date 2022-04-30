package com.tileman.managers;

import com.tileman.ProfileTileData;
import com.tileman.GroupTileData;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;
import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.multiplayer.TilemanMultiplayerService;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class TilemanStateManager {

    private final PersistenceManager persistenceManager;
    @Getter private final TilemanGameRulesManager gameRulesManager;

    @Getter private TilemanProfile activeProfile = TilemanProfile.NONE;
    private ProfileTileData activeProfileTileData = null;

    @Getter private GroupTilemanProfile activeGroupProfile = GroupTilemanProfile.NONE;
    @Getter private GroupTileData activeGroupTileData = null;

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
        } else {
            activeGroupProfile = GroupTilemanProfile.NONE;
        }

        this.activeProfileTileData = ProfileTileDataUtil.loadProfileTileData(activeProfile, persistenceManager);
        this.gameRulesManager.setActiveProfile(profile);
        onProfileChangedEvent.forEach(listener -> listener.accept(profile, activeGroupProfile));
    }

    void setActiveGroupProfile(TilemanProfile activeProfile, GroupTilemanProfile groupProfile) {
        this.activeProfileTileData = null;

        this.activeGroupProfile = groupProfile;
        this.activeGroupTileData = ProfileTileDataUtil.loadGroupTileData(groupProfile, persistenceManager);
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
        return activeProfile.isGroupTileman() ? activeGroupTileData.getProfileTileData(activeProfile.getAccountHashLong()) : activeProfileTileData;
    }

    public ProfileTileData getProfileDataForProfile(TilemanProfile profile) {
        return getProfileDataForProfile(profile.getAccountHashLong());
    }

    public ProfileTileData getProfileDataForProfile(long accountHash) {
        if (accountHash == -1) {
            return null;
        }
        if (activeGroupTileData != null) {
            return activeGroupTileData.getProfileTileData(accountHash);
        } else if (activeProfile != null && accountHash == activeProfile.getAccountHashLong()) {
            return activeProfileTileData;
        }
        return null;
    }

    public int countUnlockedTiles() {
        if (activeProfile.equals(TilemanProfile.NONE)) {
            return 0;
        }

        if (activeProfile.isGroupTileman()) {
            return activeGroupTileData.countTiles();
        } else {
            return activeProfileTileData.countTiles();
        }
    }
}
