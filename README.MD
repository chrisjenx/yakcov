# Yet Another Kotlin Compose Validation library

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

## Before running!
 - check your system with [KDoctor](https://github.com/Kotlin/kdoctor)
 - install JDK 17 or higher on your machine

## Testing
- run tests with `./gradlew test`
