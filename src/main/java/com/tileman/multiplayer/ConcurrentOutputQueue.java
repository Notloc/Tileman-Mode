package com.tileman.multiplayer;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentOutputQueue<T> {
    private final Queue<T> outputQueue = new LinkedList<T>();
    private final ObjectOutputStream objectOutputStream;

    private final Lock l = new ReentrantLock();

    public ConcurrentOutputQueue(OutputStream outputStream) throws IOException {
        this.objectOutputStream = new ObjectOutputStream(outputStream);
    }

    public void queueData(T... data) {
        l.lock();
        try {
            Collections.addAll(outputQueue, data);
        } finally {
            l.unlock();
        }
    }

    public void flush() throws IOException {
        if (outputQueue.isEmpty()) {
            return;
        }

        l.lock();
        try {
            while (!outputQueue.isEmpty()) {
                objectOutputStream.writeObject(outputQueue.remove());
            }
            objectOutputStream.flush();
        } finally {
            l.unlock();
        }
    }
}
