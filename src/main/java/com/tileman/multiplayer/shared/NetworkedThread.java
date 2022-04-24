package com.tileman.multiplayer.shared;

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

    protected static void assertPacketType(TilemanPacket packet, TilemanPacketType expectedType) throws UnexpectedPacketTypeException {
        if (packet.packetType != expectedType) {
            throw new UnexpectedPacketTypeException(String.format("Unexpected packet type. Expected %s but received %s.", expectedType, packet.packetType));
        }
    }

    public void executeInBusyLoop(BusyFunction function) throws InterruptedException, UnexpectedPacketTypeException {
        executeInBusyLoop(function, 50);
    }

    public void executeInBusyLoop(BusyFunction function, long sleepMs) throws InterruptedException, UnexpectedPacketTypeException {
        while (!isShutdown()) {
            if (function.run() == BusyFunction.Status.CONTINUE) {
                return;
            }
            sleep(sleepMs);
        }
    }
}

