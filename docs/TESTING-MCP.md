# Testing the MCP Server (Spring AI 2.0.2)

How to test the `@McpTool` tools in `ProductMcpTools`. They are served over **stateful Streamable HTTP** at `/mcp` (`type: sync`, `protocol: streamable`). This guide covers what to test at each level, which APIs to use, and the gotchas found in this repo.

> Spring AI has **no official MCP testing guide or test slice**, and its MCP samples ship no real tests. The patterns here come from Spring AI's own source tests (`SyncMcpToolProviderTests`), the MCP Java SDK client docs and the MCP Inspector CLI docs. See [Sources](#sources).

---

## 1. Test pyramid: what goes where

| Level | Tool | Speed | What it proves |
|---|---|---|---|
| **Unit (most tests)** | JUnit 5 + Mockito + AssertJ | ms | Helper logic, error translation, tool-method delegation |
| **Unit: MCP spec layer** | `SyncMcpToolProvider` (no HTTP) | ms | Generated tool names, descriptions, JSON input schema and annotations; JSON args bound to records; exceptions mapped to `isError` |
| **Integration: real MCP client** | `@SpringBootTest(RANDOM_PORT)` + `McpSyncClient` + Testcontainers Postgres | seconds | Full protocol over Streamable HTTP: `initialize`, `tools/list` contract snapshot, `tools/call` against a real DB |
| **Protocol smoke in CI** | MCP Inspector `--cli` | seconds | The packaged app is spec-compliant and reachable |
| **LLM evals (opt-in, later)** | `ChatModel` + MCP tool callbacks, Spring AI `Evaluator`s | slow, costs money, non-deterministic | The model picks the right tool and arguments from your descriptions |

Rule of thumb:
- Put **logic** in unit tests.
- Put the MCP **contract** (names, schema, annotations, error shape) in spec-layer unit tests, plus **one** snapshot in the integration test.
- Cover the **wiring** (transport, autoconfig, DB) with a **handful** of integration tests that use a real MCP client.
- Use **LLM evals** only when you change tool descriptions or the model.

---

## 2. Unit tests: plain JUnit + Mockito, no Spring context

These tests don't involve MCP at all; they are ordinary Java tests.

| Class under test | What to test |
|---|---|
| `McpPageHelper` | `null` pagination → page 0 / size 20; partial `Pagination(null, 5)`; `page = -1`, `size = 0` and `size = 101` throw `ProductException(VALIDATION_ERROR)` with the `pagination.page` / `pagination.size` keys; the sort is passed through to the `PageRequest` |
| `McpSortHelper` | `null`/empty sort → `id ASC`; unknown property → error key `sort[i].property`; a `null` element in the list; the `id` tie-breaker is appended once and not duplicated when `id` is already requested; a requested direction on `id` is kept |
| `SortOrder.toOrder()` | `null` direction → ASC |
| `ProductListFilter.toProductFilter()` | All fields map; all-null input |
| `McpToolErrors` | `ProductException`, `ConstraintViolationException` and `PropertyReferenceException` each become `IllegalArgumentException("CODE: message {params}")`; other exceptions pass through unchanged |
| `ProductMcpTools` (mocked `ProductService`, real helpers) | Each tool delegates the right arguments; `list_products(null, null, null)` uses `ProductFilter.empty()` and `PageRequest.of(0, 20, Sort.by("id"))`; `delete_product` returns `"Product 1 deleted"`; a not-found from the service becomes `IllegalArgumentException` starting with `PRODUCT_NOT_FOUND` |
| `ProductSpecifications`, `ValidationErrors`, `ProductMapper` | Ordinary unit tests; the LIKE escaping of `%` and `_` is a worthwhile edge case |

---

## 3. Unit tests for the MCP spec layer, without HTTP

`SyncMcpToolProvider` turns the `@McpTool` bean into the same `SyncToolSpecification`s the server registers. Calling `callHandler()` exercises the real JSON schema generation, binding of JSON arguments to `ProductListFilter` / `SortOrder` / `Pagination`, and exception-to-`isError` mapping, all in milliseconds. This is the pattern Spring AI uses to test `@McpTool` itself.

