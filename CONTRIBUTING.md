# Contributing Guide

Thanks for contributing to `spring-boot-starter-swagger-mcp`.

## Prerequisites

- Java 17+
- Git

## Local Setup

```bash
git clone https://github.com/Neo1228/spring-boot-starter-swagger-mcp.git
cd spring-boot-starter-swagger-mcp
./gradlew test
```

## Development Rules

- Keep changes focused and minimal.
- Add or update tests when behavior changes.
- Keep public behavior documented in `README.md` and `CHANGELOG.md`.
- Do not commit generated artifacts, build outputs, or credentials.

## Commit Style

Conventional commit style is recommended:

- `feat: ...`
- `fix: ...`
- `docs: ...`
- `test: ...`
- `chore: ...`

## Pull Requests

1. Create a feature branch.
2. Add tests for new behavior.
3. Ensure `./gradlew test` passes.
4. Update docs and changelog.
5. Open a PR with:
- clear summary
- why the change is needed
- risk/compatibility notes

## Issue Reports

Please include:

- Spring Boot version
- Spring AI version
- Java version
- Reproducible sample or logs

## Release Notes

Maintainers should update `CHANGELOG.md` for user-visible changes before release.

