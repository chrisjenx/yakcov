# Yet Another Kotlin Compose Validation library

![Maven Central Version](https://img.shields.io/maven-central/v/com.chrisjenx.yakcov/library)

`TextField` validation is a pain, hopefully this is a bit easier

![](sample/assets/YakcovDemo.gif)

```kotlin
val emailValidator = rememberTextFieldValueValidator(
    rules = listOf(Required, Email)
)
with(emailValidator) {
    OutlinedTextField(
        value = value,
        onValueChange = ::onValueChange,
        modifier = Modifier
            .validationConfig(
                // will start validation on loss of focus
                validateOnFocusLost = true,
                // will shake the field when invalid and validate() is called
                shakeOnInvalid = true,
                // when false will delay [isError] until validate() is called
                showErrorOnInteraction = false,
            )
            .fillMaxWidth(),
        label = { Text("Email*") },
        placeholder = { Text("Email") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
        isError = isError(), // will mark the field error when validation has started and is invalid
        supportingText = supportingText(), // will show the validation message, or error message
    )
}
```

### Presenter-driven validation (headless)

Yakcov also works when a **presenter owns the form state** — useful for snapshot-presenter
(Molecule) and reducer-MVI screens. The field draft and validity become unit-testable without
entering composition; only the message render (`text()` / `supportingText()`) stays in Compose.

**(a) Snapshot / Molecule presenter** — the presenter owns a mutable `FieldValidator`:

```kotlin
// presenter — owns the FieldValidator as a field; draft + validity testable without composition
class CheckoutPresenter {
    val email = FieldValidator("", listOf(Required, Email))
    // CurrencyAmount here is your own ValueValidatorRule — compose built-ins with your domain rules
    val amount = FieldValidator("", listOf(Required, CurrencyAmount))

    fun submit(): Boolean = listOf(email, amount).allValid() // reveals, then checks
}

// reusable binder, written once in app code (one line per field at the call site)
@Composable
fun ValidatedTextField(field: FieldValidator<String>, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = field.value,
        onValueChange = field::onValueChange,
        label = { Text(label) },
        isError = field.state.isError,
        supportingText = field.state.supportingText(),
        modifier = modifier.onFocusLost { field.onBlur() },
    )
}

ValidatedTextField(presenter.email, label = "Email")
```

> Construct a `FieldValidator` **once** and hold it (a presenter class / DI / `remember`). Don't
> construct it inside a recomposing presenter body, and don't copy it — both reset its state.

**Observability:** `FieldValidator` takes an optional `observer` — a `FieldValidatorObserver`
fired after every mutation commits (`ValueChanged` / `Revealed` / `Reset`) with the post-mutation
value + state. Handy for analytics, logging, or driving UI like the sample's live state-flow
visualizer ([StateFlowSample](sample/src/main/java/com/chrisjenx/yakcov/sample/StateFlowSample.kt)).
No event fires at construction; observers must not throw.

The binder above uses the `String` overload of `OutlinedTextField` for brevity. For reformatting
fields (currency, phone) bind a local `TextFieldValue` and push only `.text` to `onValueChange` so the
cursor/selection survives — see `ValidatedTextField` in the [sample](sample/src/main/java/com/chrisjenx/yakcov/sample/PresenterSample.kt).

**(b) Reducer-MVI** — keep your `Model` immutable; store each field's draft + its serializable
`FieldValidationState` and recompute with the pure `toFieldState` fold in `reduce()` (no
`FieldValidator`, no Compose, no IO — so `reduce` is unit-testable without composition). `severity`
is recomputed on every keystroke and answers *"is it valid?"* (drives `canSubmit`); `showError` only
flips true on blur/submit and answers *"show the error yet?"* (drives the red text). That split is
yakcov's whole value. Cross-field rules go the pure way — an inline `ValueValidatorRule` closing over
the other draft; the built-in `PasswordMatches` couples to a *mutable* validator and does **not** fit
a reducer.

```kotlin
private val emailRules = listOf(Required, Email)
private val passwordRules = listOf(Required, MinLength(8))
// PURE cross-field rule: a SAM closing over the current password draft (rebuilt in reduce).
private fun confirmRules(password: String) = listOf<ValueValidatorRule<String>>(
    Required,
    ValueValidatorRule { if (it == password) RegularValidationResult.success()
                         else RegularValidationResult.error("Passwords do not match") },
)

enum class Field { Email, Password, Confirm }

data class Model(
    val emailDraft: String = "",    val emailState: FieldValidationState = FieldValidationState.Pristine,
    val passwordDraft: String = "", val passwordState: FieldValidationState = FieldValidationState.Pristine,
    val confirmDraft: String = "",  val confirmState: FieldValidationState = FieldValidationState.Pristine,
    val submitted: Boolean? = null, // null = not attempted, true = accepted, false = blocked
) {
    // SEVERITY-only validity (ignores reveal) — live from the first frame (drive a label or disable Submit).
    val canSubmit get() = listOf(emailState, passwordState, confirmState).hasNoErrors()
}

sealed interface Event {
    data class Changed(val field: Field, val text: String) : Event
    data class Blurred(val field: Field) : Event   // reveal one field
    data object Submit : Event                     // reveal all
}

// PURE: (Model, Event) -> Model. Copy this into a unit test as-is.
fun reduce(model: Model, event: Event): Model = when (event) {
    is Event.Changed -> when (event.field) {
        // typing: recompute severity live, but PRESERVE showError (no error pops mid-keystroke)
        Field.Email    -> model.copy(emailDraft = event.text,
            emailState = emailRules.toFieldState(event.text, model.emailState.showError), submitted = null)
        Field.Password -> model.copy(passwordDraft = event.text,
            passwordState = passwordRules.toFieldState(event.text, model.passwordState.showError),
            // password drives the cross-field rule — re-fold confirm against the new password
            confirmState = confirmRules(event.text).toFieldState(model.confirmDraft, model.confirmState.showError),
            submitted = null)
        Field.Confirm  -> model.copy(confirmDraft = event.text,
            confirmState = confirmRules(model.passwordDraft).toFieldState(event.text, model.confirmState.showError),
            submitted = null)
    }
    is Event.Blurred -> when (event.field) {  // reveal just that one field (showError = true)
        Field.Email    -> model.copy(emailState = emailRules.toFieldState(model.emailDraft, showError = true))
        Field.Password -> model.copy(passwordState = passwordRules.toFieldState(model.passwordDraft, showError = true))
        Field.Confirm  -> model.copy(confirmState = confirmRules(model.passwordDraft).toFieldState(model.confirmDraft, showError = true))
    }
    Event.Submit -> model.copy(  // reveal ALL, then record acceptance off severity
        emailState    = emailRules.toFieldState(model.emailDraft, showError = true),
        passwordState = passwordRules.toFieldState(model.passwordDraft, showError = true),
        confirmState  = confirmRules(model.passwordDraft).toFieldState(model.confirmDraft, showError = true),
    ).let { it.copy(submitted = it.canSubmit) }
}

// Seed by running the rules with showError = false (NOT Pristine) so canSubmit is honest at load —
// hasNoErrors() treats a never-validated Pristine field as valid even if it's required + empty.
fun initialModel() = Model(
    emailState = emailRules.toFieldState("", showError = false),
    passwordState = passwordRules.toFieldState("", showError = false),
    confirmState = confirmRules("").toFieldState("", showError = false),
)
```

A minimal store needs no ViewModel — `mutableStateOf` is already observable. Keep it `@Stable` (it
owns a `mutableStateOf`); do NOT mark it `@Immutable`:

```kotlin
@Stable
class Store(initial: Model) {
    var model by mutableStateOf(initial); private set
    fun dispatch(e: Event) { model = reduce(model, e) }
}
// UI = f(state). For process-death survival hold the store via rememberSaveable with a Saver that
// persists the drafts + each field's showError and re-runs the rules on restore (see MviSample.kt
// for the full storeSaver/modelSaver wiring):
// val store = rememberSaveable(saver = storeSaver) { Store(initialModel()) }
// OutlinedTextField(value = store.model.emailDraft,
//     onValueChange = { store.dispatch(Event.Changed(Field.Email, it)) },
//     isError = store.model.emailState.isError,                 // gated on showError
//     supportingText = store.model.emailState.supportingText(), // null until revealed
//     modifier = Modifier.onFocusLost { store.dispatch(Event.Blurred(Field.Email)) })
// Button(onClick = { store.dispatch(Event.Submit) }) {         // enabled; Submit reveals untouched fields
//     Text(if (store.model.canSubmit) "Create account" else "Fix errors") // label is severity-driven
// }
```

**Shake** stays UI-owned: keep a `rememberShakingState()` in the screen and call `shakingState.shake()`
when an invalid submit reduces `submitted` to `false`.

**Persistence (no new dep):** `FieldValidationState` is `@Immutable @Serializable` — only `severity`
+ `showError` persist; the message (`result`) is `@Transient` and recomputes. Persist the whole model
with `rememberSaveable` and a `Saver` that stores the drafts + each field's `showError`, then
**rehydrate on restore by re-running the rules through `toFieldState`** so the transient message
reappears with `showError` preserved (`runtime-saveable` comes transitively with material3). For
cross-process JSON instead, add a kotlinx-serialization runtime and encode `FieldValidationState`
directly (it persists `Outcome` by constant *name*). On the Molecule/`FieldValidator` path (a) the
same idea applies: persist the draft + `showError` yourself, reconstruct
`FieldValidator(initial = restoredDraft, rules)` and call `reveal()`/`onBlur()` when `showError` was
true (or construct with `initialValidate = true`) before first composition, so the rules re-run and
a previously-revealed message reappears. If you attach an `observer`, prefer the
`initialValidate = true` form on restore — it fires no event, whereas `reveal()`/`onBlur()` emit a
`Revealed` event that an analytics tap would over-count as a real user reveal.

See the runnable, process-death-surviving multi-field version in the
[sample](sample/src/main/java/com/chrisjenx/yakcov/sample/MviSample.kt).

> Yakcov declares `kotlinx-serialization-core`, `compose-runtime`, and `compose-runtime-saveable` as
> `compileOnly`. If you serialize `FieldValidationState`, add a serialization runtime (e.g.
> `kotlinx-serialization-json`). If you use `FieldValidationState.Saver` / `rememberSaveable`, make
> sure `org.jetbrains.compose.runtime:runtime-saveable` is on the classpath (it comes transitively
> with `compose-foundation`/`material3`, so any Compose-UI module already has it). If your presenter
> **unit tests** construct `FieldValidator` or read `.value`/`.state`, add `org.jetbrains.compose:runtime`
> to your test source set (it is not pulled transitively).

### Other Features

- Multiplatform support! Android, JVM, JS, Wasm, iOS!
- `TextFieldValueValidator` - a validator for `TextFieldValue` that can be used with `TextField` and `OutlinedTextField`
- `GenericValueValidator` - a generic validator that can be used to validate any type
  - Can use with any Composable, not just `TextField`s!
- Build in `ValueValidatorRule`s to make putting forms together easier
- `Outcome` - Supports different levels of severity for validation messages, `Error`, `Warning`, `Info`, `Success`
- `ValidationConfig` - Allows you to configure how the validation should behave to user interaction

## Dependencies

By default we publish
to [Maven Central](https://central.sonatype.com/artifact/com.chrisjenx.yakcov/library).

We publish all targets (Android, JVM, JS, Wasm, iOS) you can include the common library in your
project and it will pull in the correct target for what ever targets you have specified.

```kotlin
commonMain {
    dependencies {
        implementation("com.chrisjenx.yakcov:library:${version}")
    }
}
```

If you only need a specific target you can include that directly, for example for Android:

```kotlin
dependencies {
    implementation("com.chrisjenx.yakcov:library-android:${version}")
}
```

### Phone number validation (libphonenumber)

Yakcov's phone-number validation rules uses [luca992](https://github.com/luca992/libphonenumber-kotlin) port of libphonenumber. The Yakcov library declares this as `compileOnly`, so if you plan to use any phone-number validation rules you must add the dependency yourself in your app or shared module.

Kotlin Multiplatform (recommended):

```kotlin
commonMain {
    dependencies {
        implementation("io.github.luca992.libphonenumber-kotlin:libphonenumber:0.1.8")
    }
}
```

## Build locally

- check your system with [KDoctor](https://github.com/Kotlin/kdoctor)
- install JDK 17 or higher on your machine

## Testing

- You will need Chrome installed for JS based tests to run
- run tests with `./gradlew :library:allTests`
