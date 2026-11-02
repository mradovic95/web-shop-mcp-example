package com.example.webshop.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