```java
public class ProductMcpToolsSpecTest {

    private final ProductService productService = mock(ProductService.class);
    private List<SyncToolSpecification> specs;

    @BeforeEach
    public void setUp() {
        ProductMcpTools tools = new ProductMcpTools(productService, new McpSortHelper(), new McpPageHelper());
        specs = new SyncMcpToolProvider(List.of(tools)).getToolSpecifications();
    }

    @Test
    public void exposesExactlyFiveTools() {
        assertThat(specs).extracting(s -> s.tool().name())
                .containsExactlyInAnyOrder("list_products", "get_product", "create_product", "update_product", "delete_product");
    }

    @Test
    public void annotationHintsAreCorrect() {
        assertThat(spec("get_product").tool().annotations().readOnlyHint()).isTrue();
        assertThat(spec("delete_product").tool().annotations().destructiveHint()).isTrue();
    }

    @Test
    public void listProductsBindsNestedArguments() {
        when(productService.findAll(any(), any())).thenReturn(PageResponse.of(Page.empty()));

        CallToolResult result = call("list_products", Map.of(
                "filter", Map.of("minPrice", 10, "active", true),
                "sort", List.of(Map.of("property", "price", "direction", "DESC")),
                "pagination", Map.of("page", 1, "size", 5)));

        assertThat(result.isError()).isFalse();
        verify(productService).findAll(
                argThat(f -> f.minPrice().compareTo(BigDecimal.TEN) == 0 && f.active()),
                argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5
                        && p.getSort().equals(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id")))));
    }

    @Test
    public void unknownSortPropertyIsToolError() {
        CallToolResult result = call("list_products", Map.of("sort", List.of(Map.of("property", "secret"))));

        assertThat(result.isError()).isTrue();
        assertThat(((TextContent) result.content().getFirst()).text()).contains("VALIDATION_ERROR");
        verifyNoInteractions(productService);
    }

    private CallToolResult call(String name, Map<String, Object> args) {
        return spec(name).callHandler().apply(mock(McpSyncServerExchange.class),
                CallToolRequest.builder(name).arguments(args).build());
    }

    private SyncToolSpecification spec(String name) {
        return specs.stream().filter(s -> s.tool().name().equals(name)).findFirst().orElseThrow();
    }
}
```

Imports:
- `org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider`
- `io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification`
- `io.modelcontextprotocol.server.McpSyncServerExchange`
- `io.modelcontextprotocol.spec.McpSchema.*` (`CallToolRequest`, `CallToolResult`, `TextContent`)

In MCP SDK 2.x, `new CallToolRequest(name, args)` is deprecated; use `CallToolRequest.builder(...)` instead.

Also worth covering at this level:
- **Input schema assertions.** For example, `list_products` has no required properties, and `create_product` requires `name`, `price` and `active`. You can assert on `spec.tool().inputSchema()`, which is a `Map` in SDK 2.x.
- **A missing primitive argument.** `create_product` without `active` hits the primitive `boolean` parameter. Decide whether you want an error or `false`, and pin that behaviour in a test.

---

## 4. Integration tests with a real MCP client (implemented)

**This is the only level that proves a real client can connect, list tools and call them.** Run them with `./mvnw test`; Docker must be running.

| File | Role |
|---|---|
| `src/test/java/com/example/webshop/TestcontainersConfiguration.java` | `@ServiceConnection` Postgres 17 container (Testcontainers 2: `org.testcontainers.postgresql.PostgreSQLContainer`), shared through the Spring context cache |
| `src/test/java/com/example/webshop/endpoint/mcp/McpIntegrationTest.java` | Base class: `@SpringBootTest(RANDOM_PORT)`, empties the `product` table, the MCP client lives in the `helper/McpTestHelper` bean (connects once when the web server starts; `callTool`, `text`, `read`, `toJson`, `listTools`, `initializeResult`); test products are built (not saved) with `ProductTestInventory.product(...)` |
| `src/test/java/com/example/webshop/endpoint/mcp/McpServerContractTest.java` | `initialize` result, and the `tools/list` contract snapshot |
| `src/test/java/com/example/webshop/endpoint/mcp/ProductMcpToolsTest.java` | Happy path and error path for every tool against real Postgres |
| `src/test/resources/mcp/tools-list.json` | The committed contract: name, title, description, inputSchema and annotations of every tool |

How it works:
- **The client is built by hand** with the SDK's JDK transport. `spring-ai-starter-mcp-client` isn't used here, because it initializes clients when the context is created, before the random port is known.
- **Docker Compose isn't started in tests.** `spring-boot-docker-compose` skips itself in tests by default.
- **Each test creates its own data.**
  - Liquibase creates only the schema in tests; the sample data file is included with `context: dev` in `db.changelog-master.yaml`.
  - The base class calls `deleteAll()` before every test.
  - `@Transactional` rollback can't be used, because the tool call runs on the server's HTTP thread, not the test thread.
- **Contract snapshot.**
  - `tools/list` is serialized with the SDK's `McpJsonMapper` and compared with `JSONAssert` in `NON_EXTENSIBLE` mode. That mode ignores array order but fails on added, removed or changed fields.
  - Renaming a parameter, rewording a description, changing a hint or making a field required silently breaks MCP clients and changes how LLMs use the tool. This test makes every such change show up as a PR diff.
  - When a change is intended, update `tools-list.json` in the same PR.
