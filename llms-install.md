# Agent Install Guide: Swagger MCP Bridge Example Server

Swagger MCP Bridge is a Spring Boot starter that exposes SpringDoc OpenAPI operations as MCP tools. This repository includes a minimal runnable WebMVC example that MCP hosts can use for discovery and smoke testing.

## Fastest path: run the published container

Use the GHCR example image when available:

```bash
docker run --rm -p 8080:8080 ghcr.io/neo1228/swagger-mcp-bridge-example:latest
```

Then connect the MCP client to the Streamable HTTP endpoint:

```text
http://localhost:8080/mcp
```

The example also exposes:

- OpenAPI document: `http://localhost:8080/v3/api-docs`
- Sample REST endpoint: `http://localhost:8080/hello?name=Bridge`
- Expected MCP tool: `api_gethello`

## Local source build path

If the container image is not available yet, build from source:

```bash
git clone https://github.com/Neo1228/spring-boot-starter-swagger-mcp.git
cd spring-boot-starter-swagger-mcp
./gradlew publishToMavenLocal
cd examples/minimal-webmvc-gradle
./gradlew bootRun
```

Connect the MCP client to:

```text
http://localhost:8080/mcp
```

## MCP transport notes

- Transport: Streamable HTTP
- Endpoint path: `/mcp`
- Requires no API keys for the minimal example
- Java bytecode target: 17
- Tested Java versions in CI: 17, 21, 25

## What the example demonstrates

The bundled controller exposes `GET /hello`, SpringDoc publishes it through `/v3/api-docs`, and Swagger MCP Bridge registers it as an MCP tool named `api_gethello`. The bridge also registers meta tools for discovery, validation, workflow planning, and workflow execution.

## Static server card

The example exposes a static server card for marketplace scanners at `/.well-known/mcp/server-card.json`.
