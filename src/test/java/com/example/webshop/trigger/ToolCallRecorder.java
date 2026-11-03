package com.example.webshop.trigger;

import com.example.webshop.helper.McpTestHelper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.TestComponent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@TestComponent
public class ToolCallRecorder {

    private static final TypeRef<Map<String, Object>> ARGUMENTS = new TypeRef<>() {
    };

    private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

    private final List<RecordedToolCall> calls = new CopyOnWriteArrayList<>();

    private final McpTestHelper mcpTestHelper;

    private List<ToolCallback> toolCallbacks;

    public ToolCallRecorder(McpTestHelper mcpTestHelper) {
        this.mcpTestHelper = mcpTestHelper;
    }

    public synchronized List<ToolCallback> toolCallbacks() {
        if (toolCallbacks == null) {
            ToolCallback[] mcpCallbacks = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(mcpTestHelper.client())
                    .build()
                    .getToolCallbacks();
            toolCallbacks = Arrays.stream(mcpCallbacks)
                    .map(callback -> (ToolCallback) new RecordingToolCallback(callback,
                            toolInput -> record(toolName(callback), toolInput)))
                    .toList();
        }
        return toolCallbacks;
    }

    public List<RecordedToolCall> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
    }

    private void record(String name, String toolInput) {
        calls.add(new RecordedToolCall(name, parse(toolInput)));
    }

    private String toolName(ToolCallback callback) {
        if (callback instanceof SyncMcpToolCallback mcpCallback) {
            return mcpCallback.getOriginalToolName();
        }
        return callback.getToolDefinition().name();
    }

    private Map<String, Object> parse(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(toolInput, ARGUMENTS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
