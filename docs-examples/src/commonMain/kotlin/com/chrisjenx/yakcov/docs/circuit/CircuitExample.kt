package com.chrisjenx.yakcov.docs.circuit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.FieldValidator
import com.chrisjenx.yakcov.onFocusLost
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.textChanges
import com.chrisjenx.yakcov.validate
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter

// --8<-- [start:circuit-state]
/**
 * Circuit state. Each field contributes its editing buffer (a TextFieldState — the field owns the
 * text) and the FieldValidator that follows it; both are @Stable references. Events flow back via
 * the sink.
 */
data class SignUpState(
    val emailText: TextFieldState,
    val email: FieldValidator<String>,
    val passwordText: TextFieldState,
    val password: FieldValidator<String>,
    val submitted: Boolean?,
    val eventSink: (SignUpEvent) -> Unit,
) : CircuitUiState

sealed interface SignUpEvent : CircuitUiEvent {
    data object Submit : SignUpEvent
}
// --8<-- [end:circuit-state]

// --8<-- [start:circuit-presenter]
class SignUpPresenter : Presenter<SignUpState> {
    @Composable
    override fun present(): SignUpState {
        // Circuit presenters are composable functions, so `remember` keeps the text buffers and
        // validators across recompositions (`rememberRetained` to survive Circuit's retention).
        val emailText = remember { TextFieldState() }
        val passwordText = remember { TextFieldState() }
        val email = remember { FieldValidator("", rules = listOf(Required, Email)) }
        val password = remember { FieldValidator("", rules = listOf(Required, MinLength(8))) }
        // One-way hand-off: the field's text -> its validator. Never the other way.
        LaunchedEffect(emailText) { emailText.textChanges().collect(email::onValueChange) }
        LaunchedEffect(passwordText) { passwordText.textChanges().collect(password::onValueChange) }
        var submitted by remember { mutableStateOf<Boolean?>(null) }
        return SignUpState(emailText, email, passwordText, password, submitted) { event ->
            when (event) {
                // Form-level validate(): shows errors on every field, true when all pass.
                SignUpEvent.Submit -> submitted = listOf(email, password).validate()
            }
        }
    }
}
// --8<-- [end:circuit-presenter]

// --8<-- [start:circuit-ui]
@Composable
fun SignUpUi(state: SignUpState, modifier: Modifier = Modifier) {
    Column(modifier) {
        ValidatedField(state.emailText, state.email, label = "Email")
        ValidatedField(state.passwordText, state.password, label = "Password")
        Button(
            onClick = { state.eventSink(SignUpEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
        state.submitted?.let { Text(if (it) "Valid — proceeding" else "Fix the errors above") }
    }
}

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
// --8<-- [end:circuit-ui]

// --8<-- [start:circuit-host]
/**
 * Minimal host. In a real app you register SignUpPresenter/SignUpUi in a Circuit via
 * Presenter.Factory + Ui.Factory and render through CircuitContent(screen); a routed
 * Screen needs @Parcelize on Android, which is app-level wiring beyond validation —
 * so we keep it out of this example.
 */
@Composable
fun SignUpCircuitHost() {
    val presenter = remember { SignUpPresenter() }
    SignUpUi(presenter.present())
}
// --8<-- [end:circuit-host]
