package com.example.webshop.exception;

import java.util.Map;

public class ProductException extends RuntimeException {

    private final ProductErrorCode errorCode;
    private final Map<String, Object> params;

    public ProductException(ProductErrorCode errorCode, Map<String, Object> params, String message) {
        super(message);
        this.errorCode = errorCode;
        this.params = params != null ? params : Map.of();
    }

    public ProductException(ProductErrorCode errorCode, String message) {
        this(errorCode, Map.of(), message);
    }

    public ProductException(ProductErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.params = Map.of();
    }

    public ProductErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
