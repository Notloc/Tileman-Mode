package com.tileman.multiplayer;

public interface BusyFunction {
    Status run() throws UnexpectedPacketTypeException;

    enum Status {
        CONTINUE,
        FINISHED
    }
}
