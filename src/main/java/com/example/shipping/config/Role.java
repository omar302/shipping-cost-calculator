package com.example.shipping.config;

// A closed set, so the properties bind to it directly: a key configured with anything
// else fails at startup rather than authenticating with an authority that matches
// nothing and silently losing the access it was meant to carry.
public enum Role {

    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
