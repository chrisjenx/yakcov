package com.chrisjenx.yakcov.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chrisjenx.yakcov.generic.IsChecked
import com.chrisjenx.yakcov.generic.ListNotEmpty
import com.chrisjenx.yakcov.generic.rememberGenericValueValidator
import com.chrisjenx.yakcov.sample.ui.theme.YakcovTheme
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.PasswordMatches
import com.chrisjenx.yakcov.strings.Phone
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.strings.rememberTextFieldValueValidator
import com.chrisjenx.yakcov.validate

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YakcovTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
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
                                    .validationConfig(
                                        validateOnFocusLost = true,
                                        shakeOnInvalid = true,
                                    )
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Email,
                                ),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val phoneValidator = rememberTextFieldValueValidator(
                            rules = listOf(Required, Phone()),
                        )
                        with(phoneValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Phone") },
                                modifier = Modifier
                                    .validationConfig(validateOnFocusLost = true)
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Phone,
                                ),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        var firstTextField by remember { mutableStateOf(TextFieldValue()) }
                        val minLength = remember { derivedStateOf { firstTextField.text.length } }
                        val matchLength = rememberTextFieldValueValidator(
                            rules = listOf(MinLength(minLength)), alwaysShowRule = true,
                        )

                        OutlinedTextField(
                            value = firstTextField,
                            onValueChange = { firstTextField = it },
                            label = { Text("Use this length") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        with(matchLength) {
                            OutlinedTextField(
                                value = value,
                                label = { Text("Match Above length") },
                                modifier = Modifier
                                    .validationConfig(
                                        validateOnFocusLost = true,
                                        shakeOnInvalid = true
                                    )
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Number,
                                ),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
                                PasswordMatches(passwordValidator),
                            ),
                            alwaysShowRule = true
                        )
                        with(passwordValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Password") },
                                modifier = Modifier
                                    .validationConfig(validateOnFocusLost = true)
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                supportingText = supportingText(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                            )
                        }
                        with(passwordMatchesValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Confirm Password") },
                                modifier = Modifier
                                    .validationConfig(validateOnFocusLost = true)
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }

                        Button(
                            onClick = {
                                passwordValidator.reset()
                                passwordMatchesValidator.reset()
                            }
                        ) {
                            Text(text = "Reset Validation State")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Error on submit",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        val requiredValidator =
                            rememberTextFieldValueValidator(rules = listOf(Required))
                        with(requiredValidator) {
                            OutlinedTextField(
                                value = value,
                                label = { Text(text = "Name") },
                                modifier = Modifier
                                    .validationConfig(
                                        validateOnFocusLost = true,
                                        shakeOnInvalid = true,
                                        showErrorOnInteraction = false,
                                    )
                                    .fillMaxWidth(),
                                onValueChange = ::onValueChange,
                                isError = isError(),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Text,
                                ),
                                singleLine = true,
                                supportingText = supportingText()
                            )
                        }

                        val genericValidator = rememberGenericValueValidator<Boolean?>(
                            state = null, rules = listOf(IsChecked),
                        )
                        with(genericValidator) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .toggleable(
                                        value = genericValidator.value == true,
                                        onValueChange = { genericValidator.onValueChange(it) },
                                        role = Role.Checkbox
                                    )
                                    .validationConfig(
                                        validateOnFocusLost = true,
                                        shakeOnInvalid = true,
                                    )
                                    .padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = genericValidator.value == true,
                                    // null recommended for accessibility with screenreaders
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        uncheckedColor = if (genericValidator.isError()) MaterialTheme.colorScheme.error else Color.Unspecified,
                                    )
                                )
                                Text(
                                    text = "Option selection",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = if (genericValidator.isError()) MaterialTheme.colorScheme.error else Color.Unspecified,
                                )
                            }
                            supportingText()?.invoke()
                        }

                        val listValidator = rememberGenericValueValidator(
                            state = emptyList<String>(), rules = listOf(ListNotEmpty()),
                        )

                        Text(text = "List of items", style = MaterialTheme.typography.headlineSmall)
                        with(listValidator) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.Gray)
                                    .validationConfig(shakeOnInvalid = true)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                value.forEach { string ->
                                    ListItem(
                                        headlineContent = { Text(text = string) },
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    listValidator.onValueChange(listValidator.value - string)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete item"
                                                )
                                            }
                                        }
                                    )
                                }
                                Button(
                                    onClick = {
                                        listValidator.onValueChange(listValidator.value + "Item ${listValidator.value.size}")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = "Add item")
                                }
                            }
                            supportingText()?.invoke()
                        }

                        // Validate button
                        Button(
                            onClick = {
                                listOf(
                                    emailValidator,
                                    phoneValidator,
                                    passwordValidator,
                                    passwordMatchesValidator,
                                    requiredValidator,
                                    genericValidator,
                                    listValidator,
                                ).validate()
                            },
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(text = "Validate")
                        }
                    }
                }
            }
        }
    }
}
