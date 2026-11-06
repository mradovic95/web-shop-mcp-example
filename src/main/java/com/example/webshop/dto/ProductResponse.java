package com.example.webshop.dto;

import com.example.webshop.domain.ProductType;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        ProductType productType,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
