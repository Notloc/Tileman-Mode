package com.tileman.multiplayer;

import java.io.Serializable;

public class TilemanPacket implements Serializable {
    public static final long SERVER_ID = 0;


    PacketType packetType;
    long sender;
    String message;

    private TilemanPacket() {}

    public static TilemanPacket CreateRegionDataRequest(long sender, int regionId) {
        TilemanPacket packet = new TilemanPacket();
        packet.packetType = PacketType.REGION_DATA_REQUEST;
        packet.message = String.valueOf(regionId);
        packet.sender = sender;
        return packet;
    }

    public static TilemanPacket CreateRegionDataResponse(long sender, int regionId) {
        TilemanPacket packet = new TilemanPacket();
        packet.packetType = PacketType.REGION_DATA_RESPONSE;
        packet.message = String.valueOf(regionId);
        packet.sender = sender;
        return packet;
    }

    public static TilemanPacket CreateTileUpdatePacket(long sender, boolean state) {
        TilemanPacket packet = new TilemanPacket();
        packet.packetType = PacketType.TILE_UPDATE;
        packet.message = Boolean.toString(state);
        packet.sender = sender;
        return packet;
    }
}

enum PacketType {
    REGION_DATA_REQUEST,
    REGION_DATA_RESPONSE,
    TILE_UPDATE
}
