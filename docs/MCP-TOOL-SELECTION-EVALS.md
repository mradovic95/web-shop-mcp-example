# MCP tool-selection evals ("does the agent trigger the right tool?")

The integration tests in [TESTING-MCP.md](TESTING-MCP.md) prove the server works: a client calling `list_products` with
given arguments gets the right page back. They do **not** prove that a model reading our tool descriptions will choose
`list_products`, or fill in `filter.maxPrice` correctly, when a user writes "active products under 50 EUR, cheapest
first".
Tool-selection ("trigger") tests close that gap. They live in `src/test/java/com/example/webshop/trigger`.

## Is this relevant for this project?

Yes, but as an **opt-in layer**, not part of `./mvnw test`.

| Why it matters here                                                                                                                                         | Why it stays opt-in                                             |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| `list_products` has nested `filter` / `sort` / `pagination` arguments the model must map from free text.                                                    | Needs an OpenAI API key and costs money per run.                |
| `create_product` and `update_product` overlap; `update_product` tells the model to call `get_product` first.                                                | Model output is not fully deterministic, even at temperature 0. |
| `delete_product` is destructive, so read-only questions must never trigger it.                                                                              | Results depend on the model version, not only on our code.      |
| Descriptions are prompts: a small wording change can silently change behaviour. The `tools-list.json` snapshot shows the change; the eval shows its effect. |                                                                 |

## Design

`ProductMcpToolTriggerTest` runs against the same Spring Boot server and Testcontainers Postgres as the integration
tests:

1. `McpTestHelper` connects a real MCP client over Streamable HTTP and exposes it through `client()`.
2. `ToolCallRecorder` turns the server's tools into Spring AI `ToolCallback`s (`SyncMcpToolCallbackProvider`, already on
   the classpath via the MCP server starter) and wraps each one in a `RecordingToolCallback`. The wrapper records the
   tool name (`SyncMcpToolCallback.getOriginalToolName()`) and the parsed JSON arguments as a `RecordedToolCall`, then
   delegates to the real tool.
3. `TriggerChatHelper` sends the prompt to `OpenAiChatModel` with those callbacks. The system message is a short agent
   instruction plus the server's `instructions` from `initialize`, the way a real MCP client passes them. Spring AI runs
   the tool-calling loop, so tools really execute against the database and multi-step flows
   (`get_product` → `update_product`) are visible.
4. `McpTriggerTest.assertTriggered` re-seeds the catalog, runs the prompt **3 times** and requires **at least 2** runs
   to pass the check. The failure message prints every run's tool calls.

### Build setup

- Test dependency `org.springframework.ai:spring-ai-openai` (the model module, not the starter, so normal tests don't
  need a key). In Spring AI 2.0.2 it is built on the official `openai-java` SDK:
  `OpenAiChatModel.builder().options(OpenAiChatOptions.builder().apiKey(..).model(..).temperature(0.0).toolCallbacks(..).build()).build()`.
- Tests tagged `@Tag("trigger")` plus `@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")`.
- Surefire excludes the `trigger` tag by default; the `trigger` Maven profile runs only that tag:

```bash
OPENAI_API_KEY=sk-... ./mvnw test -Ptrigger
OPENAI_API_KEY=sk-... OPENAI_EVAL_MODEL=gpt-4.1 ./mvnw test -Ptrigger -Dtest=ProductMcpToolTriggerTest#testUpdateProduct
```

- `OPENAI_EVAL_MODEL` picks the model (default `gpt-4.1-mini`). Models that reject `temperature` need that
  option dropped.
- One full run is 24 model conversations (8 cases × 3 runs), a few cents with a mini model.

### Cases

| Test                                   | Prompt                                                               | Expected                                                                                     |
|----------------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `testListProducts`                     | "Show me active products under 50 EUR, cheapest first."              | `list_products` with `filter.active=true`, `filter.maxPrice` 49.99–50, `sort[0]` = price ASC |
| `testListProductsTextSearch`           | "Do you sell any keyboards?"                                         | `list_products` with "keyboard" in `filter.query` or `filter.name`                           |
| `testGetProduct`                       | "Show me the details of product {id}."                               | `get_product(id)`                                                                            |
| `testCreateProduct`                    | "Add a new active product called 'USB-C cable' that costs 9.99 EUR." | `create_product` with those values; no update/delete                                         |
| `testUpdateProduct`                    | "Change the price of product {id} to 19.99 EUR."                     | `get_product` before `update_product`, name unchanged; no create/delete                      |
| `testDeleteProduct`                    | "Delete product {id}."                                               | `delete_product`, only for that id                                                           |
| `testNoToolForUnrelatedQuestion`       | "What is the capital of France?"                                     | no tool calls                                                                                |
| `testListProductsDoesNotTriggerWrites` | "Which products are currently inactive?"                             | `list_products` with `filter.active=false`; no write tools                                   |

### Writing cases

1. Seed data per run: every run starts from the same catalog, even after a previous run deleted or changed rows.
2. Accept every correct shape (e.g. `direction` omitted means ASC, `query` or `name` both search). Over-strict checks
   make good model behaviour look like a failure.
