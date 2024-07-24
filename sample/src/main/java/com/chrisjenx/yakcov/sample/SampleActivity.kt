package com.chrisjenx.yakcov.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrisjenx.yakcov.Email
import com.chrisjenx.yakcov.MinLength
import com.chrisjenx.yakcov.PasswordMatches
import com.chrisjenx.yakcov.Required
import com.chrisjenx.yakcov.rememberTextFieldValueValidator
import com.chrisjenx.yakcov.sample.ui.theme.YakcovTheme

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YakcovTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        Text(text = "Email", style = MaterialTheme.typography.headlineSmall)
                        val emailValidator = rememberTextFieldValueValidator(
                            rules = listOf(Required, Email), alwaysShowRule = true
                        )
                        with(emailValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Email") },
                                modifier = Modifier
                                    .validateFocusChanged()
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Email,
                                ),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }

                        Text(text = "Password", style = MaterialTheme.typography.headlineSmall)
                        // Password example
                        val passwordValidator = rememberTextFieldValueValidator(
                            rules = listOf(Required, MinLength(minLength = 6)),
                            alwaysShowRule = true
                        )
                        val passwordMatchesValidator = rememberTextFieldValueValidator(
                            rules = listOf(
                                Required,
                                MinLength(minLength = 6),
                                PasswordMatches(passwordValidator)
                            ),
                            alwaysShowRule = true
                        )

                        with(passwordValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Password") },
                                modifier = Modifier
                                    .validateFocusChanged()
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }
                        with(passwordMatchesValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Confirm Password") },
                                modifier = Modifier
                                    .validateFocusChanged()
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    YakcovTheme {
        Greeting("Android")
    }
}