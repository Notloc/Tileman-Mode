package com.tileman;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

public class GroupTileData {
    private ConcurrentHashMap<Long, ProfileTileData> tileDataByAccount = new ConcurrentHashMap<>();
    private ConcurrentLinkedQueue<ProfileTileData> allProfileTileData = new ConcurrentLinkedQueue<>();

    public void forEachProfile(BiConsumer<Long, ProfileTileData> consumer) {
        tileDataByAccount.keySet().forEach(accountHash -> {
            consumer.accept(accountHash, tileDataByAccount.get(accountHash));
        });
    }

    public void insertProfileTileData(Long accountHash, ProfileTileData profileTileData) {
        tileDataByAccount.put(accountHash, profileTileData);
        allProfileTileData.add(profileTileData);
    }

    public void removeProfile(Long accountHash) {
        ProfileTileData tileData = tileDataByAccount.get(accountHash);
        if (tileData != null) {
            tileDataByAccount.remove(accountHash);
            allProfileTileData.remove(tileData);
        }
    }

    public Set<TilemanModeTile> getRegion(long accountHash, int regionId) {
        if (hasRegion(accountHash, regionId)) {
            return tileDataByAccount.get(accountHash).getRegion(regionId);
        }
        return null;
    }

    public int getRegionHash(long accountHash, int regionId) {
        if (!tileDataByAccount.containsKey(accountHash) || !tileDataByAccount.get(accountHash).hasRegion(regionId)) {
            return -1;
        }
        return tileDataByAccount.get(accountHash).getRegion(regionId).hashCode();
    }

    public boolean hasRegion(long accountHash, int regionId) {
        if (!tileDataByAccount.containsKey(accountHash) || !tileDataByAccount.get(accountHash).hasRegion(regionId)) {
            return false;
        }
        return true;
    }

    public void setRegionTiles(long accountHash, int regionId, Collection<TilemanModeTile> tiles) {
        ensureAccountHash(accountHash);
        tileDataByAccount.get(accountHash).setRegion(regionId, tiles);
    }

    private void ensureAccountHash(long accountHash) {
        if (!tileDataByAccount.containsKey(accountHash)) {
            tileDataByAccount.put(accountHash, new ProfileTileData());
        }
    }

    public ProfileTileData getProfileTileData(long accountHash) {
        ensureAccountHash(accountHash);
        return tileDataByAccount.get(accountHash);
    }

    public void setTile(long accountHash, TilemanModeTile tile, boolean state) {
        ensureAccountHash(accountHash);
        if (state) {
            tileDataByAccount.get(accountHash).addTile(tile.getRegionId(), tile);
        } else {
            tileDataByAccount.get(accountHash).removeTile(tile.getRegionId(), tile);
        }
    }

    public Collection<ProfileTileData> getAllProfileTileData() {
        return allProfileTileData;
    }

    public int countTiles() {
        int tileCount = 0;
        for (long key : tileDataByAccount.keySet()) {
            tileCount += tileDataByAccount.get(key).countTiles();
        }
        return tileCount;
    }
}
