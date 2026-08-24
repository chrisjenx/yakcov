package com.chrisjenx.yakcov

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.chrisjenx.yakcov.strings.TextFieldValueValidator
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ModifiersCursorTest {

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun movesCursorToEndOnFocusGain() = runComposeUiTest {
        var field by mutableStateOf(TextFieldValue("hello", selection = TextRange(0)))
        setContent {
            TextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier
                    .testTag("a")
                    .onFocusCursorToEnd(value = field, onValueChange = { field = it }),
            )
        }
        onNodeWithTag("a").requestFocus()
        waitForIdle()
        assertEquals(TextRange(5), field.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun highlightSelectsWholeText() = runComposeUiTest {
        var field by mutableStateOf(TextFieldValue("hello", selection = TextRange(0)))
        setContent {
            TextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier
                    .testTag("a")
                    .onFocusCursorToEnd(
                        value = field,
                        onValueChange = { field = it },
                        highlight = true,
                    ),
            )
        }
        onNodeWithTag("a").requestFocus()
        waitForIdle()
        // Reversed range is a deliberate workaround to land the cursor at the end.
        assertEquals(TextRange(5, 0), field.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun usesLiveValueNotCompositionTimeValue() = runComposeUiTest {
        // Guards the rememberUpdatedState requirement: the deferred write must see the
        // latest text, not the value captured when the modifier was created.
        var field by mutableStateOf(TextFieldValue("", selection = TextRange(0)))
        setContent {
            Column {
                TextField(
                    value = field,
                    onValueChange = { field = it },
                    modifier = Modifier
                        .testTag("a")
                        .onFocusCursorToEnd(value = field, onValueChange = { field = it }),
                )
                TextField(value = "", onValueChange = {}, modifier = Modifier.testTag("b"))
            }
        }
        onNodeWithTag("b").requestFocus()
        field = field.copy(text = "abcd")
        waitForIdle()
        onNodeWithTag("a").requestFocus()
        waitForIdle()
        assertEquals(TextRange(4), field.selection)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun memberOverloadStillMovesCursorToEnd() = runComposeUiTest {
        val validator = TextFieldValueValidator(value = "hello")
        setContent {
            with(validator) {
                TextField(
                    value = validator.value,
                    onValueChange = { validator.value = it },
                    modifier = Modifier.testTag("a").onFocusCursorToEnd(),
                )
            }
        }
        onNodeWithTag("a").requestFocus()
        waitForIdle()
        assertEquals(TextRange(5), validator.value.selection)
    }
}
