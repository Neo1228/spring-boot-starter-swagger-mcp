# Versioning Policy

This project follows Semantic Versioning.

## Version Format

- Stable release: `MAJOR.MINOR.PATCH` (example: `1.2.3`)
- Prerelease: `MAJOR.MINOR.PATCH-<label>` (example: `1.2.3-rc.1`)
- Snapshot: `MAJOR.MINOR.PATCH-SNAPSHOT` (example: `1.2.4-SNAPSHOT`)

## Change Rules

- MAJOR: breaking behavior or configuration changes
- MINOR: backward-compatible features
- PATCH: backward-compatible bug fixes

## Release Expectations

- Every non-snapshot release must have:
  - a dated changelog entry in `CHANGELOG.md`
  - a git tag using the `v<version>` format
- CI validates SemVer format and changelog presence during release workflow.
