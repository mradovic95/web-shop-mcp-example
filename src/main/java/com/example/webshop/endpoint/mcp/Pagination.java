package com.example.webshop.endpoint.mcp;

import org.springframework.ai.mcp.annotation.McpToolParam;

public record Pagination(
        @McpToolParam(description = "Zero-based page index, default 0", required = false) Integer page,
        @McpToolParam(description = "Page size 1-100, default 20", required = false) Integer size
) {
}
