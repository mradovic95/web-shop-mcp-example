package com.example.webshop.endpoint.mcp;

import static com.example.webshop.inventory.ProductTestInventory.product;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.webshop.domain.Product;
import com.example.webshop.dto.PageResponse;
import com.example.webshop.dto.ProductResponse;
import com.example.webshop.helper.McpTestHelper;
import com.example.webshop.repository.ProductRepository;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

public class ProductMcpToolsTest extends McpIntegrationTest {

    private static final TypeRef<PageResponse<ProductResponse>> PAGE = new TypeRef<>() {
    };

    private static final TypeRef<ProductResponse> PRODUCT = new TypeRef<>() {
    };

    @Autowired
    private McpTestHelper mcpTestHelper;

    @Autowired
    private ProductRepository productRepository;

    @Test
    public void testListProducts() {
        // GIVEN
        productRepository.save(product("Sticker", "5.00", true));
        productRepository.save(product("Mug", "20.00", true));
        Product hoodie = productRepository.save(product("Hoodie", "30.00", true));
        productRepository.save(product("Jacket", "40.00", false));
        Product shoes = productRepository.save(product("Shoes", "50.00", true));
        Map<String, Object> arguments = Map.of(
                "filter", Map.of("active", true, "minPrice", 10),
                "sort", List.of(Map.of("property", "price", "direction", "DESC")),
                "pagination", Map.of("page", 0, "size", 2));

        // WHEN
        PageResponse<ProductResponse> page = mcpTestHelper.read(mcpTestHelper.callTool("list_products", arguments), PAGE);

        // THEN
        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isFalse();

        // assert product one
        ProductResponse firstResult = page.content().get(0);
        assertThat(firstResult.id()).isEqualTo(shoes.getId());
        assertThat(firstResult.name()).isEqualTo(shoes.getName());
        assertThat(firstResult.description()).isEqualTo(shoes.getDescription());
        assertThat(firstResult.price()).isEqualByComparingTo(shoes.getPrice());
        assertThat(firstResult.active()).isEqualTo(shoes.isActive());
        assertThat(firstResult.createdAt()).isNotNull();
        assertThat(firstResult.updatedAt()).isNotNull();

        // assert product two
        ProductResponse secondResult = page.content().get(1);
        assertThat(secondResult.id()).isEqualTo(hoodie.getId());
        assertThat(secondResult.name()).isEqualTo(hoodie.getName());
        assertThat(secondResult.description()).isEqualTo(hoodie.getDescription());
        assertThat(secondResult.price()).isEqualByComparingTo(hoodie.getPrice());
        assertThat(secondResult.active()).isEqualTo(hoodie.isActive());
        assertThat(secondResult.createdAt()).isNotNull();
        assertThat(secondResult.updatedAt()).isNotNull();
    }

