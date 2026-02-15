# spring-boot-starter-swagger-mcp

Spring Boot starter that automatically converts SpringDoc OpenAPI operations into MCP tools.

This project lets an MCP client (Claude Desktop, MCP Inspector, custom agents) call your existing REST APIs without manual function/tool definitions.

## Features

- Auto-scan OpenAPI (`/v3/api-docs`) on app startup
- Convert each REST operation to an MCP tool
- Smart context tools
- `meta_discover_api_tools`: select relevant tools by intent
- `meta_invoke_api_by_intent`: pick + execute the best tool
- Response optimizer
- JSONPath projection via `_projection`
- Summarization/truncation controls for large payloads
- Security controls
- Risky operation detection
- Confirmation token for dangerous actions
- Optional role-based visibility/execution checks
- Audit logging for tool execution
- Spring Boot auto-configuration starter packaging

## Compatibility

- Java 17+
- Spring Boot 3.5.x (tested)
- Spring AI BOM 1.1.2
- SpringDoc 2.8.3

## Install

### Gradle

```kotlin
dependencies {
    implementation("io.github.neo1228:spring-boot-starter-swagger-mcp:0.1.0-SNAPSHOT")
}
```

### Maven

```xml
<dependency>
  <groupId>io.github.neo1228</groupId>
  <artifactId>spring-boot-starter-swagger-mcp</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

1. Add the dependency.
2. Ensure your app exposes SpringDoc OpenAPI (`/v3/api-docs`).
3. Enable MCP server transport endpoint.
4. Run the app.
5. Connect MCP client to your endpoint (default: `/mcp`).

Example `application.yml`:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE_HTTP
        streamable-http:
          mcp-endpoint: /mcp

swagger:
  mcp:
    enabled: true
    api-docs-path: /v3/api-docs
    tool-name-prefix: api_
    smart-context:
      enabled: true
      gateway-tool-enabled: true
      gateway-only: false
      default-top-k: 8
      min-score: 0.08
    response:
      summarize-by-default: false
      summary-threshold-chars: 4000
      max-chars: 8000
      projection-argument-enabled: true
    security:
      expose-risky-tools: true
      require-confirmation-for-risky-operations: true
      confirmation-token: CONFIRM
```

## Runtime Behavior

- Startup:
- Fetch OpenAPI JSON from `swagger.mcp.api-docs-path`
- Convert operations to MCP tool schemas
- Register tools to `McpSyncServer`
- Tool execution:
- Resolve path/query/header/body arguments
- Internal HTTP loopback call to your own app
- Optimize response (projection/summarize/truncate)
- Return MCP result with text + structured payload

## Built-in Meta Tools

- `<prefix>meta_discover_api_tools`
- Input: `query`, optional `topK`
- Returns top matching tools with score
- `<prefix>meta_invoke_api_by_intent`
- Input: `query`, optional `topK`, `arguments`, optional `_confirm`
- Chooses best tool and executes it

Default prefix is `api_`.

## Security Notes

- Risky operations can require `_confirm` token.
- You can block path patterns completely.
- You can require any role for risky or protected paths.
- For production, keep `swagger.mcp.security.confirmation-token` secret and rotate it.

## Configuration Reference

Primary prefix: `swagger.mcp.*`

- `enabled` (default: `true`)
- `api-docs-path` (default: `/v3/api-docs`)
- `tool-name-prefix` (default: `api_`)
- `include-path-patterns`, `exclude-path-patterns`
- `include-http-methods`
- `execution.*`
- `base-url`, `connect-timeout`, `read-timeout`
- `copy-incoming-authorization-header`, `copy-incoming-cookie-header`
- `default-headers`
- `smart-context.*`
- `enabled`, `gateway-tool-enabled`, `gateway-only`, `default-top-k`, `min-score`
- `response.*`
- `max-chars`, `summary-threshold-chars`, `max-depth`, `max-object-entries`
- `max-array-items`, `truncate-strings-at`, `projection-argument-enabled`, `summarize-by-default`
- `security.*`
- `audit-log-enabled`, `expose-risky-tools`, `require-confirmation-for-risky-operations`
- `confirmation-token`, `risky-http-methods`, `risky-path-patterns`
- `blocked-path-patterns`, `role-protected-path-patterns`, `required-any-role`

## Development

```bash
./gradlew test
./gradlew build
```

## Publish

This project supports publishing to OSSRH and GitHub Packages through environment variables:

- `OSSRH_USERNAME`, `OSSRH_PASSWORD`
- `SIGNING_KEY`, `SIGNING_PASSWORD`
- `GITHUB_ACTOR`, `GITHUB_TOKEN`

Run:

```bash
./gradlew publish
```

## License

Apache License 2.0. See `LICENSE`.

