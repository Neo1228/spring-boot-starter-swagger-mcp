#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_JSON="$ROOT_DIR/registry/server.json"
EXAMPLE_SERVER_JSON="$ROOT_DIR/examples/minimal-webmvc-gradle/src/main/resources/static/.well-known/mcp/server.json"
SERVER_CARD="$ROOT_DIR/examples/minimal-webmvc-gradle/src/main/resources/static/.well-known/mcp/server-card.json"
SCHEMA_URL="https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json"
SCHEMA_FILE="${TMPDIR:-/tmp}/mcp-server.schema.json"

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require jq
require curl

jq -e . "$SERVER_JSON" >/dev/null
jq -e . "$EXAMPLE_SERVER_JSON" >/dev/null
jq -e . "$SERVER_CARD" >/dev/null

cmp -s "$SERVER_JSON" "$EXAMPLE_SERVER_JSON" || {
  echo "Example /.well-known/mcp/server.json must match registry/server.json" >&2
  exit 1
}

curl -fsSL "$SCHEMA_URL" -o "$SCHEMA_FILE"

python3 - "$SERVER_JSON" "$SCHEMA_FILE" <<'PY'
import json
import sys
from urllib.parse import urlparse

server_path, schema_path = sys.argv[1], sys.argv[2]
server = json.load(open(server_path, encoding="utf-8"))
schema = json.load(open(schema_path, encoding="utf-8"))

required = ["$schema", "name", "title", "description", "repository", "version"]
missing = [key for key in required if not server.get(key)]
if missing:
    raise SystemExit(f"server.json missing required metadata: {missing}")

if len(server.get("description", "")) > 100:
    raise SystemExit("server.json description must be 100 characters or less for the official publisher")

if server["$schema"] != "https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json":
    raise SystemExit("server.json schema URL is not the current pinned registry schema")

if not server["name"].startswith("io.github.Neo1228/"):
    raise SystemExit("server name must stay under the GitHub-authenticated io.github.neo1228 namespace")

repository = server.get("repository", {})
if repository.get("source") != "github" or repository.get("url") != "https://github.com/Neo1228/spring-boot-starter-swagger-mcp":
    raise SystemExit("repository metadata must point at the public Neo1228 GitHub repository")

packages = server.get("packages") or []
if len(packages) != 1:
    raise SystemExit("expected exactly one runnable OCI example package")

package = packages[0]
if package.get("registryType") != "oci":
    raise SystemExit("official registry package must use OCI for the runnable example image")
identifier = package.get("identifier", "")
if not identifier.startswith("ghcr.io/neo1228/swagger-mcp-bridge-example:"):
    raise SystemExit("OCI identifier must point at the GHCR example image with an immutable version tag")
if identifier.endswith(":latest"):
    raise SystemExit("official registry metadata must not use the mutable latest tag")

transport = package.get("transport") or {}
if transport.get("type") != "streamable-http":
    raise SystemExit("example package must declare streamable-http transport")
url = transport.get("url")
parsed = urlparse(url or "")
if parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1"} or parsed.path != "/mcp":
    raise SystemExit("package transport URL must point at the local example /mcp endpoint")

runtime_args = [arg.get("value") for arg in package.get("runtimeArguments", [])]
if package.get("runtimeHint") != "docker" or runtime_args[:4] != ["run", "--rm", "-p", "8080:8080"]:
    raise SystemExit("OCI package must include docker runtime hint and port mapping arguments")

# Keep a light schema sanity check without vendoring a full JSON Schema validator.
defs = schema.get("definitions") or {}
if "Package" not in defs or "StreamableHttpTransport" not in defs:
    raise SystemExit("downloaded registry schema did not contain expected package/transport definitions")

print("registry/server.json marketplace metadata looks publishable")
PY

python3 - "$SERVER_CARD" <<'PY'
import json
import sys

card = json.load(open(sys.argv[1], encoding="utf-8"))
server_info = card.get("serverInfo") or {}
if not server_info.get("name") or not server_info.get("version"):
    raise SystemExit("server-card.json requires serverInfo.name and serverInfo.version")
if card.get("authentication", {}).get("required") is not False:
    raise SystemExit("minimal example server-card should declare no auth requirement")
tools = card.get("tools") or []
if not any(tool.get("name") == "api_gethello" for tool in tools):
    raise SystemExit("server-card.json must expose the example api_gethello tool")
print("server-card.json marketplace metadata looks scannable")
PY

IMAGE="$(jq -r '.packages[0].identifier' "$SERVER_JSON")"
TOKEN="$(curl -fsSL "https://ghcr.io/token?service=ghcr.io&scope=repository:neo1228/swagger-mcp-bridge-example:pull" | jq -r .token)"
curl -fsSI \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.oci.image.index.v1+json" \
  "https://ghcr.io/v2/neo1228/swagger-mcp-bridge-example/manifests/${IMAGE##*:}" >/dev/null

echo "GHCR image manifest is publicly reachable: $IMAGE"
