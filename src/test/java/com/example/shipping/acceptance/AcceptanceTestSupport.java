package com.example.shipping.acceptance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Every endpoint needs a configured key (api-security.specs.md), so every acceptance
// class needs one. The keys are supplied here rather than taken from
// application.properties, so the suite never depends on what a deployment configures.
// Declaring one property set for all of them also keeps every acceptance class on a
// single cached Spring context.
@SpringBootTest(properties = {
        "shipping.api-keys.test-user-key=USER",
        "shipping.api-keys.test-admin-key=ADMIN",
})
@AutoConfigureMockMvc
abstract class AcceptanceTestSupport {

    protected static final String API_KEY_HEADER = "X-API-Key";
    protected static final String USER_KEY = "test-user-key";
    protected static final String ADMIN_KEY = "test-admin-key";

    @Autowired
    protected MockMvcTester mvc;
}
