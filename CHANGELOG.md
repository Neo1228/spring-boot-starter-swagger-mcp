# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project follows Semantic Versioning.

## [Unreleased]

### Added

- Versioning policy document (`VERSIONING.md`)
- Minimal consumer sample project (`examples/minimal-webmvc-gradle`)

### Changed

- CI now tests Java 17 and Java 21 matrix
- Publish workflow now validates SemVer format and changelog release entry
- Publish workflow now runs tests before artifact publication
- Build now verifies project version format for publish tasks
- README and release guide expanded for external-consumer usage

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
