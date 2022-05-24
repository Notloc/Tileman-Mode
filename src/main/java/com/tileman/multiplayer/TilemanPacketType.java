package com.tileman.multiplayer;

// TODO: convert all of these into response and request objects
public enum TilemanPacketType {
    AUTHENTICATION,
    AUTHENTICATION_RESPONSE,

    GROUP_PROFILE_REQUEST,
    GROUP_PROFILE_RESPONSE,

    REGION_DATA_REQUEST,
    REGION_DATA_RESPONSE,
    TILE_UPDATE,
    END_OF_DATA,

    TILE_SYNC_REQUEST,

    JOIN_EVENT,
    LEAVE_EVENT,

    REGION_HASH_REPORT,

}