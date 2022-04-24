package com.tileman.multiplayer.shared;

public class ValueHolder<T> {
    public T value;

    public ValueHolder(T defaultValue) {
        this.value = defaultValue;
    }

}
