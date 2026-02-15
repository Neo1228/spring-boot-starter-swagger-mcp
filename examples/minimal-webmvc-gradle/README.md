# Minimal WebMVC Example (Gradle)

This example shows how a separate Spring Boot project can consume `spring-boot-starter-swagger-mcp` as a dependency.

## Prerequisite (for local testing)

From the repository root:

```bash
./gradlew publishToMavenLocal
```

This installs `io.github.neo1228:spring-boot-starter-swagger-mcp:0.1.0-SNAPSHOT` into your local Maven cache.

## Run

```bash
./gradlew bootRun
```

## Verify

1. OpenAPI document: `http://localhost:8080/v3/api-docs`
2. MCP endpoint: `http://localhost:8080/mcp`
3. Generated tool example: `api_gethello`
