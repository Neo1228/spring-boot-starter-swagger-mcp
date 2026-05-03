#!/usr/bin/env bash
set -euo pipefail

version="${1:-${PROJECT_VERSION:-}}"
if [[ -z "$version" ]]; then
  echo "Usage: $0 <version>" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

rm -rf build/central-staging build/central-bundle.zip
./gradlew -PprojectVersion="$version" clean test publishMavenJavaPublicationToCentralBundleRepository --no-daemon
(
  cd build/central-staging
  zip -qr ../central-bundle.zip .
)

echo "build/central-bundle.zip"
