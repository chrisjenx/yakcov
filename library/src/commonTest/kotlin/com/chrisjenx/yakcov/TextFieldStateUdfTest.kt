package com.chrisjenx.yakcov

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The recommended UDF shape for a field that owns a [TextFieldState] (see
 * docs/decisions/textfieldstate.md): the component owns the state, the immutable Model holds the text
 * as a `String` plus a [FieldValidationState], and the reducer folds the rules exactly as it does for
 * the TextFieldValue hoist today.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldStateUdfTest {

    private val nameRules = listOf(Required, MinLength(3))

    private data class Model(
        val name: String = "",
        val nameState: FieldValidationState,
        val submitAttempts: Int = 0,
    )

    private sealed interface Event {
        data class NameChanged(val text: String) : Event
        data object NameFocusLost : Event
        data object Submit : Event
    }

    private fun reduce(model: Model, event: Event): Model = when (event) {
        is Event.NameChanged -> model.copy(
            name = event.text,
            nameState = nameRules.toFieldState(event.text, model.nameState.showError),
        )
        Event.NameFocusLost -> model.copy(nameState = nameRules.toFieldState(model.name, showError = true))
        Event.Submit -> model.copy(
            nameState = nameRules.toFieldState(model.name, showError = true),
            submitAttempts = model.submitAttempts + 1,
        )
    }

    /** Stands in for the component: owns the editing buffer and the store. */
    private class Harness(seed: Model, private val reducer: (Model, Event) -> Model) {
        val name = TextFieldState(seed.name)
        var model by mutableStateOf(seed)
        fun dispatch(event: Event) { model = reducer(model, event) }
    }

    private fun newHarness() =
        Harness(Model(nameState = nameRules.toFieldState("", showError = false)), ::reduce)

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun stateToModelHandOff_errorsOnBlurNotWhileTyping() = runComposeUiTest {
        val h = newHarness()
        setContent {
            LaunchedEffect(h.name) { h.name.textChanges().collect { h.dispatch(Event.NameChanged(it)) } }
            Column {
                TextField(
                    state = h.name,
                    modifier = Modifier.testTag("name").validationBehavior(
                        isError = h.model.nameState.isError,
                        onFocusLost = { h.dispatch(Event.NameFocusLost) },
                    ),
                    isError = h.model.nameState.isError,
                )
                TextField(state = TextFieldState(), modifier = Modifier.testTag("other"))
            }
        }
        // The untouched required field blocks submit from frame one without showing anything.
        assertFalse(listOf(h.model.nameState).hasNoErrors())
        assertFalse(h.model.nameState.isError)

        onNodeWithTag("name").requestFocus()
        onNodeWithTag("name").performTextInput("ab")
        waitForIdle()
        assertEquals("ab", h.model.name, "the Model receives the field's text")
        assertFalse(h.model.nameState.isError, "no error while typing")

        onNodeWithTag("other").requestFocus()
        waitForIdle()
        assertTrue(h.model.nameState.isError, "error on blur")

        onNodeWithTag("name").requestFocus()
        onNodeWithTag("name").performTextInput("c")
        waitForIdle()
        assertFalse(h.model.nameState.isError, "live after blur")
        assertTrue(listOf(h.model.nameState).hasNoErrors())
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun programmaticWrite_reachesTheModelWithCaretAtEnd() = runComposeUiTest {
        val h = newHarness()
        setContent {
            LaunchedEffect(h.name) { h.name.textChanges().collect { h.dispatch(Event.NameChanged(it)) } }
            TextField(state = h.name, modifier = Modifier.testTag("name"))
        }
        // A suggestion pick / preset fill: write the state, never the Model. The flow echoes it back.
        runOnIdle { h.name.setTextAndPlaceCursorAtEnd("Suggested") }
        waitForIdle()
        assertEquals("Suggested", h.model.name)
        assertEquals(TextRange(9), h.name.selection, "caret at end — replaces asCaretEndEntry")
        assertTrue(listOf(h.model.nameState).hasNoErrors())

        runOnIdle { h.name.clearText() }
        waitForIdle()
        assertEquals("", h.model.name)
        assertFalse(listOf(h.model.nameState).hasNoErrors())
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun reducerReseed_viaVersionedSeed_doesNotFightTyping() = runComposeUiTest {
        // The reducer can't touch the state. It publishes a one-shot seed (text + version); the UI
        // applies it only when the version changes, so it never overwrites in-flight keystrokes.
        data class Seed(val text: String, val version: Int)
        val h = newHarness()
        var seed by mutableStateOf(Seed("", 0))
        setContent {
            LaunchedEffect(h.name) { h.name.textChanges().collect { h.dispatch(Event.NameChanged(it)) } }
            LaunchedEffect(seed.version) {
                if (seed.version > 0) h.name.setTextAndPlaceCursorAtEnd(seed.text)
            }
            TextField(state = h.name, modifier = Modifier.testTag("name"))
        }
        onNodeWithTag("name").performTextInput("typed")
        waitForIdle()
        assertEquals("typed", h.name.text.toString(), "an unchanged seed version never writes")

        seed = Seed("From server", 1)
        waitForIdle()
        assertEquals("From server", h.name.text.toString())
        assertEquals("From server", h.model.name)
        assertEquals(TextRange(11), h.name.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun everyInvalidSubmit_shakes_viaSubmitCounter() = runComposeUiTest {
        val h = newHarness()
        mainClock.autoAdvance = false
        setContent {
            TextField(
                state = h.name,
                modifier = Modifier.testTag("name").validationBehavior(
                    isError = h.model.nameState.isError,
                    shakeTrigger = h.model.submitAttempts,
                ),
            )
        }
        mainClock.advanceTimeByFrame()
        val rest = onNodeWithTag("name").fetchSemanticsNode().boundsInRoot.left

        fun shiftAfterSubmit(): Float {
            h.dispatch(Event.Submit)
            var max = 0f
            repeat(4) {
                mainClock.advanceTimeBy(10L)
                val shift = abs(onNodeWithTag("name").fetchSemanticsNode().boundsInRoot.left - rest)
                if (shift > max) max = shift
            }
            // let the shake settle before the next submit
            mainClock.advanceTimeBy(500L)
            return max
        }

        assertTrue(shiftAfterSubmit() > 0f, "first invalid submit shakes")
        // A second invalid submit folds to an EQUAL FieldValidationState; the counter still fires.
        assertTrue(shiftAfterSubmit() > 0f, "second invalid submit shakes too")
    }
}
