package com.chrisjenx.yakcov.docs.viewmodel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.FieldValidator
import com.chrisjenx.yakcov.hasNoErrors
import com.chrisjenx.yakcov.onFocusLost
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.toFieldState
import com.chrisjenx.yakcov.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/* ===== Recommended wiring: the validator LIVES IN the ViewModel ===== */

// --8<-- [start:vm]
class SignUpViewModel : ViewModel() {
    // FieldValidator has a plain (non-@Composable) constructor — safe to own in a
    // ViewModel. It is snapshot-state backed, so composables reading value/state
    // recompose automatically, and it survives configuration changes with the VM.
    val email = FieldValidator("", rules = listOf(Required, Email))
    val password = FieldValidator("", rules = listOf(Required, MinLength(8)))

    /** Form-level submit: shows errors on every field, true when all pass. Unit-testable without UI. */
    fun submit(): Boolean = listOf(email, password).validate()
}
// --8<-- [end:vm]

// --8<-- [start:vm-ui]
@Composable
fun SignUpScreen(viewModel: SignUpViewModel = viewModel { SignUpViewModel() }) {
    Column {
        ValidatedField(viewModel.email, label = "Email")
        ValidatedField(viewModel.password, label = "Password")
        Button(
            onClick = { viewModel.submit() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
    }
}

// The @Composable rendering helpers are called HERE, in composition — never in the
// ViewModel. isError is a plain property; supportingText() resolves string resources.
@Composable
private fun ValidatedField(field: FieldValidator<String>, label: String) {
    OutlinedTextField(
        value = field.value,
        onValueChange = field::onValueChange,
        label = { Text(label) },
        isError = field.state.isError,
        supportingText = field.state.supportingText(),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusLost { field.onFocusLost() },
    )
}
// --8<-- [end:vm-ui]

/* ===== Alternative wiring: value-hoist — immutable UiState, validator logic stays pure ===== */

// --8<-- [start:vm-hoist]
private val emailRules = listOf(Required, Email)
private val passwordRules = listOf(Required, MinLength(8))

data class SignUpUiState(
    val email: String = "",
    val emailState: FieldValidationState = FieldValidationState.Pristine,
    val password: String = "",
    val passwordState: FieldValidationState = FieldValidationState.Pristine,
) {
    val canSubmit: Boolean get() = listOf(emailState, passwordState).hasNoErrors()
}

class HoistedSignUpViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        // Seed by folding the rules with showError = false so canSubmit is honest
        // before any input (a pristine required field must not look submittable).
        SignUpUiState(
            emailState = emailRules.toFieldState("", showError = false),
            passwordState = passwordRules.toFieldState("", showError = false),
        )
    )
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        // Recompute severity on every keystroke, but PRESERVE showError so no
        // error pops while the user is still typing.
        it.copy(email = value, emailState = emailRules.toFieldState(value, it.emailState.showError))
    }

    fun onEmailFocusLost() = _state.update {
        it.copy(emailState = emailRules.toFieldState(it.email, showError = true))
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, passwordState = passwordRules.toFieldState(value, it.passwordState.showError))
    }

    fun onPasswordFocusLost() = _state.update {
        it.copy(passwordState = passwordRules.toFieldState(it.password, showError = true))
    }

    /** Submit: force errors visible on all fields, then read validity off severity. */
    fun submit(): Boolean = _state.updateAndGet {
        it.copy(
            emailState = emailRules.toFieldState(it.email, showError = true),
            passwordState = passwordRules.toFieldState(it.password, showError = true),
        )
    }.canSubmit
}
// --8<-- [end:vm-hoist]

// --8<-- [start:vm-hoist-ui]
@Composable
fun HoistedSignUpScreen(
    viewModel: HoistedSignUpViewModel = viewModel { HoistedSignUpViewModel() },
) {
    val state by viewModel.state.collectAsState()
    Column {
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            isError = state.emailState.isError,
            supportingText = state.emailState.supportingText(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusLost(viewModel::onEmailFocusLost),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            isError = state.passwordState.isError,
            supportingText = state.passwordState.supportingText(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusLost(viewModel::onPasswordFocusLost),
        )
        Button(
            onClick = { viewModel.submit() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.canSubmit) "Create account" else "Fix errors to submit") }
    }
}
// --8<-- [end:vm-hoist-ui]
