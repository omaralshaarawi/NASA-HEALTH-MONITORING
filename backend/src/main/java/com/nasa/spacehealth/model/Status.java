package com.nasa.spacehealth.model;

public enum Status {
    GREEN, YELLOW, RED;

    public static Status of(int level) {
        return values()[level];
    }
}
