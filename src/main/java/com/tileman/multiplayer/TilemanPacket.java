package com.tileman.multiplayer;

import java.io.Serializable;
import static com.tileman.multiplayer.TilemanPacketType.*;

public class TilemanPacket implements Serializable {
    public final TilemanPacketType packetType;
    public final String message;

    private TilemanPacket(TilemanPacketType packetType, String message) {
        this.packetType = packetType;
        this.message = message;
    }

    public static TilemanPacket createAuthenticationPacket(String hashedPassword) {
        return new TilemanPacket(AUTHENTICATION, hashedPassword);
    }

    public static TilemanPacket createAuthenticationResponsePacket(boolean isValidated) {
        return new TilemanPacket(AUTHENTICATION_RESPONSE, Boolean.toString(isValidated));
    }

    public static TilemanPacket createRegionDataRequest() {
        return new TilemanPacket(REGION_DATA_REQUEST, "");
    }

    public static TilemanPacket createRegionDataResponse() {
        return new TilemanPacket(REGION_DATA_RESPONSE, "");
    }

    // client
        // client connects
            // asks for all tile data for other players
    
    // server
        // client connections
            // asks for all data from player


    public static TilemanPacket createTileSyncRequest() {
        return new TilemanPacket(TILE_SYNC_REQUEST, "");
    }


    public static TilemanPacket createRegionHashReport() {
        return new TilemanPacket(REGION_HASH_REPORT, "");
    }









    public static TilemanPacket createTileUpdatePacket(boolean state) {
        return new TilemanPacket(TILE_UPDATE, Boolean.toString(state));
    }

    public static TilemanPacket createEndOfDataPacket() {
        return new TilemanPacket(END_OF_DATA, "");
    }
}
