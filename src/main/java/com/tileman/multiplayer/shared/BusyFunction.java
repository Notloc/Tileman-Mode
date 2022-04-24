package com.tileman.multiplayer.shared;

public interface BusyFunction {
    Status run() throws UnexpectedPacketTypeException;

    enum Status {
        CONTINUE,
        FINISHED
    }
}
