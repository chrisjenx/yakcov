# Yet Another Kotlin Compose Validation library

![Maven Central Version](https://img.shields.io/maven-central/v/com.chrisjenx.yakcov/library)

`TextField` validation is a pain, hopefully this is a bit easier

```kotlin
val emailValidator = TextFieldValueValidator(
    rules = listOf(Required, Email)
)
with(emailValiator) {
    OutlinedTextField(
        value = value,
        onValueChange = ::validate,
        modifier = Modifier.validateFocusChanged().fillMaxWidth(),
        label = { Text("Email*") },
        placeholder = { Text("Email") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
        isError = isError(),
        supportingText = supportingText(),
    )
}
```

## Dependencies
By default we publish to maven central: https://central.sonatype.com/artifact/com.chrisjenx.yakcov/library

We publish all targets (Android, JVM, JS, Wasm, iOS) you can include the common library in your project and 
it will pull in the correct target for what ever targets you have specified.
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



## Build locally
 - check your system with [KDoctor](https://github.com/Kotlin/kdoctor)
 - install JDK 17 or higher on your machine

## Testing
- You will need Chrome installed for JS based tests to run
- run tests with `./gradlew :library:test`
