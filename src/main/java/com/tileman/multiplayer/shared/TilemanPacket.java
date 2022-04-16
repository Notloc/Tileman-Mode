package com.tileman.multiplayer.shared;

import java.io.Serializable;
import static com.tileman.multiplayer.shared.TilemanPacketType.*;

public class TilemanPacket implements Serializable {
    public static final long SERVER_ID = -1;

    public final TilemanPacketType packetType;
    public final long sender;
    public String message;

    private TilemanPacket(TilemanPacketType packetType, long sender) {
        this.packetType = packetType;
        this.sender = sender;
    }

    public static TilemanPacket createRegionDataRequest(long sender, int regionId) {
        TilemanPacket packet = new TilemanPacket(REGION_DATA_REQUEST, sender);
        packet.message = String.valueOf(regionId);
        return packet;
    }

    public static TilemanPacket createRegionDataResponse(long sender, int regionId) {
        TilemanPacket packet = new TilemanPacket(REGION_DATA_RESPONSE, sender);
        packet.message = String.valueOf(regionId);
        return packet;
    }

    public static TilemanPacket createTileUpdatePacket(long sender, boolean state) {
        TilemanPacket packet = new TilemanPacket(TILE_UPDATE, sender);
        packet.message = Boolean.toString(state);
        return packet;
    }

    public static TilemanPacket createEndOfDataPacket(long sender) {
        TilemanPacket packet = new TilemanPacket(END_OF_DATA, sender);
        return packet;
    }
}
