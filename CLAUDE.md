# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Yakcov (Yet Another Kotlin COmpose Validation) is a Kotlin Multiplatform Compose validation library published to Maven Central as `com.chrisjenx.yakcov:library`. It targets Android, JVM, JS, WasmJS, and iOS (arm64, simulatorArm64). Compose MP no longer supports Apple x86_64.

## Build & Test Commands

```bash
# Run all tests (requires Chrome for JS tests)
./gradlew :library:allTests

# Run tests by platform
./gradlew :library:testDebugUnitTest        # Android
./gradlew :library:jvmTest                   # JVM
./gradlew jsBrowserTest                      # JS (needs Chrome)
./gradlew wasmJsBrowserTest                  # WasmJS (needs Chrome)
./gradlew :library:iosSimulatorArm64Test     # iOS (macOS only)

# Build check
./gradlew build

# Publish locally for testing
./gradlew :library:publishToMavenLocal

# Release to Maven Central — dual-channel, interactive, needs signing keys
./scripts/release.sh                  # release all channels (stable + next)
./scripts/release.sh --channel stable # one channel only
./scripts/release.sh --dry-run        # show the plan, make no changes

# Test release script logic without side effects
./scripts/test-release-logic.sh

# Check for newer upstream Compose versions (see Dual-Channel Releases)
python3 scripts/check-compose-updates.py --check       # report drift (no changes)
python3 scripts/check-compose-updates.py --self-test    # offline unit tests
```

Requires JDK 17+. CI uses JDK 21 (Zulu). CI (`.github/workflows/checks.yml`) runs every platform's tests as a **matrix across both Compose channels**; the `next` (beta) leg is `continue-on-error`. The single required status check is the aggregate `CI-Gate` job — set branch protection on that, not on individual matrix legs (which change with `compose-releases.toml`). JS/WasmJS tests run through Karma with `ChromeHeadless`; if Chrome isn't auto-detected, set `CHROME_BIN` to the Chrome/Chromium binary.

## Modules

- **`:library`** — The published KMP library. All source under `library/src/`.
- **`:sample`** — Android demo app showing library usage.

## Architecture

The core validation model is a hierarchy rooted in `ValueValidator<V, R>`:

- **`ValueValidator<V, R>`** (`ValueValidator.kt`) — Abstract base class holding a `MutableState<V>`, a list of `ValueValidatorRule<R>`, and derived state for `validationResults`, `isValid`, `isError()`. The type params are `V` = the value type held in state, `R` = the type passed to rules for validation. Provides Compose `Modifier` extensions (`validationConfig`) for focus-lost validation, shake-on-invalid, and error display control.

- **`TextFieldValueValidator`** (`strings/TextFieldValueValidator.kt`) — Extends `ValueValidator<TextFieldValue, String>`. The primary validator for Compose `TextField`/`OutlinedTextField`. Maps `TextFieldValue` to `String` for rule evaluation. Use `rememberTextFieldValueValidator()` in Compose.

- **`GenericValueValidator<T>`** (`generic/GenericValueValidator.kt`) — Extends `ValueValidator<T, T>`. Validates any arbitrary type directly. Use `rememberGenericValueValidator()` in Compose.

- **`FieldValidator<V>`** (`FieldValidator.kt`) — Headless, presenter-ownable validator with a **plain constructor** (no `@Composable`, no coroutine scope, no `Modifier` dependency) for snapshot-presenter (Molecule) screens. Owns its draft in a snapshot `value` cell and folds the `ValueValidatorRule` list into a plain `@Immutable @Serializable` `FieldValidationState` (`severity` + `showError` + a `@Transient` `ValidationResult`) via the pure `toFieldState` bridges in `FieldValidationState.kt`. It is **separate from** `ValueValidator` (not a refactor), so the existing API is untouched. **It is a mutable, reference-identity holder — do not place it inside an immutable reducer-MVI `Model`;** for reducer-MVI store the draft + `FieldValidationState` and call `toFieldState` in `reduce()`. Render messages with `FieldValidationState.text()`/`supportingText()`; submit with `List<FieldValidator<*>>.allValid()` (reveals then checks).

- **`ValueValidatorRule<V>`** (`ValueValidatorRule.kt`) — SAM interface. Implement `validate(value: V): ValidationResult` to create custom rules.

- **`ValidationResult`** — Interface with `format(): String?` (Composable) and `outcome(): Outcome`. Two implementations:
  - `RegularValidationResult` — plain string messages
  - `ResourceValidationResult` — Compose `StringResource`-backed (i18n)
  - `Outcome` enum: `ERROR(40)`, `WARNING(30)`, `INFO(20)`, `SUCCESS(10)` — severity-ranked

- **Built-in rules** are in `strings/StringValueValidatorRules.kt` (Required, Email, Phone, etc.) and `generic/GenericValueValidatorRules.kt`.

## Platform-Specific Code

The only expect/actual split in production code is the `phoneUtil: PhoneNumberUtil` provider in `RegexExt.kt`, with actuals across `androidMain`, `iosMain`, `jsMain`, `jvmMain`, `wasmJsMain` (the `emailRegex` in that file is common code). Test source sets add their own (`PlatformIgnore` annotations, `initPhoneNumberUtil()`).

