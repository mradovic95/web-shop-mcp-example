package com.example.webshop.endpoint.rest;

import com.example.webshop.dto.ErrorResponse;
import com.example.webshop.exception.ProductErrorCode;
import com.example.webshop.exception.ProductException;
import com.example.webshop.exception.ValidationErrors;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.example.webshop.endpoint.rest")
@Slf4j
public class ShopExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Map<ProductErrorCode, HttpStatus> STATUS_MAP = Map.ofEntries(
            Map.entry(ProductErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST),
            Map.entry(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND)
    );

    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ErrorResponse> handleProductException(ProductException ex) {
        log.warn("Product exception: {} [{}]", ex.getMessage(), ex.getErrorCode());
        HttpStatus status = STATUS_MAP.getOrDefault(ex.getErrorCode(), HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse response = new ErrorResponse(
                ex.getMessage(),
                ex.getErrorCode().name(),
                ex.getParams()
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return handleProductException(ValidationErrors.from(ex));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReference(PropertyReferenceException ex) {
        return handleProductException(ValidationErrors.from(ex));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected exception", ex);
        ErrorResponse response = new ErrorResponse(
                "Internal server error",
                "INTERNAL_ERROR",
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String message = error.isBindingFailure() ? "invalid value" : error.getDefaultMessage();
            details.merge(error.getField(), message, (first, second) -> first + "; " + second);
        }
        ErrorResponse response = new ErrorResponse("Validation failed", ProductErrorCode.VALIDATION_ERROR.name(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : ex.getMessage();
        String errorCode = statusCode.value() == HttpStatus.BAD_REQUEST.value()
                ? ProductErrorCode.VALIDATION_ERROR.name()
                : errorCodeOf(statusCode);
        ErrorResponse response = new ErrorResponse(message, errorCode, Map.of());
        return ResponseEntity.status(statusCode).headers(headers).body(response);
    }

    private static String errorCodeOf(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }
}
