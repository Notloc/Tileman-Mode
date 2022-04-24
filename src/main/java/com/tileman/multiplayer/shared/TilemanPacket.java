package com.tileman.multiplayer.shared;

import com.tileman.shared.TilemanProfile;

import java.io.Serializable;
import static com.tileman.multiplayer.shared.TilemanPacketType.*;

public class TilemanPacket implements Serializable {
    public final TilemanPacketType packetType;
    public final long sender;
    public final String message;

    private TilemanPacket(TilemanPacketType packetType, TilemanProfile profile, String message) {
        this.packetType = packetType;
        this.sender = Long.parseLong(profile.getAccountHash());
        this.message = message;
    }

    public static TilemanPacket createAuthenticationPacket(TilemanProfile profile, String hashedPassword) {
        return new TilemanPacket(AUTHENTICATION, profile, hashedPassword);
    }

    public static TilemanPacket createAuthenticationResponsePacket(TilemanProfile profile, boolean isValidated) {
        return new TilemanPacket(AUTHENTICATION_RESPONSE, profile, Boolean.toString(isValidated));
    }

    public static TilemanPacket createRegionDataRequest(TilemanProfile profile, int regionId) {
        return new TilemanPacket(REGION_DATA_REQUEST, profile, String.valueOf(regionId));
    }

    public static TilemanPacket createRegionDataResponse(TilemanProfile profile, int regionId) {
        return new TilemanPacket(REGION_DATA_RESPONSE, profile, String.valueOf(regionId));
    }

    public static TilemanPacket createTileUpdatePacket(TilemanProfile profile, boolean state) {
        return new TilemanPacket(TILE_UPDATE, profile, Boolean.toString(state));
    }

    public static TilemanPacket createEndOfDataPacket(TilemanProfile profile) {
        return new TilemanPacket(END_OF_DATA, profile, "");
    }
}
