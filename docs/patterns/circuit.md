# Circuit

**Recommended wiring: the validator lives in the presenter.**
[Circuit](https://slackhq.github.io/circuit/) presenters are composable functions, so
`remember { FieldValidator(...) }` works there naturally — the presenter owns the form
state, and the UI stays a dumb renderer of `CircuitUiState`.

## State and events

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/circuit/CircuitExample.kt:circuit-state"
```

## The presenter

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/circuit/CircuitExample.kt:circuit-presenter"
```

## The UI

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/circuit/CircuitExample.kt:circuit-ui"
```

## Hosting it

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/circuit/CircuitExample.kt:circuit-host"
```

!!! note "What this example leaves out"
    A real app routes via a `Screen` + `CircuitContent`, registering the presenter and
    UI through `Presenter.Factory` / `Ui.Factory`. `Screen` is `Parcelable` on Android
    (`@Parcelize` plumbing), which is app-level wiring unrelated to validation — so the
    example hosts the presenter directly. Only `circuit-foundation` is needed for
    everything shown here, and `rememberRetained` is a drop-in upgrade for `remember`
    if you want the validators to survive Circuit's retention scope.

Prefer immutable state throughout? The [MVI value-hoist wiring](mvi.md) drops into a
Circuit presenter unchanged — fold rules with `toFieldState` and put
`FieldValidationState` in your `CircuitUiState` instead of the validator.
