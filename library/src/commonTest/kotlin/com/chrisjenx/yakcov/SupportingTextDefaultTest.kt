package com.chrisjenx.yakcov

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class SupportingTextDefaultTest {

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun fieldState_text_fallsBackToDefault_whenNoMessage() = runComposeUiTest {
        setContent {
            // Pristine field has no message, so the default hint shows.
            assertEquals("hint", FieldValidationState.Pristine.text(default = "hint"))
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun fieldState_text_prefersMessageOverDefault() = runComposeUiTest {
        val withMsg = FieldValidationState(
            severity = Outcome.ERROR, showError = true,
            result = RegularValidationResult.error("Bad value"),
        )
        setContent {
            assertEquals("Bad value", withMsg.text(default = "hint"))
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun fieldState_text_noDefault_isNullWhenNoMessage() = runComposeUiTest {
        // backward compatible: no default => null
        setContent {
            assertNull(FieldValidationState.Pristine.text())
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun fieldState_supportingText_fallsBackToDefault() = runComposeUiTest {
        setContent {
            assertNotNull(FieldValidationState.Pristine.supportingText(default = "hint"))
            assertNull(FieldValidationState.Pristine.supportingText())
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun fieldState_supportingText_rendersDefaultHint() = runComposeUiTest {
        setContent {
            FieldValidationState.Pristine.supportingText(default = "hint")?.invoke()
        }
        onNodeWithText("hint").assertIsDisplayed()
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun fieldState_supportingText_rendersMessageNotDefault() = runComposeUiTest {
        val withMsg = FieldValidationState(
            severity = Outcome.ERROR, showError = true,
            result = RegularValidationResult.error("Bad value"),
        )
        setContent {
            withMsg.supportingText(default = "hint")?.invoke()
        }
        // message wins over the default (string-level precedence is pinned by
        // fieldState_text_prefersMessageOverDefault)
        onNodeWithText("Bad value").assertIsDisplayed()
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun validator_supportingText_fallsBackToDefault_whenNoMessage() = runComposeUiTest {
        val validator = object : ValueValidator<String, String>(
            state = mutableStateOf(""),
            rules = emptyList(),
            validateMapper = { validate(it) }
        ) {}
        setContent {
            assertNull(validator.supportingText())
            assertNotNull(validator.supportingText(default = "hint"))
        }
    }
}
