package com.example.webshop.endpoint.mcp;

import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Sort;

public record SortOrder(
        @McpToolParam(description = "Property to sort by") String property,
        @McpToolParam(description = "ASC or DESC, default ASC", required = false) Sort.Direction direction
) {

    public Sort.Order toOrder() {
        return new Sort.Order(direction == null ? Sort.Direction.ASC : direction, property);
    }
}
