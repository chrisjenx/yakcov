# Custom rules

A rule is anything implementing `ValueValidatorRule<V>` — return a `ValidationResult`
from `validate(value)`. For the rules that already ship, see
[built-in rules](built-in-rules.md); to run an existing rule only sometimes, see
[`onlyWhen`](built-in-rules.md#conditional-rules).

## Reusable rule types

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:custom-rule"
```

Use a `data class` instead of `data object` when the rule takes parameters
(`MinLength(minLength = 8)` is built that way).

## One-off rules (SAM lambdas)

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:custom-rule-sam"
```

## Severities and results

`ValidationResult.outcome()` ranks `ERROR` > `WARNING` > `INFO` > `SUCCESS`. Only
`ERROR` blocks `validate()`/submission — warnings and info messages display through
`supportingText` without failing the field.

Two result implementations ship with the library:

- `RegularValidationResult.error/warning/info/success("message")` — plain strings,
  shown verbatim
- `ResourceValidationResult` — backed by Compose `StringResource` for i18n; all the
  built-in rules use this

## Conventions

- **Return success for blank input** and let `Required` own emptiness — that way every
  rule composes with optional fields (`listOf(UsZipCode)` allows blank,
  `listOf(Required, UsZipCode)` doesn't).
- Rules run on **every value change**; keep `validate` cheap and side-effect free.