3. Add a **negative** case for every risky tool: a prompt that should call nothing, or only a read tool.

## Flakiness, cost and when to run

- **pass@k vs pass^k.** pass@k = at least one of k runs succeeds; pass^k = all k succeed. With a 75% per-run success
  rate, pass@3 ≈ 98% but pass^3 ≈ 42%. "2 of 3" sits in between; raise the run count / required passes for tools that
  must trigger reliably (e.g. never deleting on a read question).
- Pin the model (`OPENAI_EVAL_MODEL`) so a provider update doesn't look like a regression in our code.
- Run nightly and on PRs that change a tool name, description, argument record or the server `instructions`, not on
  every PR.
- When a case fails, read the recorded tool calls before changing the description: sometimes the expectation is wrong.
  Keep a few prompts you never tune descriptions against, so you don't overfit.

## Alternatives considered

| Option                                                                                                                             | What it gives                                                                            | Why not chosen here                                                                         |
|------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **promptfoo** – MCP provider plus `trajectory:tool-used`, `trajectory:tool-args-match`, `trajectory:tool-sequence`, `tool-call-f1` | YAML test cases, any LLM provider, CLI and CI ready, cost reporting                      | Second toolchain (Node) beside Maven; a good fit if non-Java people should own the prompts. |
| **DeepEval** – `MCPUseMetric`, `MCPTaskCompletionMetric`                                                                           | LLM-as-judge scoring of tool and argument choice                                         | Python/pytest; judge scores add their own variance.                                         |
| **mcp-evals**                                                                                                                      | Node package + GitHub Action, YAML evals scored by an LLM                                | Defaults to OpenAI as the judge; less control over assertions.                              |
| **LangChain agentevals / LangSmith**                                                                                               | Trajectory matching (`strict`, `unordered`, `subset`, `superset`)                        | Python/TS, built around LangChain traces.                                                   |
| **Braintrust / OpenAI trace grading**                                                                                              | Hosted dashboards over traces                                                            | SaaS; you still write the tool-choice scorer yourself.                                      |
| **Claude Code headless** – `claude --bare -p "…" --mcp-config mcp.json --output-format stream-json --verbose`                      | Tests the real Claude Code client; parse `tool_use` blocks named `mcp__web-shop__<tool>` | Claude-only; check that the `system/init` event lists the server before trusting a run.     |
| **Spring AI evaluators** (`RelevancyEvaluator`, `FactCheckingEvaluator`)                                                           | Judge the final answer text                                                              | Don't look at tool calls; useful later for answer quality.                                  |

Deterministic checks that need no LLM, and which complement this suite:

- **MCP Inspector CLI** –
  `npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http --method tools/list`
  (see [TESTING-MCP.md §5](TESTING-MCP.md)).
- **Conformance suite** – `npx @modelcontextprotocol/conformance server --url http://localhost:8080/mcp` checks protocol
  behaviour and tool input schemas against the spec. Pin the version; scenario names are still changing.

## Writing descriptions that trigger well

From Anthropic's tool-writing guidance:

- Describe a tool as you would to a new colleague: when to use it, what each argument means, what comes back.
- Use unambiguous argument names and strict schemas.
- Say how tools relate (as `update_product` does with `get_product`).
- Measure every description change with an eval instead of guessing.

## Sources

- Anthropic – Writing effective tools for agents: https://www.anthropic.com/engineering/writing-tools-for-agents
- Anthropic – Demystifying evals for AI agents (pass@k /
  pass^k): https://anthropic.com/engineering/demystifying-evals-for-ai-agents (read via secondary write-ups)
- Claude Code headless mode: https://code.claude.com/docs/en/headless
- promptfoo MCP provider: https://www.promptfoo.dev/docs/providers/mcp/ ·
  assertions: https://www.promptfoo.dev/docs/configuration/expected-outputs/
- DeepEval MCP
  metrics: https://deepeval.com/docs/metrics-mcp-use · https://deepeval.com/docs/metrics-mcp-task-completion
- mcp-evals: https://github.com/mclenhard/mcp-evals
- LangChain
  agentevals: https://github.com/langchain-ai/agentevals · https://docs.langchain.com/langsmith/trajectory-evals.md
- Braintrust – what is an MCP eval: https://www.braintrust.dev/articles/what-is-an-mcp-eval
- OpenAI trace grading: https://developers.openai.com/api/docs/guides/trace-grading
- Spring AI MCP client: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html
- Spring AI evaluation testing: https://docs.spring.io/spring-ai/reference/api/testing.html
- MCP Inspector CLI: https://modelcontextprotocol.io/docs/2026-07-28/tools/inspector/cli.md
- MCP conformance suite: https://socket.dev/npm/package/@modelcontextprotocol/conformance/overview/0.1.6
- Benchmarks (ideas for task design, not tools):
  MCP-Universe https://alphaxiv.org/benchmarks/salesforce-ai-research/mcp-universe ·
  MCPMark https://arxiv.org/html/2509.24002v1 · LiveMCPBench https://arxiv.org/html/2508.01780v1 ·
  TRAJECT-Bench (scoring tool choice and arguments separately) https://arxiv.org/html/2510.04550v1
