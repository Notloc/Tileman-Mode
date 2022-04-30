package com.tileman;

import com.tileman.multiplayer.ConcurrentSetMap;

import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// TODO: make threadsafe
public class ProfileTileData {
    private final long accountHash;
    private ConcurrentSetMap<Integer, TilemanModeTile> tilesByRegion = new ConcurrentSetMap<>();
    private int tileCount = 0;

    public ProfileTileData(TilemanProfile profile) {
        this(profile.getAccountHashLong());
    }

    public ProfileTileData(long accountHash) {
        this.accountHash = accountHash;
    }

    public void forEachRegion(BiConsumer<Integer, Set<TilemanModeTile>> consumer) {
        tilesByRegion.keySet().forEach(regionId -> {
            consumer.accept(regionId, tilesByRegion.get(regionId));
        });
    }

    public Set<TilemanModeTile> getRegion(int regionId) {
        return tilesByRegion.get(regionId);
    }

    public void setRegion(int regionId, Collection<TilemanModeTile> tiles) {
        if (tilesByRegion.containsKey(regionId)) {
            tileCount -= tilesByRegion.remove(regionId).stream().count();
        }

        if (tiles != null && !tiles.isEmpty()) {
            tilesByRegion.addAll(regionId, tiles);
            tileCount += tiles.stream().count();
        }
    }

    public boolean hasRegion(int regionId) {
        return tilesByRegion.containsKey(regionId);
    }

    public boolean hasTile(int regionId, TilemanModeTile tile) {
        if (!hasRegion(regionId)) {
            return false;
        }
        return tilesByRegion.get(regionId).contains(tile);
    }

    public void addTile(int regionId, TilemanModeTile tile) {
        if(tilesByRegion.add(regionId, tile)) {
            tileCount++;
        }
    }

    public void removeTile(int regionId, TilemanModeTile tile) {
        if(tilesByRegion.remove(regionId, tile)) {
            tileCount--;
        }
    }


    public int countTiles() {
        return tileCount;
    }
}
