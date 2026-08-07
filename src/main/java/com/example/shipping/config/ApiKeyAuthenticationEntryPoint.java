package com.example.shipping.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class ApiKeyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter problemDetails;

    ApiKeyAuthenticationEntryPoint(ProblemDetailWriter problemDetails) {
        this.problemDetails = problemDetails;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        problemDetails.write(response, HttpStatus.UNAUTHORIZED, "Unauthorised", explain(request));
    }

    // Naming the rule but never the key: a rejected key is usually a real credential,
    // mistyped or aimed at the wrong environment, and echoing it would write it into
    // the response, proxy logs and error trackers. A header sent but empty names no
    // key at all, so it is unrecognised rather than missing.
    private String explain(HttpServletRequest request) {
        return request.getHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER) == null
                ? "API key is required"
                : "API key is not recognised";
    }
}
