package com.chrisjenx.yakcov.docs.mvi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.hasNoErrors
import com.chrisjenx.yakcov.onFocusCursorToEnd
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.textChanges
import com.chrisjenx.yakcov.toFieldState
import com.chrisjenx.yakcov.validationBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    /** Monotonic submit counter — drives shake. A repeated invalid submit yields an
     *  EQUAL FieldValidationState, so a state diff alone can never re-fire the shake. */
    val submitAttempts: Int = 0,
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
            submitAttempts = model.submitAttempts + 1,
        )
        surfaced.copy(submitted = surfaced.canSubmit)
    }
}
// --8<-- [end:mvi-reduce]

// --8<-- [start:mvi-store]
// The component: your ViewModel / Decompose component / presenter. It owns two things:
//  - the store (Model + dispatch) — the source of truth for everything except the keystrokes, and
//  - one TextFieldState per field — the editing buffer, owned by the field on the Compose clock.
// Text flows ONE way: field -> textChanges() -> dispatch -> reduce. Nothing ever writes the Model
// back into a field while the user types, so no keystroke or paste can be lost to a late update.
@Stable
class SignUpComponent(scope: CoroutineScope, initial: SignUpModel = initialSignUpModel()) {
    val email = TextFieldState(initial.email)
    val password = TextFieldState(initial.password)

    var model by mutableStateOf(initial)
        private set

    fun dispatch(event: SignUpEvent) {
        model = reduce(model, event)
    }

    init {
        // The whole hand-off. textChanges() emits plain Strings (current text first, then each
        // change) and ignores caret moves. In a ViewModel pass viewModelScope; in Decompose, the
        // component's lifecycle scope.
        scope.launch { email.textChanges().collect { dispatch(SignUpEvent.EmailChanged(it)) } }
        scope.launch { password.textChanges().collect { dispatch(SignUpEvent.PasswordChanged(it)) } }
    }
}
// --8<-- [end:mvi-store]

// --8<-- [start:mvi-writes]
// Programmatic writes (suggestion pick, prefill, clear) go to the TextFieldState — the same path as
// typing — and reach the Model through textChanges(). The caret lands at the end.
fun SignUpComponent.prefillEmail(suggestion: String) = email.setTextAndPlaceCursorAtEnd(suggestion)

fun SignUpComponent.clearForm() {
    email.clearText()
    password.clearText()
}
// --8<-- [end:mvi-writes]

// --8<-- [start:mvi-ui]
@Composable
fun MviSignUpScreen() {
    // In an app this is your ViewModel / component; remembered here to keep the example standalone.
    val scope = rememberCoroutineScope()
    val component = remember { SignUpComponent(scope) }
    val model = component.model
    Column {
        OutlinedTextField(
            state = component.email, // the field owns the text — no value / onValueChange
            label = { Text("Email") },
            isError = model.emailState.isError,
            supportingText = model.emailState.supportingText(),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusCursorToEnd(state = component.email)
                .validationBehavior(
                    isError = model.emailState.isError,
                    shakeTrigger = model.submitAttempts,
                    onFocusLost = { component.dispatch(SignUpEvent.EmailFocusLost) },
                ),
        )
        OutlinedTextField(
            state = component.password,
            label = { Text("Password") },
            isError = model.passwordState.isError,
            supportingText = model.passwordState.supportingText(),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .validationBehavior(
                    isError = model.passwordState.isError,
                    shakeTrigger = model.submitAttempts,
                    onFocusLost = { component.dispatch(SignUpEvent.PasswordFocusLost) },
                ),
        )
        Button(
            onClick = { component.dispatch(SignUpEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (model.canSubmit) "Create account" else "Fix errors to submit") }
        model.submitted?.let { Text(if (it) "Valid — proceeding" else "Fix the errors above") }
    }
}
// --8<-- [end:mvi-ui]
