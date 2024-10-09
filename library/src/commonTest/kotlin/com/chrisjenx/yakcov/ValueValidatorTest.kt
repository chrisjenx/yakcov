package com.chrisjenx.yakcov

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.chrisjenx.yakcov.strings.Required
import org.jetbrains.compose.resources.stringResource
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleRequired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ValueValidatorTest {

    private val noRulesValueValidator = object : ValueValidator<String, String>(
        state = mutableStateOf(""),
        rules = emptyList(),
        validateMapper = { validate(it) }
    ) {}

    private val ruleValueValidator = object : ValueValidator<String, String>(
        state = mutableStateOf(""),
        rules = listOf(Required),
        validateMapper = { validate(it) }
    ) {}


    @Test
    fun noRulesValueValidator() {
        assertEquals(
            expected = ValueValidator.InternalState.Validating(),
            noRulesValueValidator.internalState,
            "internalState should be Validating"
        )
        assertTrue(noRulesValueValidator.isValid, "isValid should be true")
        assertEquals(
            expected = 0, noRulesValueValidator.validationResults.size,
            "Should have no validation results"
        )
        assertFalse(
            noRulesValueValidator.isError(),
            "isError should be false"
        )
        assertEquals(
            expected = ValidationResult.Outcome.SUCCESS,
            noRulesValueValidator.outcome(),
            "outcome should be SUCCESS"
        )
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun noRulesValueValidatorCompose() = runComposeUiTest {
        setContent {
            assertEquals(
                expected = null, noRulesValueValidator.getValidationResultString(),
                "format should be null"
            )
            assertEquals(
                expected = null, noRulesValueValidator.supportingText(),
                "supportingText should be null"
            )
        }
    }

    @Test
    fun rulesValueValidator_notValidating() {
        assertEquals(
            expected = ValueValidator.InternalState.Initial,
            ruleValueValidator.internalState,
            "internalState should be Initial"
        )
        assertFalse(
            ruleValueValidator.isValid,
            "isValid should be false"
        )
        assertEquals(
            expected = 1, ruleValueValidator.validationResults.size,
            "Should have 1 validation results"
        )
        assertFalse(
            ruleValueValidator.isError(),
            "isError should be false"
        )
        assertNull(
            ruleValueValidator.outcome(),
            "outcome should be null"
        )
    }

    @Test
    fun rulesValueValidator_validating() {
        ruleValueValidator.validate(value = null) // Start validating
        assertEquals(
            expected = ValueValidator.InternalState.Validating(),
            ruleValueValidator.internalState,
            "internalState should be Validating"
        )
        assertFalse(
            ruleValueValidator.isValid,
            "isValid should be false"
        )
        assertEquals(
            expected = 1, ruleValueValidator.validationResults.size,
            "Should have 1 validation results"
        )
        assertTrue(
            ruleValueValidator.isError(),
            "isError should be true"
        )
        assertEquals(
            expected = ValidationResult.Outcome.ERROR,
            ruleValueValidator.outcome(),
            "outcome should be ERROR"
        )
    }

    @Test
    fun rulesValueValidator_valid() {
        ruleValueValidator.validate(value = "Value") // Start validating
        assertEquals(
            expected = ValueValidator.InternalState.Validating(),
            ruleValueValidator.internalState,
            "internalState should be Validating"
        )
        assertTrue(
            ruleValueValidator.isValid,
            "isValid should be true"
        )
        assertEquals(
            expected = 1, ruleValueValidator.validationResults.size,
            "Should have 1 validation results"
        )
        assertFalse(
            ruleValueValidator.isError(),
            "isError should be false"
        )
        assertEquals(
            expected = ValidationResult.Outcome.SUCCESS,
            ruleValueValidator.outcome(),
            "outcome should be SUCCESS"
        )
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun rulesValueValidatorCompose_notValidating() = runComposeUiTest {
        setContent {
            assertEquals(
                expected = stringResource(Res.string.ruleRequired),
                ruleValueValidator.getValidationResultString(),
                "getValidationResultString should be Required"
            )
            assertEquals(
                expected = null, ruleValueValidator.supportingText(),
                "supportingText should be null"
            )
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun rulesValueValidatorCompose_validating() = runComposeUiTest {
        ruleValueValidator.validate(value = null) // Start validating
        setContent {
            assertEquals(
                expected = stringResource(Res.string.ruleRequired),
                ruleValueValidator.getValidationResultString(),
                "getValidationResultString should be Required"
            )
            assertNotNull(
                ruleValueValidator.supportingText(),
                "supportingText should not be null"
            )
        }
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun rulesValueValidatorCompose_success() = runComposeUiTest {
        // Note, these might change as we want to support, alwaysShowRules, which means SUCCESS will
        //  need to return the rule message.
        ruleValueValidator.validate(value = "Value") // Start validating
        setContent {
            assertNull(
                ruleValueValidator.getValidationResultString(),
                "getValidationResultString should be Required"
            )
            assertNull(
                ruleValueValidator.supportingText(),
                "supportingText should be null"
            )
        }
    }

}
