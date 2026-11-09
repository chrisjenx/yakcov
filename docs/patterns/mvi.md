# MVI / UDF

**Recommended wiring: the field owns a `TextFieldState`, the Model observes it.** A reducer's
contract is `(Model, Event) -> Model` with an immutable Model. The text being typed must **not**
round-trip through that Model, because a Model update that lands after the next key resets the field
and drops the keystroke or misplaces a paste. Instead:

- each field owns a **`TextFieldState`** (the editing buffer, on the Compose clock), held by your
  component, never by the Model;
- the **Model** holds each field's text as a plain `String` plus its immutable
  `FieldValidationState`;
- the **reducer** folds rules with the pure `toFieldState(text, showError)`, exactly as before.

Text flows one way: **field → `textChanges()` → `dispatch` → `reduce`**. See the
[TextFieldState decision record](../decisions/textfieldstate.md) for why, including a side-by-side
demo of the old shape losing input.

!!! note "Compose versions"
    `TextFieldState` exists on every Compose line yakcov ships (1.11+). Compose 1.13 deprecates
    the `value`/`onValueChange` text fields this replaces, so new forms should start here.

## Setup in five steps

### 1. Model and events

The Model holds **text as a `String`**, not a `TextFieldValue` or a `TextFieldState`. The caret and
selection live in the field's `TextFieldState`, so the Model doesn't need them.

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-model"
```

### 2. The reducer

Plain Kotlin, unit-testable, and **unchanged** from the pre-`TextFieldState` shape: a `Changed`
event carries the new text and preserves `showError`, while `FocusLost`/`Submit` force it to `true`.

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-reduce"
```

### 3. The component owns the `TextFieldState`s and pipes their text into the store

This is the only new piece. Create one `TextFieldState` per field **once**, as a `val` on the
component (a ViewModel, a Decompose component, a presenter). Then collect `textChanges()` into
`dispatch`.

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-store"
```

### 4. Bind the UI

Pass `state =` to the field. There is no `value`/`onValueChange`. Errors, supporting text, shake
and focus-loss come from the Model, exactly as before.

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-ui"
```

### 5. Programmatic writes

Write to the **`TextFieldState`**, never to the Model. The write travels the same path as typing,
and the Model catches up through `textChanges()`. `setTextAndPlaceCursorAtEnd` leaves the caret at
the end, so there's no `TextRange` bookkeeping.

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/mvi/MviExample.kt:mvi-writes"
```

When the **reducer** decides the text, for example a server load or a template fill, it can't touch
the state because it's pure. Have it publish a one-shot, versioned seed, and apply that seed only
when its version changes:

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/textfieldstate/TextFieldStateExample.kt:tfs-writes"
```

## Rules of the road

- **Never put a `TextFieldState` in the Model.** It's a mutable, reference-identity holder, so
  `copy()` aliases it and `==` becomes identity. It lives on the component, like any other UI-owned
  state.
- **Never echo the Model back into a field.** For example,
  `LaunchedEffect(model.email) { email.setText…(model.email) }` compares the field against a Model
  that is one update behind and overwrites the newest keystrokes. Use the versioned seed above.
- **`showError` is threaded, not recomputed.** `Changed` preserves it, and `FocusLost` and `Submit`
  force it to `true`. That gives "errors on blur or submit, never mid-keystroke".
- **`canSubmit` is honest from the first frame.** Seed each field with
  `rules.toFieldState("", showError = false)`, not `FieldValidationState.Pristine`.
  `hasNoErrors()` reads severity and ignores `showError`.
- **An action in the same frame as an edit:** the Model catches up on the next snapshot apply,
  before the next frame, so a click sees the final text. If something can fire on the very frame
  of an edit, carry the text in the event: `Submit(email = component.email.text.toString())`.
- **Formatting** goes in `inputTransformation` (to restrict input) and `outputTransformation` (to
  display it differently). Rules see the stored text: after the input transformation and before
  the output one. See [the decision record](../decisions/textfieldstate.md#5-transformations-raw-or-transformed-text).
- **Shake is driven by a monotonic counter, not a state diff.** `FieldValidationState` equality
  includes its message, so a *second* invalid submit produces an `==` state, nothing recomposes,
  and a diff-driven shake would fire only once. Thread a `submitAttempts` counter in the Model and
  pass it as `shakeTrigger`. `Modifier.validationBehavior(isError, shakeTrigger, onFocusLost)`
  bundles that with focus-loss handling.
- **Screen readers get no signal from shake.** It's a purely visual `graphicsLayer` translation,
  so if the form must be accessible, announce a blocked submit yourself.
- **Give disposable fields a stable `key`.** The "don't shake on first frame" guard remembers the
  trigger it was born with. A field re-created after its subtree was disposed adopts the current
  counter as its baseline and swallows a shake that fell due while it was gone.
- **Cross-field rules** (confirm-password and the like) are SAM lambdas that close over the Model.
  See [custom rules](../recipes/custom-rules.md).

## Migrating from `value` / `onValueChange`

| Before | After |
|---|---|
| Model holds `TextFieldValue` (or `String`) and the field renders it | Model holds `String`; the component holds a `TextFieldState` |
| `OutlinedTextField(value = model.x, onValueChange = { dispatch(XChanged(it)) })` | `OutlinedTextField(state = component.x)` plus `x.textChanges().collect { dispatch(XChanged(it)) }` on the component |
| `XChanged(value: TextFieldValue)`, folding on `value.text` | `XChanged(text: String)`, folding on `text`; the reducer is otherwise unchanged |
| Prefill: `TextFieldValue(text, TextRange(text.length))` | `TextFieldState(text)` (caret at the end) or `state.setTextAndPlaceCursorAtEnd(text)` |
| Filter keystrokes in `onValueChange` and clamp the caret | `inputTransformation = …`; the caret is handled for you |
| `VisualTransformation` | `outputTransformation = …` |
| `onFocusCursorToEnd(value, onValueChange)` | `onFocusCursorToEnd(state = component.x)` |
| `validationBehavior(...)` | unchanged |

## Persistence & process death

`FieldValidationState` is `@Immutable @Serializable`: `severity` and `showError` persist, while the
message (`result`) is `@Transient` and gets recomputed. The Model's `String`s are the source of
truth for restore. Persist them with each field's `showError`, then **rehydrate by re-running the
rules through `toFieldState`**, and construct the component's `TextFieldState`s from the restored
text (`SignUpComponent(scope, initial = restored)` does both). For cross-process JSON, add a
kotlinx-serialization runtime and encode `FieldValidationState` directly. It persists `Outcome` by
constant name.
