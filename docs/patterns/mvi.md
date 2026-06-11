# MVI / UDF

**Recommended wiring: value-hoist.** A reducer's contract is `(Model, Event) -> Model`
with an immutable model — owning a *mutable* validator inside that model fights the
pattern (and `FieldValidator`'s own docs forbid it). Instead, hoist the raw value and
fold the rules with the pure `toFieldState(value, showError)` helpers; the immutable
`FieldValidationState` rides in the model like any other value.

## Model and events

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-model"
```

## The reducer

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-reduce"
```

## The store

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-store"
```

## The UI

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-ui"
```

Notes on the wiring:

- **`showError` is threaded, not recomputed.** `Changed` events preserve the current
  `showError` (severity updates silently while typing); `FocusLost` and `Submit` force
  it `true`. This reproduces the "don't flash red mid-keystroke" behavior the
  in-composition validators give you for free.
- **The reducer is plain Kotlin** — no Compose, no coroutines — so
  `reduce(model, Submit)` is trivially unit-testable.
- **The store is swappable.** Replace `SignUpStore` with a ViewModel exposing a
  `StateFlow<SignUpModel>` (or your MVI framework's container) without touching
  `reduce`, the model, or the events.
- Cross-field rules (confirm-password, etc.) are SAM lambdas closing over the model —
  see [custom rules](../recipes/custom-rules.md).
