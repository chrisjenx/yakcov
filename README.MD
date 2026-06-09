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
        onValueChange = field::onChange,
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

The binder above uses the `String` overload of `OutlinedTextField` for brevity. For reformatting
fields (currency, phone) bind a local `TextFieldValue` and push only `.text` to `onChange` so the
cursor/selection survives — see `ValidatedTextField` in the [sample](sample/src/main/java/com/chrisjenx/yakcov/sample/PresenterSample.kt).

**(b) Reducer-MVI** — keep your `Model` immutable; store the draft + the serializable
`FieldValidationState` and recompute with the pure `toFieldState` in `reduce()` (no `FieldValidator`):

```kotlin
data class Model(
    val emailDraft: String = "",
    val emailState: FieldValidationState = FieldValidationState.Pristine,
)
sealed interface Event { data class EmailChanged(val text: String) : Event; data object Submit : Event }
private val emailRules = listOf(Required, Email)

fun reduce(model: Model, event: Event): Model = when (event) {
    is Event.EmailChanged -> model.copy(
        emailDraft = event.text,
        emailState = emailRules.toFieldState(event.text, showError = model.emailState.showError),
    )
    Event.Submit -> model.copy(
        emailState = emailRules.toFieldState(model.emailDraft, showError = true), // reveal
    )
}

fun Model.canSubmit(): Boolean = listOf(emailState).hasNoErrors()
```

**Shake** stays UI-owned: keep a `rememberShakingState()` in the screen and call `shakingState.shake()`
in response to the presenter's "invalid submit" signal.

**Persistence:** `FieldValidationState` is `@Immutable @Serializable` — only `severity` + `showError`
persist; the message (`result`) is `@Transient` and recomputes on restore. Persist with
kotlinx.serialization into a `SavedStateHandle`/`DataStore`, or use `rememberSaveable(stateSaver =
FieldValidationState.Saver)`. To restore: persist the draft yourself, reconstruct
`FieldValidator(initial = restoredDraft, rules)` and call `reveal()`/`onBlur()` (or seed `showError`)
before first composition so the rules re-run and the message reappears.

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
