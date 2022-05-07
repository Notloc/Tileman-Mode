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
import com.tileman.ProfileTileData;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;
import com.tileman.runelite.TilemanModeConfig;
import com.tileman.TilemanProfileExportData;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public final class TilemanProfileUtil {
    private TilemanProfileUtil() {}

    public static TilemanProfile createProfile(long accountHash, String accountName, PersistenceManager persistenceManager) {
        if (accountHash == -1) {
            return TilemanProfile.NONE;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, accountName);
        saveProfile(profile, persistenceManager);
        return profile;
    }

    public static TilemanProfile createProfileWithLegacyData(long accountHash, String accountName, PersistenceManager persistenceManager) {
        TilemanProfile profile = createProfile(accountHash, accountName, persistenceManager);
        TilemanGameRulesManager.createAndSaveGameRulesWithLegacyData(profile, persistenceManager);

        ProfileTileDataUtil.saveAllLegacyTilesToProfile(profile, persistenceManager);
        return profile;
    }

    public static TilemanProfile createProfileWithGroundMarkerData(long accountHash, String accountName, PersistenceManager persistenceManager) {
        TilemanProfile profile = createProfile(accountHash, accountName, persistenceManager);
        ProfileTileDataUtil.importAndSaveGroundMarkerTilesToProfile(profile, persistenceManager);
        return profile;
    }

    static TilemanProfile loadProfile(long accountHash, PersistenceManager persistenceManager) {
        String key = TilemanProfile.getProfileKey(String.valueOf(accountHash));
        return persistenceManager.loadFromJsonOrDefault(TilemanModeConfig.CONFIG_GROUP, key, TilemanProfile.class, TilemanProfile.NONE);
    }

    static void saveProfile(TilemanProfile profile, PersistenceManager persistenceManager) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, TilemanProfile.getProfileKey(profile.getAccountHash()), profile);
    }

    public static String exportProfileJson(TilemanProfile profile, ProfileTileData tileData) {
        if (profile.equals(TilemanProfile.NONE)) {
            return "";
        }
        return new Gson().toJson(new TilemanProfileExportData(profile, tileData));
    }

    public static TilemanProfile importProfileJson(String maybeJson, long accountHash, String accountName, PersistenceManager persistenceManager) {
        TilemanProfileExportData importedProfileData = null;
        try {
            importedProfileData = new Gson().fromJson(maybeJson, TilemanProfileExportData.class);
        } catch (JsonParseException e) {}

        if (importedProfileData == null || importedProfileData.regionIds.size() != importedProfileData.regionTiles.size()) {
            return TilemanProfile.NONE;
        }

        TilemanProfile profile = new TilemanProfile(accountHash, accountName);
        saveProfile(profile, persistenceManager);
        for (int i = 0; i < importedProfileData.regionIds.size(); i++) {
            int regionId = importedProfileData.regionIds.get(i);
            Set<TilemanModeTile> tiles = importedProfileData.regionTiles.get(i);
            ProfileTileDataUtil.saveRegion(profile, regionId, tiles, persistenceManager);
        }
        return profile;
    }

    public static void deleteProfile(TilemanProfile profile, PersistenceManager persistenceManager) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }

        String groupPrefix = TilemanModeConfig.CONFIG_GROUP + ".";
        List<String> regionKeys = persistenceManager.findFullKeysByPrefix(groupPrefix + profile.getRegionPrefix());
        for (int i = 0; i < regionKeys.size(); i++) {
            regionKeys.set(i, regionKeys.get(i).replace(groupPrefix, ""));
        }
        for (String key : regionKeys) {
            persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, key);
        }
        persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, profile.getProfileKey());
        persistenceManager.delete(TilemanModeConfig.CONFIG_GROUP, profile.getGameRulesKey());
    }
}
