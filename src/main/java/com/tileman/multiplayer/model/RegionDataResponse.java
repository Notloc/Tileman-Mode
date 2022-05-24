package com.tileman.multiplayer.model;

import com.tileman.TilemanModeTile;
import com.tileman.multiplayer.AccountRegionId;
import lombok.Value;

import java.io.Serializable;
import java.util.Set;

@Value
public class RegionDataResponse implements Serializable {
    final AccountRegionId accountRegionId;
    final Set<TilemanModeTile> regionTiles;
}
