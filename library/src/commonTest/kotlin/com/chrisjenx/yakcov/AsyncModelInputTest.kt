package com.chrisjenx.yakcov

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.strings.TextFieldStateValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The reason for the TextFieldState migration: text that round-trips through a UDF model can lose
 * keystrokes and misplace pastes when the model updates *later* than the keys arrive (a reducer on
 * another dispatcher, a store hop, a slow frame). Every late model write resets the field's edit
 * buffer to stale text.
 *
 * Each scenario runs the legacy `value`/`onValueChange` hoist as a **control** (asserting it does
 * go wrong, so the harness is proven able to detect the bug) and the TextFieldState shape from
 * docs/decisions/textfieldstate.md, which must keep every keystroke: the field owns the text on the
 * Compose clock and the model only *observes* it through [textChanges].
 *
 * Timing: keys arrive every [KEY_GAP_MS]; the model applies each change [MODEL_LAG_MS] later.
 */
@OptIn(ExperimentalTestApi::class)
class AsyncModelInputTest {

    private val typed = "the quick brown fox"

    private fun ComposeUiTest.typeWithGaps(text: String) {
        text.forEach {
            onNodeWithTag(FIELD).performTextInput(it.toString())
            mainClock.advanceTimeBy(KEY_GAP_MS)
        }
    }

    private fun ComposeUiTest.settle() = mainClock.advanceTimeBy(1_000)

    // region typing

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun control_legacyHoistThroughAsyncModel_dropsKeystrokes() = runComposeUiTest {
        val model = MutableStateFlow(TextFieldValue(""))
        setContent {
            val scope = rememberCoroutineScope()
            val value by model.collectAsState()
            TextField(
                value = value,
                onValueChange = { v -> scope.launch { delay(MODEL_LAG_MS); model.value = v } },
                modifier = Modifier.testTag(FIELD),
            )
        }
        onNodeWithTag(FIELD).requestFocus()
        mainClock.autoAdvance = false
        typeWithGaps(typed)
        settle()
        assertNotEquals(typed, model.value.text, "control: the legacy hoist should lose input here")
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun textFieldState_asyncModel_keepsEveryKeystroke() = runComposeUiTest {
        val state = TextFieldState()
        val model = MutableStateFlow("")
        setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(state) {
                state.textChanges().collect { t -> scope.launch { delay(MODEL_LAG_MS); model.value = t } }
            }
            TextField(state = state, modifier = Modifier.testTag(FIELD))
        }
        onNodeWithTag(FIELD).requestFocus()
        mainClock.autoAdvance = false
        typeWithGaps(typed)
        settle()
        assertEquals(typed, state.text.toString(), "the field keeps every keystroke")
        assertEquals(typed, model.value, "and the model converges on it")
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun textFieldStateValidator_asyncObserver_keepsEveryKeystroke_andValidatesSync() = runComposeUiTest {
        val validator = TextFieldStateValidator(rules = listOf(Required, MinLength(5)))
        val model = MutableStateFlow("")
        setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(validator) {
                validator.textFieldState.textChanges()
                    .collect { t -> scope.launch { delay(MODEL_LAG_MS); model.value = t } }
            }
            with(validator) {
                TextField(
                    state = textFieldState,
                    modifier = Modifier.testTag(FIELD).validationConfig(showErrorOnInteraction = false),
                )
            }
        }
        onNodeWithTag(FIELD).requestFocus()
        mainClock.autoAdvance = false
        typeWithGaps("abc")
        // Validity is derived from the state on the same frame — it doesn't wait for the model.
        mainClock.advanceTimeByFrame()
        assertEquals("abc", validator.value)
        assertTrue(!validator.isValid, "MinLength(5) fails on 'abc' without waiting for the model")
        typeWithGaps("de")
        mainClock.advanceTimeByFrame()
        assertTrue(validator.isValid)
        settle()
        assertEquals("abcde", model.value)
    }

    // endregion

    // region paste / mid-text edit

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun control_legacyHoistThroughAsyncModel_misplacesMidTextPaste() = runComposeUiTest {
        val model = MutableStateFlow(TextFieldValue("helloworld", selection = TextRange(10)))
        setContent {
            val scope = rememberCoroutineScope()
            val value by model.collectAsState()
            TextField(
                value = value,
                onValueChange = { v -> scope.launch { delay(MODEL_LAG_MS); model.value = v } },
                modifier = Modifier.testTag(FIELD),
            )
        }
        onNodeWithTag(FIELD).requestFocus()
        mainClock.autoAdvance = false
        typeWithGaps("!!")
        onNodeWithTag(FIELD).performTextInputSelection(TextRange(5))
        mainClock.advanceTimeBy(KEY_GAP_MS)
        onNodeWithTag(FIELD).performTextInput(" big ")
        mainClock.advanceTimeBy(KEY_GAP_MS)
        typeWithGaps("x")
        settle()
        assertNotEquals("hello big xworld!!", model.value.text, "control: the legacy hoist should corrupt this")
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun textFieldState_asyncModel_midTextPasteLandsAtCaret() = runComposeUiTest {
        val state = TextFieldState("helloworld")
        val model = MutableStateFlow("helloworld")
        setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(state) {
                state.textChanges().collect { t -> scope.launch { delay(MODEL_LAG_MS); model.value = t } }
            }
            TextField(state = state, modifier = Modifier.testTag(FIELD))
        }
        onNodeWithTag(FIELD).requestFocus()
        mainClock.autoAdvance = false
        typeWithGaps("!!")
        onNodeWithTag(FIELD).performTextInputSelection(TextRange(5))
        mainClock.advanceTimeBy(KEY_GAP_MS)
        onNodeWithTag(FIELD).performTextInput(" big ") // a paste: one multi-char commit at the caret
        mainClock.advanceTimeBy(KEY_GAP_MS)
        typeWithGaps("x")
        settle()
        assertEquals("hello big xworld!!", state.text.toString())
        assertEquals(TextRange(11), state.selection, "the caret stays after the pasted+typed text")
        assertEquals("hello big xworld!!", model.value)
    }

    // endregion

    // region reducer re-seed while the model lags

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun versionedSeed_underAsyncModel_appliesOnce_thenTypingContinuesFromIt() = runComposeUiTest {
        data class Seed(val text: String, val version: Int)
        val state = TextFieldState()
        val model = MutableStateFlow("")
        var seed by mutableStateOf(Seed("", 0))
        setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(state) {
                state.textChanges().collect { t -> scope.launch { delay(MODEL_LAG_MS); model.value = t } }
            }
            LaunchedEffect(seed.version) { if (seed.version > 0) state.setTextAndPlaceCursorAtEnd(seed.text) }
            TextField(state = state, modifier = Modifier.testTag(FIELD))
        }
        onNodeWithTag(FIELD).requestFocus()
        mainClock.autoAdvance = false
        typeWithGaps("abc")
        seed = Seed("Preset ", 1) // e.g. a reducer's "fill from template", arriving mid-typing
        mainClock.advanceTimeByFrame()
        typeWithGaps("value")
        settle()
        assertEquals("Preset value", state.text.toString(), "seed applied once; lagging model never echoed back")
        assertEquals("Preset value", model.value)
    }

    // endregion

    private companion object {
        const val FIELD = "field"
        const val KEY_GAP_MS = 10L
        const val MODEL_LAG_MS = 16L
    }
}
