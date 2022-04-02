package com.tileman.multiplayer;

import java.io.Serializable;

public class TilemanPacket implements Serializable {

    String message;

    public TilemanPacket(String message) {
        this.message = message;
    }

}
