package com.tileman.multiplayer.model;

import com.tileman.TilemanProfile;
import lombok.Value;

import java.awt.*;
import java.io.Serializable;

@Value
public class ProfileUpdateRequest implements Serializable {
    private final TilemanProfile profile;
    private final String name;
    private final Color color;
}
