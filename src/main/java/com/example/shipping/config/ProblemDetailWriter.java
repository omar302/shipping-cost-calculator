package com.example.shipping.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectMapper;

// Refusals raised inside the security filter chain never reach ShippingExceptionHandler,
// so they build their own problem detail. Shared by both refusal paths so a refused
// request looks the same whether it was unauthenticated or unauthorised.
//
// This is the filter-chain counterpart to ShippingExceptionHandler.badRequest. The two
// deliberately stay separate — merging would couple the controller layer to the filter
// chain — so a change to the shape here needs the same change there.
class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
