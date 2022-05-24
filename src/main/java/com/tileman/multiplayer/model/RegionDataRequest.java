package com.tileman.multiplayer.model;

import com.tileman.multiplayer.AccountRegionId;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
public class RegionDataRequest implements Serializable {
    final List<AccountRegionId> accountRegionIds;
}
