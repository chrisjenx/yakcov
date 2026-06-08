package com.chrisjenx.yakcov

import androidx.compose.runtime.saveable.SaverScope
import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldValidationStateTest {

    // Pure rules producing eager (non-resource) results so messages are readable without composition.
    private val requiredRule = ValueValidatorRule<String> {
        if (it.isBlank()) RegularValidationResult.error("required")
        else RegularValidationResult.success()
    }
    private val minLenRule = ValueValidatorRule<String> {
        if (it.length < 3) RegularValidationResult.warning("too short")
        else RegularValidationResult.success()
    }

    @Test
    fun singleRule_error_foldsToErrorWithMessage() {
        val state = listOf(requiredRule).toFieldState(value = "", showError = true)
        assertEquals(Outcome.ERROR, state.severity)
        assertTrue(state.isError)
        assertEquals("required", state.result?.messageOrNull())
    }

    @Test
    fun multipleRules_foldsToMostSevere() {
        val state = listOf(minLenRule, requiredRule).toFieldState(value = "", showError = true)
        assertEquals(Outcome.ERROR, state.severity)
        assertEquals("required", state.result?.messageOrNull())
    }

    @Test
    fun warningOnly_isWarningNotError() {
        val state = listOf(minLenRule).toFieldState(value = "ab", showError = true)
        assertEquals(Outcome.WARNING, state.severity)
        assertTrue(state.isWarning)
        assertFalse(state.isError)
    }

    @Test
    fun allPass_foldsToSuccess() {
        val state = listOf(requiredRule, minLenRule).toFieldState(value = "okay", showError = true)
        assertEquals(Outcome.SUCCESS, state.severity)
        assertFalse(state.isError)
    }

    @Test
    fun showErrorFalse_severityStaysButNotFlaggedError() {
        val state = listOf(requiredRule).toFieldState(value = "", showError = false)
        assertEquals(Outcome.ERROR, state.severity)
        assertFalse(state.isError)
    }

    @Test
    fun emptyRules_foldsToPristine() {
        val state = emptyList<ValueValidatorRule<String>>().toFieldState(value = "x", showError = true)
        assertEquals(FieldValidationState.Pristine, state)
    }

    @Test
    fun hasNoErrors_trueWhenNoError_falseWhenAnyError() {
        val ok = listOf(requiredRule).toFieldState(value = "okay", showError = true)
        val bad = listOf(requiredRule).toFieldState(value = "", showError = true)
        assertTrue(listOf(ok).hasNoErrors())
        assertFalse(listOf(ok, bad).hasNoErrors())
    }

    @Test
    fun hasNoErrors_checksSeverityNotShowError() {
        // An ERROR-severity field that hasn't been revealed (showError=false) still counts as an
        // error for the submit check — hasNoErrors looks at severity, not the revealed flag.
        val hiddenError = listOf(requiredRule).toFieldState(value = "", showError = false)
        assertFalse(hiddenError.isError)            // not flagged to the user yet…
        assertFalse(listOf(hiddenError).hasNoErrors()) // …but submit must still see it as invalid
    }

    @Test
    fun equality_includesResult_soMessageChangesPropagate() {
        // Same severity + showError but different message MUST be unequal, else mutableStateOf /
        // distinctUntilChanged would elide the update and show a stale error message.
        val a = FieldValidationState(Outcome.ERROR, true, RegularValidationResult.error("min 6"))
        val b = FieldValidationState(Outcome.ERROR, true, RegularValidationResult.error("no spaces"))
        assertNotEquals(a, b)
        // …and structurally-identical results stay equal (no spurious churn on no-op re-folds).
        val c = FieldValidationState(Outcome.ERROR, true, RegularValidationResult.error("min 6"))
        assertEquals(a, c)
    }

    @Test
    fun serialization_dropsTransientResult_keepsSeverityAndShowError() {
        val original = FieldValidationState(
            severity = Outcome.ERROR,
            showError = true,
            result = RegularValidationResult.error("required"),
        )
        val json = Json.encodeToString(FieldValidationState.serializer(), original)
        val restored = Json.decodeFromString(FieldValidationState.serializer(), json)
        assertEquals(Outcome.ERROR, restored.severity)
        assertTrue(restored.showError)
        assertNull(restored.result) // @Transient — not serialized; recomputed after reconstruct+revalidate
        assertTrue(restored.isError)
    }

    @Test
    fun saver_roundTrips_keepsSeverityAndShowError() {
        val original = FieldValidationState(Outcome.WARNING, showError = true)
        val scope = SaverScope { true }
        val saved = with(FieldValidationState.Saver) { scope.save(original) }
        val restored = FieldValidationState.Saver.restore(saved!!)
        assertEquals(Outcome.WARNING, restored?.severity)
        assertTrue(restored!!.showError)
        assertNull(restored.result)
    }
}
