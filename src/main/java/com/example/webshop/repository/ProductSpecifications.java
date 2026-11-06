package com.example.webshop.repository;

import com.example.webshop.domain.Product;
import com.example.webshop.domain.ProductType;
import com.example.webshop.dto.ProductFilter;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {

    private static final char ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> from(ProductFilter filter) {
        if (filter == null) {
            return Specification.unrestricted();
        }
        List<Specification<Product>> specs = new ArrayList<>();
        addIfPresent(specs, textContains(filter.query()));
        addIfPresent(specs, nameContains(filter.name()));
        addIfPresent(specs, hasProductType(filter.productType()));
        addIfPresent(specs, priceAtLeast(filter.minPrice()));
        addIfPresent(specs, priceAtMost(filter.maxPrice()));
        addIfPresent(specs, isActive(filter.active()));
        addIfPresent(specs, createdFrom(filter.createdFrom()));
        addIfPresent(specs, createdTo(filter.createdTo()));
        return Specification.allOf(specs);
    }

    public static Specification<Product> textContains(String text) {
        if (isBlank(text)) {
            return null;
        }
        String pattern = likePattern(text);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE));
    }

    public static Specification<Product> nameContains(String name) {
        if (isBlank(name)) {
            return null;
        }
        String pattern = likePattern(name);
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, ESCAPE);
    }

    public static Specification<Product> hasProductType(ProductType productType) {
        return productType == null ? null
                : (root, query, cb) -> cb.equal(root.get("productType"), productType);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return min == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        return max == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> isActive(Boolean active) {
        return active == null ? null
                : (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Product> createdFrom(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Product> createdTo(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    private static void addIfPresent(List<Specification<Product>> specs, Specification<Product> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String likePattern(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
