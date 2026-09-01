#!/bin/bash

# Yakcov Dual-Channel Release Script
# Reads compose-releases.toml and builds/publishes for each configured channel.
# Usage: ./scripts/release.sh [--channel stable|next|all] [--dry-run]

set -e

# Source shared release utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib-release.sh"

# Defaults
CHANNEL_FILTER="all"
DRY_RUN=false
VERSION_PATCHED=false

# Restore libs.versions.toml on unexpected exit (Ctrl+C, errors, etc.)
trap '
    if $VERSION_PATCHED; then
        print_warn "Restoring libs.versions.toml after unexpected exit..."
        restore_versions
    fi
' EXIT

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --channel)
            CHANNEL_FILTER="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            echo "Usage: ./scripts/release.sh [--channel stable|next|all] [--dry-run]"
            echo ""
            echo "Options:"
            echo "  --channel <name>  Release only the specified channel (default: all)"
            echo "  --dry-run         Show what would happen without making changes"
            echo "  -h, --help        Show this help"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Check we're in the right directory
if [ ! -f "compose-releases.toml" ]; then
    print_error "compose-releases.toml not found. Please run from the project root."
    exit 1
fi

if [ ! -f "gradle/libs.versions.toml" ]; then
    print_error "gradle/libs.versions.toml not found."
    exit 1
fi

# --- Release logic for a single channel ---

release_channel() {
    local channel="$1"
    local compose_ver
    compose_ver=$(read_channel_key "$channel" "compose")
    local tag
    tag=$(next_tag_for "$compose_ver")

    print_channel "$channel" "Compose version: $compose_ver"
    print_channel "$channel" "Next tag: $tag"

    if $DRY_RUN; then
        print_channel "$channel" "[DRY RUN] Would clean build state"
        print_channel "$channel" "[DRY RUN] Would patch libs.versions.toml"
        print_channel "$channel" "[DRY RUN] Would build and publish with version: $tag"
        print_channel "$channel" "[DRY RUN] Would create and push git tag: $tag"
        print_channel "$channel" "[DRY RUN] Would create GitHub Release: $tag"
        return 0
    fi

    # Clean build state (yarn.lock and JS/Wasm caches differ per Compose/Kotlin version)
    print_channel "$channel" "Cleaning build state..."
    ./gradlew :library:clean --quiet 2>/dev/null || true
    rm -rf kotlin-js-store

    # Patch versions
    VERSION_PATCHED=true
    patch_versions "$channel"

    # Build and publish
    print_channel "$channel" "Building and publishing version $tag..."
    if ! ./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -PpublishVersion="$tag"; then
        print_error "Build/publish failed for channel '$channel'"
        restore_versions
        VERSION_PATCHED=false
        return 1
    fi

    # Restore versions before tagging (clean working tree)
    restore_versions
    VERSION_PATCHED=false

    # Create and push tag
    print_channel "$channel" "Creating git tag: $tag"
    git tag "$tag"
    print_channel "$channel" "Pushing tag to remote"
    git push origin "$tag"

    # Create a GitHub Release for the tag — a pushed tag is not a Release on its own. Prerelease
    # keys off the channel (the next/prerelease channel), not the tag's "-N" collision suffix.
    # Non-fatal: the artifact is already on Maven Central and the tag is pushed.
    if command -v gh >/dev/null 2>&1; then
        local prerelease=""
        if [ "$channel" != "stable" ]; then prerelease="--prerelease"; fi
        print_channel "$channel" "Creating GitHub Release: $tag"
        gh release create "$tag" --title "$tag" --generate-notes --verify-tag $prerelease \
            || print_channel "$channel" "WARN: gh release create failed — tag is pushed, create the Release manually"
    else
        print_channel "$channel" "gh CLI not found — skipping GitHub Release (tag pushed); install gh or create it manually"
    fi

    print_channel "$channel" "Released successfully: $tag"
    return 0
}

# --- Main ---

CHANNELS=$(list_channels)

if [ -z "$CHANNELS" ]; then
    print_error "No channels found in compose-releases.toml"
    exit 1
fi

# Filter channels
if [ "$CHANNEL_FILTER" != "all" ]; then
    if ! echo "$CHANNELS" | grep -q "^${CHANNEL_FILTER}$"; then
        print_error "Channel '$CHANNEL_FILTER' not found in compose-releases.toml"
        print_info "Available channels: $(echo "$CHANNELS" | tr '\n' ' ')"
        exit 1
    fi
    CHANNELS="$CHANNEL_FILTER"
fi

# Checked up front, not in release_channel: that recomputes next_tag_for after the previous
# channel's tag is pushed, which hides the clash behind a "-N" suffix.
# shellcheck disable=SC2086  # intentional word splitting over the channel list
if ! assert_distinct_channel_tags $CHANNELS; then
    exit 1
fi

# Show plan
echo ""
print_info "=== Release Plan ==="
for channel in $CHANNELS; do
    compose_ver=$(read_channel_key "$channel" "compose")
    material3_ver=$(read_channel_key "$channel" "compose-material3")
    kotlin_ver=$(read_channel_key "$channel" "kotlin")
    tag=$(next_tag_for "$compose_ver")
    echo ""
    print_channel "$channel" "compose=$compose_ver  material3=$material3_ver  kotlin=${kotlin_ver:-<inherited>}"
    print_channel "$channel" "tag=$tag"
done
echo ""

if $DRY_RUN; then
    print_warn "[DRY RUN] No changes will be made."
    echo ""
    for channel in $CHANNELS; do
        release_channel "$channel"
        echo ""
    done
    print_info "Dry run complete."
    exit 0
fi

# Confirm
read -p "$(echo -e "${YELLOW}Continue with release? (y/N):${NC} ")" -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_info "Aborted by user"
    exit 0
fi

# Execute releases
SUCCEEDED=()
FAILED=()

for channel in $CHANNELS; do
    echo ""
    print_info "=== Releasing channel: $channel ==="
    if release_channel "$channel"; then
        SUCCEEDED+=("$channel")
    else
        FAILED+=("$channel")
    fi
done

# Summary
echo ""
print_info "=== Release Summary ==="
if [ ${#SUCCEEDED[@]} -gt 0 ]; then
    print_info "Succeeded: ${SUCCEEDED[*]}"
fi
if [ ${#FAILED[@]} -gt 0 ]; then
    print_error "Failed: ${FAILED[*]}"
    print_warn "Re-run with --channel <name> to retry failed channels."
    exit 1
fi

print_info "All channels released successfully!"
