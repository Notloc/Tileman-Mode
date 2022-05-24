package com.tileman.multiplayer.model;

import com.tileman.TilemanModeTile;
import lombok.Value;

import java.io.Serializable;

@Value
public class TileUpdateResponse implements Serializable {
    final TilemanModeTile tile;
    final boolean tileState;
    final Long account;
}
