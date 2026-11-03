package com.example.webshop.inventory;

import java.math.BigDecimal;

import com.example.webshop.domain.Product;

public final class ProductTestInventory {

    private ProductTestInventory() {
    }

    public static Product product(String name, String price, boolean active) {
        return Product.builder()
                .name(name)
                .description(name + " description")
                .price(new BigDecimal(price))
                .active(active)
                .build();
    }
}
