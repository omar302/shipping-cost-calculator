package com.example.shipping.controller;

import com.example.shipping.service.InvalidParcelWeightException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class ShippingExceptionHandler {

    @ExceptionHandler(InvalidParcelWeightException.class)
    public ProblemDetail handleInvalidParcelWeight(InvalidParcelWeightException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid parcel weight");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, explain(exception));
        problem.setTitle("Invalid shipping request");
        return problem;
    }

    // A wrong-typed value can name the field that broke; malformed JSON has no single
    // field to blame, so it keeps the generic explanation.
    private String explain(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof InvalidFormatException invalidFormat) {
            return "Parcel weight must be a number, but was \"%s\"".formatted(invalidFormat.getValue());
        }
        return "Request body could not be read";
    }
}
