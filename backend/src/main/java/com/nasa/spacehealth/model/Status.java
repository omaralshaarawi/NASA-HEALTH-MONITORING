package com.nasa.spacehealth.model;

/** contract.ts Status. The ordinal is the engine's numeric level (0 GREEN, 1 YELLOW, 2 RED). */
public enum Status {
    GREEN, YELLOW, RED;

    public static Status of(int level) {
        return values()[level];
    }
}
