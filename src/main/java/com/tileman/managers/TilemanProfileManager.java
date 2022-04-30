/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
 * Copyright (c) 2022, Colton Campbell <https://github.com/Notloc>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tileman.managers;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;
import com.tileman.runelite.TilemanModeConfig;
import com.tileman.runelite.TilemanModePlugin;
import com.tileman.TilemanProfileExportData;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class TilemanProfileManager {

    private final Client client;

    private final PersistenceManager persistenceManager;
    @Getter private final TilemanGameRulesManager gameRulesManager;

    private TilemanProfile activeProfile = TilemanProfile.NONE;

    @Getter(AccessLevel.PACKAGE)
    private Map<Integer, List<TilemanModeTile>> tilesByRegion = new HashMap<>();

    public List<Consumer<TilemanProfile>> onProfileChangedEvent = new ArrayList<>();

    public  TilemanProfileManager(TilemanModePlugin plugin, Client client, PersistenceManager persistenceManager) {
        this.client = client;
        this.persistenceManager = persistenceManager;
        this.gameRulesManager = new TilemanGameRulesManager(this, persistenceManager);

        plugin.onLoginStateChangedEvent.add(this::onLoginStateChanged);
    }

    private void onLoginStateChanged(GameState gameState) {
        if (gameState == GameState.LOGGED_IN) {
            TilemanProfile profile = loadProfile(client.getAccountHash());
            setActiveProfile(profile);
        } else {
            setActiveProfile(TilemanProfile.NONE);
        }
    }

    public boolean hasActiveProfile() {
        return !activeProfile.equals(TilemanProfile.NONE);
    }

    public TilemanProfile getActiveProfile() {
        return activeProfile;
    }

    void setActiveProfile(TilemanProfile profile) {
        this.activeProfile = profile;
        this.gameRulesManager.setActiveProfile(profile);
        this.tilesByRegion = TilemanTileDataManager.loadAllTiles(profile, persistenceManager);

        onProfileChangedEvent.forEach(listener -> listener.accept(profile));
    }

    private TilemanProfile loadProfile(long accountHash) {
        String key = TilemanProfile.getProfileKey(String.valueOf(accountHash));
        return persistenceManager.loadFromJsonOrDefault(TilemanModeConfig.CONFIG_GROUP, key, TilemanProfile.class, TilemanProfile.NONE);
    }

    private void saveProfile(TilemanProfile profile) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, TilemanProfile.getProfileKey(profile.getAccountHash()), profile);
    }

    TilemanProfile createProfile() {
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            return TilemanProfile.NONE;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, client.getLocalPlayer().getName());
        saveProfile(profile);
        return profile;
    }

    TilemanProfile createProfileWithLegacyData() {
        TilemanProfile profile = createProfile();
        gameRulesManager.createGameRulesWithLegacyData(profile);

        Map<Integer, List<TilemanModeTile>> legacyTileData = TilemanTileDataManager.loadAllLegacyTilesFromConfig(persistenceManager);
        TilemanTileDataManager.saveAllTiles(profile, legacyTileData, persistenceManager);

        return profile;
    }

    TilemanProfile createProfileWithGroundMarkerData() {
        TilemanProfile profile = createProfile();
        TilemanTileDataManager.importAndSaveGroundMarkerTilesToProfile(profile, persistenceManager);
        return profile;
    }

    String exportProfile() {
        if (!hasActiveProfile()) {
            return "";
        }
        return new Gson().toJson(new TilemanProfileExportData(activeProfile, tilesByRegion));
    }

    TilemanProfile importProfileAsNew(String maybeJson, long accountHash) {
        TilemanProfileExportData importedProfileData = null;
        try {
            importedProfileData = new Gson().fromJson(maybeJson, TilemanProfileExportData.class);
        } catch (JsonParseException e) {}

        if (importedProfileData == null || importedProfileData.regionIds.size() != importedProfileData.regionTiles.size()) {
            return TilemanProfile.NONE;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, client.getLocalPlayer().getName());
        saveProfile(profile);
        for (int i = 0; i < importedProfileData.regionIds.size(); i++) {
            int regionId = importedProfileData.regionIds.get(i);
            List<TilemanModeTile> tiles = importedProfileData.regionTiles.get(i);
            TilemanTileDataManager.saveTiles(profile, regionId, tiles, persistenceManager);
        }
        return profile;
    }

    void deleteActiveProfile() {
        if (activeProfile.equals(TilemanProfile.NONE)) {
            return;
        }

        String groupPrefix = TilemanModeConfig.CONFIG_GROUP + ".";
        List<String> regionKeys = persistenceManager.findFullKeysByPrefix(groupPrefix + activeProfile.getRegionPrefix());
        for (int i = 0; i < regionKeys.size(); i++) {
            regionKeys.set(i, regionKeys.get(i).replace(groupPrefix, ""));
        }
        for (String key : regionKeys) {
            persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, key);
        }
        persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, activeProfile.getProfileKey());
        persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, activeProfile.getGameRulesKey());

        setActiveProfile(TilemanProfile.NONE);
    }

    public void saveTiles(TilemanProfile profile, int regionId, List<TilemanModeTile> tiles) {
        TilemanTileDataManager.saveTiles(profile, regionId, tiles, persistenceManager);
    }
}
