package com.example.webshop.endpoint.mcp;

import com.example.webshop.exception.ProductErrorCode;
import com.example.webshop.exception.ProductException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpSortHelper {

    public Sort sort(List<SortOrder> sort, List<String> sortable, String tieBreaker) {
        List<SortOrder> requested = sort == null ? List.of() : sort;
        validate(requested, sortable);

        Sort result = Sort.by(requested.stream().map(SortOrder::toOrder).toList());
        return result.getOrderFor(tieBreaker) == null ? result.and(Sort.by(tieBreaker)) : result;
    }

    private void validate(List<SortOrder> requested, List<String> sortable) {
        Map<String, Object> errors = new LinkedHashMap<>();
        for (int i = 0; i < requested.size(); i++) {
            SortOrder order = requested.get(i);
            if (order == null || order.property() == null || !sortable.contains(order.property())) {
                errors.put("sort[" + i + "].property", "must be one of " + sortable);
            }
        }
        if (!errors.isEmpty()) {
            throw new ProductException(ProductErrorCode.VALIDATION_ERROR, errors, "Validation failed");
        }
    }
}
