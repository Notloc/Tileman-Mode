package com.tileman.multiplayer;

public class UnexpectedNetworkObjectException extends Exception {
    private String message;

    public UnexpectedNetworkObjectException(Object obj) {
        super("Unexpected network object encountered: " + obj.getClass().getSimpleName());
    }
}
