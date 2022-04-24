package com.tileman.multiplayer.shared;

import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObjectInputStreamBufferThread extends Thread {

    private InputStream rawInputStream;
    private boolean running;

    final ConcurrentLinkedQueue<Object> queuedInputData = new ConcurrentLinkedQueue<>();

    public ObjectInputStreamBufferThread(InputStream rawInputStream) {
        this.rawInputStream = rawInputStream;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            ObjectInputStream input = new ObjectInputStream(rawInputStream);
            while (running) {
                Object obj = input.readObject();
                queuedInputData.add(obj);
            }
            input.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public TilemanPacket waitForNextPacket(NetworkedThread host) throws NetworkShutdownException, UnexpectedPacketTypeException, InterruptedException, NetworkTimeoutException {
        return waitForNextObject(host);
    }

    public <T> T waitForNextObject(NetworkedThread host) throws NetworkShutdownException, InterruptedException, NetworkTimeoutException, UnexpectedPacketTypeException {
        ValueHolder<Object> objectHolder = new ValueHolder<>(null);
        host.executeInBusyLoop(() -> {
           Object obj = tryGetNextObject();
            if (obj != null) {
                objectHolder.value = obj;
                return BusyFunction.Status.FINISHED;
            } else {
                return BusyFunction.Status.CONTINUE;
            }
        });
        return (T)objectHolder.value;
    }

    public TilemanPacket tryGetNextPacket() {
        return (TilemanPacket)tryGetNextObject();
    }

    public Object tryGetNextObject() {
        if (queuedInputData.peek() != null) {
            return queuedInputData.remove();
        }
        return null;
    }

    /**
     * Stops the BufferThread
     * Takes up to 0.5s if blocked by I/O
     */
    public void forceStop() {
        running = false;

        Thread target = this;
        Runnable stopIfNeeded = () -> {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (target.isAlive()) {
                target.stop();
            }
        };

        Thread thread = new Thread(stopIfNeeded);
        thread.start();
    }
}
