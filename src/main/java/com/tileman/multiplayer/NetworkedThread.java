package com.tileman.multiplayer;

import lombok.extern.slf4j.Slf4j;
import java.io.Closeable;
import java.io.IOException;

public abstract class NetworkedThread extends Thread implements IShutdown {

    protected boolean isShuttingDown = false;
    public boolean isShutdown() { return isShuttingDown; }

    protected static void validateEndOfDataPacket(Object data) throws UnexpectedPacketTypeException {
        if (data instanceof TilemanPacket) {
            TilemanPacket packet = (TilemanPacket) data;
            if (packet.packetType != TilemanPacketType.END_OF_DATA) {
                throw new UnexpectedPacketTypeException("Expected an END_OF_DATA packet. Received " + packet.packetType + " packet.");
            }
            return;
        }
        throw new UnexpectedPacketTypeException("Expected an END_OF_DATA packet. Received object: " + data.getClass().getSimpleName());
    }
}
