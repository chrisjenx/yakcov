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

### iOS
You will need to add the following pod to your iOS project:

```kotlin
cocoaPods {
    pod("libPhoneNumber-iOS", version = "~> 1.2")
}
```

## Build locally

- check your system with [KDoctor](https://github.com/Kotlin/kdoctor)
- install JDK 17 or higher on your machine

## Testing

- You will need Chrome installed for JS based tests to run
- run tests with `./gradlew :library:allTests`
