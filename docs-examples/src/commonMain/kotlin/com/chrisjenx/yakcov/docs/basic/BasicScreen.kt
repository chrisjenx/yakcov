package com.chrisjenx.yakcov.docs.basic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.strings.rememberTextFieldValueValidator
import com.chrisjenx.yakcov.validate

// --8<-- [start:basic]
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
// --8<-- [end:basic]
