package com.example.webshop.dto;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductFilter(
        String query,
        String name,
        @PositiveOrZero BigDecimal minPrice,
        @PositiveOrZero BigDecimal maxPrice,
        Boolean active,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo
) {

    public static ProductFilter empty() {
        return new ProductFilter(null, null, null, null, null, null, null);
    }
}
