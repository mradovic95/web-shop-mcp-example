package com.example.webshop.trigger;

import com.example.webshop.helper.McpTestHelper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.context.TestComponent;

import java.util.List;
import java.util.Objects;

@TestComponent
public class TriggerChatHelper {

    private static final String DEFAULT_MODEL = "gpt-4.1-mini";

    private static final String AGENT_INSTRUCTIONS = """
            You are a web shop assistant. Use the available tools when they help answer the user's request. \
            Do not ask for confirmation; perform the requested action directly.""";

    private final McpTestHelper mcpTestHelper;

    private final ToolCallRecorder toolCallRecorder;

    private OpenAiChatModel chatModel;

    public TriggerChatHelper(McpTestHelper mcpTestHelper, ToolCallRecorder toolCallRecorder) {
        this.mcpTestHelper = mcpTestHelper;
        this.toolCallRecorder = toolCallRecorder;
    }

    public List<RecordedToolCall> run(String prompt) {
        toolCallRecorder.reset();
        chatModel().call(new Prompt(List.of(
                new SystemMessage(systemMessage()),
                new UserMessage(prompt))));
        return toolCallRecorder.calls();
    }

    public String model() {
        return Objects.requireNonNullElse(System.getenv("OPENAI_EVAL_MODEL"), DEFAULT_MODEL);
    }

    private synchronized OpenAiChatModel chatModel() {
        if (chatModel == null) {
            chatModel = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .apiKey(System.getenv("OPENAI_API_KEY"))
                            .model(model())
                            .temperature(0.0)
                            .toolCallbacks(toolCallRecorder.toolCallbacks())
                            .build())
                    .build();
        }
        return chatModel;
    }

    private String systemMessage() {
        String serverInstructions = mcpTestHelper.initializeResult().instructions();
        return serverInstructions == null ? AGENT_INSTRUCTIONS : AGENT_INSTRUCTIONS + "\n\n" + serverInstructions;
    }
}
