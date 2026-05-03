# Marketplace and Registry Readiness

Swagger MCP Bridge is a Spring Boot starter plus a packaged runnable example MCP server. Use this page when reviewing or submitting the project to MCP directories.


## Naming and coordinate policy

| Surface | Name |
|---|---|
| Project / docs | Swagger MCP Bridge |
| Maven dependency | `io.github.neo1228:openapi-mcp-spring-boot-starter` |
| Official MCP Registry server | `io.github.Neo1228/swagger-mcp-bridge` |
| Runnable example image | `ghcr.io/neo1228/swagger-mcp-bridge-example:<version>` |

Keep these names synchronized with `scripts/verify-project-consistency.sh`. The Maven artifact is the reusable Spring Boot starter; the MCP Registry entry is the runnable example server package.

## Public package surfaces

| Surface | Status | Evidence |
|---|---|---|
| Maven Central starter | Release workflow prepared | `.github/workflows/release-central.yml`, `scripts/build-central-bundle.sh` |
| GHCR runnable MCP server | Prepared and published by workflow | `ghcr.io/neo1228/swagger-mcp-bridge-example:<version>` |
| Official MCP Registry metadata | Prepared | `registry/server.json` |
| Static MCP discovery metadata | Prepared | `/.well-known/mcp/server.json`, `/.well-known/mcp/server-card.json` in the example app |
| Smithery URL publishing | Compatible when hosted | Streamable HTTP `/mcp` endpoint plus static server card |
| MCP Find | PR-ready | category: `Developer Tools`; language: `Java` |

## Official MCP Registry

The official registry hosts metadata only; the runnable artifact must already be available from a supported package registry. This project uses the official Docker/OCI package path with GHCR.

Required metadata is in `registry/server.json`:

- `name`: `io.github.Neo1228/swagger-mcp-bridge`
- package type: `oci`
- image: `ghcr.io/neo1228/swagger-mcp-bridge-example:<version>`
- transport: Streamable HTTP at `http://localhost:8080/mcp`
- Docker runtime hint and port mapping: `docker run --rm -p 8080:8080 ...`

The Docker image carries the ownership verification label required by the registry:

```text
io.modelcontextprotocol.server.name=io.github.Neo1228/swagger-mcp-bridge
```

Before publishing, verify metadata and public image reachability:

```bash
scripts/verify-marketplace-metadata.sh
```

Publish through GitHub Actions by running **Publish Example MCP Server** with `publishRegistry=true`, or manually:

```bash
mcp-publisher login github
cd registry
mcp-publisher publish
```

## Smithery

Smithery URL publishing requires a public HTTPS MCP endpoint with Streamable HTTP. The example server is compatible with that model once deployed behind HTTPS:

```bash
smithery mcp publish "https://your-host.example.com/mcp" -n @neo1228/swagger-mcp-bridge
```

The example also serves static scanner metadata for Smithery at:

```text
/.well-known/mcp/server-card.json
```

For local validation through Smithery Uplink:

```bash
docker run --rm -p 8080:8080 ghcr.io/neo1228/swagger-mcp-bridge-example:0.1.0
smithery mcp add http://localhost:8080/mcp --id swagger-mcp-bridge-example
smithery tool list swagger-mcp-bridge-example
```

Do not claim a hosted Smithery listing until a public HTTPS deployment URL has been submitted and accepted.

## MCP Find

MCP Find asks submitters to open a PR against `community-servers.yml` with the server name, repository URL, description, and category. Suggested listing:

```yaml
- name: Swagger MCP Bridge
  repo: https://github.com/Neo1228/spring-boot-starter-swagger-mcp
  description: Spring Boot starter and runnable example server that turns SpringDoc OpenAPI operations into MCP tools with validation, workflow orchestration, and guardrails.
  category: Developer Tools
  language: Java
```

If the published MCP Find repository link is unavailable, wait for the directory to fix the GitHub target rather than opening unrelated issues.

## Release order

1. Keep CI green.
2. Build and upload the Maven Central bundle for the starter.
3. Publish the GHCR example image for the same version.
4. Verify `registry/server.json` and public image reachability.
5. Publish to the official MCP Registry.
6. Submit Smithery only after a public HTTPS deployment exists.
7. Submit MCP Find once its GitHub submission repository is reachable.
