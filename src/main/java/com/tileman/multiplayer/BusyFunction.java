package com.tileman.multiplayer;

public interface BusyFunction {
    Status run() throws UnexpectedNetworkObjectException;

    enum Status {
        CONTINUE,
        FINISHED
    }
}
