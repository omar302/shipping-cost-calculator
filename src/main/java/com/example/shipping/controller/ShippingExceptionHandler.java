package com.example.shipping.controller;

import com.example.shipping.service.InvalidDestinationZoneException;
import com.example.shipping.service.InvalidOrderTotalException;
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
        return badRequest("Invalid parcel weight", exception.getMessage());
    }

    @ExceptionHandler(InvalidDestinationZoneException.class)
    public ProblemDetail handleInvalidDestinationZone(InvalidDestinationZoneException exception) {
        return badRequest("Invalid destination zone", exception.getMessage());
    }

    @ExceptionHandler(InvalidOrderTotalException.class)
    public ProblemDetail handleInvalidOrderTotal(InvalidOrderTotalException exception) {
        return badRequest("Invalid order total", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return badRequest("Invalid shipping request", explain(exception));
    }

    // Every rejection is the same RFC 9457 shape: a 400 titled with the rule that was
    // broken, and a detail naming the value that broke it.
    private ProblemDetail badRequest(String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(title);
        return problem;
    }

    // A wrong-typed value can name the field that broke; malformed JSON has no single
    // field to blame, so it keeps the generic explanation.
    private String explain(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof InvalidFormatException invalidFormat) {
            String field = numericFieldLabel(invalidFormat);
            if (field != null) {
                return "%s must be a number, but was \"%s\"".formatted(field, invalidFormat.getValue());
            }
        }
        return "Request body could not be read";
    }

    // The request carries more than one numeric field, so a value the body reader cannot
    // parse must name the field it came from rather than a fixed one. Anything else is
    // not a field we can describe, so it falls back to the generic explanation.
    private String numericFieldLabel(InvalidFormatException invalidFormat) {
        var path = invalidFormat.getPath();
        if (path.isEmpty()) {
            return null;
        }
        return switch (path.get(path.size() - 1).getPropertyName()) {
            case "weightKg" -> "Parcel weight";
            case "orderTotal" -> "Order total";
            case null, default -> null;
        };
    }
}
