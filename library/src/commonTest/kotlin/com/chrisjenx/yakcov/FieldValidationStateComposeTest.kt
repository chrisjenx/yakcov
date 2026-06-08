package com.chrisjenx.yakcov

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import com.chrisjenx.yakcov.ValidationResult.Outcome
import org.jetbrains.compose.resources.stringResource
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleRequired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class FieldValidationStateComposeTest {

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun text_resolvesRegularAndResourceResults() = runComposeUiTest {
        val regular = FieldValidationState(
            severity = Outcome.ERROR, showError = true,
            result = RegularValidationResult.error("Bad value"),
        )
        val resource = FieldValidationState(
            severity = Outcome.ERROR, showError = true,
            result = ResourceValidationResult.error(Res.string.ruleRequired),
        )
        setContent {
            assertEquals("Bad value", regular.text())
            assertEquals(stringResource(Res.string.ruleRequired), resource.text())
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun supportingText_nullWhenNoMessage_nonNullWhenMessage() = runComposeUiTest {
        val empty = FieldValidationState.Pristine
        val withMsg = FieldValidationState(
            severity = Outcome.ERROR, showError = true,
            result = RegularValidationResult.error("Bad value"),
        )
        setContent {
            assertNull(empty.supportingText())
            assertNotNull(withMsg.supportingText())
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun onFocusLost_invokesCallbackWhenFocusMovesAway() = runComposeUiTest {
        var lostCount = 0
        setContent {
            Column {
                TextField(
                    value = "", onValueChange = {},
                    modifier = Modifier.testTag("a").onFocusLost { lostCount++ },
                )
                TextField(
                    value = "", onValueChange = {},
                    modifier = Modifier.testTag("b"),
                )
            }
        }
        onNodeWithTag("a").requestFocus()
        assertEquals(0, lostCount)
        onNodeWithTag("b").requestFocus()
        assertEquals(1, lostCount)
    }
}
