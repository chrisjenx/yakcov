package com.chrisjenx.yakcov

import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun onFocusLost_revealsError() {
        val v = newValidator()
        v.onValueChange("")
        v.onFocusLost()
        assertTrue(v.state.showError)
        assertTrue(v.state.isError)
    }

    @Test
    fun validate_revealsError() {
        val v = newValidator()
        v.validate()
        assertTrue(v.state.showError)
        assertTrue(v.state.isError)
    }

    @Test
    fun showError_isStickyAcrossSubsequentChanges() {
        val v = newValidator()
        v.onFocusLost()
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
        v.onFocusLost()
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
    fun listHelpers_validate() {
        val a = newValidator()
        val b = FieldValidator(initial = "ok", rules = listOf(requiredRule))
        // validate() reveals first, so an untouched-but-empty required field cannot masquerade as valid
        assertFalse(listOf(a, b).validate())
        assertTrue(a.state.showError) // revealed as a side effect
        a.onValueChange("now ok")
        assertTrue(listOf(a, b).validate())
    }

    @Test
    fun observer_valueChanged_firesWithAfterValueAndState() {
        val events = mutableListOf<FieldValidatorEvent<String>>()
        val v = FieldValidator(
            initial = "", rules = listOf(requiredRule),
            observer = { events += it },
        )
        v.onValueChange("hello")
        val e = events.single()
        assertIs<FieldValidatorEvent.ValueChanged<String>>(e)
        assertEquals("hello", e.value)
        assertEquals(Outcome.SUCCESS, e.state.severity)
        assertFalse(e.state.showError)
    }

    @Test
    fun observer_validated_firesOnFocusLostValidateAndListHelpers() {
        val events = mutableListOf<FieldValidatorEvent<String>>()
        val v = FieldValidator("", listOf(requiredRule), observer = { events += it })
        v.onFocusLost()
        v.validate()
        listOf(v).validate() // reveals first -> third Validated
        assertEquals(3, events.size)
        assertTrue(events.all { it is FieldValidatorEvent.Validated })
        assertTrue(events.all { it.state.showError })
    }

    @Test
    fun observer_reset_firesOnBothOverloads() {
        val events = mutableListOf<FieldValidatorEvent<String>>()
        val v = FieldValidator("seed", listOf(requiredRule), observer = { events += it })
        v.reset()
        v.reset("other")
        assertEquals(2, events.size)
        val first = events[0]
        assertIs<FieldValidatorEvent.Reset<String>>(first)
        assertEquals("seed", first.value)
        val second = events[1]
        assertIs<FieldValidatorEvent.Reset<String>>(second)
        assertEquals("other", second.value)
        assertEquals(FieldValidationState.Pristine, second.state)
    }

    @Test
    fun observer_noEventAtConstruction_evenWithInitialValidate() {
        val events = mutableListOf<FieldValidatorEvent<String>>()
        FieldValidator("", listOf(requiredRule), initialValidate = true, observer = { events += it })
        assertTrue(events.isEmpty())
    }

    @Test
    fun observer_readsConsistentCommittedPair() {
        var validator: FieldValidator<String>? = null
        var checked = 0
        val v = FieldValidator<String>(
            initial = "", rules = listOf(requiredRule),
            observer = { e ->
                val owner = validator!!
                assertEquals(owner.value, e.value)
                assertEquals(owner.state, e.state)
                checked++
            },
        )
        validator = v
        v.onValueChange("x")
        v.validate()
        v.reset()
        assertEquals(3, checked)
    }
}
