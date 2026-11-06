package com.example.webshop.endpoint.mcp;

import com.example.webshop.domain.ProductType;
import com.example.webshop.dto.ProductFilter;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductListFilter(
        @McpToolParam(description = "Free text, case-insensitive, matched against name or description", required = false) String query,
        @McpToolParam(description = "Case-insensitive substring of the product name", required = false) String name,
        @McpToolParam(description = "Only products of this type", required = false) ProductType productType,
        @McpToolParam(description = "Minimum price in EUR (inclusive)", required = false) BigDecimal minPrice,
        @McpToolParam(description = "Maximum price in EUR (inclusive)", required = false) BigDecimal maxPrice,
        @McpToolParam(description = "true = only active products, false = only inactive, omit for both", required = false) Boolean active,
        @McpToolParam(description = "Created at or after this ISO-8601 instant, e.g. 2026-01-01T00:00:00Z", required = false) Instant createdFrom,
        @McpToolParam(description = "Created at or before this ISO-8601 instant, e.g. 2026-12-31T23:59:59Z", required = false) Instant createdTo
) {

    public ProductFilter toProductFilter() {
        return new ProductFilter(query, name, productType, minPrice, maxPrice, active, createdFrom, createdTo);
    }
}
