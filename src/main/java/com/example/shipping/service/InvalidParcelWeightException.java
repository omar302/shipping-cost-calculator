package com.example.shipping.service;

public class InvalidParcelWeightException extends RuntimeException {

    public InvalidParcelWeightException(String message) {
        super(message);
    }
}
