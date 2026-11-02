package com.example.webshop.mapper;

import com.example.webshop.domain.Product;
import com.example.webshop.dto.CreateProductRequest;
import com.example.webshop.dto.ProductResponse;
import com.example.webshop.dto.UpdateProductRequest;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toEntity(CreateProductRequest request) {
        return Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .active(request.active())
                .build();
    }

    public Product toUpdatedEntity(Product product, UpdateProductRequest request) {
        return product.toBuilder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .active(request.active())
                .build();
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