- **Error paths over the wire:** `PRODUCT_NOT_FOUND`, Bean Validation (blank name, negative price), page size above 100, unknown sort property. Each asserts `isError == true` and the error code.
- **Not here:** unit-level permutations. Keep integration tests to a handful.

Gotchas:
- Run the integration tests with the protocol you deploy (`streamable`).
  - If you switch to `stateless`, run them against both protocols.
  - Tools whose method parameters need a session or exchange are **silently dropped** in stateless mode, with only a log warning. The snapshot test catches this.
- Hibernate runs with `ddl-auto: validate`, so these tests also prove the Liquibase schema matches the `Product` entity.
- `@LocalServerPort` lives in `org.springframework.boot.test.web.server` in Boot 4.1.

---

## 5. Protocol smoke tests with MCP Inspector

Start the app (`./mvnw spring-boot:run`, or the container), then run:

```bash
npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http --method tools/list

npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http \
  --method tools/call --tool-name get_product --tool-arg id=1
```

- This works as a **deployment smoke test** and as an independent, non-Java check that the server follows the spec.
- Newer Inspector versions add `--strict` (schema portability checks) and distinct exit codes, such as `5` when a tool call returns `isError`. Check these against the version you install before relying on them in CI.
- For manual exploration, use the Inspector UI (`npx @modelcontextprotocol/inspector`), choose the **Streamable HTTP** transport, and point it at `http://localhost:8080/mcp`.

---

## 6. End-to-end tests with an LLM (not now; when it's worth it)

Do this when you change **tool descriptions**, add tools whose purpose overlaps with existing ones, or switch models. To the model, tool descriptions are effectively prompts.

**Tool selection.**
- In Spring AI 2.x, `ChatModel` returns the tool calls the model wants without executing them, so you can assert on the choice directly:

```java
ChatResponse response = chatModel.call(new Prompt("Show me active products under 50 EUR",
        ToolCallingChatOptions.builder().toolCallbacks(mcpToolCallbacks).build()));

assertThat(response.getResult().getOutput().getToolCalls())
        .anySatisfy(call -> {
            assertThat(call.name()).endsWith("list_products");
            assertThat(call.arguments()).contains("\"maxPrice\"");
        });
```

- The tool callbacks (`mcpToolCallbacks` above) come from `SyncMcpToolCallbackProvider`, which `spring-ai-starter-mcp-client` provides.
- Client-side tool names may be prefixed, hence `endsWith`.

**Answer quality.** Use Spring AI's `RelevancyEvaluator` and `FactCheckingEvaluator`.

**Running them:**
- Mark them `@Tag("eval")` and enable them only when an API key is set.
- Run them **nightly, not on PRs**.
- Use temperature 0, run each case several times and assert a pass rate (for example ≥ 90%) rather than a single result.

---

## 7. MCP-specific gotchas

- **A tool's name, description, input schema and annotation hints are its public API.** Snapshot them.
- **Missing annotations default to the cautious value.**
  - Per the MCP spec, a tool without annotations is treated as possibly destructive and not read-only.
  - `create_product` has no annotations, so clients may ask for extra confirmation. Decide on this deliberately and pin it in the snapshot.
- **Error mapping.**
  - With `@McpTool`, a `RuntimeException` becomes `CallToolResult(isError = true)`, and its message is what the model sees.
  - `McpToolErrors` shapes domain and validation errors. Other exceptions, such as a DB failure, currently pass through with their raw message.
  - Add a test that internal details (SQL, class names) never leak into tool results.
- **Silent filtering.** A `sync` server drops tool methods that return `Mono`/`Flux`, logging only a warning. The `tools/list` snapshot is your safety net.
- **Timeouts.** The client's default request timeout is 20s, and a timed-out call keeps running on the server. Use a short timeout in tests.

---

## Sources

- Spring AI MCP annotations server: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html
- Spring AI `SyncMcpToolProviderTests` (2.0.x): https://github.com/spring-projects/spring-ai/tree/2.0.x/mcp/mcp-annotations/src/test
- Spring AI stateless MCP server: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stateless-server-boot-starter-docs.html
- Spring AI MCP client starter: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html
- Spring AI testing / evaluators: https://docs.spring.io/spring-ai/reference/api/testing.html
- Spring AI tool calling with `ChatModel`: https://docs.spring.io/spring-ai/reference/api/tools/chatmodel-tool-calling.html
- MCP Java SDK client: https://java.sdk.modelcontextprotocol.io/latest/client/
- MCP Inspector: https://modelcontextprotocol.io/docs/tools/inspector
