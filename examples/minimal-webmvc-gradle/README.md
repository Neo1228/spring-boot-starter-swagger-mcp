# Swagger MCP Bridge Minimal WebMVC Example

A tiny Spring Boot WebMVC consumer for **Swagger MCP Bridge**.

It installs the starter from this repository into `mavenLocal()`, loads the unchanged consumer coordinates
`io.github.neo1228:openapi-mcp-spring-boot-starter:0.1.0-SNAPSHOT`, and exposes SpringDoc OpenAPI operations as MCP tools.

## What this example proves

- A normal Spring Boot 3.5.x WebMVC application can consume the bridge as a starter.
- SpringDoc's `/v3/api-docs` document is converted into MCP tool metadata at startup.
- The generated MCP endpoint stays on the standard streamable HTTP path: `/mcp`.
- Tool names keep the configured `api_` prefix, so `getHello` becomes `api_gethello`.
- Static marketplace metadata is served from `/.well-known/mcp/` for Smithery and registry-style scanners.

## Requirements

- Java 17 or newer
- The repository root build available locally
- Gradle wrapper included in this example

## Install the starter locally

From the repository root:

```bash
./gradlew publishToMavenLocal
```

This publishes the current snapshot to your local Maven cache:

```text
io.github.neo1228:openapi-mcp-spring-boot-starter:0.1.0-SNAPSHOT
```

## Run the example

From this directory:

```bash
./gradlew bootRun
```

## Verify manually

After the application starts on `localhost:8080`:

1. OpenAPI document: <http://localhost:8080/v3/api-docs>
2. Sample API operation: <http://localhost:8080/hello?name=Bridge>
3. MCP streamable HTTP endpoint: <http://localhost:8080/mcp>
4. Smithery/static server card: <http://localhost:8080/.well-known/mcp/server-card.json>
5. MCP Registry server metadata: <http://localhost:8080/.well-known/mcp/server.json>
6. Expected generated tool name: `api_gethello`

## Key files

- `build.gradle.kts` — consumes the local snapshot starter and pins the same Spring Boot / SpringDoc / Spring AI line as the root project.
- `src/main/resources/application.yml` — enables Swagger MCP Bridge and configures the MCP endpoint.
- `src/main/java/com/example/minimal/HelloController.java` — exposes one annotated API operation for tool generation.

## Notes for maintainers

Keep this example in sync whenever the root project changes:

- starter version or Maven coordinates
- Spring Boot / SpringDoc / Spring AI compatibility line
- MCP endpoint defaults or property names
- public project name and README branding

## Static marketplace metadata

The example exposes static marketplace metadata for scanners and submission review:

- `/.well-known/mcp/server-card.json` — Smithery-compatible capability card.
- `/.well-known/mcp/server.json` — same server metadata as `registry/server.json`.

Run `../../scripts/verify-marketplace-metadata.sh` from this directory's parent repository to check JSON validity, metadata synchronization, and public GHCR image reachability.
