#!/bin/bash

# Test script to verify dual-channel release logic without making changes
# This script tests the logic of release.sh without creating tags or publishing

set -e

# Source shared release utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib-release.sh"

PASS=0
FAIL=0

# Capture $? before local resets it
assert_ok() {
    local rc=$?
    local label="$1"
    if [ $rc -eq 0 ]; then
        print_info "✓ $label"
        PASS=$((PASS + 1))
    else
        print_error "✗ $label"
        FAIL=$((FAIL + 1))
    fi
}

assert_not_empty() {
    local label="$1"
    local value="$2"
    if [ -n "$value" ]; then
        print_info "✓ $label: $value"
        PASS=$((PASS + 1))
    else
        print_error "✗ $label: (empty)"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
print_info "=== Testing Dual-Channel Release Logic ==="
echo ""

# Test 1: compose-releases.toml exists and is parseable
print_info "Test 1: compose-releases.toml"
test -f "compose-releases.toml"
assert_ok "compose-releases.toml exists"

CHANNELS=$(list_channels)
assert_not_empty "Channels found" "$CHANNELS"

# Test 2: Each channel has required keys
print_info ""
print_info "Test 2: Channel configuration"
for channel in $CHANNELS; do
    compose_ver=$(read_channel_key "$channel" "compose")
    material3_ver=$(read_channel_key "$channel" "compose-material3")
    kotlin_ver=$(read_channel_key "$channel" "kotlin")

    assert_not_empty "[$channel] compose" "$compose_ver"
    assert_not_empty "[$channel] compose-material3" "$material3_ver"
    if [ -n "$kotlin_ver" ]; then
        print_info "  [$channel] kotlin override: $kotlin_ver"
    else
        print_info "  [$channel] kotlin: inherited from libs.versions.toml"
    fi
done

# Test 3: Tag computation
print_info ""
print_info "Test 3: Tag computation"
for channel in $CHANNELS; do
    compose_ver=$(read_channel_key "$channel" "compose")
    tag=$(next_tag_for "$compose_ver")
    assert_not_empty "[$channel] next tag" "$tag"

    # Show existing tags for context
    existing=$(git tag -l "$compose_ver" "${compose_ver}-[0-9]*" 2>/dev/null | sort -V)
    if [ -n "$existing" ]; then
        print_info "  [$channel] existing tags: $(echo "$existing" | tr '\n' ' ')"
    fi
done

# Test 4: libs.versions.toml patching (dry run — patch then restore)
print_info ""
print_info "Test 4: Version patching (dry run)"
ORIGINAL_COMPOSE=$(grep "^compose = " gradle/libs.versions.toml | head -1 | sed 's/compose = "\(.*\)"/\1/')
ORIGINAL_MATERIAL3=$(grep "^compose-material3 = " gradle/libs.versions.toml | sed 's/compose-material3 = "\(.*\)"/\1/')
ORIGINAL_KOTLIN=$(grep "^kotlin = " gradle/libs.versions.toml | sed 's/kotlin = "\(.*\)"/\1/')

assert_not_empty "Original compose" "$ORIGINAL_COMPOSE"
assert_not_empty "Original compose-material3" "$ORIGINAL_MATERIAL3"
assert_not_empty "Original kotlin" "$ORIGINAL_KOTLIN"

for channel in $CHANNELS; do
    compose_ver=$(read_channel_key "$channel" "compose")
    material3_ver=$(read_channel_key "$channel" "compose-material3")
    kotlin_ver=$(read_channel_key "$channel" "kotlin")

    # Patch using shared function
    patch_versions "$channel"

    # Verify patch applied
    PATCHED_COMPOSE=$(grep "^compose = " gradle/libs.versions.toml | head -1 | sed 's/compose = "\(.*\)"/\1/')
    PATCHED_MATERIAL3=$(grep "^compose-material3 = " gradle/libs.versions.toml | sed 's/compose-material3 = "\(.*\)"/\1/')

    if [ "$PATCHED_COMPOSE" = "$compose_ver" ] && [ "$PATCHED_MATERIAL3" = "$material3_ver" ]; then
        print_info "✓ [$channel] patch applied correctly (compose=$PATCHED_COMPOSE, material3=$PATCHED_MATERIAL3)"
        PASS=$((PASS + 1))
    else
        print_error "✗ [$channel] patch failed (got compose=$PATCHED_COMPOSE, material3=$PATCHED_MATERIAL3)"
        FAIL=$((FAIL + 1))
    fi

    if [ -n "$kotlin_ver" ]; then
        PATCHED_KOTLIN=$(grep "^kotlin = " gradle/libs.versions.toml | sed 's/kotlin = "\(.*\)"/\1/')
        if [ "$PATCHED_KOTLIN" = "$kotlin_ver" ]; then
            print_info "✓ [$channel] kotlin patch applied correctly ($PATCHED_KOTLIN)"
            PASS=$((PASS + 1))
        else
            print_error "✗ [$channel] kotlin patch failed (got $PATCHED_KOTLIN)"
            FAIL=$((FAIL + 1))
        fi
    fi

    # Restore
    restore_versions
done

# Verify restore worked
RESTORED_COMPOSE=$(grep "^compose = " gradle/libs.versions.toml | head -1 | sed 's/compose = "\(.*\)"/\1/')
if [ "$RESTORED_COMPOSE" = "$ORIGINAL_COMPOSE" ]; then
    print_info "✓ libs.versions.toml restored to original"
    PASS=$((PASS + 1))
else
    print_error "✗ libs.versions.toml NOT restored (got $RESTORED_COMPOSE, expected $ORIGINAL_COMPOSE)"
    FAIL=$((FAIL + 1))
fi

# Test 5: JSON matrix output (used by CI)
print_info ""
print_info "Test 5: JSON matrix output"
MATRIX=$("$SCRIPT_DIR/lib-release.sh" --json-matrix)
if echo "$MATRIX" | python3 -c "import sys,json; json.load(sys.stdin)" 2>/dev/null; then
    print_info "✓ JSON matrix is valid: $MATRIX"
    PASS=$((PASS + 1))
else
    print_error "✗ JSON matrix is invalid: $MATRIX"
    FAIL=$((FAIL + 1))
fi

# Test 6: Gradle wrapper exists
print_info ""
print_info "Test 6: Prerequisites"
if [ -f "./gradlew" ]; then
    print_info "✓ Gradle wrapper found"
    PASS=$((PASS + 1))
else
    print_error "✗ Gradle wrapper not found"
    FAIL=$((FAIL + 1))
fi

# Summary
echo ""
print_info "=== TEST SUMMARY ==="
for channel in $CHANNELS; do
    compose_ver=$(read_channel_key "$channel" "compose")
    material3_ver=$(read_channel_key "$channel" "compose-material3")
    kotlin_ver=$(read_channel_key "$channel" "kotlin")
    tag=$(next_tag_for "$compose_ver")
    print_channel "$channel" "compose=$compose_ver  material3=$material3_ver  kotlin=${kotlin_ver:-$ORIGINAL_KOTLIN}  →  tag=$tag"
done
echo ""
print_info "Gradle Command: ./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -PpublishVersion=\$TAG"
echo ""
print_info "Passed: $PASS  Failed: $FAIL"
if [ "$FAIL" -gt 0 ]; then
    print_error "Some tests failed!"
    exit 1
fi
print_info "✓ All tests passed!"
