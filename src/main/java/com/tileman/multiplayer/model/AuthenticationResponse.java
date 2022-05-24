package com.tileman.multiplayer.model;

import lombok.Value;

import java.io.Serializable;

@Value
public class AuthenticationResponse implements Serializable {
    public final boolean authenticationSuccessful;
}
