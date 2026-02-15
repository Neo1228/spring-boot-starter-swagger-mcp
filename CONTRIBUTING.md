# Contributing

## Principles

- Keep changes focused and minimal.
- Add or update tests for behavior changes.
- Update user-facing docs (`README.md`, `CHANGELOG.md`) when needed.

## Local Setup

```bash
git clone https://github.com/Neo1228/spring-boot-starter-swagger-mcp.git
cd spring-boot-starter-swagger-mcp
./gradlew test
```

To validate dependency-consumer behavior locally:

```bash
./gradlew publishToMavenLocal
cd examples/minimal-webmvc-gradle
./gradlew bootRun
```

## Commit Message Convention

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

- `feat:` A new feature
- `fix:` A bug fix
- `docs:` Documentation only changes
- `style:` Changes that do not affect the meaning of the code (white-space, formatting, etc)
- `refactor:` A code change that neither fixes a bug nor adds a feature
- `perf:` A code change that improves performance
- `test:` Adding missing tests or correcting existing tests
- `chore:` Changes to the build process or auxiliary tools and libraries

## Coding Standards

- Follow the standard Java coding conventions.
- Use 4 spaces for indentation.
- Keep line length under 120 characters where possible.
- Ensure all public classes and methods have Javadoc if the logic is complex.

## Pull Request Checklist

1. `./gradlew test` passes.
2. No generated artifacts or secrets are included.
3. PR description explains scope, reason, and compatibility impact.
4. `CHANGELOG.md` is updated for user-facing changes.
5. Commit messages follow the convention.
