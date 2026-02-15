# spring-boot-starter-swagger-mcp

Spring Boot starter that automatically exposes SpringDoc OpenAPI operations as MCP tools.

## Core Use Case: Start From an Empty Spring Boot Project

This is the primary goal of the project: connect a brand-new Spring API service to AI tooling with minimal setup.

### 1. Create a new Spring Boot app

Generate a project with:

- Java 17
- Spring Boot 3.5.x
- Spring Web

### 2. Add dependencies (`build.gradle.kts`)

```kotlin
plugins {
    id("org.springframework.boot") version "3.5.8"
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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.3")
    implementation("io.github.neo1228:spring-boot-starter-swagger-mcp:<latest-version>")
}
```

### 3. Add one controller

```java
@RestController
public class HelloController {

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

1. Start app: `./gradlew bootRun`
2. Check OpenAPI: `http://localhost:8080/v3/api-docs`
3. Check MCP endpoint: `http://localhost:8080/mcp`
4. Connect from an MCP client (Claude Desktop, MCP Inspector, etc.)

Generated tools are exposed automatically (for example, `api_hello`).

## Existing Project Integration

If you already have a Spring Boot API service:

1. Add this starter dependency
2. Add SpringDoc dependency
3. Add `spring.ai.mcp.server.*` and `swagger.mcp.*` settings
4. Restart app

No manual per-endpoint tool definition is required.

## Key Features

- OpenAPI to MCP tool conversion
- Smart-context gateway tools
- `meta_discover_api_tools`
- `meta_invoke_api_by_intent`
- Response optimization (`_projection`, summarization, truncation)
- Risk controls (`_confirm`, blocked path patterns, role checks, audit logs)

## Compatibility

- Java 17+
- Spring Boot 3.5.x
- Spring AI BOM 1.1.2

Spring Boot 4.x is not supported yet in this repository.

## Development

- Test: `./gradlew test`
- Release process: `RELEASING.md`
- Contribution guide: `CONTRIBUTING.md`
- Security reporting: `SECURITY.md`

## License

Apache License 2.0 (`LICENSE`)

