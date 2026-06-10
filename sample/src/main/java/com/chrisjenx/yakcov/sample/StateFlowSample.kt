package com.chrisjenx.yakcov.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.FieldValidator
import com.chrisjenx.yakcov.FieldValidatorEvent
import com.chrisjenx.yakcov.ValidationResult.Outcome
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.validate
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
    // Blur and submit are the same transform here: reveal errors on the current draft. The ticker
    // still labels them apart (see emitFrom) — only the model math is shared.
    FlowEvent.Blurred, FlowEvent.Submit -> model.copy(
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

// Show the TAIL of the draft (what was just typed), with a leading ellipsis when truncated, so
// consecutive ticker lines differ and the chip matches the end of the field — not a repeated prefix.
private fun String.quoted(): String {
    val max = 12
    return if (length <= max) "\"$this\"" else "\"…${takeLast(max)}\""
}

private fun FieldValidationState.shortLabel(): String =
    "${severity.name}, ${if (showError) "shown" else "hidden"}"

private fun chipsOf(state: FieldValidationState, draft: String, identity: String): List<Chip> = listOf(
    Chip(
        name = "severity",
        value = state.severity.name,
        tone = when (state.severity) {
            Outcome.ERROR -> Tone.ERROR
            Outcome.WARNING -> Tone.WARNING
            Outcome.INFO -> Tone.NEUTRAL
            Outcome.SUCCESS -> Tone.SUCCESS
        },
    ),
    Chip(name = "showError", value = state.showError.toString(), tone = Tone.NEUTRAL),
    Chip(name = "draft", value = draft.quoted(), tone = Tone.NEUTRAL),
    Chip(name = "identity", value = identity, tone = Tone.NEUTRAL),
)

/** Presenter tap: FieldValidatorObserver events -> FlowTicks. */
private fun TickFeed.emitFrom(event: FieldValidatorEvent<String>) {
    val (edge, call) = when (event) {
        is FieldValidatorEvent.ValueChanged -> Edge.INPUT to "onValueChange(${event.value.quoted()})"
        is FieldValidatorEvent.Validated -> Edge.COMMIT to "validate()"
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

// --- The visualizer screen: one field drives both engines; tabs switch the full-width panel. ----

@Composable
fun StateFlowScreen(modifier: Modifier = Modifier) {
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

    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "State flow — type once, watch both",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "The same input drives a presenter-owned FieldValidator AND a reducer-MVI " +
                "store. Switch tabs to compare how each reacts to the same keystroke, blur, and submit.",
            style = MaterialTheme.typography.bodySmall,
        )

        // Tabs pinned just under the intro so you can switch flows even with the keyboard open.
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Presenter") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Reducer-MVI") },
            )
        }

        // The live panel + field + submit scroll as a group, so the button never gets squashed when
        // the keyboard is up; the field auto-scrolls into view on focus, keeping the panel above it.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // One full-width panel for the selected tab (roomier than side-by-side). Both engines
            // keep running off the field, so each tab's feed reflects the full history on switch.
            when (selectedTab) {
                0 -> StateFlowPanel(
                    title = "FieldValidator · mutates in place",
                    nodes = listOf("TextField", "FieldValidator", "UI"),
                    edgeLabels = listOf("onValueChange()", "mutates .state"),
                    ticks = presenterFeed.ticks,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> StateFlowPanel(
                    title = "reduce() · new Model per event",
                    nodes = listOf("TextField", "reduce()", "UI"),
                    edgeLabels = listOf("Event", "new Model"),
                    ticks = mviFeed.ticks,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                // validator.value is the single source of truth; the String overload keeps the cursor
                // at the end on external changes, so no separate TextFieldValue state is needed.
                value = validator.value,
                onValueChange = {
                    // DEMO-ONLY fan-out: real apps pick ONE mechanism. Driving both lets the panel
                    // above animate whichever pipeline you've selected from identical input.
                    validator.onValueChange(it)
                    store.dispatch(FlowEvent.Changed(it))
                },
                label = { Text("Email — drives both engines") },
                isError = validator.state.isError,
                supportingText = validator.state.supportingText(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusLost {
                        validator.onFocusLost()
                        store.dispatch(FlowEvent.Blurred)
                    },
            )
            Button(
                onClick = {
                    listOf(validator).validate()
                    store.dispatch(FlowEvent.Submit)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Submit") }
        }
    }
}
