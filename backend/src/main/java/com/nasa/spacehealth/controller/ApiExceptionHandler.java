package com.nasa.spacehealth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every error leaves as {"status": 404, "error": "Not Found", "message": "..."}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.status(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "Parameter '" + e.getName() + "' has an invalid value: " + e.getValue());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException e) {
        return body(HttpStatus.BAD_REQUEST, "Missing parameter '" + e.getParameterName() + "'");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("status", status.value());
        b.put("error", status.getReasonPhrase());
        b.put("message", message);
        return ResponseEntity.status(status).body(b);
    }
}
