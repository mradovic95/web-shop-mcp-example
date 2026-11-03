package com.example.webshop.helper;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestComponent
public class McpTestHelper {

    private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

    private McpSyncClient client;

    private InitializeResult initializeResult;

    @EventListener
    public void connect(WebServerInitializedEvent event) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + event.getWebServer().getPort())
                .endpoint("/mcp")
                .build();
        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        initializeResult = client.initialize();
    }

    @EventListener(ContextClosedEvent.class)
    public void close() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    public McpSyncClient client() {
        return client;
    }

    public InitializeResult initializeResult() {
        return initializeResult;
    }

    public ListToolsResult listTools() {
        return client.listTools();
    }

    public CallToolResult callTool(String name, Map<String, Object> arguments) {
        return client.callTool(CallToolRequest.builder(name).arguments(arguments).build());
    }

    public String text(CallToolResult result) {
        assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) result.content().getFirst()).text();
    }

    public <T> T read(CallToolResult result, TypeRef<T> type) {
        assertThat(result.isError()).as(() -> "tool error: " + text(result)).isFalse();
        try {
            return jsonMapper.readValue(text(result), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
