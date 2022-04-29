package com.tileman.multiplayer.shared;

import java.io.Serializable;

public class PlayerRegionId implements Serializable {
    public final long accountHash;
    public final int regionId;

    public PlayerRegionId(long accountHash, int regionId) {
        this.accountHash = accountHash;
        this.regionId = regionId;
    }
}
