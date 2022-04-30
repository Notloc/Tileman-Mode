package com.tileman.multiplayer;

import java.io.Serializable;

public class RegionDataHash implements Serializable {

    public final long accountHash;
    public final int regionId;
    public final int dataHash;

    public RegionDataHash(long accountHash, int regionId, int dataHash) {
        this.accountHash = accountHash;
        this.regionId = regionId;
        this.dataHash = dataHash;
    }
}
