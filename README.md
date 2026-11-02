# web-shop-mcp-example

Example web shop that exposes the same product management functionality through
**REST** (for classic clients) and **MCP** (for AI agents). Both endpoints delegate
to a single `ProductService`, so business rules and validation live in one place.

Stack: Java 25, Spring Boot 4.1, Spring AI 2.0 (MCP server, WebMVC, streamable HTTP),
Spring Data JPA / Hibernate, PostgreSQL (docker compose), Lombok.

## Architecture

```
com.example.webshop
├── domain/          JPA entities (Lombok)             Product
├── repository/      Spring Data repositories          ProductRepository
├── dto/             Records                           ProductResponse, Create/UpdateProductRequest
├── mapper/          Manual entity <-> DTO mapping     ProductMapper
├── service/         Business logic (shared)           ProductService
├── exception/       Domain exceptions
├── endpoint/rest/   REST controller + error handling  ProductRestController
├── endpoint/mcp/    MCP tools for agents              ProductMcpTools
└── config/          Sample data seeding               DataSeeder
```

```
REST client ──> ProductRestController ─┐
                                       ├──> ProductService ──> ProductMapper / ProductRepository ──> PostgreSQL
AI agent ─────> ProductMcpTools ───────┘
```

## Running

Requires JDK 25 (`sdk env` picks it up from `.sdkmanrc`) and Docker.

```bash
./mvnw spring-boot:run
```

Spring Boot's docker compose support starts PostgreSQL from `compose.yaml` automatically
(dev-time only — when running the packaged jar, configure `spring.datasource.*` yourself).
Three sample products are seeded on first start.

## REST API — `/api/products`

```bash
curl localhost:8080/api/products
curl localhost:8080/api/products/1
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"name":"Cap","price":12.50,"active":true}'
curl -X PUT localhost:8080/api/products/4 -H 'Content-Type: application/json' \
  -d '{"name":"Cap","price":14.00,"active":true}'
curl -X DELETE localhost:8080/api/products/4
```

Errors are returned as RFC 9457 `ProblemDetail` (404 not found, 400 validation).

## MCP server — `http://localhost:8080/mcp`

Streamable HTTP transport. Tools:

| Tool             | Description                                   |
|------------------|-----------------------------------------------|
| `list_products`  | List all products (read-only)                 |
| `get_product`    | Get a product by id (read-only)               |
| `create_product` | Create a product                              |
| `update_product` | Replace all fields of a product               |
| `delete_product` | Delete a product (destructive)                |

Connect Claude Code:

```bash
claude mcp add --transport http web-shop http://localhost:8080/mcp
```

Or inspect interactively:

```bash
npx @modelcontextprotocol/inspector
# Transport: Streamable HTTP, URL: http://localhost:8080/mcp
```
