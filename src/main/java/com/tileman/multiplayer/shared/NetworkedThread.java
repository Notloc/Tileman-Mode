package com.tileman.multiplayer.shared;

public abstract class NetworkedThread extends Thread implements IShutdown {

    protected boolean isShuttingDown = false;
    public boolean isShutdown() { return isShuttingDown; }

    public static void assertPacketType(TilemanPacket packet, TilemanPacketType expectedType) throws UnexpectedPacketTypeException {
        if (packet.packetType != expectedType) {
            throw new UnexpectedPacketTypeException(String.format("Unexpected packet type. Expected %s but received %s.", expectedType, packet.packetType));
        }
    }

    public void executeInBusyLoop(BusyFunction function) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        executeInBusyLoop(function, 25, 25000);
    }

    public void executeInBusyLoop(BusyFunction function, long sleepMs) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        executeInBusyLoop(function, sleepMs, 25000);
    }

    public void executeInBusyLoop(BusyFunction function, long sleepMs, long timeout) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        double time = System.currentTimeMillis();
        while (!isShutdown()) {
            if (function.run() == BusyFunction.Status.CONTINUE) {
                return;
            }
            sleep(sleepMs);

            if (time + timeout < System.currentTimeMillis()) {
                throw new NetworkTimeoutException();
            }
        }
        throw new NetworkShutdownException();
    }
}

