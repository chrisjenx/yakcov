package com.chrisjenx.yakcov.docs.mvi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.hasNoErrors
import com.chrisjenx.yakcov.onFocusLost
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.toFieldState

// --8<-- [start:mvi-model]
// Plain top-level rule lists: shared by reduce() and any unit test, no Compose needed.
private val emailRules: List<ValueValidatorRule<String>> = listOf(Required, Email)
private val passwordRules: List<ValueValidatorRule<String>> = listOf(Required, MinLength(8))

// Every field is a val; FieldValidationState is @Immutable, so the Model is value-stable.
data class SignUpModel(
    val email: String = "",
    val emailState: FieldValidationState = FieldValidationState.Pristine,
    val password: String = "",
    val passwordState: FieldValidationState = FieldValidationState.Pristine,
    /** null = not attempted, true = accepted, false = blocked (errors surfaced). */
    val submitted: Boolean? = null,
) {
    /** Driven purely by severity (ignores showError) — live from the first frame. */
    val canSubmit: Boolean get() = listOf(emailState, passwordState).hasNoErrors()
}

/** Seed with rules pre-run (showError = false) so canSubmit is honest before any input. */
fun initialSignUpModel(): SignUpModel = SignUpModel(
    emailState = emailRules.toFieldState("", showError = false),
    passwordState = passwordRules.toFieldState("", showError = false),
)

sealed interface SignUpEvent {
    data class EmailChanged(val value: String) : SignUpEvent
    data object EmailFocusLost : SignUpEvent
    data class PasswordChanged(val value: String) : SignUpEvent
    data object PasswordFocusLost : SignUpEvent
    data object Submit : SignUpEvent
}
// --8<-- [end:mvi-model]

// --8<-- [start:mvi-reduce]
// THE pure core: (Model, Event) -> Model. No Compose, no coroutines, no IO.
// showError is threaded, not recomputed: Changed preserves it; FocusLost/Submit force it true.
fun reduce(model: SignUpModel, event: SignUpEvent): SignUpModel = when (event) {
    is SignUpEvent.EmailChanged -> model.copy(
        email = event.value,
        emailState = emailRules.toFieldState(event.value, model.emailState.showError),
        submitted = null,
    )
    SignUpEvent.EmailFocusLost -> model.copy(
        emailState = emailRules.toFieldState(model.email, showError = true),
    )
    is SignUpEvent.PasswordChanged -> model.copy(
        password = event.value,
        passwordState = passwordRules.toFieldState(event.value, model.passwordState.showError),
        submitted = null,
    )
    SignUpEvent.PasswordFocusLost -> model.copy(
        passwordState = passwordRules.toFieldState(model.password, showError = true),
    )
    SignUpEvent.Submit -> {
        // Surface errors on every field, then read validity off severity.
        val surfaced = model.copy(
            emailState = emailRules.toFieldState(model.email, showError = true),
            passwordState = passwordRules.toFieldState(model.password, showError = true),
        )
        surfaced.copy(submitted = surfaced.canSubmit)
    }
}
// --8<-- [end:mvi-reduce]

// --8<-- [start:mvi-store]
// Single source of truth + single dispatch entry point. The only Compose-aware piece;
// swap for a ViewModel + StateFlow without touching reduce/Model/Event.
@Stable
class SignUpStore(initial: SignUpModel = initialSignUpModel()) {
    var model by mutableStateOf(initial)
        private set

    fun dispatch(event: SignUpEvent) {
        model = reduce(model, event)
    }
}
// --8<-- [end:mvi-store]

// --8<-- [start:mvi-ui]
@Composable
fun MviSignUpScreen(store: SignUpStore = remember { SignUpStore() }) {
    val model = store.model
    Column {
        OutlinedTextField(
            value = model.email,
            onValueChange = { store.dispatch(SignUpEvent.EmailChanged(it)) },
            label = { Text("Email") },
            isError = model.emailState.isError,
            supportingText = model.emailState.supportingText(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusLost { store.dispatch(SignUpEvent.EmailFocusLost) },
        )
        OutlinedTextField(
            value = model.password,
            onValueChange = { store.dispatch(SignUpEvent.PasswordChanged(it)) },
            label = { Text("Password") },
            isError = model.passwordState.isError,
            supportingText = model.passwordState.supportingText(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusLost { store.dispatch(SignUpEvent.PasswordFocusLost) },
        )
        Button(
            onClick = { store.dispatch(SignUpEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (model.canSubmit) "Create account" else "Fix errors to submit") }
        model.submitted?.let { Text(if (it) "Valid — proceeding" else "Fix the errors above") }
    }
}
// --8<-- [end:mvi-ui]
