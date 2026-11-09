# Circuit

**Recommended wiring: the presenter owns each field's `TextFieldState` and the validator that
follows it.** [Circuit](https://slackhq.github.io/circuit/) presenters are composable functions, so
`remember { TextFieldState() }`, `remember { FieldValidator(...) }` and a `LaunchedEffect` that
pipes `textChanges()` into the validator all work there naturally. The field owns the text, the
presenter owns the form state, and the UI stays a dumb renderer of `CircuitUiState`. The text is
never rendered from state, so a late recomposition can't drop a keystroke (see the
[decision record](../decisions/textfieldstate.md)).

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
    if you want the text buffers and validators to survive Circuit's retention scope.

Prefer immutable state throughout? The [MVI/UDF wiring](mvi.md) drops into a Circuit presenter
unchanged. Fold rules with `toFieldState`, put the text `String` and the `FieldValidationState` in
your `CircuitUiState` instead of the validator, and keep the `TextFieldState` in the presenter.
