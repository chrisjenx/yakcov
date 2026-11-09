package com.chrisjenx.yakcov.docs.textfieldstate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.hasNoErrors
import com.chrisjenx.yakcov.onFocusCursorToEnd
import com.chrisjenx.yakcov.strings.Decimal
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.strings.rememberTextFieldStateValidator
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.textChanges
import com.chrisjenx.yakcov.toFieldState
import com.chrisjenx.yakcov.validate
import com.chrisjenx.yakcov.validationBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// --8<-- [start:tfs-validator]
@Composable
fun EmailField() {
    // Owns a saveable TextFieldState and observes it — no onValueChange anywhere.
    val email = rememberTextFieldStateValidator(rules = listOf(Required, Email))
    Column {
        with(email) {
            OutlinedTextField(
                state = textFieldState,
                label = { Text("Email") },
                modifier = Modifier
                    .onFocusCursorToEnd()
                    .validationConfig(
                        validateOnFocusLost = true,
                        shakeOnInvalid = true,
                        showErrorOnInteraction = false, // errors on blur/submit only
                    ),
                isError = isError(),
                supportingText = supportingText(),
            )
        }
        Button(onClick = { if (listOf(email).validate()) { /* submit */ } }) { Text("Save") }
    }
}
// --8<-- [end:tfs-validator]

// --8<-- [start:tfs-udf-model]
private val amountRules: List<ValueValidatorRule<String>> = listOf(Required, Decimal)

/** Immutable: the text is a String snapshot. The TextFieldState lives on the component, not here. */
data class ExpenseModel(
    val amount: String = "",
    val amountState: FieldValidationState = amountRules.toFieldState("", showError = false),
    val submitAttempts: Int = 0,
) {
    val canSubmit: Boolean get() = listOf(amountState).hasNoErrors()
}

sealed interface ExpenseEvent {
    data class AmountChanged(val text: String) : ExpenseEvent
    data object AmountFocusLost : ExpenseEvent
    data object Submit : ExpenseEvent
}

fun reduce(model: ExpenseModel, event: ExpenseEvent): ExpenseModel = when (event) {
    // Identical in shape to the TextFieldValue hoist: fold on the text, preserve showError.
    is ExpenseEvent.AmountChanged -> model.copy(
        amount = event.text,
        amountState = amountRules.toFieldState(event.text, model.amountState.showError),
    )
    ExpenseEvent.AmountFocusLost ->
        model.copy(amountState = amountRules.toFieldState(model.amount, showError = true))
    ExpenseEvent.Submit -> model.copy(
        amountState = amountRules.toFieldState(model.amount, showError = true),
        submitAttempts = model.submitAttempts + 1,
    )
}
// --8<-- [end:tfs-udf-model]

// --8<-- [start:tfs-udf-component]
/** The component (Decompose component, ViewModel, presenter…) — NOT the Model. */
@Stable
class ExpenseComponent(scope: CoroutineScope) {
    /** The field's editing buffer. A `val`, created once, never copied into the Model. */
    val amount = TextFieldState()

    var model by mutableStateOf(ExpenseModel())
        private set

    fun dispatch(event: ExpenseEvent) {
        model = reduce(model, event)
    }

    init {
        // state → model: the one-way hand-off. Text only; caret moves don't emit.
        scope.launch { amount.textChanges().collect { dispatch(ExpenseEvent.AmountChanged(it)) } }
    }
}
// --8<-- [end:tfs-udf-component]

// --8<-- [start:tfs-udf-ui]
@Composable
fun ExpenseForm(component: ExpenseComponent) {
    val model = component.model
    OutlinedTextField(
        state = component.amount,
        modifier = Modifier
            .onFocusCursorToEnd(state = component.amount)
            .validationBehavior(
                isError = model.amountState.isError,
                shakeTrigger = model.submitAttempts,
                onFocusLost = { component.dispatch(ExpenseEvent.AmountFocusLost) },
            ),
        isError = model.amountState.isError,
        supportingText = model.amountState.supportingText(),
    )
}
// --8<-- [end:tfs-udf-ui]

// --8<-- [start:tfs-writes]
/** A reducer-owned, one-shot write request. The version makes each request distinct. */
data class Seed(val text: String, val version: Int)

@Composable
fun ProgrammaticWrites(state: TextFieldState, suggestions: List<String>, seed: Seed?) {
    // Reducer re-seed (server load, edit prefill): apply only when the version changes, so it can
    // never overwrite keystrokes the Model hasn't caught up with yet.
    LaunchedEffect(seed?.version) {
        if (seed != null) state.setTextAndPlaceCursorAtEnd(seed.text)
    }
    Column {
        OutlinedTextField(state = state)
        // Suggestion pick / preset fill: write the state at the event's source. Caret lands at the
        // end; the textChanges() collector carries the text to the Model.
        suggestions.forEach { s ->
            Button(onClick = { state.setTextAndPlaceCursorAtEnd(s) }) { Text(s) }
        }
        // Clear.
        Button(onClick = { state.clearText() }) { Text("Clear") }
    }
}
// --8<-- [end:tfs-writes]

// --8<-- [start:tfs-transform]
/** Input: reject anything but digits and one dot. Rules see the filtered text. */
val CurrencyInput = InputTransformation.byValue { current, proposed ->
    val filtered = proposed.filter { it.isDigit() || it == '.' }
    if (filtered.count { it == '.' } > 1) current else filtered
}

/** Output: display-only "12.5" → "12.50". Rules and the Model still see "12.5". */
val CurrencyDisplay = OutputTransformation {
    val dot = asCharSequence().indexOf('.')
    if (dot >= 0) repeat(2 - (length - dot - 1)) { insert(length, "0") }
}

@Composable
fun CurrencyField(component: ExpenseComponent) {
    val model = component.model
    OutlinedTextField(
        state = component.amount,
        inputTransformation = CurrencyInput,
        outputTransformation = CurrencyDisplay,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.validationBehavior(
            isError = model.amountState.isError,
            shakeTrigger = model.submitAttempts,
            onFocusLost = { component.dispatch(ExpenseEvent.AmountFocusLost) },
        ),
        isError = model.amountState.isError,
        supportingText = model.amountState.supportingText(),
    )
}
// --8<-- [end:tfs-transform]
