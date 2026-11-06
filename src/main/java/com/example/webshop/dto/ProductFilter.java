package com.example.webshop.dto;

import com.example.webshop.domain.ProductType;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductFilter(
        String query,
        String name,
        ProductType productType,
        @PositiveOrZero BigDecimal minPrice,
        @PositiveOrZero BigDecimal maxPrice,
        Boolean active,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo
) {

    public static ProductFilter empty() {
        return new ProductFilter(null, null, null, null, null, null, null, null);
    }
}
