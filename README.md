# spring-boot-starter-swagger-mcp

[![CI](https://github.com/Neo1228/spring-boot-starter-swagger-mcp/actions/workflows/ci.yml/badge.svg)](https://github.com/Neo1228/spring-boot-starter-swagger-mcp/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Awesome MCP Servers](https://img.shields.io/badge/awesome--mcp--servers-listed-blueviolet.svg)](https://github.com/punkpeye/awesome-mcp-servers/pull/2059)

Spring Boot starter that automatically exposes SpringDoc OpenAPI operations as MCP tools.

## What You Get

- Auto-discovery of OpenAPI operations from your running Spring app
- Auto-registration of MCP tools for discovered API operations
- Smart-context tools: `meta_get_api_capabilities`, `meta_validate_api_call`, `meta_discover_api_tools`, `meta_describe_api_tool`, `meta_list_api_groups`, `meta_plan_api_workflow`, `meta_invoke_api_workflow`, `meta_invoke_api_by_intent`
- API gateway, catalog, and workflow layer that lets MCP clients inspect capabilities, validate calls, explore grouped APIs, plan multi-step calls, and execute them with dry-run safety
- Rich MCP input schemas from OpenAPI constraints (required fields, enum, numeric/string/array/object limits, examples, deprecation hints)
- Response optimization with projection/summarization controls
- Execution guardrails: required argument validation, unresolved path-template protection, and safe `_headers` filtering
- Risk controls for dangerous operations (`_confirm`, blocked paths, role checks, audit logs)

## Architecture

```mermaid
graph TD
    User([User / LLM Client]) <--> MCP[MCP Client / Claude Desktop]
    MCP <--> Bridge[Swagger MCP Bridge /starter/]
    Bridge --> Catalog[Operation Catalog /groups + contracts/]
    Bridge --> Workflow[Workflow Orchestrator /plan + dry-run + execute/]
    Bridge <--> Docs[SpringDoc OpenAPI /v3/api-docs]
    Bridge <--> API[Your Spring Controller /hello]
```

## Quick Start (New Empty Spring Boot Project)

### 1. Create a Spring Boot app

Use:

- Java 17+
- Spring Boot 3.5.x
- Spring Web

### 2. Add dependencies

Gradle (`build.gradle.kts`):

```kotlin
plugins {
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.17")
    implementation("io.github.neo1228:spring-boot-starter-swagger-mcp:<version>")
}
```

Maven (`pom.xml`):

```xml
<properties>
  <swagger-mcp.version>0.1.0-SNAPSHOT</swagger-mcp.version>
</properties>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>2.8.17</version>
  </dependency>
  <dependency>
    <groupId>io.github.neo1228</groupId>
    <artifactId>spring-boot-starter-swagger-mcp</artifactId>
    <version>${swagger-mcp.version}</version>
  </dependency>
</dependencies>
```

Use a release version (for example `0.1.0`) when consuming from a remote artifact repository.

### 3. Add one controller

```java
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @Operation(operationId = "getHello", summary = "Get greeting message")
    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "world") String name) {
        return Map.of("message", "Hello " + name);
    }
}
```

### 4. Add configuration (`application.yml`)

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
```

### 5. Run and verify

1. Start app: `./gradlew bootRun` or `./mvnw spring-boot:run`
2. Verify OpenAPI: `http://localhost:8080/v3/api-docs`
3. Verify MCP endpoint: `http://localhost:8080/mcp`
4. Connect from an MCP client

Generated tool names follow `<tool-name-prefix><operation-id>` (example: `api_gethello`).


## MCP Client Workflow

This starter exposes direct API tools and a meta-tool layer so general MCP clients can work with large APIs without guessing tool names upfront:

1. `meta_get_api_capabilities` returns API catalog stats, available gateway tools, orchestration features, safety policy, and response controls.
2. `meta_list_api_groups` summarizes the exposed API catalog by OpenAPI tag/group.
3. `meta_discover_api_tools` finds relevant operations for a natural-language request.
4. `meta_describe_api_tool` returns the selected tool's method/path, parameters, required arguments, request body schema, risk flags, and full MCP input schema.
5. `meta_validate_api_call` validates one generated API tool call without dispatching HTTP, including required arguments, risky-operation confirmation, and dispatch preview.
6. `meta_plan_api_workflow` turns a workflow goal into a deterministic candidate step plan with contracts and risk flags.
7. `meta_invoke_api_workflow` dry-runs or executes multiple generated API tools sequentially.
8. `meta_invoke_api_by_intent` can select and invoke the best matching operation when the client already has enough arguments.

The configured `tool-name-prefix` is still applied, so the default generated names are `api_meta_get_api_capabilities`, `api_meta_validate_api_call`, `api_meta_list_api_groups`, `api_meta_discover_api_tools`, `api_meta_describe_api_tool`, `api_meta_plan_api_workflow`, `api_meta_invoke_api_workflow`, and `api_meta_invoke_api_by_intent`.

Recommended client loop:

1. Call `api_meta_get_api_capabilities` once to learn the gateway features and safety policy.
2. Use `api_meta_discover_api_tools` or `api_meta_list_api_groups` to find candidate operations.
3. Use `api_meta_describe_api_tool` for exact argument schema.
4. Use `api_meta_validate_api_call` before risky or generated calls.
5. For multi-step work, call `api_meta_plan_api_workflow`, then `api_meta_invoke_api_workflow` with `dryRun=true`, then execute with `dryRun=false` only after validation is clean.

Workflow execution is intentionally safe by default:

- `meta_validate_api_call` and `meta_invoke_api_workflow` dry-runs validate tool names, arguments, required fields, dispatch paths, and risk flags before dispatching HTTP.
- A workflow step has `{ "id": "...", "toolName": "...", "arguments": { ... } }`.
- Later steps can read previous structured results with JSONPath interpolation: `${create:$.order.id}`.
- If the whole argument value is a template, the resolved raw value is passed through. If a template is embedded in a longer string, the value is stringified.
- Recursive meta-tool orchestration is blocked; workflow steps can invoke generated API operation tools only.
- Risky HTTP methods still require the configured `_confirm` token even inside a workflow.

Example validation payload:

```json
{
  "toolName": "api_getorder",
  "arguments": {
    "orderId": "order-1"
  }
}
```

Example workflow payload:

```json
{
  "dryRun": false,
  "steps": [
    {
      "id": "create",
      "toolName": "api_createorder",
      "arguments": {
        "body": { "id": "order-1", "item": "shoe" },
        "_confirm": "CONFIRM"
      }
    },
    {
      "id": "read",
      "toolName": "api_getorder",
      "arguments": {
        "orderId": "${create:$.order.id}"
      }
    }
  ]
}
```

For larger APIs, set `swagger.mcp.smart-context.gateway-only=true` to expose only this gateway/meta layer instead of registering every operation as a top-level MCP tool.

## Use It Before Public Release (Local Development Install)

If the artifact is not published to a remote registry yet:

1. Build and publish to local Maven cache:
   - `./gradlew publishToMavenLocal`
2. In your consumer app:
   - add `mavenLocal()` repository
   - use version `0.1.0-SNAPSHOT` (or your chosen local version)

## Key Configuration

- `swagger.mcp.enabled`: enable/disable bridge (default `true`)
- `swagger.mcp.api-docs-path`: OpenAPI docs path (default `/v3/api-docs`)
- `swagger.mcp.tool-name-prefix`: tool name prefix (default `api_`)
- `swagger.mcp.smart-context.gateway-only`: expose only meta tools
- `swagger.mcp.execution.allowed-argument-headers`: optional allowlist for dynamic `_headers` passed by MCP clients
- `swagger.mcp.execution.blocked-argument-headers`: denylist for dynamic `_headers`; defaults block hop-by-hop/transport-sensitive headers like `Host`, `Content-Length`, `Connection`, and `Transfer-Encoding`
- `swagger.mcp.security.require-confirmation-for-risky-operations`: require `_confirm` token for risky methods

For risky HTTP methods (`POST`, `PUT`, `PATCH`, `DELETE`), default policy requires `_confirm=CONFIRM`. The adapter also validates missing required path/query/header/body arguments before dispatching HTTP, so MCP clients get a clear tool error instead of a malformed API call.

## Compatibility Matrix

| Starter | Java | Spring Boot | springdoc-openapi | Spring AI BOM |
|---|---|---|---|---|
| 0.1.x | 17+ | 3.5.x | 2.8.17 | 1.1.5 |

Spring Boot 4.x is intentionally not supported in the 0.1.x line. Stay on Spring Boot 3.5.x with springdoc-openapi 2.8.x unless this repository cuts a new major/minor compatibility line.

## Example Consumer Project

See `examples/minimal-webmvc-gradle` for a minimal app using this starter.

## Release And Versioning

- Release process: `RELEASING.md`
- Versioning policy: `VERSIONING.md`
- Changelog: `CHANGELOG.md`

## Development

- Run tests: `./gradlew test`
- Contribution guide: `CONTRIBUTING.md`
- Security reporting: `SECURITY.md`

## License

Apache License 2.0 (`LICENSE`)
