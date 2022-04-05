package com.tileman.multiplayer;

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

    public TilemanPacket getNextPacket() {
        if (queuedInputData.peek() != null) {
            return (TilemanPacket)queuedInputData.remove();
        }
        return null;
    }

    public Object getNextObject() {
        if (queuedInputData.peek() != null) {
            return queuedInputData.remove();
        }
        return null;
    }

    public Object waitForData(NetworkedThread thread) throws ShutdownException, InterruptedException {
        Object data = null;
        while (!thread.isShutdown()) {
            data = getNextObject();
            if (data != null) {
                break;
            } else {
                sleep(10);
            }
        }

        if (thread.isShutdown()) {
            throw new ShutdownException();
        }

        return data;
    }

    /**
     * Stops the BufferThread
     * Takes up to 0.5s if blocked by I/O
     */
    public void teardown() {
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
