package com.tileman.multiplayer.model;

import com.tileman.TilemanModeTile;
import lombok.Value;

import java.io.Serializable;

@Value
public class TileUpdateRequest implements Serializable {
    final TilemanModeTile tile;
    final boolean tileState;
}
