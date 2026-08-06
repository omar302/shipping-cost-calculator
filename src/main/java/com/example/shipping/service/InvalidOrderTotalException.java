package com.example.shipping.service;

public class InvalidOrderTotalException extends RuntimeException {

    public InvalidOrderTotalException(String message) {
        super(message);
    }
}
