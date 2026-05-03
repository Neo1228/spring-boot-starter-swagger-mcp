#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MAVEN_COORDINATE="io.github.neo1228:openapi-mcp-spring-boot-starter"
OLD_MAVEN_COORDINATE="io.github.neo1228:spring-boot-starter-swagger-mcp"
ARTIFACT_ID="openapi-mcp-spring-boot-starter"
REGISTRY_NAME="io.github.Neo1228/swagger-mcp-bridge"
GHCR_IMAGE_PREFIX="ghcr.io/neo1228/swagger-mcp-bridge-example:"
REPO_URL="https://github.com/Neo1228/spring-boot-starter-swagger-mcp"

fail() {
  echo "Consistency check failed: $*" >&2
  exit 1
}

require_file_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "$file does not contain: $needle"
}

if grep -R "$OLD_MAVEN_COORDINATE" -n \
  --exclude=verify-project-consistency.sh \
  --exclude-dir=.git \
  --exclude-dir=.gradle \
  --exclude-dir=build \
  --exclude-dir=.omx \
  .; then
  fail "old Maven coordinate is still present"
fi

require_file_contains build.gradle.kts "artifactId = \"$ARTIFACT_ID\""
require_file_contains build.gradle.kts "name.set(\"OpenAPI MCP Spring Boot Starter\")"
require_file_contains settings.gradle.kts "rootProject.name = \"$ARTIFACT_ID\""
require_file_contains README.md "$MAVEN_COORDINATE"
require_file_contains examples/minimal-webmvc-gradle/build.gradle.kts "implementation(\"$MAVEN_COORDINATE:\$openApiMcpVersion\")"
require_file_contains examples/minimal-webmvc-gradle/README.md "$MAVEN_COORDINATE:0.1.0-SNAPSHOT"
require_file_contains RELEASING.md "Artifact: \`$ARTIFACT_ID\`"
require_file_contains .github/workflows/release-central.yml "name=$ARTIFACT_ID-\$VERSION"
require_file_contains build.gradle.kts "$REPO_URL"

jq -e . registry/server.json >/dev/null
jq -e . examples/minimal-webmvc-gradle/src/main/resources/static/.well-known/mcp/server.json >/dev/null
cmp -s registry/server.json examples/minimal-webmvc-gradle/src/main/resources/static/.well-known/mcp/server.json \
  || fail "registry/server.json and example /.well-known/mcp/server.json diverged"

[[ "$(jq -r '.name' registry/server.json)" == "$REGISTRY_NAME" ]] || fail "registry name mismatch"
[[ "$(jq -r '.repository.url' registry/server.json)" == "$REPO_URL" ]] || fail "registry repository URL mismatch"
[[ "$(jq -r '.packages[0].identifier' registry/server.json)" == ${GHCR_IMAGE_PREFIX}* ]] || fail "registry GHCR image mismatch"
[[ "$(jq -r '.packages[0].transport.type' registry/server.json)" == "streamable-http" ]] || fail "registry transport type mismatch"
[[ "$(jq -r '.packages[0].transport.url' registry/server.json)" == "http://localhost:8080/mcp" ]] || fail "registry transport URL mismatch"

require_file_contains examples/minimal-webmvc-gradle/Dockerfile "io.modelcontextprotocol.server.name=\"$REGISTRY_NAME\""
require_file_contains .github/workflows/publish-example-server.yml "io.modelcontextprotocol.server.name=$REGISTRY_NAME"
require_file_contains scripts/verify-marketplace-metadata.sh "$REGISTRY_NAME"
require_file_contains docs/marketplace-readiness.md "$REGISTRY_NAME"
require_file_contains docs/marketplace-readiness.md "$MAVEN_COORDINATE"

echo "project naming, release, and registry metadata are consistent"
