# Releasing

This project uses tag-based publishing.

## One-time Setup

Configure repository secrets:

- `OSSRH_USERNAME`
- `OSSRH_PASSWORD`
- `SIGNING_KEY` (ASCII-armored private key)
- `SIGNING_PASSWORD`

## Release Flow

1. Ensure `master` is green (`CI` workflow passing).
2. Update `CHANGELOG.md` and move notable items from `Unreleased` to the target version section.
3. Create and push a tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

4. `Publish` workflow resolves `projectVersion=0.1.0` from the tag and runs:

```bash
./gradlew -PprojectVersion=0.1.0 publish
```

5. Workflow creates a GitHub Release automatically from the pushed tag.

## Manual Publish (optional)

You can also trigger `Publish` workflow manually and provide `version` input.

## Snapshot Publish

To publish snapshots manually from local:

```bash
./gradlew -PprojectVersion=0.1.1-SNAPSHOT publish
```

