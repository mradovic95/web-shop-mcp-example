package com.example.webshop.trigger;

import java.util.List;
import java.util.Map;

public record TriggerRun(Map<String, Long> ids, List<RecordedToolCall> calls) {

    public static final List<String> WRITE_TOOLS = List.of("create_product", "update_product", "delete_product");

    public Long id(String productName) {
        return ids.get(productName);
    }

    public List<String> toolNames() {
        return calls.stream().map(RecordedToolCall::name).toList();
    }

    public List<RecordedToolCall> calls(String toolName) {
        return calls.stream().filter(call -> call.name().equals(toolName)).toList();
    }
}
