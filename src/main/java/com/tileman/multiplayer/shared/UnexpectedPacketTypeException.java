package com.tileman.multiplayer.shared;

public class UnexpectedPacketTypeException extends Exception {
    private String message;

    public UnexpectedPacketTypeException(String message) {
        super(message);
    }
}
