# Release Script Documentation

## Overview

The `release.sh` script automates dual-channel releases for the Yakcov library. It reads `compose-releases.toml` to determine which Compose Multiplatform versions to target, then builds and publishes an artifact for each channel to Maven Central.

## What the Script Does

For each channel defined in `compose-releases.toml`:

1. **Determines the next tag** by analyzing existing git tags and incrementing
2. **Cleans build state** (Gradle build + `kotlin-js-store`) to avoid stale JS/Wasm caches
3. **Patches `gradle/libs.versions.toml`** with the channel's compose, material3, and kotlin versions
4. **Builds and publishes** to Maven Central via Gradle with `-PpublishVersion=$TAG`
5. **Creates and pushes the git tag** to the remote repository
6. **Restores `libs.versions.toml`** to its original state

## Configuration

### `compose-releases.toml`

Defines the release channels at the project root:

```toml
[stable]
compose = "1.11.1"
compose-material3 = "1.11.0-alpha07"
xcode = "26.3"  # Compose 1.11 iOS needs a newer Xcode than the runner default

[next]
track = "prerelease"
compose = "1.12.0-alpha01"
compose-material3 = "1.12.0-alpha01"
kotlin = "2.4.0"          # Compose 1.12 bundles the compose-compiler with Kotlin 2.4.0
xcode = "26.3"
```

| Key | Required | Description |
|-----|----------|-------------|
| `compose` | Yes | Compose Multiplatform plugin + dependency version |
| `compose-material3` | Yes | Material3 artifact version (often differs from compose) |
| `kotlin` | No | Kotlin version override. Omit to inherit from `libs.versions.toml` |
| `track` | No | Upstream channel the version tracker follows: `stable` or `prerelease`. Defaults to `stable` for `[stable]`, `prerelease` otherwise |
| `xcode` | No | Xcode version for the Apple CI/publish job. Omit to use the runner default |

Comment out a section to skip that channel (e.g. retire `[next]` to release only stable). Note that `compose` is shared but **AGP and the Gradle wrapper are not per-channel** — a channel needing a newer AGP/compileSdk (as Compose 1.12 does: AGP ≥ 9.1.0 / compileSdk 37) raises the floor for the whole repo. See CLAUDE.md → Dual-Channel Releases → *Toolchain coupling*.

## Usage

### Prerequisites

- Yakcov signing keys on your machine (required for Maven Central publishing)
- Run from the project root directory
- Push permissions to the repository

### Commands

```bash
# Release all channels (default)
./scripts/release.sh

# Release a specific channel only
./scripts/release.sh --channel stable
./scripts/release.sh --channel next

# Preview what would happen without making changes
./scripts/release.sh --dry-run

# Combine flags
./scripts/release.sh --channel next --dry-run
```

### Example Output

```
[INFO] === Release Plan ===

[stable] compose=1.11.1  material3=1.11.0-alpha07  kotlin=<inherited>
[stable] tag=1.11.1
[next] compose=1.12.0-alpha01  material3=1.12.0-alpha01  kotlin=2.4.0
[next] tag=1.12.0-alpha01

Continue with release? (y/N):
```

## Tag Naming

| Channel | First release | Subsequent releases |
|---------|--------------|---------------------|
| stable | `1.11.1` | `1.11.1-1`, `1.11.1-2` |
| next | `1.12.0-alpha01` | `1.12.0-alpha01-1`, `1.12.0-alpha01-2` |

The script automatically detects existing tags and increments.

The `-N` suffix disambiguates **sequential re-releases of one channel**. It is not a way to
publish two channels at once: because a tag is only created at the end of a channel's release,
channels sharing a `compose` version all resolve to the *same* tag up front, and the publish
matrix runs them in parallel — racing one Maven version, one git tag and one GitHub release.
`assert_distinct_channel_tags` therefore refuses the release plan and the CI matrix outright:

```
[ERROR] Channels 'stable' and 'next' both resolve to release tag '1.12.0'.
[ERROR] Give them distinct 'compose' versions in compose-releases.toml, or retire one channel.
```

Auto-suffixing instead would silently publish a duplicate build as `1.12.0-1`, which *outranks*
`1.12.0` in Gradle/Maven version ordering — so when upstream has no prerelease ahead of stable,
release only the stable channel (`--channel stable`) rather than syncing the two.

## CI Matrix Testing

The CI workflow (`.github/workflows/checks.yml`) automatically tests against all channels defined in `compose-releases.toml`. Each test job (JS, JVM, Apple) runs once per channel using a matrix strategy.

## Failure Handling

- If the build fails for a channel, no tag is pushed (clean rollback)
- If one channel succeeds and another fails, the script reports which failed
- Re-run with `--channel <name>` to retry only the failed channel

## Testing

Test the release logic without making any changes:

```bash
./scripts/test-release-logic.sh
```

This validates: TOML parsing, tag computation, version patching, and restoration.

## Manual Override

To publish manually without the script:

```bash
./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -PpublishVersion=1.11.1
```

## CI Release (GitHub Actions)

A GitHub Actions workflow allows releasing from CI instead of locally.

### Setup (one-time)

Your local machine uses `~/.gradle/gradle.properties` for Maven Central credentials and GPG signing. CI needs the same values as GitHub secrets.

1. **Create a GitHub environment** called `maven-central` at:
   `https://github.com/chrisjenx/yakcov/settings/environments`

2. **Add these secrets** to the `maven-central` environment, using the values from your `~/.gradle/gradle.properties`:

   | Secret | gradle.properties equivalent | Description |
   |--------|------------------------------|-------------|
   | `MAVEN_CENTRAL_USERNAME` | `mavenCentralUsername` | Sonatype Central Portal user token username |
   | `MAVEN_CENTRAL_PASSWORD` | `mavenCentralPassword` | Sonatype Central Portal user token password |
   | `SIGNING_KEY` | n/a (file-based locally) | Run: `gpg --export-secret-keys --armor <key-id>` (full output) |
   | `SIGNING_KEY_ID` | `signing.keyId` | Last 8 hex characters of your GPG key ID |
   | `SIGNING_KEY_PASSWORD` | `signing.password` | GPG key passphrase |

   > **Note:** Locally you use `signing.secretKeyRingFile` to point at a GPG keyring file. CI uses the in-memory key (`signingInMemoryKey`) instead — this is the ASCII-armored export of the same key.

### Usage

**From GitHub UI:**
1. Go to Actions → "Release" workflow
2. Click "Run workflow"
3. Select channel (`all`, `stable`, or `next`)
4. Optionally check "Dry run" to build without publishing
5. Click "Run workflow"

**From CLI:**
```bash
# Release all channels
gh workflow run release.yml -f channel=all

# Release stable only
gh workflow run release.yml -f channel=stable

# Dry run
gh workflow run release.yml -f channel=all -f dry-run=true
```

## Troubleshooting

### "compose-releases.toml not found"
- Run the script from the project root

### Build fails for one channel
- Check if the Compose/Kotlin version combination is compatible
- Try running `./gradlew build` with the patched versions manually
- Re-run with `--channel <name>` after fixing

### Maven Central publishing fails
- Ensure signing keys are configured
- Check network connectivity
- Verify Maven Central credentials

## Files

| File | Purpose |
|------|---------|
| `compose-releases.toml` | Defines Compose version channels |
| `scripts/release.sh` | Main dual-channel release script |
| `scripts/test-release-logic.sh` | Non-destructive test script |
| `RELEASE_SCRIPT.md` | This documentation |
