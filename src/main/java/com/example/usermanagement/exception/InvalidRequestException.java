package com.example.usermanagement.exception;

/**
 * Client sent something the API deliberately does not support (e.g. sorting by an unindexed column).
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
