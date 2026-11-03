package com.example.webshop.trigger;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.Consumer;

public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    private final Consumer<String> recorder;

    public RecordingToolCallback(ToolCallback delegate, Consumer<String> recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        recorder.accept(toolInput);
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        recorder.accept(toolInput);
        return delegate.call(toolInput, toolContext);
    }
}
