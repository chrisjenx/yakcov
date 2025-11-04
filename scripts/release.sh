#!/bin/bash

# Yakcov Release Script
# Based on RELEASE.md instructions
# Automates the release process for Yakcov library

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check if we're in the right directory
if [ ! -f "RELEASE_SCRIPT.md" ]; then
    print_error "Not in yakcov project root directory. Please run from project root."
    exit 1
fi

# Parse compose version from libs.versions.toml (ignore pre-release/build metadata; keep only semver)
RAW_COMPOSE_VERSION=$(grep "^compose = " gradle/libs.versions.toml | head -1 | sed 's/compose = "\(.*\)"/\1/')

if [ -z "$RAW_COMPOSE_VERSION" ]; then
    print_error "Could not find compose version in gradle/libs.versions.toml"
    exit 1
fi

# Extract strict semver (MAJOR.MINOR.PATCH) e.g. 1.10.0 from 1.10.0-beta01
COMPOSE_VERSION=$(echo "$RAW_COMPOSE_VERSION" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)

if [ -z "$COMPOSE_VERSION" ]; then
    print_error "Could not parse semver (MAJOR.MINOR.PATCH) from compose version: $RAW_COMPOSE_VERSION"
    exit 1
fi

print_info "Found compose version: $RAW_COMPOSE_VERSION (using semver: $COMPOSE_VERSION)"

# Determine release branch name
RELEASE_BRANCH="release/$COMPOSE_VERSION"
print_info "Release branch: $RELEASE_BRANCH"

# Check if release branch exists locally
if git branch --list | grep -q "$RELEASE_BRANCH"; then
    print_info "Release branch $RELEASE_BRANCH exists locally, switching to it"
    git checkout "$RELEASE_BRANCH"
else
    # Check if release branch exists remotely
    if git branch -r --list | grep -q "origin/$RELEASE_BRANCH"; then
        print_info "Release branch $RELEASE_BRANCH exists remotely, checking it out"
        git checkout -b "$RELEASE_BRANCH" "origin/$RELEASE_BRANCH"
    else
        print_info "Creating new release branch: $RELEASE_BRANCH"
        git checkout -b "$RELEASE_BRANCH"
    fi
fi

# Find existing tags for this version to determine next increment
BASE_TAG="$COMPOSE_VERSION"
print_info "Looking for existing tags with base: $BASE_TAG"

# Get all tags that match this version pattern, sorted
EXISTING_TAGS=$(git tag -l "$BASE_TAG*" | sort -V)

if [ -z "$EXISTING_TAGS" ]; then
    # No tags exist for this version, create the base tag
    NEW_TAG="$BASE_TAG"
    print_info "No existing tags found for version $COMPOSE_VERSION, creating: $NEW_TAG"
else
    print_info "Existing tags for $COMPOSE_VERSION:"
    echo "$EXISTING_TAGS"
    
    # Find the highest increment
    HIGHEST_INCREMENT=0
    
    # Check for base tag (no increment)
    if echo "$EXISTING_TAGS" | grep -q "^${BASE_TAG}$"; then
        HIGHEST_INCREMENT=0
    fi
    
    # Check for incremented tags (BASE_TAG-1, BASE_TAG-2, etc.)
    for tag in $EXISTING_TAGS; do
        if [[ $tag =~ ^${BASE_TAG}-([0-9]+)$ ]]; then
            increment=${BASH_REMATCH[1]}
            if [ $increment -gt $HIGHEST_INCREMENT ]; then
                HIGHEST_INCREMENT=$increment
            fi
        fi
    done
    
    # Create next increment
    NEXT_INCREMENT=$((HIGHEST_INCREMENT + 1))
    NEW_TAG="$BASE_TAG-$NEXT_INCREMENT"
    print_info "Next tag will be: $NEW_TAG"
fi

# Confirm before creating tag and publishing
print_warn "About to:"
echo "  - Create git tag: $NEW_TAG"
echo "  - Push tag to remote"
echo "  - Build and publish to Maven Central"
echo ""
read -p "Continue? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_info "Aborted by user"
    exit 0
fi

# Create and push the tag
print_info "Creating git tag: $NEW_TAG"
git tag "$NEW_TAG"

print_info "Pushing tag to remote"
git push origin "$NEW_TAG"

# Build and release to Maven Central
print_info "Building and publishing to Maven Central..."
print_warn "Make sure you have the Yakcov keys on your machine!"
echo ""

./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -Drelease=true

print_info "Release completed successfully!"
print_info "Tag created: $NEW_TAG"
print_info "Branch: $RELEASE_BRANCH"