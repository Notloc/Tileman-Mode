package com.tileman.multiplayer.model;

import com.tileman.multiplayer.RegionDataHash;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
public class RegionHashReportResponse implements Serializable {
    final List<RegionDataHash> regionHashData;
}
