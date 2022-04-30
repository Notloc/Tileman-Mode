package com.tileman.multiplayer;

public class UnexpectedPacketTypeException extends Exception {
    private String message;

    public UnexpectedPacketTypeException(String message) {
        super(message);
    }
}
