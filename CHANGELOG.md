# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project follows Semantic Versioning.

## [Unreleased]

### Added

- Versioning policy document (`VERSIONING.md`)
- Minimal consumer sample project (`examples/minimal-webmvc-gradle`)

### Changed

- Updated the Spring Boot 3.5 compatibility line to Spring Boot 3.5.9, springdoc-openapi 2.8.17, and Spring AI BOM 1.1.5
- Updated Gradle wrapper to 9.5.0 and GitHub Actions release/setup actions
- Dependabot now groups routine updates and ignores Spring Boot 4 / springdoc 3 / Spring AI 2 major-line moves for the 0.1.x compatibility line
- README now links the listed `awesome-mcp-servers` PR and clarifies the supported compatibility line
- Minimal example now uses the same 0.1.x dependency line and executable Gradle wrapper as the root project
- CI now tests Java 17 and Java 21 matrix
- Publish workflow now validates SemVer format and changelog release entry
- Publish workflow now runs tests before artifact publication
- Build now verifies project version format for publish tasks
- README and release guide expanded for external-consumer usage

### Deprecated

- None

### Removed

- None

### Fixed

- None

### Security

- None

## [0.1.0] - 2026-02-15

### Added

- Spring Boot auto-configuration starter for Swagger/OpenAPI to MCP tool bridge
- OpenAPI operation to MCP tool schema converter
- MCP server adapter for tool registration and execution
- Smart context tools (`meta_discover_api_tools`, `meta_invoke_api_by_intent`)
- Response optimizer with JSONPath projection and summarization
- Security policy layer with risky-operation confirmation, role checks, and audit logging
- Unit and integration tests for conversion and runtime registration/execution
- OSS-friendly metadata, CI workflow, contribution/security docs
