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

### Testing `Phone` on Android

In an Android **local unit test** (plain JVM or Robolectric) `Phone` reports every number invalid
until you configure it. On Android the `PhoneNumberUtil` is built from an application `Context`
captured by an androidx.startup provider, and libphonenumber reads its metadata through
compose-resources — a local unit test creates neither `ContentProvider`
([robolectric#9603](https://github.com/robolectric/robolectric/issues/9603),
[CMP-6612](https://youtrack.jetbrains.com/issue/CMP-6612)).

This fails **silently**: `isPhoneNumber` degrades to `false` rather than throwing, so
`assertFalse("abc".isPhoneNumber())` passes whether or not validation actually works, while the
positive path is never exercised. One call fixes it:

```kotlin
class PhoneFieldTest {
    @Before fun setUp() = initPhoneNumberUtilForTest()
    @After fun tearDown() = resetPhoneNumberUtilForTest()

    @Test fun acceptsAFrenchNumber() {
        assertTrue("+33 1 42 68 53 00".isPhoneNumber("FR"))
    }
}
```

`initPhoneNumberUtilForTest()` needs no `Context` and loads libphonenumber's real metadata, so
**every** region validates. It only requires that your module keeps Android resources available to
unit tests — which is also what Robolectric needs:

```kotlin
android { testOptions { unitTests { isIncludeAndroidResources = true } } }
```

Call `resetPhoneNumberUtilForTest()` from `@After`. The util is a per-JVM singleton, so without it a
configured test class silently decides whether a later class that *forgot* to configure passes —
making a suite's result depend on execution order within the shard.

To manage metadata loading yourself, pass your own instance:
`initPhoneNumberUtilForTest(PhoneNumberUtil.createInstance(myLoader))`.

Other targets need no setup: JVM, iOS, JS and WasmJS resolve metadata without a `Context`. Android
*instrumented* tests need no setup either, since androidx.startup runs there.

## Roll your own

Need a specific national format or an extension field? Write a `ValueValidatorRule<String>` —
see [custom rules](custom-rules.md). The dependency-free `String?.isPhoneNumberFormat()` helper
is public and reusable as a building block.
