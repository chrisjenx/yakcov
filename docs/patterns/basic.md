# Basic screen

**When to use:** quick forms and screens with no state holder — the validators live
directly in the composable.

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/basic/BasicScreen.kt:basic"
```

How the wiring works:

- `rememberTextFieldStateValidator` keeps each validator across recompositions. It owns the
  field's `TextFieldState` (the text, saved across configuration change) *and* its validation
  state, and it validates by observing the state, so there's no `onValueChange` to wire. The
  older `rememberTextFieldValueValidator` (`value`/`onValueChange`) still works.
- `Modifier.validationConfig(validateOnFocusLost = true, shakeOnInvalid = true)` starts
  validation when the user leaves the field and shakes it when a submit finds it
  invalid. Nothing turns red while they're still typing.
- The submit button gates on `listOf(email, password).validate()` — this surfaces
  errors on **every** field (including ones the user never touched) and returns `true`
  only when all rules pass.

When the form grows a real state holder, graduate to the
[ViewModel](viewmodel.md), [MVI](mvi.md), or [Circuit](circuit.md) wiring.
