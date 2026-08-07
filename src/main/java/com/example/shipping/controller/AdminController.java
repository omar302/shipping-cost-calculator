package com.example.shipping.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    // The response is deliberately empty. api-security.specs.md pins only who may
    // reach this endpoint — ADMIN yes, USER no — and states that its content needs a
    // spec of its own. Do not invent a rates response shape here.
    @GetMapping("/rates")
    public ResponseEntity<Void> rates() {
        return ResponseEntity.ok().build();
    }
}
