package com.example.shipping.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// The documentation is public precisely so an integrator can read it before being
// issued a key, which only helps if it says a key is needed and which header carries
// it. Declared once and required globally; the public documentation paths are exempted
// in SecurityConfig, not here.
@Configuration
@SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key")
@OpenAPIDefinition(security = @SecurityRequirement(name = "apiKey"))
public class OpenApiConfig {
}
