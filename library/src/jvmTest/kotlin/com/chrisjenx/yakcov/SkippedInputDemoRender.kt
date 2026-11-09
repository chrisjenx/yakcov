package com.chrisjenx.yakcov

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the skipped-input demo (the JVM twin of the sample's `SkippedInputActivity`) to PNG
 * frames, one per keystroke, for the PR / decision record. Not a regression test — the assertions
 * live in [AsyncModelInputTest]. Runs only when `YAKCOV_DEMO_OUT` is set:
 *
 * ```
 * YAKCOV_DEMO_OUT=/tmp/demo ./gradlew :library:jvmTest --tests '*SkippedInputDemoRender*' --rerun
 * ```
 *
 * Both fields receive the SAME keystroke on the same frame (via the IME's insert-at-cursor action,
 * so focus never moves) and feed the SAME async model, which applies each change [MODEL_LAG_MS]
 * late; keys arrive every [KEY_GAP_MS].
 */
@OptIn(ExperimentalTestApi::class)
class SkippedInputDemoRender {

    private val sentence = "the quick brown fox jumps over the lazy dog"
    private val rules = listOf(Required, MinLength(3))

    @Test
    fun renderFrames() {
        val out = System.getenv("YAKCOV_DEMO_OUT")?.let(::File) ?: return
        out.mkdirs()
        runComposeUiTest {
            val legacyModel = MutableStateFlow(TextFieldValue(""))
            val stateModel = MutableStateFlow("")
            val state = TextFieldState()
            setContent {
                val scope = rememberCoroutineScope()
                LaunchedEffect(state) {
                    state.textChanges().collect { t -> scope.launch { delay(MODEL_LAG_MS); stateModel.value = t } }
                }
                val legacy by legacyModel.collectAsState()
                val modelText by stateModel.collectAsState()
                DemoScreen(
                    legacy = legacy,
                    onLegacyChange = { v -> scope.launch { delay(MODEL_LAG_MS); legacyModel.value = v } },
                    state = state,
                    stateModelText = modelText,
                    stateFieldState = rules.toFieldState(modelText, showError = false),
                )
            }
            mainClock.autoAdvance = false
            var frame = 0
            fun capture() {
                val img = onNodeWithTag("demo").captureToImage().toAwtImage()
                ImageIO.write(img, "png", File(out, "frame_%03d.png".format(frame++)))
            }
            mainClock.advanceTimeByFrame()
            capture()
            sentence.forEach { c ->
                for (tag in listOf("legacyField", "stateField")) {
                    onNodeWithTag(tag).performSemanticsAction(SemanticsActions.InsertTextAtCursor) {
                        it(AnnotatedString(c.toString()))
                    }
                }
                mainClock.advanceTimeBy(KEY_GAP_MS)
                capture()
            }
            repeat(6) { mainClock.advanceTimeBy(100); capture() } // let the async model settle
            File(out, "result.txt").writeText(
                "legacy field: ${legacyModel.value.text}\nstate field:  ${state.text}\nstate model:  ${stateModel.value}\n",
            )
        }
    }

    @Composable
    private fun DemoScreen(
        legacy: TextFieldValue,
        onLegacyChange: (TextFieldValue) -> Unit,
        state: TextFieldState,
        stateModelText: String,
        stateFieldState: FieldValidationState,
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .testTag("demo")
                    .width(560.dp)
                    .background(Color.White)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Same keystrokes · same async model (${MODEL_LAG_MS} ms late)",
                    style = MaterialTheme.typography.titleMedium)
                Text("Legacy: TextFieldValue hoisted through the model",
                    style = MaterialTheme.typography.labelLarge, color = Color(0xFFB3261E))
                OutlinedTextField(
                    value = legacy,
                    onValueChange = onLegacyChange,
                    label = { Text("value + onValueChange") },
                    modifier = Modifier.fillMaxWidth().testTag("legacyField"),
                )
                Text("model: \"${legacy.text}\"", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
                Text("TextFieldState: field owns the text, model observes",
                    style = MaterialTheme.typography.labelLarge, color = Color(0xFF146C2E))
                OutlinedTextField(
                    state = state,
                    label = { Text("state + textChanges()") },
                    supportingText = stateFieldState.supportingText(default = "Required, at least 3 characters"),
                    modifier = Modifier.fillMaxWidth().testTag("stateField"),
                )
                Text("model: \"$stateModelText\"", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    private companion object {
        const val KEY_GAP_MS = 10L
        const val MODEL_LAG_MS = 16L
    }
}
