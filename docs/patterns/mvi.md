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
  see [custom rules](../recipes/custom-rules.md). The built-in `PasswordMatches` couples
  to a *mutable* validator and does **not** fit a reducer.
- **Shake is driven by a monotonic counter, not a state diff.** `FieldValidationState` equality
  includes its message, so a *second* invalid submit produces an `==` state — no recomposition, and a
  diff-driven shake would fire once and never again. Thread a `submitAttempts` counter in the model
  and pass it as `shakeTrigger`; `Modifier.validationBehavior(isError, shakeTrigger, onFocusLost)`
  bundles that with focus-loss handling. `Modifier.shakeOnInvalid(isError, trigger)` is the
  standalone form.
- **Screen readers get no signal from shake.** It is a purely visual `graphicsLayer` translation, and
  a repeat invalid submit changes nothing in the semantics tree. If the form must be accessible,
  announce the failure yourself on submit.
- **Give disposable fields a stable `key`.** The "don't shake on first frame" guard remembers the
  trigger it was born with, so a field re-created after its subtree was disposed — a `LazyColumn` item
  without a stable `key`, a nav destination re-entered — adopts the current counter as its new
  baseline and swallows any shake that became due while it was gone.
- **`onFocusCursorToEnd` needs a `TextFieldValue` in your model,** not a `String`. The examples on
  this page hoist `String`, so adopting cursor-to-end means migrating the field's model type.
  `TextFieldValue` is a value type and is safe inside an immutable model.

## Persistence & process death

`FieldValidationState` is `@Immutable @Serializable` — `severity` + `showError`
persist, the message (`result`) is `@Transient` and recomputes. Persist the drafts plus
each field's `showError` with `rememberSaveable` (a `FieldValidationState.Saver` is
provided), then **rehydrate on restore by re-running the rules through `toFieldState`**
so the message reappears with `showError` preserved. For cross-process JSON, add a
kotlinx-serialization runtime and encode `FieldValidationState` directly (it persists
`Outcome` by constant name). See the runnable, process-death-surviving version in
[`MviSample.kt`](https://github.com/chrisjenx/yakcov/blob/main/sample/src/main/java/com/chrisjenx/yakcov/sample/MviSample.kt).
