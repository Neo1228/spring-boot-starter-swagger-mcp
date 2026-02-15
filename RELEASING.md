# Releasing

## Required Secrets

Configure these GitHub Actions secrets:

- `OSSRH_USERNAME`
- `OSSRH_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

## Release Steps

1. Ensure `master` CI is green.
2. Update `CHANGELOG.md`.
3. Create and push a tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

4. The `Publish` workflow runs automatically and publishes artifacts.

