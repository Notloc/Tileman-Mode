package com.tileman.multiplayer.client;

public enum TilemanClientState {
    CONNECTING("Connecting"),
    AUTHENTICATING("Authenticating"),
    SYNCING("Syncing"),
    CONNECTED("Connected"),
    DISCONNECTED("Disconnected");

    private TilemanClientState(String label) {
        this.label = label;
    }

    private String label;

    public String getLabel() {
        return label;
    }
}
