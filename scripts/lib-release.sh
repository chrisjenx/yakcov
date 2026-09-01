#!/bin/bash
# Shared release utilities for Yakcov
# Source this file from release scripts, or call directly for CI matrix output.
#
# Usage (sourced):
#   source scripts/lib-release.sh
#   list_channels
#   read_channel_key "stable" "compose"
#   next_tag_for "1.10.3"
#   select_channels "stable"
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
print_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; }
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

# A channel's tag derives from its compose version, and a tag is only created at the end of
# that channel's release — so two channels sharing a version resolve to one tag, which the
# publish matrix then races in parallel. Auto-suffixing instead would ship a duplicate as
# 1.12.0-1, which outranks 1.12.0 in Gradle/Maven ordering, so refuse loudly.
#
# Compares the declared versions rather than next_tag_for's output: that keeps the check
# independent of which tags the checkout happens to have (checks.yml fetches none,
# release.yml fetches all).
#
# Usage: assert_distinct_channel_versions <channel> [channel...]
assert_distinct_channel_versions() {
    local seen_versions=() seen_channels=()
    local channel compose_ver i

    for channel in "$@"; do
        compose_ver=$(read_channel_key "$channel" "compose")
        # Channels without a compose version are skipped by the matrix too
        [ -z "$compose_ver" ] && continue

        for i in "${!seen_versions[@]}"; do
            if [ "${seen_versions[$i]}" = "$compose_ver" ]; then
                print_error "Channels '${seen_channels[$i]}' and '$channel' both declare compose = '$compose_ver'."
                print_error "Give them distinct 'compose' versions in compose-releases.toml, or retire one channel."
                return 1
            fi
        done

        seen_versions+=("$compose_ver")
        seen_channels+=("$channel")
    done

    return 0
}

# Echo the channels a caller should operate on, newline-separated, after validating both the
# config and the filter. Every consumer goes through here so the distinctness invariant cannot
# be reached without being checked.
#
# The invariant is asserted over the WHOLE file before filtering: it is a property of the
# config, not of the subset in play, so releasing one channel at a time must not sidestep it.
#
# Diagnostics go to stderr (print_error already does) because stdout is the channel list.
# Usage: channels=$(select_channels [all|<channel>]) || exit 1
select_channels() {
    local filter="${1:-all}"
    local channels
    channels=$(list_channels)

    if [ -z "$channels" ]; then
        print_error "No channels found in compose-releases.toml"
        return 1
    fi

    # shellcheck disable=SC2086  # intentional word splitting over the channel list
    assert_distinct_channel_versions $channels || return 1

    if [ "$filter" != "all" ]; then
        if ! echo "$channels" | grep -q "^${filter}$"; then
            print_error "Channel '$filter' not found in compose-releases.toml"
            print_info "Available channels: $(echo "$channels" | tr '\n' ' ')" >&2
            return 1
        fi
        channels="$filter"
    fi

    echo "$channels"
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
    local channels
    channels=$(select_channels "$channel_filter") || return 1

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
