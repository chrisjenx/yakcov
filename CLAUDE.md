# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Yakcov (Yet Another Kotlin COmpose Validation) is a Kotlin Multiplatform Compose validation library published to Maven Central as `com.chrisjenx.yakcov:library`. It targets Android, JVM, JS, WasmJS, and iOS (x64, arm64, simulatorArm64).

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

# Release to Maven Central (interactive, needs signing keys)
./scripts/release.sh

# Test release script logic without side effects
./scripts/test-release-logic.sh
```

Requires JDK 17+. CI uses JDK 21 (Zulu).

## Modules

- **`:library`** — The published KMP library. All source under `library/src/`.
- **`:sample`** — Android demo app showing library usage.

## Architecture

The core validation model is a hierarchy rooted in `ValueValidator<V, R>`:

- **`ValueValidator<V, R>`** (`ValueValidator.kt`) — Abstract base class holding a `MutableState<V>`, a list of `ValueValidatorRule<R>`, and derived state for `validationResults`, `isValid`, `isError()`. The type params are `V` = the value type held in state, `R` = the type passed to rules for validation. Provides Compose `Modifier` extensions (`validationConfig`) for focus-lost validation, shake-on-invalid, and error display control.

- **`TextFieldValueValidator`** (`strings/TextFieldValueValidator.kt`) — Extends `ValueValidator<TextFieldValue, String>`. The primary validator for Compose `TextField`/`OutlinedTextField`. Maps `TextFieldValue` to `String` for rule evaluation. Use `rememberTextFieldValueValidator()` in Compose.

- **`GenericValueValidator<T>`** (`generic/GenericValueValidator.kt`) — Extends `ValueValidator<T, T>`. Validates any arbitrary type directly. Use `rememberGenericValueValidator()` in Compose.

- **`ValueValidatorRule<V>`** (`ValueValidatorRule.kt`) — SAM interface. Implement `validate(value: V): ValidationResult` to create custom rules.

- **`ValidationResult`** — Interface with `format(): String?` (Composable) and `outcome(): Outcome`. Two implementations:
  - `RegularValidationResult` — plain string messages
  - `ResourceValidationResult` — Compose `StringResource`-backed (i18n)
  - `Outcome` enum: `ERROR(40)`, `WARNING(30)`, `INFO(20)`, `SUCCESS(10)` — severity-ranked

- **Built-in rules** are in `strings/StringValueValidatorRules.kt` (Required, Email, Phone, etc.) and `generic/GenericValueValidatorRules.kt`.

## Platform-Specific Code

The only expect/actual split is `RegexExt` — platform-specific regex behavior across `androidMain`, `iosMain`, `jsMain`, `jvmMain`, `wasmJsMain`.

## Key Design Decisions

- **All Compose dependencies are `compileOnly`** in `commonMain` to avoid forcing transitive deps on consumers. Tests use `implementation` so they actually resolve.
- **libphonenumber-kotlin is `compileOnly`** — consumers must add it themselves if they use phone validation rules.
- **Versioning is git-tag based**: release builds use `gitCurrentTag`, dev builds append `-${gitSha}`. Pass `-Drelease=true` or gradle property `release` for clean versions.
- **Validators are `@Stable`, not `@Immutable`** — they hold mutable state. If embedding in a data class, the parent must also be `@Stable`.
- **Do not copy validators** — update `validator.value` or call `validator.validate(newValue)` instead of creating new instances, which resets validation state.
