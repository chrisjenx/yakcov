package com.chrisjenx.yakcov.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.sample.ui.theme.YakcovTheme
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.textChanges
import com.chrisjenx.yakcov.toFieldState
import com.chrisjenx.yakcov.validationBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Skipped-input demo: the reason to move fields to TextFieldState.
 *
 * Both fields go through the SAME asynchronous store: every change is reduced on
 * [Dispatchers.Default] after [SkippedInputStore.lagMs], the way a store hop, a reducer on another
 * dispatcher, or a slow frame makes a model update arrive after the next key.
 *
 * - **Legacy**: `OutlinedTextField(value, onValueChange)` renders the model's `TextFieldValue`. Each
 *   late model write resets the field's edit buffer to stale text, so fast typing and pastes lose
 *   characters or land in the wrong place.
 * - **TextFieldState**: the field owns its text on the Compose clock; the model only observes it via
 *   `textChanges()` and folds the rules in the reducer. Nothing writes back into the field.
 *
 * Launch: `adb shell am start -n com.chrisjenx.yakcov.sample/.SkippedInputActivity`
 */
class SkippedInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YakcovTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets.safeDrawing) { p ->
                    SkippedInputScreen(Modifier.padding(p))
                }
            }
        }
    }
}

private val nameRules: List<ValueValidatorRule<String>> = listOf(Required, MinLength(3))

data class SkippedInputModel(
    val legacy: TextFieldValue = TextFieldValue(""),
    val name: String = "",
    val nameState: FieldValidationState = nameRules.toFieldState("", showError = false),
    val submitAttempts: Int = 0,
)

sealed interface SkippedInputEvent {
    data class LegacyChanged(val value: TextFieldValue) : SkippedInputEvent
    data class NameChanged(val text: String) : SkippedInputEvent
    data object NameFocusLost : SkippedInputEvent
    data object Submit : SkippedInputEvent
    data object Clear : SkippedInputEvent
}

fun reduce(model: SkippedInputModel, event: SkippedInputEvent): SkippedInputModel = when (event) {
    is SkippedInputEvent.LegacyChanged -> model.copy(legacy = event.value)
    is SkippedInputEvent.NameChanged -> model.copy(
        name = event.text,
        nameState = nameRules.toFieldState(event.text, model.nameState.showError),
    )
    SkippedInputEvent.NameFocusLost ->
        model.copy(nameState = nameRules.toFieldState(model.name, showError = true))
    SkippedInputEvent.Submit -> model.copy(
        nameState = nameRules.toFieldState(model.name, showError = true),
        submitAttempts = model.submitAttempts + 1,
    )
    SkippedInputEvent.Clear -> SkippedInputModel()
}

/** A deliberately asynchronous store: reduces on a background dispatcher after [lagMs]. */
class SkippedInputStore(private val scope: CoroutineScope) {
    private val _model = MutableStateFlow(SkippedInputModel())
    val model: StateFlow<SkippedInputModel> = _model
    var lagMs by mutableLongStateOf(16L)

    fun dispatch(event: SkippedInputEvent) {
        val lag = lagMs
        scope.launch(Dispatchers.Default) {
            if (lag > 0) delay(lag)
            _model.update { reduce(it, event) }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SkippedInputScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val store = remember { SkippedInputStore(scope) }
    // The component-owned editing buffer for the TextFieldState field — never in the Model.
    val name = remember { TextFieldState() }
    LaunchedEffect(name) {
        name.textChanges().collect { store.dispatch(SkippedInputEvent.NameChanged(it)) }
    }
    val model by store.model.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Skipped input", style = MaterialTheme.typography.headlineSmall)
        Text("Model lag: ${store.lagMs} ms (every change is reduced off the main thread this late)")
        Slider(
            value = store.lagMs.toFloat(),
            onValueChange = { store.lagMs = it.toLong() },
            valueRange = 0f..100f,
        )

        Text("Legacy — TextFieldValue hoisted through the model", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = model.legacy,
            onValueChange = { store.dispatch(SkippedInputEvent.LegacyChanged(it)) },
            label = { Text("Type fast or paste here") },
            modifier = Modifier.fillMaxWidth().testTag("legacyField"),
        )
        Text("model: \"${model.legacy.text}\"", modifier = Modifier.testTag("legacyModel"))

        Text("TextFieldState — field owns the text, model observes", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            state = name,
            label = { Text("Type fast or paste here") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stateField")
                .validationBehavior(
                    isError = model.nameState.isError,
                    shakeTrigger = model.submitAttempts,
                    onFocusLost = { store.dispatch(SkippedInputEvent.NameFocusLost) },
                ),
            isError = model.nameState.isError,
            supportingText = model.nameState.supportingText(default = "Required, at least 3 characters"),
        )
        Text("model: \"${model.name}\"", modifier = Modifier.testTag("stateModel"))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { store.dispatch(SkippedInputEvent.Submit) }) { Text("Submit") }
            OutlinedButton(
                onClick = {
                    name.clearText()
                    store.dispatch(SkippedInputEvent.Clear)
                },
                modifier = Modifier.testTag("clear"),
            ) { Text("Clear") }
        }
    }
}
