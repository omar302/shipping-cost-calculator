package com.example.shipping.service;

public class InvalidDestinationZoneException extends RuntimeException {

    public InvalidDestinationZoneException(String message) {
        super(message);
    }
}
