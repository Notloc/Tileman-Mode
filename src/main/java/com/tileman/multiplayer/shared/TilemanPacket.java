package com.tileman.multiplayer.shared;

import com.sun.org.apache.xpath.internal.operations.Bool;
import com.tileman.shared.TilemanProfile;

import java.io.Serializable;
import static com.tileman.multiplayer.shared.TilemanPacketType.*;

public class TilemanPacket implements Serializable {
    public final TilemanPacketType packetType;
    public final String sender;
    public final String message;

    private TilemanPacket(TilemanPacketType packetType, TilemanProfile profile, String message) {
        this.packetType = packetType;
        this.sender = profile.getAccountHash();
        this.message = message;
    }

    public static TilemanPacket createValidationPacket(TilemanProfile profile, String hashedPassword) {
        return new TilemanPacket(VALIDATION, profile, hashedPassword);
    }

    public static TilemanPacket createValidationResponsePacket(TilemanProfile profile, boolean isValidated) {
        return new TilemanPacket(VALIDATION_RESPONSE, profile, Boolean.toString(isValidated));
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
