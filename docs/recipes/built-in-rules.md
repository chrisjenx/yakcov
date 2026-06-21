# Built-in rules

Yakcov ships rules for the common cases so you rarely have to write your own. Every rule
implements `ValueValidatorRule<V>`; drop them into a validator's `rules` list. To write your
own, see [custom rules](custom-rules.md).

!!! note "Blank / null pass through"
    The string **format** rules treat **blank** input as valid (`MinLength` included), and the
    value-membership/bounds generic rules (`InList`, `Min`, `Max`, `InRange`) treat
    **null** as valid — so `Required` (or generic `Required`) is the single source of
    presence-checking. Compose it alongside the rule whenever an empty field should be rejected:
    `listOf(Email)` accepts an empty field, `listOf(Required, Email)` doesn't. The presence/state
    checks (`Required`, `ListNotEmpty`, `IsChecked`) and the date-part rules
    (`DayValidation`/`MonthValidation`/`YearValidation`, which need a value to parse) are the
    exceptions — they reject blank/null.

## String rules

`com.chrisjenx.yakcov.strings` — validate the text of a `TextField`
(`rememberTextFieldValueValidator`).

| Rule | Passes when |
|---|---|
| `Required` | the value is not blank |
| `Email` | the value is a valid email address |
| `Phone(region)` | a valid phone number for an ISO region — needs libphonenumber, see [phone validation](phone.md) |
| `PhoneFormat` | the value *looks like* a phone number — lenient, no extra dependency |
| `Numeric` | the value parses as a whole number |
| `Decimal` | the value parses as a decimal |
| `MinValue(n)` / `MaxValue(n)` | the parsed numeric value is `>= n` / `<= n` |
| `MinLength(n)` / `MaxLength(n)` | the length is within bounds (`MinLength` can `trim` and exclude whitespace) |
| `HexColor` | a CSS hex color: `#RGB`, `#RGBA`, `#RRGGBB` or `#RRGGBBAA` (case-insensitive) |
| `OneOf(allowed)` | the value is one of a `Set<String>` — trims and ignores case by default (`ignoreCase` / `trim` flags) |
| `DayValidation` / `MonthValidation` / `YearValidation` | the day/month/year part forms a valid date (given a sibling `LocalDate`) |
| `PasswordMatches(other)` | the value equals another field's value |

## Generic rules

`com.chrisjenx.yakcov.generic` — validate a typed value `T` directly, with
`rememberGenericValueValidator(state, rules)`.

| Rule | Passes when |
|---|---|
| `Required<T>()` | the value is not null |
| `InList(allowed)` | the value is one of `allowed` (null passes) |
| `ListNotEmpty()` | the `List` value is non-empty |
| `IsChecked` / `IsNotChecked` | a `Boolean?` is / isn't `true` |
| `Min(n)` / `Max(n)` / `InRange(min, max)` | a numeric `N? where N : Number, Comparable` is in range — `null` and `NaN` pass |

The typed numeric bounds complement the string-based `MinValue`/`MaxValue` for fields whose
value is already numeric. Because they pass `null` through, the validator's value type must be
nullable (e.g. `Int?`):

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:generic-bounds"
```

## Conditional rules

`onlyWhen` (and the underlying `Optional`) wrap **any** rule — string or generic — so it runs
only while a `State<Boolean>` is `true`, and passes otherwise. Use it for
conditionally-required or optional-when-hidden fields instead of writing a bespoke variant:

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:conditional"
```

For **"optional, but at least N chars if the user types something"**, gate only `Required` and
leave the length rule ungated — `Required.onlyWhen(state)` owns the empty case while the ungated
`MinLength` still enforces length once a value is typed (because blank always passes through to
`Required`):

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:optional-min-length"
```

## Severities

Rules aren't limited to pass/fail. `ValidationResult.outcome()` is ranked
`ERROR` > `WARNING` > `INFO` > `SUCCESS`; only `ERROR` blocks `validate()`/submission, while
warnings and info still surface through [`supportingText`](../getting-started.md#showing-errors).
See [custom rules](custom-rules.md#severities-and-results) for grading your own.
