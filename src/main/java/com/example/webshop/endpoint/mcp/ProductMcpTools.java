package com.example.webshop.endpoint.mcp;

import com.example.webshop.domain.ProductType;
import com.example.webshop.dto.CreateProductRequest;
import com.example.webshop.dto.PageResponse;
import com.example.webshop.dto.ProductFilter;
import com.example.webshop.dto.ProductResponse;
import com.example.webshop.dto.UpdateProductRequest;
import com.example.webshop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductMcpTools {

    private static final List<String> SORTABLE = List.of("id", "name", "price", "productType", "active", "createdAt", "updatedAt");

    private final ProductService productService;
    private final McpSortHelper sortHelper;
    private final McpPageHelper pageHelper;

    @McpTool(name = "list_products",
            title = "List products",
            description = """
                    Search the web shop catalog. Takes three optional objects: filter (all fields optional, \
                    combined with AND), sort (list of {property, direction} in priority order, so each field can \
                    have its own direction; default id ASC) and pagination ({page, size}, default page 0, size 20, \
                    max size 100). The response includes content plus page, size, totalElements, totalPages, \
                    first and last; request further pages with pagination.page. Prices are in EUR.""",
            annotations = @McpTool.McpAnnotations(title = "List products", readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public PageResponse<ProductResponse> listProducts(
            @McpToolParam(description = "Filters, all optional and combined with AND", required = false) ProductListFilter filter,
            @McpToolParam(description = "Sort orders in priority order, each with its own direction. "
                    + "property: one of id, name, price, productType, active, createdAt, updatedAt. Default id ASC", required = false) List<SortOrder> sort,
            @McpToolParam(description = "Zero-based page and page size (1-100). Default page 0, size 20", required = false) Pagination pagination) {
        return McpToolErrors.translating(() -> productService.findAll(
                filter == null ? ProductFilter.empty() : filter.toProductFilter(),
                pageHelper.pageable(pagination, sortHelper.sort(sort, SORTABLE, "id"))));
    }

    @McpTool(name = "get_product",
            title = "Get product",
            description = "Get a single product by its numeric id.",
            annotations = @McpTool.McpAnnotations(title = "Get product", readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public ProductResponse getProduct(
            @McpToolParam(description = "Product id") Long id) {
        return McpToolErrors.translating(() -> productService.findById(id));
    }

    @McpTool(name = "create_product",
            title = "Create product",
            description = "Create a new product.",
            annotations = @McpTool.McpAnnotations(title = "Create product", destructiveHint = false, openWorldHint = false))
    public ProductResponse createProduct(
            @McpToolParam(description = "Product name") String name,
            @McpToolParam(description = "Product description", required = false) String description,
            @McpToolParam(description = "Price in EUR with up to 2 decimals") BigDecimal price,
            @McpToolParam(description = "Product category") ProductType productType,
            @McpToolParam(description = "Whether the product is visible/sellable in the shop") boolean active) {
        return McpToolErrors.translating(() -> productService.create(
                new CreateProductRequest(name, description, price, productType, active)));
    }

    @McpTool(name = "update_product",
            title = "Update product",
            description = "Replace all fields of an existing product identified by id. Call get_product first to keep unchanged values.",
            annotations = @McpTool.McpAnnotations(title = "Update product", destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public ProductResponse updateProduct(
            @McpToolParam(description = "Id of the product to update") Long id,
            @McpToolParam(description = "Product name") String name,
            @McpToolParam(description = "Product description", required = false) String description,
            @McpToolParam(description = "Price in EUR with up to 2 decimals") BigDecimal price,
            @McpToolParam(description = "Product category") ProductType productType,
            @McpToolParam(description = "Whether the product is visible/sellable in the shop") boolean active) {
        return McpToolErrors.translating(() -> productService.update(id,
                new UpdateProductRequest(name, description, price, productType, active)));
    }

    @McpTool(name = "delete_product",
            title = "Delete product",
            description = "Permanently delete a product by id.",
            annotations = @McpTool.McpAnnotations(title = "Delete product", destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public String deleteProduct(
            @McpToolParam(description = "Id of the product to delete") Long id) {
        return McpToolErrors.translating(() -> {
            productService.delete(id);
            return "Product " + id + " deleted";
        });
    }
}
