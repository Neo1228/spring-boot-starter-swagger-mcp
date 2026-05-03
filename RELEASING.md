# Releasing Swagger MCP Bridge

This document describes how to publish Swagger MCP Bridge as a Maven dependency and how to publish the runnable example server for MCP directories and registries.

## Required GitHub Secrets

### Maven Central

Create a Sonatype Central Portal user token and store it as:

- `CENTRAL_USERNAME`
- `CENTRAL_PASSWORD`

Configure signing for non-snapshot Maven Central releases:

- `SIGNING_KEY` (ASCII-armored private key)
- `SIGNING_PASSWORD`

### GitHub Packages / GHCR

The workflows use the repository `GITHUB_TOKEN` for GitHub Packages and GitHub Container Registry.

## Pre-Release Checklist

1. `master` CI is green.
2. `CHANGELOG.md` has a dated entry for the target version (`## [x.y.z] - YYYY-MM-DD`).
3. Target version follows Semantic Versioning (`x.y.z` optionally with prerelease suffix).
4. README usage snippets still match current behavior and defaults.
5. `registry/server.json` matches the intended public server name, image coordinates, Docker runtime hint, and Streamable HTTP transport URL.
6. Static discovery metadata under `examples/minimal-webmvc-gradle/src/main/resources/static/.well-known/mcp/` is synchronized.
7. `scripts/verify-marketplace-metadata.sh` passes and confirms the GHCR image manifest is publicly reachable.
8. The example server builds and starts locally.

## Local Dry Run

Run before tagging:

```bash
./gradlew clean test
./gradlew -PprojectVersion=0.1.0 publishToMavenLocal
cd examples/minimal-webmvc-gradle && ./gradlew test
```

Validate coordinates in local Maven cache:

- Group: `io.github.neo1228`
- Artifact: `spring-boot-starter-swagger-mcp`
- Version: `0.1.0` (or chosen version)

Build the Central Portal bundle locally:

```bash
scripts/build-central-bundle.sh 0.1.0
unzip -l build/central-bundle.zip | head
```

Build the runnable example image locally:

```bash
docker build \
  -f examples/minimal-webmvc-gradle/Dockerfile \
  -t ghcr.io/neo1228/swagger-mcp-bridge-example:0.1.0 \
  .

docker run --rm -p 8080:8080 ghcr.io/neo1228/swagger-mcp-bridge-example:0.1.0
```

Manual smoke checks after startup:

- `http://localhost:8080/v3/api-docs`
- `http://localhost:8080/hello?name=Bridge`
- `http://localhost:8080/mcp`
- `http://localhost:8080/.well-known/mcp/server-card.json`
- `http://localhost:8080/.well-known/mcp/server.json`

## Publish Maven Central

Tag-based publish is recommended:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The `Release to Maven Central` workflow:

1. resolves `v0.1.0` to `0.1.0`,
2. validates SemVer and changelog entry,
3. runs tests,
4. builds a signed Central Portal deployment bundle,
5. uploads it to the Central Portal Publisher API,
6. creates a GitHub Release for tag builds.

The workflow defaults to `USER_MANAGED` publishing so the deployment is validated first and can be released from the Central Portal UI. Use `AUTOMATIC` only when the release process is fully trusted.

Manual publish is also available via `workflow_dispatch` by providing a `version` input.

## Publish GitHub Packages

Use `Publish to GitHub Packages` from GitHub Actions when you want a GitHub Packages build without creating a Maven Central release.

## Publish The Example MCP Server Image

Use `Publish Example MCP Server` from GitHub Actions or push a `v*` tag. The workflow publishes:

- `ghcr.io/neo1228/swagger-mcp-bridge-example:<version>`
- `ghcr.io/neo1228/swagger-mcp-bridge-example:latest`

The image includes the MCP Registry ownership label:

```text
io.modelcontextprotocol.server.name=io.github.neo1228/swagger-mcp-bridge
```

## Publish MCP Registry Metadata

`registry/server.json` is prepared for the official MCP Registry using the GHCR image package type.

For a registry publish from GitHub Actions, run `Publish Example MCP Server` with `publishRegistry=true`. The workflow renders the image tag into `server.json`, authenticates with GitHub OIDC, and runs `mcp-publisher publish`.

For a manual publish:

```bash
scripts/verify-marketplace-metadata.sh
cd registry
mcp-publisher login github
mcp-publisher publish
```

## Post-Release Verification

1. Confirm the GitHub Release is created.
2. Confirm Maven Central shows:
   - `.jar`
   - `-sources.jar`
   - `-javadoc.jar`
   - `.pom`
   - signatures/checksums
3. Validate install in a clean consumer Spring Boot project.
4. Pull the GHCR example image and run the manual smoke checks above.
5. If published, verify MCP Registry search returns `io.github.neo1228/swagger-mcp-bridge`.
6. Submit Smithery only after the example server is reachable from a public HTTPS URL; see `docs/marketplace-readiness.md`.
7. Submit MCP Find through its `community-servers.yml` PR path once the target GitHub repository is reachable.

## Snapshot Publishing

For snapshots, publish from local/branch builds with `-PprojectVersion=x.y.z-SNAPSHOT`.

Example:

```bash
./gradlew -PprojectVersion=0.2.0-SNAPSHOT publishToMavenLocal
```
