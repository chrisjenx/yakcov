# Yet Another Kotlin Compose Validation library

![Maven Central Version](https://img.shields.io/maven-central/v/com.chrisjenx.yakcov/library)

`TextField` validation is a pain, hopefully this is a bit easier

**[Full documentation & pattern guides →](https://chrisjenx.github.io/yakcov/)**
— basic screens, [ViewModel](https://chrisjenx.github.io/yakcov/patterns/viewmodel/),
[MVI/UDF](https://chrisjenx.github.io/yakcov/patterns/mvi/), and
[Circuit](https://chrisjenx.github.io/yakcov/patterns/circuit/) wiring, with every
example compiled in CI against the current API.

![](sample/assets/YakcovDemo.gif)

<!-- keep in sync with docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/basic/BasicScreen.kt -->
```kotlin
@Composable
fun BasicSignUpScreen(onSubmit: (email: String, password: String) -> Unit) {
    // No state holder: the validators live in the composable, remembered across
    // recompositions. Each one owns its TextFieldValue draft + validation state.
    val email = rememberTextFieldValueValidator(rules = listOf(Required, Email))
    val password = rememberTextFieldValueValidator(rules = listOf(Required, MinLength(8)))

    Column {
        with(email) {
            OutlinedTextField(
                value = value,
                onValueChange = ::onValueChange,
                label = { Text("Email") },
                isError = isError(),
                supportingText = supportingText(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    // Show errors when focus leaves the field; shake on invalid submit.
                    .validationConfig(validateOnFocusLost = true, shakeOnInvalid = true),
            )
        }
        with(password) {
            OutlinedTextField(
                value = value,
                onValueChange = ::onValueChange,
                label = { Text("Password") },
                isError = isError(),
                supportingText = supportingText(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .validationConfig(validateOnFocusLost = true, shakeOnInvalid = true),
            )
        }
        Button(
            onClick = {
                // Form-level validate(): surfaces errors on every field, true when all pass.
                if (listOf(email, password).validate()) {
                    onSubmit(email.value.text, password.value.text)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
    }
}
```

Prefer the state holder to own the form? Yakcov also works headlessly — a
ViewModel/presenter owns a `FieldValidator`, or a pure MVI reducer folds rules with
`toFieldState`. See the
[ViewModel](https://chrisjenx.github.io/yakcov/patterns/viewmodel/) and
[MVI/UDF](https://chrisjenx.github.io/yakcov/patterns/mvi/) guides.

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

Phone-number rules need the (compileOnly) libphonenumber dependency added to your app —
see [phone validation](https://chrisjenx.github.io/yakcov/recipes/phone/).

## Build locally

- check your system with [KDoctor](https://github.com/Kotlin/kdoctor)
- install JDK 17 or higher on your machine

## Testing

- You will need Chrome installed for JS based tests to run
- run tests with `./gradlew :library:allTests`
