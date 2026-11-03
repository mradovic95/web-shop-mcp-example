package com.example.webshop.endpoint.mcp;

import com.example.webshop.helper.McpTestHelper;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class McpServerContractTest extends McpIntegrationTest {

    @Autowired
    private McpTestHelper mcpTestHelper;

    @Test
    public void testInitialize() {
        // GIVEN
        String expectedName = "web-shop-mcp";

        // WHEN
        InitializeResult result = mcpTestHelper.initializeResult();

        // THEN
        assertThat(result.serverInfo().name()).isEqualTo(expectedName);
        assertThat(result.serverInfo().version()).isEqualTo("1.0.0");
        assertThat(result.instructions()).contains("Prices are in EUR");
        assertThat(result.capabilities().tools()).isNotNull();
    }

    @Test
    public void testListTools() throws Exception {
        // GIVEN
        String expected = new ClassPathResource("mcp/tools-list.json").getContentAsString(StandardCharsets.UTF_8);

        // WHEN
        String actual = mcpTestHelper.toJson(mcpTestHelper.listTools().tools());

        // THEN
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.NON_EXTENSIBLE);
    }
}
