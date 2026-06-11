package com.chrisjenx.yakcov.docs.circuit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.chrisjenx.yakcov.validate
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter

// --8<-- [start:circuit-state]
/** Circuit state: validators ride along as @Stable references; events flow back via the sink. */
data class SignUpState(
    val email: FieldValidator<String>,
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
        // Circuit presenters are composable functions, so `remember` keeps the
        // validators (and the in-flight draft) across recompositions.
        val email = remember { FieldValidator("", rules = listOf(Required, Email)) }
        val password = remember { FieldValidator("", rules = listOf(Required, MinLength(8))) }
        var submitted by remember { mutableStateOf<Boolean?>(null) }
        return SignUpState(email, password, submitted) { event ->
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
        ValidatedField(state.email, label = "Email")
        ValidatedField(state.password, label = "Password")
        Button(
            onClick = { state.eventSink(SignUpEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
        state.submitted?.let { Text(if (it) "Valid — proceeding" else "Fix the errors above") }
    }
}

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