## Dual-Channel Releases

The library is published against one or more Compose Multiplatform versions in parallel. `compose-releases.toml` (repo root) is the single source of truth: each `[section]` is a channel defining `compose` + `compose-material3` (required) and optional `kotlin`/`xcode` overrides. Currently only `stable` is active; the `next` pre-release channel is retired (commented out) until there's a newer line to ride (e.g. Compose 1.12) — re-enable it by uncommenting its section. `gradle/libs.versions.toml` holds the working defaults; channels override them at release/CI time. (Deeper release-script docs: `RELEASE_SCRIPT.md`.)

Each channel may also set `track` (`stable` | `prerelease`) to declare which upstream Compose channel the version tracker follows; it defaults to `stable` for the `[stable]` section and `prerelease` for any other section.

- **`scripts/lib-release.sh`** — shared utilities, sourced by `release.sh` and called directly by CI. `--json-matrix [channel]` emits the GitHub Actions matrix; `--patch <channel>` rewrites `compose`/`compose-material3`/`kotlin` in `libs.versions.toml` in place. Tag naming: a channel's tag is its bare `compose` version, with `-N` appended if that tag already exists (`next_tag_for`).
- **Release flow** (per channel): clean build state (incl. `rm -rf kotlin-js-store`, since the yarn lock differs per Compose/Kotlin version; `*yarn.lock` is gitignored so CI checks out fresh and only local rebuilds need this) → patch versions → `publishAndReleaseToMavenCentral -PpublishVersion=<tag>` → restore `libs.versions.toml` → create & push git tag. `release.sh` restores `libs.versions.toml` on any exit via a trap, so a failed/aborted release never leaves the working tree dirty.
- **CI publishing runs on macOS** (`release.yml` `Publish` job). Kotlin/Native cannot build the iOS targets on Linux, so publishing from ubuntu would silently drop the iOS klibs (`kotlin.native.ignoreDisabledTargets=true`). The local `release.sh` must likewise be run from a Mac.
- **Local publishing** needs `--no-configuration-cache` (config cache is on in `gradle.properties` but breaks the publish/signing tasks) plus vanniktech in-memory signing props: `ORG_GRADLE_PROJECT_signingInMemoryKey` / `signingInMemoryKeyId` / `signingInMemoryKeyPassword` + `mavenCentralUsername`/`Password`. CI maps these from repo secrets via the `maven-central` environment.
- When changing Compose/Kotlin/material3 target versions, edit `compose-releases.toml`, **not** the CI workflows — both `checks.yml` and `release.yml` derive their matrix from it.

### Version tracking

`scripts/check-compose-updates.py` (stdlib-only Python) finds newer upstream Compose releases and rewrites `compose-releases.toml`. Per channel it derives the `compose` version (latest stable or prerelease, per `track`), the matching `compose-material3` (highest sharing the compose `major.minor`), and — best-effort, only for channels that already declare `kotlin` — the Kotlin version parsed from the JetBrains release notes (falling back to the minor's `vX.Y.0` GA notes; left unchanged and flagged when it can't be determined). Modes: `--check` (report, exit 3 on drift), `--write` (apply in place), `--pr-body PATH` (markdown summary), `--self-test` (offline). The `.github/workflows/track-compose.yml` workflow runs it weekly and opens a PR (branch `chore/track-compose`) that the normal Checks matrix validates before merge. Opening that PR requires repo setting *Actions → General → "Allow GitHub Actions to create and approve pull requests."*

## Key Design Decisions

- **All Compose dependencies are `compileOnly`** in `commonMain` to avoid forcing transitive deps on consumers. Tests use `implementation` so they actually resolve.
- **libphonenumber-kotlin is `compileOnly`** — consumers must add it themselves if they use phone validation rules.
- **kotlinx-serialization-core and compose runtime-saveable are `compileOnly`** — consumers who serialize `FieldValidationState` must add a serialization runtime themselves (in practice `kotlinx-serialization-json`, which pulls core transitively). `Outcome` persists by constant *name*; don't rename its constants without a migration. `FieldValidationState.result` is `@Transient` (the kotlinx `Transient`, not `kotlin.jvm`) and equality intentionally includes `result` so message-only changes still recompose.
- **Versioning is git-tag based**, resolved in `library/build.gradle.kts` by priority: explicit `-PpublishVersion=<x>` (used by the release scripts/CI) > `gitCurrentTag` when `-Drelease=true`/`release` property is set > `${gitCurrentTag}-${gitSha}` for dev builds. The dev path shells out to git at configuration time, so a shallow/tagless clone can't resolve a version — CI uses `fetch-depth: 0` + `git fetch --tags`.
- **`compose-material3` is versioned independently of `compose`** in `libs.versions.toml` and is often a different (e.g. alpha) version even when `compose` is stable. Keep them in sync with the values in `compose-releases.toml`.
- **Concrete validators (`TextFieldValueValidator`, `GenericValueValidator`) are `@Stable`, not `@Immutable`** — they hold mutable state (the abstract `ValueValidator` base is unannotated). If embedding in a data class, the parent must also be `@Stable`.
- **Do not copy validators** — update `validator.value` or call `validator.validate(newValue)` instead of creating new instances, which resets validation state.
