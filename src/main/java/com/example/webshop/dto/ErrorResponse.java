package com.example.webshop.dto;

import java.util.Map;

public record ErrorResponse(
        String message,
        String errorCode,
        Map<String, Object> details) {
}
