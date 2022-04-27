package com.tileman.multiplayer.shared;

import java.io.Serializable;

public class RegionDataHash implements Serializable {
    public final int regionId;
    public final int dataHash;

    public RegionDataHash(int regionId, int dataHash) {
        this.regionId = regionId;
        this.dataHash = dataHash;
    }
}
