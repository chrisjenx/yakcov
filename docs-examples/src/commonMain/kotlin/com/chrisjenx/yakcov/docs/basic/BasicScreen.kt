package com.chrisjenx.yakcov.docs.basic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.strings.rememberTextFieldStateValidator
import com.chrisjenx.yakcov.validate

// --8<-- [start:basic]
@Composable
fun BasicSignUpScreen(onSubmit: (email: String, password: String) -> Unit) {
    // No state holder: the validators live in the composable, remembered across
    // recompositions. Each one owns a TextFieldState (the field's text) and validates it by
    // observing the state — there is no onValueChange to wire.
    val email = rememberTextFieldStateValidator(rules = listOf(Required, Email))
    val password = rememberTextFieldStateValidator(rules = listOf(Required, MinLength(8)))

    Column {
        with(email) {
            OutlinedTextField(
                state = textFieldState,
                label = { Text("Email") },
                isError = isError(),
                supportingText = supportingText(),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    // Show errors when focus leaves the field; shake on invalid submit.
                    .validationConfig(validateOnFocusLost = true, shakeOnInvalid = true),
            )
        }
        with(password) {
            OutlinedTextField(
                state = textFieldState,
                label = { Text("Password") },
                isError = isError(),
                supportingText = supportingText(),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .validationConfig(validateOnFocusLost = true, shakeOnInvalid = true),
            )
        }
        Button(
            onClick = {
                // Form-level validate(): surfaces errors on every field, true when all pass.
                if (listOf(email, password).validate()) {
                    onSubmit(email.value, password.value)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
    }
}
// --8<-- [end:basic]
