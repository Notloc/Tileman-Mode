package com.tileman.multiplayer;

import java.io.Serializable;

public class AccountRegionId implements Serializable {
    public final long accountHash;
    public final int regionId;

    public AccountRegionId(long accountHash, int regionId) {
        this.accountHash = accountHash;
        this.regionId = regionId;
    }
}
