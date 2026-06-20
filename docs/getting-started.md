# Getting started

## Rules

Validation is composed from `ValueValidatorRule`s. Built-ins cover the common cases —
`Required`, `Email`, `MinLength(n)`, `MaxLength(n)`, `Phone(region)` (see
[phone validation](recipes/phone.md)), `HexColor`, `OneOf(allowed)`, typed numeric
`Min`/`Max`/`InRange`, and more in `com.chrisjenx.yakcov.strings` /
`com.chrisjenx.yakcov.generic`.

Apply any rule conditionally with `onlyWhen`: `Required.onlyWhen(isBusiness)` runs the
rule only while the flag is `true` and passes otherwise.

`ValueValidatorRule` is a `fun interface`, so one-off rules are a lambda away:

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:custom-rule-sam"
```

See [custom rules](recipes/custom-rules.md) for reusable rule types and severity grading.

## Two validator families

| | In-composition | State-holder-owned (headless) |
|---|---|---|
| **Create with** | `rememberTextFieldValueValidator(rules)` / `rememberGenericValueValidator(state, rules)` | `FieldValidator(initial, rules)` — plain constructor |
| **Choose when** | The form has no state holder; everything lives in the composable | A ViewModel/presenter owns form state; you want `submit()` testable without UI |

Both are snapshot-state backed, so composables that read them recompose automatically.
The pattern guides ([ViewModel](patterns/viewmodel.md), [MVI](patterns/mvi.md),
[Circuit](patterns/circuit.md)) show which to reach for in each architecture.

## Showing errors

Validation severity and error *display* are separate channels: rules compute a severity
on every change, but nothing is shown until the validator decides errors should be
visible (focus lost, submit, …). That's why nothing flashes red while the user is still
typing.

- `isError()` / `FieldValidationState.isError` — true once errors are shown **and**
  severity is `ERROR`
- `supportingText()` — a ready-made slot value for Material `TextField`s; renders the
  most severe message, or `null` when nothing should show
- `Outcome` severities: `ERROR` > `WARNING` > `INFO` > `SUCCESS`. Warnings and info
  messages flow through the same display channel without blocking submission.

## validationConfig

For in-composition validators, the `validationConfig` modifier wires interaction
behavior:

```kotlin
modifier = Modifier.validationConfig(
    validateOnFocusLost = true,       // start validating when focus leaves the field
    shakeOnInvalid = true,            // shake the field when validate() fails
    showErrorOnInteraction = false,   // defer isError until validate() is called
)
```

For headless `FieldValidator`s there's no `validationConfig` modifier; call
`field.onFocusLost()` from `Modifier.onFocusLost { ... }` to validate-on-focus-lost,
state-holder owned. The `shakeOnInvalid` / `showErrorOnInteraction` knobs are specific to
the in-composition validators — on the headless path, drive shake yourself (see
[MVI](patterns/mvi.md)) and gate error display via the `showError` flag you thread
through `toFieldState`.

## Form-level submit

Validate everything at once; errors surface on every field and the call tells you
whether to proceed:

```kotlin
if (listOf(email, password).validate()) { /* all valid — submit */ }
```

Works on both families: `List<ValueValidator<*, *>>.validate()` and
`List<FieldValidator<*>>.validate()`.

## A complete screen

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/basic/BasicScreen.kt:basic"
```
