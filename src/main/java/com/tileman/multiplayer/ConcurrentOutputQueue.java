package com.tileman.multiplayer;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;

public class ConcurrentOutputQueue<T> {
    private final Queue<T> outputQueue = new LinkedList<T>();
    private final ObjectOutputStream objectOutputStream;

    private Lock l;

    public ConcurrentOutputQueue(OutputStream outputStream) throws IOException {
        this.objectOutputStream = new ObjectOutputStream(outputStream);
    }

    public void queueData(T... data) {
        l.lock();
        Collections.addAll(outputQueue, data);
        l.unlock();
    }

    public void flush() throws IOException {
        l.lock();
        while (!outputQueue.isEmpty()) {
            objectOutputStream.writeObject(outputQueue.remove());
        }
        objectOutputStream.flush();
        l.unlock();
    }
}
