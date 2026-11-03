package com.example.webshop.endpoint.mcp;

import com.example.webshop.TestcontainersConfiguration;
import com.example.webshop.helper.McpTestHelper;
import com.example.webshop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, McpTestHelper.class})
public abstract class McpIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    public void cleanDatabase() {
        productRepository.deleteAll();
    }
}
