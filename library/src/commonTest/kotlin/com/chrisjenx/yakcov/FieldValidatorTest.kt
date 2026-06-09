package com.chrisjenx.yakcov

import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldValidatorTest {

    private val requiredRule = ValueValidatorRule<String> {
        if (it.isBlank()) RegularValidationResult.error("required")
        else RegularValidationResult.success()
    }

    private fun newValidator(initialValidate: Boolean = false) =
        FieldValidator(initial = "", rules = listOf(requiredRule), initialValidate = initialValidate)

    @Test
    fun freshValidator_isPristine() {
        val v = newValidator()
        assertEquals("", v.value)
        assertEquals(FieldValidationState.Pristine, v.state)
        assertFalse(v.state.isError)
    }

    @Test
    fun onValueChange_validatesSilently_doesNotFlagErrorBeforeReveal() {
        val v = newValidator()
        v.onValueChange("")
        assertEquals(Outcome.ERROR, v.state.severity)
        assertFalse(v.state.showError)
        assertFalse(v.state.isError)
    }

    @Test
    fun onValueChange_updatesValue() {
        val v = newValidator()
        v.onValueChange("hello")
        assertEquals("hello", v.value)
    }

    @Test
    fun onBlur_revealsError() {
        val v = newValidator()
        v.onValueChange("")
        v.onBlur()
        assertTrue(v.state.showError)
        assertTrue(v.state.isError)
    }

    @Test
    fun reveal_revealsError() {
        val v = newValidator()
        v.reveal()
        assertTrue(v.state.showError)
        assertTrue(v.state.isError)
    }

    @Test
    fun showError_isStickyAcrossSubsequentChanges() {
        val v = newValidator()
        v.onBlur()
        v.onValueChange("ok")
        assertEquals(Outcome.SUCCESS, v.state.severity)
        assertTrue(v.state.showError)
        assertFalse(v.state.isError)
        v.onValueChange("")
        assertTrue(v.state.isError)
    }

    @Test
    fun reset_reseedsToInitialAndClearsState() {
        val v = newValidator()
        v.onValueChange("typed")
        v.onBlur()
        v.reset()
        assertEquals("", v.value) // re-seeded to initial
        assertEquals(FieldValidationState.Pristine, v.state)
        assertFalse(v.state.isError)
    }

    @Test
    fun resetWithValue_reseedsToGivenValue() {
        val v = newValidator()
        v.reset("seeded")
        assertEquals("seeded", v.value)
        assertEquals(FieldValidationState.Pristine, v.state)
    }

    @Test
    fun reset_honorsInitialValidate() {
        val v = newValidator(initialValidate = true)
        v.onValueChange("ok")
        v.reset()
        assertEquals("", v.value)
        assertTrue(v.state.showError)        // initialValidate re-reveals on reset
        assertTrue(v.state.isError)
    }

    @Test
    fun resetWithValue_honorsInitialValidate() {
        val v = newValidator(initialValidate = true)
        v.reset("ok")                        // re-seed to a valid value, initialValidate on
        assertEquals("ok", v.value)
        assertTrue(v.state.showError)        // revealed because initialValidate
        assertFalse(v.state.isError)         // …but "ok" passes, so not an error
        v.reset("")                          // re-seed to an invalid value
        assertEquals("", v.value)
        assertTrue(v.state.isError)          // validated + revealed against the new seed
    }

    @Test
    fun initialValidate_revealsAtConstruction() {
        val v = newValidator(initialValidate = true)
        assertTrue(v.state.showError)
        assertEquals(Outcome.ERROR, v.state.severity)
        assertTrue(v.state.isError)
    }

    @Test
    fun listHelpers_revealAndAllValid() {
        val a = newValidator()
        val b = FieldValidator(initial = "ok", rules = listOf(requiredRule))
        // allValid reveals first, so an untouched-but-empty required field cannot masquerade as valid
        assertFalse(listOf(a, b).allValid())
        assertTrue(a.state.showError) // revealed as a side effect
        a.onValueChange("now ok")
        assertTrue(listOf(a, b).allValid())
    }
}
