# Release Script Documentation

## Overview

The `release.sh` script automates the release process for the Yakcov library based on the instructions in `RELEASE.md`. It eliminates manual steps and reduces the chance of errors during releases.

## What the Script Does

1. **Parses the compose version** from `gradle/libs.versions.toml`
2. **Creates or switches to a release branch** following the pattern `release/X.Y.Z`
3. **Determines the next tag number** by analyzing existing tags and incrementing appropriately
4. **Creates and pushes the git tag** to the remote repository
5. **Executes the gradle publish command** to release to Maven Central

## Usage

### Prerequisites

- Ensure you have the Yakcov keys on your machine (required for Maven Central publishing)
- Run from the project root directory
- Ensure you have push permissions to the repository

### Basic Usage

```bash
./scripts/release.sh
```

The script will:
- Show you what it plans to do
- Ask for confirmation before making any changes
- Guide you through the release process with colored output

### Example Output

```
[INFO] Found compose version: 1.9.0
[INFO] Release branch: release/1.9.0
[INFO] Release branch release/1.9.0 does not exist (would create new)
[INFO] Looking for existing tags with base: 1.9.0
[INFO] No existing tags found for version 1.9.0, creating: 1.9.0
[WARN] About to:
  - Create git tag: 1.9.0
  - Push tag to remote
  - Build and publish to Maven Central

Continue? (y/N):
```

## Release Branch and Tag Naming

### Release Branches
- Format: `release/X.Y.Z` where X.Y.Z matches the compose version
- Example: `release/1.9.0`

### Tags
- First release for a version: `X.Y.Z` (e.g., `1.9.0`)
- Subsequent releases: `X.Y.Z-N` where N increments (e.g., `1.9.0-1`, `1.9.0-2`)
- The script automatically determines the next available tag number

## Testing

You can test the script logic without making any changes by running:

```bash
./scripts/test-release-logic.sh
```

This will show you what the script would do without actually creating branches, tags, or publishing.

## Error Handling

The script includes several safety checks:

- Verifies you're in the correct project directory
- Confirms the compose version can be parsed
- Shows you exactly what will happen before making changes
- Requires explicit confirmation before proceeding
- Uses `set -e` to exit immediately if any command fails

## Manual Override

If you need to bypass the script for any reason, you can still follow the manual process in `RELEASE.md`:

1. Switch to/Create a release branch matching the compose version
2. Create a tag matching the version, increment by 1 for each tag
3. Run: `./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -Drelease=true`

## Troubleshooting

### "Not in yakcov project root directory"
- Run the script from the project root where `RELEASE.md` exists

### "Could not find compose version in gradle/libs.versions.toml"
- Check that `gradle/libs.versions.toml` exists and contains a line like `compose = "X.Y.Z"`

### Maven Central publishing fails
- Ensure you have the Yakcov keys on your machine
- Check your network connection
- Verify your Maven Central credentials are configured correctly

## Files

- `scripts/release.sh` - Main release script
- `scripts/test-release-logic.sh` - Test script to validate logic
- `RELEASE_SCRIPT.md` - This documentation