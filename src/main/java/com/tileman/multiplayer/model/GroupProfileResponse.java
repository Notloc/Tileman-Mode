package com.tileman.multiplayer.model;

import com.tileman.multiplayer.GroupTilemanProfile;
import lombok.Value;

import java.io.Serializable;

@Value
public class GroupProfileResponse implements Serializable {
    public GroupTilemanProfile groupProfile;
}
