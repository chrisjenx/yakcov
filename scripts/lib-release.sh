#!/bin/bash
# Shared release utilities for Yakcov
# Source this file from release scripts, or call directly for CI matrix output.
#
# Usage (sourced):
#   source scripts/lib-release.sh
#   list_channels
#   read_channel_key "stable" "compose"
#   next_tag_for "1.10.3"
#   patch_versions "stable"
#   restore_versions
#
# Usage (CLI for CI):
#   scripts/lib-release.sh --json-matrix [--channel stable|next|all]
#   scripts/lib-release.sh --patch <channel>

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

print_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }
print_channel() { echo -e "${CYAN}[$1]${NC} $2"; }

# --- TOML parsing ---

# List all channel names from compose-releases.toml
list_channels() {
    grep '^\[' compose-releases.toml | sed 's/\[\(.*\)\]/\1/' | tr -d ' '
}

# Read a key from a specific channel section
# Usage: read_channel_key <channel> <key>
read_channel_key() {
    local channel="$1"
    local key="$2"
    awk -v section="[$channel]" -v key="$key" '
        $0 == section { found=1; next }
        /^\[/ { found=0 }
        found && $0 ~ "^"key" *=" {
            gsub(/^[^=]*= *"/, ""); gsub(/".*$/, ""); print
        }
    ' compose-releases.toml
}

# --- Tag logic ---

# Determine the next tag for a given base version.
# Uses strict matching: only exact base tag or base-N increments.
# Usage: next_tag_for <base_version>
next_tag_for() {
    local base="$1"
    # Match exact base tag and base-N patterns only (not partial prefixes)
    local existing
    existing=$(git tag -l "$base" "${base}-[0-9]*" 2>/dev/null | sort -V)

    if [ -z "$existing" ]; then
        echo "$base"
        return
    fi

    local highest=0

    while IFS= read -r tag; do
        if [[ $tag =~ ^${base}-([0-9]+)$ ]]; then
            local inc=${BASH_REMATCH[1]}
            if [ "$inc" -gt "$highest" ]; then
                highest=$inc
            fi
        fi
    done <<< "$existing"

    # If base tag exists, next is base-1 (or higher)
    if echo "$existing" | grep -q "^${base}$"; then
        echo "${base}-$((highest + 1))"
    elif [ "$highest" -gt 0 ]; then
        echo "${base}-$((highest + 1))"
    else
        echo "$base"
    fi
}

# Fail if two channels resolve to the same release tag.
#
# A tag is only created at the end of a channel's release, so next_tag_for cannot see it
# yet and channels sharing a `compose` version all resolve to one tag — which the publish
# matrix then races in parallel. Auto-suffixing instead would ship a duplicate as
# 1.12.0-1, which outranks 1.12.0 in Gradle/Maven ordering, so refuse loudly.
#
# Usage: assert_distinct_channel_tags <channel> [channel...]
assert_distinct_channel_tags() {
    local seen_tags="" seen_channels=""
    local channel compose_ver tag idx prev_tag prev_channel

    for channel in "$@"; do
        compose_ver=$(read_channel_key "$channel" "compose")
        # Channels without a compose version are skipped by the matrix too
        [ -z "$compose_ver" ] && continue
        tag=$(next_tag_for "$compose_ver")

        idx=1
        for prev_tag in $seen_tags; do
            if [ "$prev_tag" = "$tag" ]; then
                prev_channel=$(echo "$seen_channels" | cut -d' ' -f"$idx")
                print_error "Channels '$prev_channel' and '$channel' both resolve to release tag '$tag'." >&2
                print_error "Give them distinct 'compose' versions in compose-releases.toml, or retire one channel." >&2
                return 1
            fi
            idx=$((idx + 1))
        done

        seen_tags="${seen_tags:+$seen_tags }$tag"
        seen_channels="${seen_channels:+$seen_channels }$channel"
    done

    return 0
}

# --- Version patching ---

# Detect sed in-place flag (GNU vs BSD)
_sed_inplace() {
    if sed --version 2>/dev/null | grep -q GNU; then
        sed -i "$@"
    else
        sed -i '' "$@"
    fi
}

# Patch libs.versions.toml with values from a channel
# Usage: patch_versions <channel>
patch_versions() {
    local channel="$1"
    local compose_ver material3_ver kotlin_ver jvm_target_ver

    compose_ver=$(read_channel_key "$channel" "compose")
    material3_ver=$(read_channel_key "$channel" "compose-material3")
    kotlin_ver=$(read_channel_key "$channel" "kotlin")
    jvm_target_ver=$(read_channel_key "$channel" "jvm-target")

    if [ -z "$compose_ver" ]; then
        print_error "Channel '$channel' missing required 'compose' key"
        return 1
    fi
    if [ -z "$material3_ver" ]; then
        print_error "Channel '$channel' missing required 'compose-material3' key"
        return 1
    fi

    print_channel "$channel" "Patching libs.versions.toml: compose=$compose_ver, compose-material3=$material3_ver"
    _sed_inplace "s/^compose = \".*\"/compose = \"$compose_ver\"/" gradle/libs.versions.toml
    _sed_inplace "s/^compose-material3 = \".*\"/compose-material3 = \"$material3_ver\"/" gradle/libs.versions.toml

    if [ -n "$kotlin_ver" ]; then
        print_channel "$channel" "Patching kotlin=$kotlin_ver"
        _sed_inplace "s/^kotlin = \".*\"/kotlin = \"$kotlin_ver\"/" gradle/libs.versions.toml
    fi

    if [ -n "$jvm_target_ver" ]; then
        print_channel "$channel" "Patching jvmTarget=$jvm_target_ver"
        _sed_inplace "s/^jvmTarget = \".*\"/jvmTarget = \"$jvm_target_ver\"/" gradle/libs.versions.toml
    fi
}

# Restore libs.versions.toml to its git state
restore_versions() {
    git checkout -- gradle/libs.versions.toml 2>/dev/null || true
}

# --- CLI mode (for CI) ---

# Output JSON matrix for GitHub Actions
# Usage: _json_matrix [channel_filter]
_json_matrix() {
    local channel_filter="${1:-all}"
    local matrix="["
    local first=true
    local channels=""

    for channel in $(list_channels); do
        if [ "$channel_filter" != "all" ] && [ "$channel_filter" != "$channel" ]; then
            continue
        fi
        channels="${channels:+$channels }$channel"
    done

    # shellcheck disable=SC2086  # intentional word splitting over the channel list
    assert_distinct_channel_tags $channels || return 1

    for channel in $channels; do
        local compose_ver material3_ver kotlin_ver jvm_target_ver xcode_ver tag
        compose_ver=$(read_channel_key "$channel" "compose")
        material3_ver=$(read_channel_key "$channel" "compose-material3")
        kotlin_ver=$(read_channel_key "$channel" "kotlin")
        jvm_target_ver=$(read_channel_key "$channel" "jvm-target")
        xcode_ver=$(read_channel_key "$channel" "xcode")

        # Skip channels missing required compose key
        if [ -z "$compose_ver" ]; then
            continue
        fi

        tag=$(next_tag_for "$compose_ver")

        $first || matrix+=","
        first=false
        matrix+="{\"channel\":\"$channel\",\"compose\":\"$compose_ver\",\"material3\":\"$material3_ver\",\"kotlin\":\"${kotlin_ver:-}\",\"jvmTarget\":\"${jvm_target_ver:-}\",\"xcode\":\"${xcode_ver:-}\",\"tag\":\"$tag\"}"
    done

    matrix+="]"
    echo "$matrix"
}

# CLI entry point (only runs when executed directly, not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-}" in
        --json-matrix)
            _json_matrix "${2:-all}"
            ;;
        --patch)
            if [ -z "${2:-}" ]; then
                print_error "Usage: $0 --patch <channel>"
                exit 1
            fi
            patch_versions "$2"
            ;;
        --help|-h)
            echo "Usage:"
            echo "  $0 --json-matrix [all|stable|next]   Output JSON matrix for CI"
            echo "  $0 --patch <channel>                  Patch libs.versions.toml"
            echo ""
            echo "Or source this file for shell functions:"
            echo "  source $0"
            ;;
        *)
            print_error "Unknown command: ${1:-}"
            echo "Run $0 --help for usage"
            exit 1
            ;;
    esac
fi