    @Test
    public void testListProductsWithoutArguments() {
        // GIVEN
        Product first = productRepository.save(product("Mug", "20.00", true));
        Product second = productRepository.save(product("Sticker", "5.00", false));
        Product third = productRepository.save(product("Hoodie", "30.00", true));

        // WHEN
        PageResponse<ProductResponse> page = mcpTestHelper.read(mcpTestHelper.callTool("list_products", Map.of()), PAGE);

        // THEN
        assertThat(page.content()).hasSize(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(3);

        // assert product one
        ProductResponse firstResult = page.content().get(0);
        assertThat(firstResult.id()).isEqualTo(first.getId());
        assertThat(firstResult.name()).isEqualTo(first.getName());
        assertThat(firstResult.description()).isEqualTo(first.getDescription());
        assertThat(firstResult.price()).isEqualByComparingTo(first.getPrice());
        assertThat(firstResult.active()).isEqualTo(first.isActive());
        assertThat(firstResult.createdAt()).isNotNull();
        assertThat(firstResult.updatedAt()).isNotNull();

        // assert product two
        ProductResponse secondResult = page.content().get(1);
        assertThat(secondResult.id()).isEqualTo(second.getId());
        assertThat(secondResult.name()).isEqualTo(second.getName());
        assertThat(secondResult.description()).isEqualTo(second.getDescription());
        assertThat(secondResult.price()).isEqualByComparingTo(second.getPrice());
        assertThat(secondResult.active()).isEqualTo(second.isActive());
        assertThat(secondResult.createdAt()).isNotNull();
        assertThat(secondResult.updatedAt()).isNotNull();

        // assert product three
        ProductResponse thirdResult = page.content().get(2);
        assertThat(thirdResult.id()).isEqualTo(third.getId());
        assertThat(thirdResult.name()).isEqualTo(third.getName());
        assertThat(thirdResult.description()).isEqualTo(third.getDescription());
        assertThat(thirdResult.price()).isEqualByComparingTo(third.getPrice());
        assertThat(thirdResult.active()).isEqualTo(third.isActive());
        assertThat(thirdResult.createdAt()).isNotNull();
        assertThat(thirdResult.updatedAt()).isNotNull();
    }

    @Test
    public void testListProductsUnknownSortProperty() {
        // GIVEN
        Map<String, Object> arguments = Map.of("sort", List.of(Map.of("property", "secret")));

        // WHEN
        CallToolResult result = mcpTestHelper.callTool("list_products", arguments);

        // THEN
        assertThat(result.isError()).isTrue();
        assertThat(mcpTestHelper.text(result)).contains("VALIDATION_ERROR").contains("sort[0].property");
    }

    @Test
    public void testListProductsPageSizeAboveMax() {
        // GIVEN
        Map<String, Object> arguments = Map.of("pagination", Map.of("size", 500));

        // WHEN
        CallToolResult result = mcpTestHelper.callTool("list_products", arguments);

        // THEN
        assertThat(result.isError()).isTrue();
        assertThat(mcpTestHelper.text(result)).contains("VALIDATION_ERROR").contains("pagination.size");
    }

    @Test
    public void testGetProduct() {
        // GIVEN
        Product saved = productRepository.save(product("Mug", "9.50", true));

        // WHEN
        ProductResponse product = mcpTestHelper.read(mcpTestHelper.callTool("get_product", Map.of("id", saved.getId())), PRODUCT);

        // THEN
        assertThat(product.id()).isEqualTo(saved.getId());
        assertThat(product.name()).isEqualTo(saved.getName());
        assertThat(product.description()).isEqualTo(saved.getDescription());
        assertThat(product.price()).isEqualByComparingTo(saved.getPrice());
        assertThat(product.active()).isEqualTo(saved.isActive());
        assertThat(product.createdAt()).isNotNull();
        assertThat(product.updatedAt()).isNotNull();
    }

    @Test
    public void testGetProductNotFound() {
        // GIVEN
        long missingId = 999999L;

        // WHEN
        CallToolResult result = mcpTestHelper.callTool("get_product", Map.of("id", missingId));

        // THEN
        assertThat(result.isError()).isTrue();
        assertThat(mcpTestHelper.text(result)).contains("PRODUCT_NOT_FOUND").contains(String.valueOf(missingId));
    }

    @Test
    public void testCreateProduct() {
        // GIVEN
        Map<String, Object> arguments = Map.of(
                "name", "Cap",
                "description", "Baseball cap",
                "price", 12.5,
                "active", true);

        // WHEN
        ProductResponse created = mcpTestHelper.read(mcpTestHelper.callTool("create_product", arguments), PRODUCT);

        // THEN
        // assert response
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Cap");
        assertThat(created.description()).isEqualTo("Baseball cap");
        assertThat(created.price()).isEqualByComparingTo("12.50");
        assertThat(created.active()).isTrue();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        // assert from database
        Product persisted = productRepository.findById(created.id()).orElseThrow();
        assertThat(created.id()).isEqualTo(persisted.getId());
        assertThat(created.name()).isEqualTo(persisted.getName());
        assertThat(created.description()).isEqualTo(persisted.getDescription());
        assertThat(created.price()).isEqualByComparingTo(persisted.getPrice());
        assertThat(created.active()).isEqualTo(persisted.isActive());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    public void testCreateProductInvalidInput() {
        // GIVEN
        Map<String, Object> arguments = Map.of(
                "name", "",
                "price", -1,
                "active", true);

        // WHEN
        CallToolResult result = mcpTestHelper.callTool("create_product", arguments);

        // THEN
        // assert response
        assertThat(result.isError()).isTrue();
        assertThat(mcpTestHelper.text(result)).contains("VALIDATION_ERROR").contains("name").contains("price");

        // assert from database
        assertThat(productRepository.count()).isZero();
    }

    @Test
    public void testUpdateProduct() {
        // GIVEN
        Product saved = productRepository.save(product("Mug", "9.50", true));
        Map<String, Object> arguments = Map.of(
                "id", saved.getId(),
                "name", "Big Mug",
                "description", "Ceramic mug, 500 ml",
                "price", 11.9,
                "active", false);

        // WHEN
        ProductResponse updated = mcpTestHelper.read(mcpTestHelper.callTool("update_product", arguments), PRODUCT);

        // THEN
        // assert response
        assertThat(updated.id()).isEqualTo(saved.getId());
        assertThat(updated.name()).isEqualTo("Big Mug");
        assertThat(updated.description()).isEqualTo("Ceramic mug, 500 ml");
        assertThat(updated.price()).isEqualByComparingTo("11.90");
        assertThat(updated.active()).isFalse();
        assertThat(updated.createdAt()).isNotNull();
        assertThat(updated.updatedAt()).isNotNull();

        // assert from database
        Product persisted = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.id()).isEqualTo(persisted.getId());
        assertThat(updated.name()).isEqualTo(persisted.getName());
        assertThat(updated.description()).isEqualTo(persisted.getDescription());
        assertThat(updated.price()).isEqualByComparingTo(persisted.getPrice());
        assertThat(updated.active()).isEqualTo(persisted.isActive());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    public void testDeleteProduct() {
        // GIVEN
        Product saved = productRepository.save(product("Mug", "9.50", true));

        // WHEN
        CallToolResult result = mcpTestHelper.callTool("delete_product", Map.of("id", saved.getId()));

        // THEN
        // assert response
        assertThat(result.isError()).isFalse();
        assertThat(mcpTestHelper.text(result)).contains("Product " + saved.getId() + " deleted");

        // assert from database
        assertThat(productRepository.existsById(saved.getId())).isFalse();
    }
}
