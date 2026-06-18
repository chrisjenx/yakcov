# Phone validation

Yakcov ships two phone rules. Pick based on whether you need the libphonenumber dependency:

| Rule | Dependency | Checks |
|------|------------|--------|
| `PhoneFormat` | **none** | Lenient "looks like a phone number" format gate: optional leading `+`, digits and ` ( ) . / -` separators, 7–15 digits. |
| `Phone` | libphonenumber-kotlin (`compileOnly`) | Region-aware validity via Google's libphonenumber. |

## Default: `PhoneFormat` (no setup)

`PhoneFormat` has no dependencies and runs on every target. Use it when the server is
authoritative (normalizes to E.164 / verifies with a provider, e.g. Telnyx Verify) and the
client only needs a cheap format gate:

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:phoneFormat"
```

It deliberately accepts a wide range of real-world formats (international `+`, IDD `00`/`011`
prefixes, national trunk `0`, spaces/dashes/dots/parens) and leaves authoritative checks to the
server. It does **not** reject wrong-region or structurally-invalid-but-well-formed numbers —
that's `Phone`'s job. The underlying check is also available directly as the public
`String?.isPhoneNumberFormat()` helper.

## Region-aware: `Phone` (opt-in)

`Phone` validates with
[libphonenumber-kotlin](https://github.com/luca992/libphonenumber-kotlin)
(luca992's multiplatform port of Google's libphonenumber). Yakcov declares it `compileOnly`, so
this rule needs your app (or shared module) to provide the dependency:

```kotlin
commonMain {
    dependencies {
        implementation("io.github.luca992.libphonenumber-kotlin:libphonenumber:0.1.9")
    }
}
```

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:phone"
```

`Phone` also accepts a `State<String>` for the region, so the default region can react to a
country picker: `Phone(defaultRegion = countryState)`.

Without the dependency, using `Phone` fails at runtime with a missing-class error — everything
else in Yakcov (including `PhoneFormat`) works without it.

## Roll your own

Need a specific national format or an extension field? Write a `ValueValidatorRule<String>` —
see [custom rules](custom-rules.md). The dependency-free `String?.isPhoneNumberFormat()` helper
is public and reusable as a building block.
