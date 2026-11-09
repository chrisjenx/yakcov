package com.chrisjenx.yakcov.docs.viewmodel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.FieldValidator
import com.chrisjenx.yakcov.hasNoErrors
import com.chrisjenx.yakcov.onFocusLost
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.textChanges
import com.chrisjenx.yakcov.toFieldState
import com.chrisjenx.yakcov.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

// --8<-- [start:vm]
class SignUpViewModel : ViewModel() {
    // The FIELD owns the text: one TextFieldState per field, created once with the VM (so the
    // draft survives configuration changes). Never round-trip the text through the VM's state.
    val emailText = TextFieldState()
    val passwordText = TextFieldState()

    // FieldValidator has a plain (non-@Composable) constructor, so it is safe to own in a
    // ViewModel. Here it FOLLOWS the field's text and owns the validation state.
    val email = FieldValidator("", rules = listOf(Required, Email))
    val password = FieldValidator("", rules = listOf(Required, MinLength(8)))

    init {
        // One-way hand-off: text -> validator. textChanges() emits Strings and ignores caret moves.
        viewModelScope.launch { emailText.textChanges().collect(email::onValueChange) }
        viewModelScope.launch { passwordText.textChanges().collect(password::onValueChange) }
    }

    /** Form-level submit: shows errors on every field, true when all pass. Unit-testable without UI. */
    fun submit(): Boolean = listOf(email, password).validate()
}
// --8<-- [end:vm]

// --8<-- [start:vm-ui]
@Composable
fun SignUpScreen(
    onSubmit: () -> Unit,
    viewModel: SignUpViewModel = viewModel { SignUpViewModel() },
) {
    Column {
        ValidatedField(viewModel.emailText, viewModel.email, label = "Email")
        ValidatedField(viewModel.passwordText, viewModel.password, label = "Password")
        Button(
            // submit() surfaces errors on every field and returns true only when all pass.
            onClick = { if (viewModel.submit()) onSubmit() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
    }
}

// The @Composable rendering helpers are called HERE, in composition — never in the
// ViewModel. isError is a plain property; supportingText() resolves string resources.
@Composable
private fun ValidatedField(text: TextFieldState, field: FieldValidator<String>, label: String) {
    OutlinedTextField(
        state = text, // the field owns the text — no value / onValueChange
        label = { Text(label) },
        isError = field.state.isError,
        supportingText = field.state.supportingText(),
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusLost { field.onFocusLost() },
    )
}
// --8<-- [end:vm-ui]

/* ===== Alternative wiring: immutable UiState, validator logic stays pure ===== */

// --8<-- [start:vm-hoist]
private val emailRules = listOf(Required, Email)
private val passwordRules = listOf(Required, MinLength(8))

/** Text is a plain String snapshot of the field; the caret lives in the field's TextFieldState. */
data class SignUpUiState(
    val email: String = "",
    val emailState: FieldValidationState = FieldValidationState.Pristine,
    val password: String = "",
    val passwordState: FieldValidationState = FieldValidationState.Pristine,
) {
    val canSubmit: Boolean get() = listOf(emailState, passwordState).hasNoErrors()
}

class HoistedSignUpViewModel : ViewModel() {
    // The editing buffers — on the VM, NOT in SignUpUiState.
    val emailText = TextFieldState()
    val passwordText = TextFieldState()

    private val _state = MutableStateFlow(
        // Seed by folding the rules with showError = false so canSubmit is honest
        // before any input (a pristine required field must not look submittable).
        SignUpUiState(
            emailState = emailRules.toFieldState("", showError = false),
            passwordState = passwordRules.toFieldState("", showError = false),
        )
    )
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    init {
        // field -> UiState, one way. The UiState never flows back into the field, so a state
        // update that lands a frame late can't overwrite (and drop) a newer keystroke.
        viewModelScope.launch { emailText.textChanges().collect(::onEmailChange) }
        viewModelScope.launch { passwordText.textChanges().collect(::onPasswordChange) }
    }

    private fun onEmailChange(value: String) = _state.update {
        // Recompute severity on every keystroke, but PRESERVE showError so no
        // error pops while the user is still typing.
        it.copy(email = value, emailState = emailRules.toFieldState(value, it.emailState.showError))
    }

    fun onEmailFocusLost() = _state.update {
        it.copy(emailState = emailRules.toFieldState(it.email, showError = true))
    }

    private fun onPasswordChange(value: String) = _state.update {
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
            state = viewModel.emailText,
            label = { Text("Email") },
            isError = state.emailState.isError,
            supportingText = state.emailState.supportingText(),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusLost(viewModel::onEmailFocusLost),
        )
        OutlinedTextField(
            state = viewModel.passwordText,
            label = { Text("Password") },
            isError = state.passwordState.isError,
            supportingText = state.passwordState.supportingText(),
            lineLimits = TextFieldLineLimits.SingleLine,
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
