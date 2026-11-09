package com.chrisjenx.yakcov.strings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import com.chrisjenx.yakcov.AndroidJUnitIgnore
import com.chrisjenx.yakcov.JSIgnore
import com.chrisjenx.yakcov.ValueValidator
import com.chrisjenx.yakcov.onFocusCursorToEnd
import com.chrisjenx.yakcov.textChanges
import com.chrisjenx.yakcov.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TextFieldStateValidatorTest {

    // region pure — no composition

    @Test
    fun pristineRequiredField_isNotValidAndShowsNoError() {
        val validator = TextFieldStateValidator(rules = listOf(Required))
        assertFalse(validator.isValid)
        assertFalse(validator.isError())
    }

    @Test
    fun initialValidate_blankAcceptingRuleIsValidFromTheStart() {
        // An optional Email: "" passes, and initialValidate keeps it from blocking a form untouched.
        val validator = TextFieldStateValidator(rules = listOf(Email), initialValidate = true)
        assertTrue(validator.isValid)
        assertFalse(validator.isError())
    }

    @Test
    fun withoutInitialValidate_blankAcceptingRuleStillStartsNotValid() {
        // Parity with TextFieldValueValidator: Initial state is not-valid until interaction.
        val validator = TextFieldStateValidator(rules = listOf(Email))
        assertFalse(validator.isValid)
    }

    @Test
    fun rulesReadTheStateDirectly_noOnValueChangeNeeded() {
        val state = TextFieldState("")
        val validator = TextFieldStateValidator(state, rules = listOf(Required), initialValidate = true)
        assertFalse(validator.isValid)
        state.setTextAndPlaceCursorAtEnd("hi")
        assertTrue(validator.isValid)
        assertEquals("hi", validator.value)
    }

    @Test
    fun submit_revealsErrorOnUntouchedField() {
        val validator = TextFieldStateValidator(rules = listOf(Required))
        assertFalse(validator.validate())
        assertTrue(validator.isError())
    }

    @Test
    fun valueWrite_replacesTextAndPlacesCaretAtEnd() {
        val state = TextFieldState("old text", initialSelection = TextRange(0))
        val validator = TextFieldStateValidator(state)
        validator.value = "new"
        assertEquals("new", state.text.toString())
        assertEquals(TextRange(3), state.selection)
    }

    @Test
    fun seededState_startsWithCaretAtEnd() {
        // The decision record relies on this: an edit prefill needs no TextRange bookkeeping.
        val validator = TextFieldStateValidator(initialText = "prefill")
        assertEquals(TextRange(7), validator.textFieldState.selection)
    }

    @Test
    fun validateWithText_writesAndShowsErrors() {
        val validator = TextFieldStateValidator(rules = listOf(Required, MinLength(3)))
        assertFalse(validator.validate("ab"))
        assertEquals("ab", validator.textFieldState.text.toString())
        assertTrue(validator.isError())
        assertTrue(validator.validate("abc"))
        assertFalse(validator.isError())
    }

    @Test
    fun mixesWithTextFieldValueValidatorInOneSubmit() {
        // Module-by-module migration: both validator kinds in the same form submit.
        val legacy = TextFieldValueValidator(value = "ok", rules = listOf(Required))
        val migrated = TextFieldStateValidator(rules = listOf(Required))
        val form: List<ValueValidator<*, *>> = listOf(legacy, migrated)
        assertFalse(form.validate())
        assertTrue(migrated.isError())
        migrated.value = "now ok"
        assertTrue(form.validate())
    }

    // endregion

    // region composed — error timing driven by observing the state

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun typing_startsValidation_withoutOnValueChange() = runComposeUiTest {
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Required, MinLength(3)))
            TextField(state = validator.textFieldState, modifier = Modifier.testTag("a"))
        }
        assertFalse(validator.isValid)
        onNodeWithTag("a").performTextInput("abc")
        waitForIdle()
        assertTrue(validator.isValid, "typing must move a pristine field to validating")
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun freshField_showsNoErrorWhileTyping_thenOnBlur_thenLive() = runComposeUiTest {
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Required, MinLength(3)))
            Column {
                with(validator) {
                    TextField(
                        state = textFieldState,
                        modifier = Modifier.testTag("a").validationConfig(
                            validateOnFocusLost = true,
                            showErrorOnInteraction = false,
                        ),
                        isError = isError(),
                    )
                }
                TextField(state = TextFieldState(), modifier = Modifier.testTag("b"))
            }
        }
        onNodeWithTag("a").requestFocus()
        onNodeWithTag("a").performTextInput("ab")
        waitForIdle()
        assertFalse(validator.isValid)
        assertFalse(validator.isError(), "no error while a fresh field is being typed")

        onNodeWithTag("b").requestFocus()
        waitForIdle()
        assertTrue(validator.isError(), "error shows on blur")

        onNodeWithTag("a").requestFocus()
        onNodeWithTag("a").performTextInput("c")
        waitForIdle()
        assertFalse(validator.isError(), "after blur the error tracks edits live")
        assertTrue(validator.isValid)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun observerRunsFromValidationConfig_forAConstructedValidator() = runComposeUiTest {
        // A validator held outside composition (e.g. on a component) is constructed directly, not
        // remembered; validationConfig must still start the observer.
        val validator = TextFieldStateValidator(rules = listOf(Required))
        setContent {
            with(validator) {
                TextField(
                    state = textFieldState,
                    modifier = Modifier.testTag("a").validationConfig(showErrorOnInteraction = false),
                )
            }
        }
        onNodeWithTag("a").performTextInput("x")
        waitForIdle()
        assertTrue(validator.isValid)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun programmaticWriteThroughValidator_isNotInteraction() = runComposeUiTest {
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Required))
            TextField(state = validator.textFieldState, modifier = Modifier.testTag("a"))
        }
        runOnIdle { validator.value = "seeded" }
        waitForIdle()
        // Same as assigning TextFieldValueValidator.value: the field stays pristine.
        assertFalse(validator.isValid)
        assertFalse(validator.isError())
        assertEquals(TextRange(6), validator.textFieldState.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun directStateWrite_countsAsInteraction() = runComposeUiTest {
        // A suggestion pick written straight to the state behaves like the user typed it.
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Required))
            TextField(state = validator.textFieldState, modifier = Modifier.testTag("a"))
        }
        runOnIdle { validator.textFieldState.setTextAndPlaceCursorAtEnd("picked") }
        waitForIdle()
        assertTrue(validator.isValid)
        assertEquals(TextRange(6), validator.textFieldState.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun reset_returnsToPristine_andTheObserverDoesNotUndoIt() = runComposeUiTest {
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Required, MinLength(3)))
            TextField(state = validator.textFieldState, modifier = Modifier.testTag("a"))
        }
        onNodeWithTag("a").performTextInput("ab")
        waitForIdle()
        assertTrue(validator.isError())
        runOnIdle {
            validator.value = ""
            validator.reset()
        }
        waitForIdle()
        assertFalse(validator.isError())
        assertFalse(validator.isValid)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun initialValidate_composed_blankAcceptingRuleStaysValidAndQuiet() = runComposeUiTest {
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Email), initialValidate = true)
            TextField(state = validator.textFieldState, modifier = Modifier.testTag("a"))
        }
        waitForIdle()
        assertTrue(validator.isValid)
        assertFalse(validator.isError())
    }

    // endregion

    // region shake

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun invalidSubmit_shakesTheField() = runComposeUiTest {
        val validator = TextFieldStateValidator(rules = listOf(Required))
        mainClock.autoAdvance = false
        setContent {
            with(validator) {
                TextField(
                    state = textFieldState,
                    modifier = Modifier.testTag("a").validationConfig(shakeOnInvalid = true),
                )
            }
        }
        mainClock.advanceTimeByFrame()
        val rest = onNodeWithTag("a").fetchSemanticsNode().boundsInRoot.left
        runOnIdle { validator.validate() }
        var maxShift = 0f
        repeat(4) {
            mainClock.advanceTimeBy(10L)
            val shift = onNodeWithTag("a").fetchSemanticsNode().boundsInRoot.left - rest
            if (kotlin.math.abs(shift) > maxShift) maxShift = kotlin.math.abs(shift)
        }
        assertTrue(maxShift > 0f, "an invalid submit must shake the field, max shift = $maxShift")
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun validSubmit_doesNotShake() = runComposeUiTest {
        val validator = TextFieldStateValidator(initialText = "ok", rules = listOf(Required))
        mainClock.autoAdvance = false
        setContent {
            with(validator) {
                TextField(
                    state = textFieldState,
                    modifier = Modifier.testTag("a").validationConfig(shakeOnInvalid = true),
                )
            }
        }
        mainClock.advanceTimeByFrame()
        val rest = onNodeWithTag("a").fetchSemanticsNode().boundsInRoot.left
        runOnIdle { assertTrue(validator.validate()) }
        repeat(4) {
            mainClock.advanceTimeBy(10L)
            assertEquals(rest, onNodeWithTag("a").fetchSemanticsNode().boundsInRoot.left)
        }
    }

    // endregion

    // region caret

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun onFocusCursorToEnd_movesCaretToEndOnFocus() = runComposeUiTest {
        val state = TextFieldState("hello", initialSelection = TextRange(0))
        setContent {
            TextField(state = state, modifier = Modifier.testTag("a").onFocusCursorToEnd(state = state))
        }
        onNodeWithTag("a").requestFocus()
        waitForIdle()
        assertEquals(TextRange(5), state.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun onFocusCursorToEnd_highlightSelectsAll() = runComposeUiTest {
        val state = TextFieldState("hello", initialSelection = TextRange(0))
        setContent {
            TextField(
                state = state,
                modifier = Modifier.testTag("a").onFocusCursorToEnd(state = state, highlight = true),
            )
        }
        onNodeWithTag("a").requestFocus()
        waitForIdle()
        assertEquals(TextRange(0, 5), state.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun validatorMember_onFocusCursorToEnd() = runComposeUiTest {
        val validator = TextFieldStateValidator(TextFieldState("hello", initialSelection = TextRange(1)))
        setContent {
            with(validator) {
                TextField(state = textFieldState, modifier = Modifier.testTag("a").onFocusCursorToEnd())
            }
        }
        onNodeWithTag("a").requestFocus()
        waitForIdle()
        assertEquals(TextRange(5), validator.textFieldState.selection)
    }

    // endregion

    // region transformations — what text do the rules see?

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun inputTransformation_rulesSeeTheTransformedText() = runComposeUiTest {
        val digitsAndDot = InputTransformation.byValue { _, proposed ->
            proposed.filter { it.isDigit() || it == '.' }
        }
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Required, Decimal))
            TextField(
                state = validator.textFieldState,
                inputTransformation = digitsAndDot,
                modifier = Modifier.testTag("a"),
            )
        }
        onNodeWithTag("a").performTextInput("12a.5")
        waitForIdle()
        assertEquals("12.5", validator.value)
        assertTrue(validator.isValid)
    }

    @Test
    fun inputTransformation_isBypassedByProgrammaticWrites() {
        // InputTransformation only filters user edits: a preset must be normalised by the writer.
        val validator = TextFieldStateValidator(rules = listOf(Decimal), initialValidate = true)
        validator.value = "12a"
        assertEquals("12a", validator.value)
        assertFalse(validator.isValid)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun outputTransformation_rulesSeeTheStoredNotTheDisplayedText() = runComposeUiTest {
        // Display-only pad to two decimals: "12.5" is shown as "12.50" but stored as "12.5".
        val padCents = OutputTransformation {
            val dot = asCharSequence().indexOf('.')
            if (dot >= 0 && length - dot - 1 == 1) insert(length, "0")
        }
        lateinit var validator: TextFieldStateValidator
        setContent {
            validator = rememberTextFieldStateValidator(rules = listOf(Required, MaxLength(4)))
            TextField(
                state = validator.textFieldState,
                outputTransformation = padCents,
                modifier = Modifier.testTag("a"),
            )
        }
        onNodeWithTag("a").performTextInput("12.5")
        waitForIdle()
        onNodeWithTag("a").assertTextEquals("12.50")
        assertEquals("12.5", validator.value)
        assertTrue(validator.isValid, "MaxLength(4) passes on the stored 4-char text")
    }

    // endregion

    // region textChanges — the UDF hand-off

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun textChanges_emitsStringsAndIgnoresSelection() = runComposeUiTest {
        val state = TextFieldState("")
        val seen = mutableListOf<String>()
        setContent {
            LaunchedEffect(state) { state.textChanges().collect { seen += it } }
            TextField(state = state, modifier = Modifier.testTag("a"))
        }
        onNodeWithTag("a").performTextInput("ab")
        waitForIdle()
        val beforeCaretMove = seen.toList()
        runOnIdle { state.edit { selection = TextRange(0) } }
        waitForIdle()
        assertEquals("", seen.first(), "emits the current text first")
        assertEquals("ab", seen.last())
        assertEquals(beforeCaretMove, seen, "a caret move must not emit")
        assertTrue(seen.zipWithNext().none { (a, b) -> a == b }, "no consecutive duplicates: $seen")
    }

    // endregion
}
