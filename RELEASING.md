# Releasing

This document describes how to publish this starter so that other users can consume it as a regular dependency.

## Required GitHub Secrets

Configure these repository secrets before publishing:

- `OSSRH_USERNAME`
- `OSSRH_PASSWORD`
- `SIGNING_KEY` (ASCII-armored private key)
- `SIGNING_PASSWORD`

## Pre-Release Checklist

1. `main`/`master` CI is green.
2. `CHANGELOG.md` has a dated entry for the target version (`## [x.y.z] - YYYY-MM-DD`).
3. Target version follows Semantic Versioning (`x.y.z` optionally with prerelease suffix).
4. README usage snippets still match current behavior and defaults.

## Local Dry Run

Run before tagging:

```bash
./gradlew clean test
./gradlew -PprojectVersion=0.1.0 publishToMavenLocal
```

Validate coordinates in local Maven cache:

- Group: `io.github.neo1228`
- Artifact: `spring-boot-starter-swagger-mcp`
- Version: `0.1.0` (or chosen version)

## Publish A Release

Tag-based publish (recommended):

```bash
git tag v0.1.0
git push origin v0.1.0
```

What happens:

1. `Publish` workflow resolves version from tag (`v0.1.0 -> 0.1.0`)
2. Workflow validates SemVer and changelog entry
3. Workflow runs tests
4. Workflow publishes signed artifacts
5. Workflow creates a GitHub Release

Manual publish is also available via `workflow_dispatch` by providing a `version` input.

## Snapshot Publishing

For snapshots, publish from local/branch builds with `-PprojectVersion=x.y.z-SNAPSHOT`.

Example:

```bash
./gradlew -PprojectVersion=0.2.0-SNAPSHOT publishToMavenLocal
```

## Post-Release Verification

1. Confirm GitHub Release is created.
2. Confirm target repository contains:
   - `.jar`
   - `-sources.jar`
   - `-javadoc.jar`
   - `.pom`
   - signatures/checksums
3. Validate install in a clean consumer Spring Boot project.
