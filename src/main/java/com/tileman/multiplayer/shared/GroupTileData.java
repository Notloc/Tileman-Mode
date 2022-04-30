package com.tileman.multiplayer.shared;

import com.tileman.shared.TilemanModeTile;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GroupTileData {
    protected ConcurrentHashMap<Long, ConcurrentSetMap<Integer, TilemanModeTile>> tileDataByPlayer = new ConcurrentHashMap<>();

    public Set<TilemanModeTile> getRegion(long accountHash, int regionId) {
        if (hasRegion(accountHash, regionId)) {
            return tileDataByPlayer.get(accountHash).get(regionId);
        }
        return null;
    }

    public int getRegionHash(long accountHash, int regionId) {
        return tileDataByPlayer.get(accountHash).get(regionId).hashCode();
    }

    public boolean hasRegion(long accountHash, int regionId) {
        if (!tileDataByPlayer.containsKey(accountHash) || !tileDataByPlayer.get(accountHash).containsKey(regionId)) {
            return false;
        }
        return true;
    }

    public void setRegionTiles(long accountHash, int regionId, TilemanModeTile[] tiles) {
        ensureAccountHash(accountHash);
        tileDataByPlayer.get(accountHash).remove(regionId);
        tileDataByPlayer.get(accountHash).addAll(regionId, tiles);
    }

    private void ensureAccountHash(long accountHash) {
        if (!tileDataByPlayer.containsKey(accountHash)) {
            tileDataByPlayer.put(accountHash, new ConcurrentSetMap<>());
        }
    }

    public void forEachAccount(Consumer<Long> consumer) {
        tileDataByPlayer.keySet().forEach(consumer);
    }

    public ConcurrentSetMap<Integer, TilemanModeTile> getAccountData(long accountHash) {
        return tileDataByPlayer.get(accountHash);
    }

    public void setTile(long accountHash, TilemanModeTile tile, boolean state) {
        ensureAccountHash(accountHash);
        if (state) {
            tileDataByPlayer.get(accountHash).add(tile.getRegionId(), tile);
        } else {
            tileDataByPlayer.get(accountHash).remove(tile.getRegionId(), tile);
        }
    }
}
