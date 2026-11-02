package com.example.webshop.endpoint.mcp;

import com.example.webshop.dto.CreateProductRequest;
import com.example.webshop.dto.PageResponse;
import com.example.webshop.dto.ProductFilter;
import com.example.webshop.dto.ProductResponse;
import com.example.webshop.dto.UpdateProductRequest;
import com.example.webshop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ProductMcpTools {

    private final ProductService productService;

    @McpTool(name = "list_products",
            description = """
                    Search the web shop catalog. All filters are optional and combined with AND. \
                    Results are paged: the response includes content plus page, size, totalElements and totalPages; \
                    request further pages with the page parameter. Prices are in EUR.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public PageResponse<ProductResponse> listProducts(
            @McpToolParam(description = "Free text, case-insensitive, matched against name or description", required = false) String query,
            @McpToolParam(description = "Case-insensitive substring of the product name", required = false) String name,
            @McpToolParam(description = "Minimum price in EUR (inclusive)", required = false) BigDecimal minPrice,
            @McpToolParam(description = "Maximum price in EUR (inclusive)", required = false) BigDecimal maxPrice,
            @McpToolParam(description = "true = only active products, false = only inactive, omit for both", required = false) Boolean active,
            @McpToolParam(description = "Zero-based page index, default 0", required = false) Integer page,
            @McpToolParam(description = "Page size, default 20, max 100", required = false) Integer size,
            @McpToolParam(description = "Sort property: id, name, price, createdAt or updatedAt. Default id", required = false) String sortBy,
            @McpToolParam(description = "ASC or DESC, default ASC", required = false) String sortDirection) {
        ProductFilter filter = new ProductFilter(query, name, minPrice, maxPrice, active, null, null);
        Sort sort = Sort.by(
                sortDirection == null ? Sort.Direction.ASC : Sort.Direction.fromString(sortDirection),
                sortBy == null || sortBy.isBlank() ? "id" : sortBy);
        PageRequest pageable = PageRequest.of(
                page == null ? 0 : page,
                size == null ? 20 : size,
                sort);
        return McpToolErrors.translating(() -> productService.findAll(filter, pageable));
    }

    @McpTool(name = "get_product",
            description = "Get a single product by its numeric id.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public ProductResponse getProduct(
            @McpToolParam(description = "Product id") Long id) {
        return McpToolErrors.translating(() -> productService.findById(id));
    }

    @McpTool(name = "create_product",
            description = "Create a new product.")
    public ProductResponse createProduct(
            @McpToolParam(description = "Product name") String name,
            @McpToolParam(description = "Product description", required = false) String description,
            @McpToolParam(description = "Price in EUR with up to 2 decimals") BigDecimal price,
            @McpToolParam(description = "Whether the product is visible/sellable in the shop") boolean active) {
        return McpToolErrors.translating(() -> productService.create(
                new CreateProductRequest(name, description, price, active)));
    }

    @McpTool(name = "update_product",
            description = "Replace all fields of an existing product identified by id. Call get_product first to keep unchanged values.",
            annotations = @McpTool.McpAnnotations(idempotentHint = true))
    public ProductResponse updateProduct(
            @McpToolParam(description = "Id of the product to update") Long id,
            @McpToolParam(description = "Product name") String name,
            @McpToolParam(description = "Product description", required = false) String description,
            @McpToolParam(description = "Price in EUR with up to 2 decimals") BigDecimal price,
            @McpToolParam(description = "Whether the product is visible/sellable in the shop") boolean active) {
        return McpToolErrors.translating(() -> productService.update(id,
                new UpdateProductRequest(name, description, price, active)));
    }

    @McpTool(name = "delete_product",
            description = "Permanently delete a product by id.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    public String deleteProduct(
            @McpToolParam(description = "Id of the product to delete") Long id) {
        return McpToolErrors.translating(() -> {
            productService.delete(id);
            return "Product " + id + " deleted";
        });
    }
}
