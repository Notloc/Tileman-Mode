package com.tileman.multiplayer.model;

import com.tileman.TilemanProfile;
import lombok.Value;

import java.io.Serializable;

@Value
public class JoinResponse implements Serializable {
    final TilemanProfile profile;
}
