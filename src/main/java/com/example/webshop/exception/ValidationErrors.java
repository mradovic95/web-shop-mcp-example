package com.example.webshop.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.data.core.PropertyReferenceException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ValidationErrors {

    private ValidationErrors() {
    }

    public static ProductException from(ConstraintViolationException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            details.merge(leafName(violation.getPropertyPath()), violation.getMessage(),
                    (first, second) -> first + "; " + second);
        }
        return new ProductException(ProductErrorCode.VALIDATION_ERROR, details, "Validation failed");
    }

    public static ProductException from(PropertyReferenceException ex) {
        return new ProductException(ProductErrorCode.VALIDATION_ERROR,
                Map.of("sort", "Unknown property '" + ex.getPropertyName() + "'"),
                "Validation failed");
    }

    private static String leafName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name != null ? name : path.toString();
    }
}
