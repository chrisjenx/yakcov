package com.chrisjenx.yakcov.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.FieldValidator
import com.chrisjenx.yakcov.FieldValidatorEvent
import com.chrisjenx.yakcov.ValidationResult.Outcome
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.allValid
import com.chrisjenx.yakcov.onFocusLost
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.toFieldState

/*
 * State-flow visualizer: ONE form drives BOTH engines — a presenter-owned FieldValidator and a
 * reducer-MVI store — and their panels animate side-by-side from the same input. The same
 * keystroke produces two visibly different pipelines: mutate-in-place vs new-Model-per-event.
 *
 * Both engines run the same rules, so their severities always agree on screen; any divergence
 * would indicate a library bug.
 */

private val flowRules: List<ValueValidatorRule<String>> = listOf(Required, Email)

// --- Local MVI types (Flow-prefixed: MviSample.kt already owns top-level Model/Event/Field). ---

data class FlowModel(
    val draft: String = "",
    val state: FieldValidationState = flowRules.toFieldState("", showError = false),
    /** Counts Model copies — the identity teaching point (vs the validator's "same instance"). */
    val generation: Int = 0,
)

sealed interface FlowEvent {
    data class Changed(val text: String) : FlowEvent
    data object Blurred : FlowEvent
    data object Submit : FlowEvent
}

fun flowReduce(model: FlowModel, event: FlowEvent): FlowModel = when (event) {
    is FlowEvent.Changed -> model.copy(
        draft = event.text,
        state = flowRules.toFieldState(event.text, showError = model.state.showError),
        generation = model.generation + 1,
    )
    FlowEvent.Blurred -> model.copy(
        state = flowRules.toFieldState(model.draft, showError = true),
        generation = model.generation + 1,
    )
    FlowEvent.Submit -> model.copy(
        state = flowRules.toFieldState(model.draft, showError = true),
        generation = model.generation + 1,
    )
}

/** Minimal store with an instrumentation tap: [onDispatch] sees each event + the model AFTER it. */
@Stable
class FlowStore(
    initial: FlowModel,
    private val onDispatch: (event: FlowEvent, after: FlowModel) -> Unit,
) {
    var model by mutableStateOf(initial)
        private set

    fun dispatch(event: FlowEvent) {
        model = flowReduce(model, event)
        onDispatch(event, model)
    }
}

// --- Taps: map each mechanism's events into the panels' shared FlowTick currency. -------------

private fun String.quoted(): String = "\"${take(10)}\""

private fun FieldValidationState.shortLabel(): String =
    "${severity.name}, ${if (showError) "shown" else "hidden"}"

private fun chipsOf(state: FieldValidationState, draft: String, identity: String): List<Chip> = listOf(
    Chip(
        label = state.severity.name,
        tone = when (state.severity) {
            Outcome.ERROR -> Tone.ERROR
            Outcome.WARNING -> Tone.WARNING
            Outcome.INFO -> Tone.NEUTRAL
            Outcome.SUCCESS -> Tone.SUCCESS
        },
    ),
    Chip(label = if (state.showError) "shown" else "hidden", tone = Tone.NEUTRAL),
    Chip(label = draft.quoted(), tone = Tone.NEUTRAL),
    Chip(label = identity, tone = Tone.NEUTRAL),
)

/** Presenter tap: FieldValidatorObserver events -> FlowTicks. */
private fun TickFeed.emitFrom(event: FieldValidatorEvent<String>) {
    val (edge, call) = when (event) {
        is FieldValidatorEvent.ValueChanged -> Edge.INPUT to "onValueChange(${event.value.quoted()})"
        is FieldValidatorEvent.Revealed -> Edge.COMMIT to "reveal()"
        is FieldValidatorEvent.Reset -> Edge.COMMIT to "reset(${event.value.quoted()})"
    }
    emit(
        edge = edge,
        tickerLine = "$call → ${event.state.shortLabel()}",
        chips = chipsOf(event.state, event.value, identity = "same instance"),
    )
}

/** MVI tap: dispatched events -> FlowTicks. */
private fun TickFeed.emitFrom(event: FlowEvent, after: FlowModel) {
    val (edge, call) = when (event) {
        is FlowEvent.Changed -> Edge.INPUT to "Changed(${event.text.quoted()})"
        FlowEvent.Blurred -> Edge.COMMIT to "Blurred"
        FlowEvent.Submit -> Edge.COMMIT to "Submit"
    }
    emit(
        edge = edge,
        tickerLine = "$call → Model#${after.generation}",
        chips = chipsOf(after.state, after.draft, identity = "Model#${after.generation}"),
    )
}

// --- The synchronized-split section. -----------------------------------------------------------

@Composable
fun StateFlowSample() {
    val presenterFeed = remember { TickFeed() }
    val mviFeed = remember { TickFeed() }

    val validator = remember {
        FieldValidator(
            initial = "",
            rules = flowRules,
            observer = { event -> presenterFeed.emitFrom(event) },
        )
    }
    val store = remember {
        FlowStore(FlowModel()) { event, after -> mviFeed.emitFrom(event, after) }
    }

    Text(text = "State flow — type once, watch both", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "The same input drives a presenter-owned FieldValidator AND a reducer-MVI store.",
        style = MaterialTheme.typography.bodySmall,
    )

    var tfv by remember { mutableStateOf(TextFieldValue("")) }
    if (tfv.text != validator.value) {
        tfv = TextFieldValue(validator.value, TextRange(validator.value.length))
    }
    OutlinedTextField(
        value = tfv,
        onValueChange = {
            tfv = it
            // DEMO-ONLY fan-out: real apps pick ONE mechanism. Driving both lets the panels
            // below animate the two pipelines from identical input.
            validator.onValueChange(it.text)
            store.dispatch(FlowEvent.Changed(it.text))
        },
        label = { Text("Email — drives both engines") },
        isError = validator.state.isError,
        supportingText = validator.state.supportingText(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusLost {
                validator.onBlur()
                store.dispatch(FlowEvent.Blurred)
            },
    )
    Button(
        onClick = {
            listOf(validator).allValid()
            store.dispatch(FlowEvent.Submit)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Submit") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StateFlowPanel(
            title = "Presenter",
            nodes = listOf("TextField", "FieldValidator", "UI"),
            edgeLabels = listOf("onValueChange()", "mutates .state"),
            ticks = presenterFeed.ticks,
            modifier = Modifier.weight(1f),
        )
        StateFlowPanel(
            title = "Reducer-MVI",
            nodes = listOf("TextField", "reduce()", "UI"),
            edgeLabels = listOf("Event", "new Model"),
            ticks = mviFeed.ticks,
            modifier = Modifier.weight(1f),
        )
    }
}
