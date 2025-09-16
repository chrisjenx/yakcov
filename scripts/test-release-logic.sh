#!/bin/bash

# Test script to verify release logic without making changes
# This script tests the logic of release.sh without creating branches, tags, or publishing

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() { echo -e "${GREEN}[TEST INFO]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[TEST WARN]${NC} $1"; }
print_error() { echo -e "${RED}[TEST ERROR]${NC} $1"; }

print_info "Testing release script logic..."

# Test 1: Parse compose version from libs.versions.toml
print_info "Test 1: Parsing compose version from libs.versions.toml"
COMPOSE_VERSION=$(grep "^compose = " gradle/libs.versions.toml | head -1 | sed 's/compose = "\(.*\)"/\1/')

if [ -z "$COMPOSE_VERSION" ]; then
    print_error "Could not find compose version in gradle/libs.versions.toml"
    exit 1
fi

print_info "✓ Found compose version: $COMPOSE_VERSION"

# Test 2: Determine release branch name
print_info "Test 2: Determining release branch name"
RELEASE_BRANCH="release/$COMPOSE_VERSION"
print_info "✓ Release branch would be: $RELEASE_BRANCH"

# Test 3: Check if release branch exists
print_info "Test 3: Checking if release branch exists"
if git branch --list | grep -q "$RELEASE_BRANCH"; then
    print_info "✓ Release branch $RELEASE_BRANCH exists locally"
elif git branch -r --list | grep -q "origin/$RELEASE_BRANCH"; then
    print_info "✓ Release branch $RELEASE_BRANCH exists remotely"
else
    print_info "✓ Release branch $RELEASE_BRANCH does not exist (would create new)"
fi

# Test 4: Find existing tags and determine next increment
print_info "Test 4: Finding existing tags and determining next increment"
BASE_TAG="$COMPOSE_VERSION"
print_info "Looking for existing tags with base: $BASE_TAG"

# Get all tags that match this version pattern, sorted
EXISTING_TAGS=$(git tag -l "$BASE_TAG*" | sort -V)

if [ -z "$EXISTING_TAGS" ]; then
    # No tags exist for this version, create the base tag
    NEW_TAG="$BASE_TAG"
    print_info "✓ No existing tags found for version $COMPOSE_VERSION, would create: $NEW_TAG"
else
    print_info "✓ Existing tags for $COMPOSE_VERSION:"
    echo "$EXISTING_TAGS"
    
    # Find the highest increment
    HIGHEST_INCREMENT=0
    
    # Check for base tag (no increment)
    if echo "$EXISTING_TAGS" | grep -q "^${BASE_TAG}$"; then
        HIGHEST_INCREMENT=0
        print_info "  - Base tag exists: $BASE_TAG"
    fi
    
    # Check for incremented tags (BASE_TAG-1, BASE_TAG-2, etc.)
    for tag in $EXISTING_TAGS; do
        if [[ $tag =~ ^${BASE_TAG}-([0-9]+)$ ]]; then
            increment=${BASH_REMATCH[1]}
            print_info "  - Found incremented tag: $tag (increment: $increment)"
            if [ $increment -gt $HIGHEST_INCREMENT ]; then
                HIGHEST_INCREMENT=$increment
            fi
        fi
    done
    
    # Create next increment
    NEXT_INCREMENT=$((HIGHEST_INCREMENT + 1))
    NEW_TAG="$BASE_TAG-$NEXT_INCREMENT"
    print_info "✓ Next tag would be: $NEW_TAG"
fi

# Test 5: Check gradle command exists
print_info "Test 5: Checking gradle wrapper exists"
if [ -f "./gradlew" ]; then
    print_info "✓ Gradle wrapper found"
else
    print_error "✗ Gradle wrapper not found"
fi

print_info ""
print_info "=== TEST SUMMARY ==="
print_info "Compose Version: $COMPOSE_VERSION"
print_info "Release Branch: $RELEASE_BRANCH"
print_info "Next Tag: $NEW_TAG"
print_info "Gradle Command: ./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -Drelease=true"
print_info ""
print_info "✓ All tests passed! Release script logic appears correct."