package com.example.shipping.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

// The caller is authenticated but not permitted, so this never reaches the
// authentication entry point. The key is known here, and still never named.
public class ApiKeyAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter problemDetails;

    ApiKeyAccessDeniedHandler(ProblemDetailWriter problemDetails) {
        this.problemDetails = problemDetails;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        problemDetails.write(response, HttpStatus.FORBIDDEN, "Forbidden",
                "API key is not permitted to use this endpoint");
    }
}
