package com.tileman.multiplayer.model;

import com.tileman.TilemanProfile;
import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.multiplayer.client.TilemanClient;
import com.tileman.multiplayer.server.TilemanServer;
import com.tileman.multiplayer.server.TilemanServerConnectionHandler;
import lombok.Value;

import java.io.Serializable;

@Value
public class AuthenticationRequest implements Serializable {
    public final TilemanProfile profile;
    public final String hashedPassword;
}
