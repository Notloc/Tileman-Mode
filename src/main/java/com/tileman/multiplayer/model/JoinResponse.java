package com.tileman.multiplayer.model;

import lombok.Value;

import java.io.Serializable;

@Value
public class JoinResponse implements Serializable {
    final Long accountHash;
}
