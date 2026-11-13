package com.nasa.spacehealth.controller;

import org.springframework.http.HttpStatus;

/** Thrown anywhere to answer with a specific HTTP status and a plain message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
