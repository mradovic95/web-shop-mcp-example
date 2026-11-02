package com.example.webshop.endpoint.mcp;

import com.example.webshop.exception.ProductErrorCode;
import com.example.webshop.exception.ProductException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class McpPageHelper {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Pagination DEFAULTS = new Pagination(DEFAULT_PAGE, DEFAULT_SIZE);

    public Pageable pageable(Pagination pagination, Sort sort) {
        Pagination requested = Objects.requireNonNullElse(pagination, DEFAULTS);
        int page = Objects.requireNonNullElse(requested.page(), DEFAULT_PAGE);
        int size = Objects.requireNonNullElse(requested.size(), DEFAULT_SIZE);
        validate(page, size);
        return PageRequest.of(page, size, sort);
    }

    private void validate(int page, int size) {
        Map<String, Object> errors = new LinkedHashMap<>();
        if (page < 0) {
            errors.put("pagination.page", "must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.put("pagination.size", "must be between 1 and " + MAX_SIZE);
        }
        if (!errors.isEmpty()) {
            throw new ProductException(ProductErrorCode.VALIDATION_ERROR, errors, "Validation failed");
        }
    }
}
